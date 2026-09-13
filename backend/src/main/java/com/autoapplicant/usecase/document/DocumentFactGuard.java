package com.autoapplicant.usecase.document;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Deterministic, model-free fact gate. Extracts metric-like claims (percentages,
 * currency figures, multipliers, and "&lt;number&gt; &lt;metric-noun&gt;" counts) from generated
 * text and flags any that are not supported by the source career profile — catching
 * invented or inflated numbers at zero token cost, complementing the LLM reviewer loop.
 *
 * <p>Ported from an external reference implementation's {@code verify-cv-facts.mjs} (metric-claim half only). The
 * normalization is deliberately symmetric — the same extraction runs over the generated
 * document AND the source, so folding digits or stripping thousands separators can only
 * ever surface MORE claims on both sides, never hide one. Employer/title extraction from
 * the original is intentionally omitted: it is tuned to English CV-bullet phrasing and
 * would false-positive on Danish cover-letter prose.
 */
@Service
public class DocumentFactGuard {

    /**
     * Nouns a bare number is allowed to count. A claim needs one of these to be extracted.
     * Danish nouns are included because the default output language is Danish — without them a
     * fabricated Danish count ("500 brugere", "3 års erfaring") would slip past the COUNT gate.
     */
    private static final List<String> METRIC_NOUNS = List.of(
            // English
            "users", "customers", "clients", "employees", "engineers", "teams", "companies",
            "partners", "organizations", "organisations", "brands", "countries",
            "hours", "days", "weeks", "months", "years", "minutes", "seconds",
            // Abbreviations people actually type. Without these, a user writing "40 min" and a
            // model expanding it to "40 minutes" reads as a fabricated figure.
            "min", "mins", "hr", "hrs", "sec", "secs", "yr", "yrs", "wk", "wks", "mo", "mos",
            "requests", "tokens", "documents", "workflows", "pipelines", "agents",
            "interviews", "applications", "offers", "reports", "cvs", "resumes",
            "enrollments", "enrolments", "completions", "courses", "certifications",
            "certificates", "sessions", "responses", "surveys", "cohorts",
            "commits", "contributions", "repositories", "repos", "modules", "tools",
            "servers", "guides", "articles", "datasets", "examples", "deployments",
            "services", "downloads", "stars", "lines", "projects", "integrations", "tests",
            "developers", "participants", "visitors", "industries", "tasks", "positions",
            // Danish
            "brugere", "kunder", "medarbejdere", "udviklere", "ingeniører", "teams",
            "virksomheder", "partnere", "organisationer", "lande", "brancher",
            "timer", "dage", "uger", "måneder", "år", "års", "minutter", "sekunder",
            "anmodninger", "dokumenter", "ansøgninger", "tilbud", "rapporter",
            "kurser", "certificeringer", "certifikater", "sessioner", "svar",
            "deltagere", "besøgende", "projekter", "integrationer", "servere",
            "artikler", "vejledninger", "datasæt", "eksempler", "moduler", "værktøjer",
            "commits", "bidrag", "repositorier", "tjenester", "linjer", "opgaver", "stillinger",
            "ansatte", "kollegaer", "kolleger");

