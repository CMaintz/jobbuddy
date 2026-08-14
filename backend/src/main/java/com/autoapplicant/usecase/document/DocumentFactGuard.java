package com.autoapplicant.usecase.document;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** Nouns a bare number is allowed to count. A claim needs one of these to be extracted. */
    private static final List<String> METRIC_NOUNS = List.of(
            "users", "customers", "clients", "employees", "engineers", "teams", "companies",
            "partners", "organizations", "organisations", "brands", "countries",
            "hours", "days", "weeks", "months", "years", "minutes", "seconds",
            "requests", "tokens", "documents", "workflows", "pipelines", "agents",
            "interviews", "applications", "offers", "reports", "cvs", "resumes",
            "enrollments", "enrolments", "completions", "courses", "certifications",
            "certificates", "sessions", "responses", "surveys", "cohorts",
            "commits", "contributions", "repositories", "repos", "modules", "tools",
            "servers", "guides", "articles", "datasets", "examples", "deployments",
            "services", "downloads", "stars", "lines", "projects", "integrations", "tests");

    private static final Map<String, String> NOUN_SYNONYMS = Map.of(
            "repos", "repositories", "enrolments", "enrollments", "organisations", "organizations",
            "cvs", "resumes", "certificates", "certifications", "articles", "guides");

    /** Up to this many alphabetic modifiers may sit between a number and the noun it counts. */
    private static final int MODIFIER_WINDOW = 4;

    private static final Pattern PERCENT = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\s?%");
    private static final Pattern CURRENCY =
            Pattern.compile("(?<![\\w$€£])[$€£]\\s?\\d[\\d,.]*(?:\\s?[kKmMbB])?");
    private static final Pattern MULTIPLIER = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\s?x\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT = Pattern.compile(
            "\\b(\\d[\\d,.]*(?:[kKmMbB]\\b)?)\\s*\\+?\\s*(?:[A-Za-z][A-Za-z-]*\\s+){0," + MODIFIER_WINDOW + "}("
                    + String.join("|", METRIC_NOUNS) + ")\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern THOUSANDS = Pattern.compile("(\\d)[,.\\s\\u00a0\\u202f](?=\\d{3}(?!\\d))");
    private static final Pattern SPACE_GROUP = Pattern.compile("(?<!\\d)(\\d{1,3})[\\s\\u00a0\\u202f](?=\\d{3}(?!\\d))");

    /** Code points of the "zero" digit for non-ASCII decimal-digit blocks we fold to ASCII. */
    private static final int[] DIGIT_ZEROS = {
            0x0660, 0x06f0, 0x0966, 0x09e6, 0x0a66, 0x0ae6, 0x0b66, 0x0be6, 0x0c66, 0x0ce6,
            0x0d66, 0x0e50, 0x0ed0, 0x0f20, 0x1040, 0x17e0, 0x1810};

    /** The result of a fact-gate audit: metric claims present in the document but not the source. */
    public record FactAudit(List<String> inventedMetrics) {
        public boolean clean() {
            return inventedMetrics.isEmpty();
        }
    }

    /**
     * Audit generated text against the source profile text (e.g. the contact-free career
     * profile JSON). Returns the metric claims that appear in the document but are not
     * supported by the source.
     */
    public FactAudit audit(String generatedText, String sourceText) {
        if (generatedText == null || generatedText.isBlank()) return new FactAudit(List.of());
        Set<String> allowed = metricClaims(sourceText == null ? "" : sourceText);
        List<String> invented = new ArrayList<>();
        for (String claim : metricClaims(generatedText)) {
            if (!allowed.contains(claim)) invented.add(claim);
        }
        return new FactAudit(invented);
    }

    static Set<String> metricClaims(String text) {
        String clean = stripMarkup(text);
        Set<String> claims = new LinkedHashSet<>();
        for (Pattern p : List.of(PERCENT, CURRENCY, MULTIPLIER)) {
            Matcher m = p.matcher(clean);
            while (m.find()) claims.add(normalizeClaim(m.group()));
        }
        Matcher m = COUNT.matcher(clean);
        while (m.find()) {
            String noun = m.group(2).toLowerCase();
            noun = NOUN_SYNONYMS.getOrDefault(noun, noun);
            claims.add(normalizeClaim(m.group(1) + " " + noun));
        }
        return claims;
    }

    /** Lowercase, strip thousands separators (sep + exactly 3 digits), collapse whitespace. */
    static String normalizeClaim(String claim) {
        String s = claim.toLowerCase();
        s = THOUSANDS.matcher(s).replaceAll("$1");
        return s.replaceAll("[,\\s]+", " ").trim();
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
