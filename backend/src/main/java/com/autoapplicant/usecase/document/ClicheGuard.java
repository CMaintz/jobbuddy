package com.autoapplicant.usecase.document;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic, model-free filler detector — the style counterpart to {@link DocumentFactGuard}.
 *
 * <p>Two failure modes it catches at zero token cost: Danish application clichés ("floskler") that
 * every Danish career adviser names as an instant-reject signal, and the English phrasing that now
 * reads as machine-written to a 2026 reader. Both survive the honesty rules because neither is a
 * factual error — the text is true, it is just indistinguishable from every other application.
 *
 * <p>The same phrase list feeds both directions: {@link #promptBlock} tells the model not to write
 * them, and {@link #audit} checks whether it obeyed. Findings are handed to the reviewer pass (so
 * it rewrites them) and logged by {@link GeneratedContentGuards}; they never fail generation unless
 * configured to.
 */
@Service
public class ClicheGuard {

    /** Danish "floskler" — formulaic openers and unprovable self-descriptions. */
    static final List<String> DANISH_CLICHES = List.of(
            "jeg søger hermed stillingen",
            "jeg søger herved stillingen",
            "hermed ansøger jeg",
            "jeg skriver for at søge",
            "jeg tillader mig hermed",
            "med stor interesse læste jeg",
            "jeg mener, at jeg er den rette",
            "jeg er den rette til jobbet",
            "jeg passer perfekt til stillingen",
            "holde mange bolde i luften",
            "mange bolde i luften",
            "teamplayer",
            "brænder for",
            "jeg er passioneret omkring",
            "jeg arbejder både selvstændigt og i team",
            "selvstændigt og i teams",
            "jeg er struktureret og målrettet",
            "positiv og udadvendt",
            "jeg er en dynamisk person",
            "jeg er resultatorienteret",
            "jeg er en person, der",
            "sidst men ikke mindst",
            "jeg er meget interesseret i stillingen",
            "jeg mener at kunne bidrage",
            "det lyder som en spændende mulighed",
            "en spændende og udfordrende stilling",
            "jeg trives i et travlt miljø",
            "jeg er hurtig til at lære nyt",
            "jeg har altid været fascineret af",
            "med min baggrund inden for",
            "jeg søger nye udfordringer",
            "jeg vil være et stort aktiv",
            "det ville være en drøm",
            "jeg er meget begejstret for muligheden",
            "en unik mulighed for mig");

    /** English filler that now reads as AI-generated boilerplate. */
    static final List<String> ENGLISH_CLICHES = List.of(
            "i am writing to apply",
            "i am writing to express",
            "i am excited to apply",
            "i was thrilled to see",
            "it is with great enthusiasm",
            "i hope this email finds you well",
            "i hope this message finds you well",
            "i am confident that i would be a great fit",
            "perfect fit for this role",
            "i am the perfect candidate",
            "proven track record",
            "results-driven",
            "results driven",
            "self-starter",
            "team player",
            "hit the ground running",
            "wear many hats",
            "passionate about",
            "leverage my skills",
            "in today's fast-paced",
            "fast-paced environment",
            "dynamic environment",
            "think outside the box",
            "delve into",
            "look no further",
            "align with my values",
            "excited about the opportunity to contribute",
            "last but not least",
            "i am reaching out because",
            "a great opportunity to grow",
            "i thrive in fast-paced",
            "quick learner",
            "i am seeking a new challenge",
            "always been fascinated by",
            "uniquely positioned to");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** The phrases found in the text, in the order listed. Empty means clean. */
    public record ClicheAudit(List<String> phrases) {
        public boolean clean() {
            return phrases.isEmpty();
        }
    }

    /**
     * Scans generated text for banned phrases in both languages — a Danish letter can pick up an
     * English cliché and vice versa, so language is not used to narrow the scan.
     */
    public ClicheAudit audit(String text) {
        if (text == null || text.isBlank()) return new ClicheAudit(List.of());
        String normalized = WHITESPACE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ")
                .replace('’', '\'')   // curly apostrophe → straight, so "today's" matches
                .replace('–', '-').replace('—', '-');
        List<String> found = new ArrayList<>();
        for (String phrase : DANISH_CLICHES) if (normalized.contains(phrase)) found.add(phrase);
        for (String phrase : ENGLISH_CLICHES) if (normalized.contains(phrase)) found.add(phrase);
        return new ClicheAudit(List.copyOf(found));
    }

    /**
     * The "never write these" prompt block for the resolved output language. Only the relevant
     * list is included (both when the language is unknown) so the prompt does not carry a wall of
     * phrases the model was never going to write.
     */
    public static String promptBlock(String resolvedLanguage) {
        boolean danish = JobLanguageDetector.isDanish(resolvedLanguage);
        boolean english = resolvedLanguage != null
                && resolvedLanguage.strip().toLowerCase(Locale.ROOT).startsWith("english");
        List<String> phrases = new ArrayList<>();
        if (danish || !english) phrases.addAll(DANISH_CLICHES);
        if (english || !danish) phrases.addAll(ENGLISH_CLICHES);
        return """
                ## Banned Phrases
                These phrases mark an application as filler — a Danish reader discards them on sight, \
                and the English ones now read as machine-written. Never use them, and do not write a \
                near-paraphrase of one either. Say the concrete thing instead.
                """ + phrases.stream().map(p -> "- \"" + p + "\"").reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