    private static final Map<String, String> NOUN_SYNONYMS = Map.ofEntries(
            Map.entry("min", "minutes"), Map.entry("mins", "minutes"),
            Map.entry("hr", "hours"), Map.entry("hrs", "hours"),
            Map.entry("sec", "seconds"), Map.entry("secs", "seconds"),
            Map.entry("yr", "years"), Map.entry("yrs", "years"),
            Map.entry("wk", "weeks"), Map.entry("wks", "weeks"),
            Map.entry("mo", "months"), Map.entry("mos", "months"),
            Map.entry("repos", "repositories"), Map.entry("enrolments", "enrollments"),
            Map.entry("organisations", "organizations"), Map.entry("cvs", "resumes"),
            Map.entry("certificates", "certifications"), Map.entry("articles", "guides"),
            Map.entry("developers", "engineers"), Map.entry("organisationer", "organizations"),
            // ── Danish → English canonical ──────────────────────────────────────────────────
            // The profile is frequently English while the letter is Danish (or the reverse), so a
            // truthful "16.000 brugere" citing "16,000 users" must not read as a fabrication. Both
            // languages fold to one token; the canonical side is English only because it is the
            // spelling the rest of the list already uses.
            Map.entry("brugere", "users"), Map.entry("kunder", "customers"),
            Map.entry("medarbejdere", "employees"), Map.entry("ansatte", "employees"),
            Map.entry("kollegaer", "employees"), Map.entry("kolleger", "employees"),
            Map.entry("udviklere", "engineers"), Map.entry("ingeniører", "engineers"),
            Map.entry("virksomheder", "companies"), Map.entry("partnere", "partners"),
            Map.entry("lande", "countries"), Map.entry("brancher", "industries"),
            Map.entry("timer", "hours"), Map.entry("dage", "days"), Map.entry("uger", "weeks"),
            Map.entry("måneder", "months"), Map.entry("år", "years"), Map.entry("års", "years"),
            Map.entry("minutter", "minutes"), Map.entry("sekunder", "seconds"),
            Map.entry("anmodninger", "requests"), Map.entry("dokumenter", "documents"),
            Map.entry("ansøgninger", "applications"), Map.entry("tilbud", "offers"),
            Map.entry("rapporter", "reports"), Map.entry("kurser", "courses"),
            Map.entry("certificeringer", "certifications"), Map.entry("certifikater", "certifications"),
            Map.entry("sessioner", "sessions"), Map.entry("svar", "responses"),
            Map.entry("deltagere", "participants"), Map.entry("besøgende", "visitors"),
            Map.entry("projekter", "projects"), Map.entry("integrationer", "integrations"),
            Map.entry("servere", "servers"), Map.entry("artikler", "guides"),
            Map.entry("vejledninger", "guides"), Map.entry("datasæt", "datasets"),
            Map.entry("eksempler", "examples"), Map.entry("moduler", "modules"),
            Map.entry("værktøjer", "tools"), Map.entry("bidrag", "contributions"),
            Map.entry("repositorier", "repositories"), Map.entry("tjenester", "services"),
            Map.entry("linjer", "lines"), Map.entry("opgaver", "tasks"),
            Map.entry("stillinger", "positions"));

    /** Up to this many alphabetic modifiers may sit between a number and the noun it counts. */
    private static final int MODIFIER_WINDOW = 4;

    private static final Pattern PERCENT = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\s?%");
    private static final Pattern CURRENCY =
            Pattern.compile("(?<![\\w$€£])[$€£]\\s?\\d[\\d,.]*(?:\\s?[kKmMbB])?");

    /**
     * Danish and Nordic amounts, which write the unit after the number ("550.000 kr.") or as a
     * prefix code ("DKK 550.000"). Without this the guard cannot check a single figure in a Danish
     * salary or budget claim — it only ever saw symbol-prefixed currencies.
     */
    private static final Pattern SUFFIX_CURRENCY = Pattern.compile(
            "\\b(\\d[\\d,.]*(?:\\s?[kKmMbB])?)\\s?(kr\\.?|kroner|dkk|nok|sek|eur|usd)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PREFIX_CURRENCY_CODE = Pattern.compile(
            "\\b(dkk|nok|sek|eur|usd)\\s?(\\d[\\d,.]*(?:\\s?[kKmMbB])?)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Currency names that mean the same unit, so "kr" and "DKK" are one claim. */
    private static final Map<String, String> CURRENCY_SYNONYMS = Map.of(
            "kr", "dkk", "kr.", "dkk", "kroner", "dkk");
    private static final Pattern MULTIPLIER = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\s?x\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT = Pattern.compile(
            "\\b(\\d[\\d,.]*(?:[kKmMbB]\\b)?)\\s*\\+?\\s*(?:[\\p{L}][\\p{L}-]*\\s+){0," + MODIFIER_WINDOW + "}("
                    + String.join("|", METRIC_NOUNS) + ")\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern THOUSANDS = Pattern.compile("(\\d)[,.\\s\\u00a0\\u202f](?=\\d{3}(?!\\d))");
    private static final Pattern SPACE_GROUP = Pattern.compile("(?<!\\d)(\\d{1,3})[\\s\\u00a0\\u202f](?=\\d{3}(?!\\d))");

    /** Code points of the "zero" digit for non-ASCII decimal-digit blocks we fold to ASCII. */
    private static final int[] DIGIT_ZEROS = {
            0x0660, 0x06f0, 0x0966, 0x09e6, 0x0a66, 0x0ae6, 0x0b66, 0x0be6, 0x0c66, 0x0ce6,
            0x0d66, 0x0e50, 0x0ed0, 0x0f20, 0x1040, 0x17e0, 0x1810};

    /**
     * The result of a fact-gate audit, in two tiers.
     *
     * @param inventedMetrics  figures whose NUMBER appears nowhere in the source. There is no
     *                         innocent explanation for these: the profile does not contain that
     *                         number in any form.
     * @param unverifiedMetrics figures whose number IS in the source but attached to something
     *                          else — "30 teams" where the profile says "30 services". Usually a
     *                          rewording, occasionally a real slip, so it is worth showing and not
     *                          worth blocking.
     */
    public record FactAudit(List<String> inventedMetrics, List<String> unverifiedMetrics) {

        public FactAudit {
            inventedMetrics = inventedMetrics != null ? List.copyOf(inventedMetrics) : List.of();
            unverifiedMetrics = unverifiedMetrics != null ? List.copyOf(unverifiedMetrics) : List.of();
        }

        /** No findings of either tier. */
        public boolean clean() {
            return inventedMetrics.isEmpty() && unverifiedMetrics.isEmpty();
        }
    }

    /**
     * Audit generated text against the source profile text (e.g. the contact-free career
     * profile JSON). Returns the metric claims that appear in the document but are not
     * supported by the source.
     */
    public FactAudit audit(String generatedText, String sourceText) {
        if (generatedText == null || generatedText.isBlank()) return new FactAudit(List.of(), List.of());
        String source = sourceText == null ? "" : sourceText;
        Set<String> allowed = new java.util.LinkedHashSet<>(metricClaims(source));
        allowed.addAll(derivedDurationClaims(source));
        Set<String> allowedNumbers = allowed.stream()
                .map(DocumentFactGuard::numberOf)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        List<String> invented = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        // Compare on the normalized form, but report the document's own spelling: a user who wrote
        // "50k users" should not be told that "50000 users" is unsupported.
        metricClaimsWithSpelling(generatedText).forEach((claim, asWritten) -> {
            if (allowed.contains(claim)) return;
            String number = numberOf(claim);
            // The number is in the profile but counting something else: most often the model
            // renamed the noun. Report it, but not as a fabrication.
            if (number != null && allowedNumbers.contains(number)) unverified.add(asWritten);
            else invented.add(asWritten);
        });
        return new FactAudit(invented, unverified);
    }

    private static String currencyCode(String raw) {
        String unit = raw.toLowerCase().strip();
        return CURRENCY_SYNONYMS.getOrDefault(unit, unit);
    }

    /** "Jan 2021 - Mar 2024" and "Feb 2019 - Present" as the profile writes them. */
    private static final Pattern DATE_RANGE = Pattern.compile(
            "\\b([A-Za-z]{3,})\\s+(\\d{4})\\s*[-–—]\\s*(?:([A-Za-z]{3,})\\s+(\\d{4})|present|nu|nuv[æa]rende)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Durations the profile implies but never spells out.
     *
     * <p>"Three years at Netcompany" is a true statement about a profile that only contains
     * "Jan 2021 - Mar 2024" — the number 3 appears nowhere in it. Without this the guard calls the
     * most ordinary sentence in a cover letter a fabrication, which is the fastest way to teach
     * someone to ignore it.
     *
     * <p>Both the per-role span and the total across roles are allowed, since a letter says either.
     * Rounding is generous in both directions: a candidate describing 30 months as "two years" or
     * "nearly three" is not lying, and the guard should not arbitrate that.
     */
    static Set<String> derivedDurationClaims(String sourceText) {
        Set<String> claims = new java.util.LinkedHashSet<>();
        if (sourceText == null || sourceText.isBlank()) return claims;

        Matcher m = DATE_RANGE.matcher(stripMarkup(sourceText));
        int totalMonths = 0;
        while (m.find()) {
            Integer startMonth = monthOf(m.group(1));
            if (startMonth == null) continue;
            int startYear = Integer.parseInt(m.group(2));
            int endMonth;
            int endYear;
            if (m.group(3) != null) {
                Integer parsed = monthOf(m.group(3));
                if (parsed == null) continue;
                endMonth = parsed;
                endYear = Integer.parseInt(m.group(4));
            } else {
                java.time.LocalDate today = java.time.LocalDate.now();
                endMonth = today.getMonthValue();
                endYear = today.getYear();
            }
            int months = (endYear - startYear) * 12 + (endMonth - startMonth);
            if (months <= 0) continue;
            totalMonths += months;
            addDurationClaims(claims, months);
        }
        if (totalMonths > 0) addDurationClaims(claims, totalMonths);
        return claims;
    }

    /** Every rounding of a span a person might reasonably write, in both languages. */
    private static void addDurationClaims(Set<String> claims, int months) {
        claims.add(normalizeClaim(months + " months"));
        int wholeYears = months / 12;
        for (int years : new int[] {wholeYears, wholeYears + 1, Math.round(months / 12f)}) {
            if (years > 0) claims.add(normalizeClaim(years + " years"));
        }
    }

    private static Integer monthOf(String name) {
        String key = name.length() >= 3 ? name.substring(0, 3).toLowerCase(java.util.Locale.ENGLISH) : name;
        return MONTH_ABBREVIATIONS.get(key);
    }

    private static final Map<String, Integer> MONTH_ABBREVIATIONS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("maj", 5), Map.entry("jun", 6), Map.entry("jul", 7),
            Map.entry("aug", 8), Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("okt", 10),
            Map.entry("nov", 11), Map.entry("dec", 12));

    /** The numeric part of a normalized claim ("30 services" → "30"), or null when there is none. */
    private static String numberOf(String claim) {
        Matcher m = LEADING_NUMBER.matcher(claim);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern LEADING_NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    static Set<String> metricClaims(String text) {
        return metricClaimsWithSpelling(text).keySet();
    }

    /**
     * Normalized claim → the text as the document actually wrote it.
     *
     * <p>Everything compares on the normalized key, so "50k users" and "50,000 users" are one
     * claim; everything the user reads uses the value, so a finding quotes their own words back.
     */
    static Map<String, String> metricClaimsWithSpelling(String text) {
        String clean = foldNumberWords(stripMarkup(text));
        Map<String, String> claims = new java.util.LinkedHashMap<>();
        for (Pattern p : List.of(PERCENT, CURRENCY, MULTIPLIER)) {
            Matcher m = p.matcher(clean);
            while (m.find()) claims.putIfAbsent(normalizeClaim(m.group()), m.group().trim());
        }
        // Suffix and prefix currency both normalize to "<amount> <code>", so "550.000 kr." and
        // "DKK 550.000" compare equal to each other and to "550.000 kroner".
        Matcher suffix = SUFFIX_CURRENCY.matcher(clean);
        while (suffix.find()) {
            claims.putIfAbsent(normalizeClaim(suffix.group(1) + " " + currencyCode(suffix.group(2))),
                    suffix.group().trim());
        }
        Matcher prefix = PREFIX_CURRENCY_CODE.matcher(clean);
        while (prefix.find()) {
            claims.putIfAbsent(normalizeClaim(prefix.group(2) + " " + currencyCode(prefix.group(1))),
                    prefix.group().trim());
        }

        Matcher m = COUNT.matcher(clean);
        while (m.find()) {
            String noun = m.group(2).toLowerCase();
            String canonical = NOUN_SYNONYMS.getOrDefault(noun, noun);
            claims.putIfAbsent(normalizeClaim(m.group(1) + " " + canonical),
                    (m.group(1) + " " + m.group(2)).trim());
        }
        return claims;
    }

    /**
     * Lowercase, strip thousands separators, expand magnitude suffixes, collapse whitespace.
     *
     * <p>Expanding k/m/b is what lets "50k users" and "50,000 users" compare equal. Without it a
     * profile written one way and a letter written the other reads as a fabricated figure, which
     * is the most common way a model rewrites a number without changing it.
     */
    static String normalizeClaim(String claim) {
        String s = claim.toLowerCase();
        s = THOUSANDS.matcher(s).replaceAll("$1");
        s = expandMagnitude(s);
        return s.replaceAll("[,\\s]+", " ").trim();
    }

    private static final Pattern MAGNITUDE =
            Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s?([kmb])\\b");

    /** "50k" → "50000", "1.5m" → "1500000". Left alone when the result would not be a whole number. */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "FE_FLOATING_POINT_EQUALITY",
            justification = "`expanded == Math.floor(expanded)` is the exact integrality test — "
                    + "floor introduces no rounding, so equality is the correct idiom for "
                    + "'is this a whole number', not an approximate comparison.")
    private static String expandMagnitude(String text) {
        Matcher m = MAGNITUDE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            double value = Double.parseDouble(m.group(1));
            long factor = switch (m.group(2)) {
                case "k" -> 1_000L;
                case "m" -> 1_000_000L;
                default -> 1_000_000_000L;
            };
            double expanded = value * factor;
            // "1.5x" is a multiplier, not a magnitude; and a fractional expansion means the suffix
            // was not a magnitude at all, so leave the text as it was.
            String replacement = expanded == Math.floor(expanded) && !Double.isInfinite(expanded)
                    ? String.valueOf((long) expanded)
                    : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Number words immediately followed by a metric noun, in both languages: "thirty services"
     * and "tredive tjenester" become "30 services".
     *
     * <p>Deliberately only when the noun follows directly. Folding every "one" and "en" would turn
     * "one of the teams" into "1 of the teams" and invent a claim that was never made — the guard
     * would then flag a document for a sentence the source phrased slightly differently.
     */
    private static final Map<String, String> NUMBER_WORDS = Map.ofEntries(
            Map.entry("one", "1"), Map.entry("two", "2"), Map.entry("three", "3"),
            Map.entry("four", "4"), Map.entry("five", "5"), Map.entry("six", "6"),
            Map.entry("seven", "7"), Map.entry("eight", "8"), Map.entry("nine", "9"),
            Map.entry("ten", "10"), Map.entry("eleven", "11"), Map.entry("twelve", "12"),
            Map.entry("fifteen", "15"), Map.entry("twenty", "20"), Map.entry("thirty", "30"),
            Map.entry("forty", "40"), Map.entry("fifty", "50"), Map.entry("hundred", "100"),
            Map.entry("thousand", "1000"),
            Map.entry("en", "1"), Map.entry("et", "1"), Map.entry("to", "2"),
            Map.entry("tre", "3"), Map.entry("fire", "4"), Map.entry("fem", "5"),
            Map.entry("seks", "6"), Map.entry("syv", "7"), Map.entry("otte", "8"),
            Map.entry("ni", "9"), Map.entry("ti", "10"), Map.entry("elleve", "11"),
            Map.entry("tolv", "12"), Map.entry("femten", "15"), Map.entry("tyve", "20"),
            Map.entry("tredive", "30"), Map.entry("fyrre", "40"), Map.entry("halvtreds", "50"),
            Map.entry("hundrede", "100"), Map.entry("tusind", "1000"));

    private static final Pattern NUMBER_WORD_COUNT = Pattern.compile(
            "\\b(" + String.join("|", NUMBER_WORDS.keySet()) + ")\\s+("
                    + String.join("|", METRIC_NOUNS) + ")\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    static String foldNumberWords(String text) {
        Matcher m = NUMBER_WORD_COUNT.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String digits = NUMBER_WORDS.get(m.group(1).toLowerCase());
            m.appendReplacement(out, Matcher.quoteReplacement(
                    digits != null ? digits + " " + m.group(2) : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Remove HTML/LaTeX markup; block boundaries become sentence breaks so claims can't chain across them. */
    static String stripMarkup(String text) {
        String s = foldDigits(text == null ? "" : text);
        s = s.replaceAll("(?is)<script\\b[^>]*>.*?</script\\b[^>]*>", " ");
        s = s.replaceAll("(?is)<style\\b[^>]*>.*?</style\\b[^>]*>", " ");
        s = s.replaceAll("(?i)</?(?:li|p|div|tr|h[1-6]|section|article|ul|ol|table|br)\\b[^>\\n]*>", ". ");
        s = s.replaceAll("(?i)</?[a-zA-Z][^>\\n]*>", " ");
        s = s.replaceAll("\\\\[a-zA-Z]+\\*?(?:\\[[^\\]]*\\])?(?:\\{([^}]*)\\})?", " $1 ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Rewrite non-ASCII decimal digits and separators to ASCII so number patterns see them. */
    static String foldDigits(String text) {
        String out = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(out.length());
        out.codePoints().forEach(cp -> {
            if (Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER && !(cp >= 0x30 && cp <= 0x39)) {
                int folded = foldDigit(cp);
                if (folded >= 0) sb.append((char) ('0' + folded));
                else sb.appendCodePoint(cp);
            } else {
                sb.appendCodePoint(cp);
            }
        });
        String s = sb.toString()
                .replace('٪', '%')   // Arabic percent sign
                .replace('٫', '.')   // Arabic decimal separator
                .replace('٬', ',');  // Arabic thousands separator
        return SPACE_GROUP.matcher(s).replaceAll("$1");
    }

    private static int foldDigit(int cp) {
        for (int zero : DIGIT_ZEROS) {
            int value = cp - zero;
            if (value >= 0 && value <= 9) return value;
        }
        return -1;
    }
}
