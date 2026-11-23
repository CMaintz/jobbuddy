package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.LetterGatePoint;
import com.autoapplicant.domain.document.structured.LetterGateResult;
import com.autoapplicant.domain.document.structured.LetterGateStop;
import com.autoapplicant.domain.document.structured.LetterGateVerdict;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.ai.AiOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * The gate a generated letter passes before render and export: one model call with fresh context
 * that sees only the letter, the posting and the checklist, never the profile, the needs or how the
 * letter was written.
 *
 * <p>The checklist is points 1, 8, 13 and 14 of the KORREKTUR prompt in the writing guide
 * (job-search-kilder, {@code prompts/ansoegning.md}), the points the guide calls gates. The model
 * judges and {@link LetterGateAnswer} turns its answer into our verdict. An answer that cannot be
 * read is asked once more, then reported as no verdict, never as a pass.
 */
@Component
public class LetterGate {

    /** Model calls one verdict may take: the first answer and one retry of an unreadable one. */
    public static final int MAX_CALLS = 2;

    private static final Logger log = LoggerFactory.getLogger(LetterGate.class);

    private static final String SYSTEM = """
            Du er en kritisk, erfaren rekrutteringskonsulent og porten, en ansøgning skal igennem, \
            før den sendes. Du har ikke set, hvordan ansøgningen blev skrevet, og du bedømmer kun \
            teksten. Vær streng: tvivl tæller som dumpet. Svar kun med gyldig JSON.""";

    // Points 1, 8, 13 and 14 of the guide's KORREKTUR prompt, in the third person: the gate reads
    // someone else's letter, so the guide's "mig" is the applicant.
    private static final String CHECKLIST = """
            Gennemgå ansøgningen mod disse fire punkter. Hvert punkt er en port: dumper ét af dem, \
            skal ansøgningen skrives om.

            1. FARVETESTEN - Handler mest af teksten om virksomheden og dens udbytte, eller om \
            ansøgeren? Mindst halvdelen af sætningerne skal handle om virksomheden. Tæl sætningerne: \
            hvor mange handler om virksomheden og dens udbytte, og hvor mange er der i alt? En sætning \
            som "Hos jer ... jeg" handler om ansøgeren. Citér, hvor teksten tipper over i for meget \
            "jeg/mig/min".

            8. FREMAD vs. BAGUD - Er ansøgningen bare CV'et skrevet om i datid ("jeg har arbejdet \
            med ..."), eller ser den fremad mod, hvad ansøgeren bidrager med? Citér de passager, der \
            skal vendes fremad.

            13. OPSLAGS-EKKO - Gengiver en sætning opslagets opgaver eller krav som liste eller \
            opremsning? Citér hver sætning, der nævner tre eller flere af opslagets opgaver/krav, der \
            løfter en opremsning ordret fra opslaget, eller som genfortæller, hvad stillingen går ud \
            på. Hver af dem dumper. En sætning, der tager fat i én opgave og siger, hvordan den løses, \
            dumper ikke.

            14. CV-REFERAT (målbart) - Tæl sætningerne i datid om ansøgeren pr. afsnit. Mere end én \
            pr. afsnit dumper. Starter et afsnit med ansøgeren i datid ("Hos X ...", "Som Y ...", "På \
            kurset ...") i stedet for arbejdsgiverens behov, dumper det. Citér hver sætning, der kunne \
            flyttes uændret ind i CV'et; hver af dem dumper. Et belæg, der er skrevet som bevis for \
            afsnittets påstand, består.

            SVAR
            Tæl, før du konkluderer. "quotes" er kun de sætninger, der dumper punktet, citeret ordret \
            fra ansøgningen; listen er tom, når punktet består. Skriv "reason" på ansøgningens sprog. \
            "verdict" er "PORT: BESTÅET", når alle fire punkter består, ellers "PORT: DUMPET". Svar \
            kun med JSON i præcis denne form, med dine egne tal og vurderinger i stedet for \
            pladsholderne:
            {
              "points": [
                {"point": 1, "employerSentences": <antal>, "totalSentences": <antal>, \
            "passed": <true|false>, "quotes": ["<citat>"], "reason": "<begrundelse>"},
                {"point": 8, "passed": <true|false>, "quotes": ["<citat>"], "reason": "<begrundelse>"},
                {"point": 13, "passed": <true|false>, "quotes": ["<citat>"], "reason": "<begrundelse>"},
                {"point": 14, "pastTenseApplicantSentencesPerParagraph": [<antal pr. afsnit>], \
            "paragraphsOpeningWithApplicantInPast": <antal>, "passed": <true|false>, \
            "quotes": ["<citat>"], "reason": "<begrundelse>"}
              ],
              "verdict": "PORT: BESTÅET | PORT: DUMPET"
            }""";

    private final ChatProviderPort provider;
    private final ObjectMapper json;

    public LetterGate(@Qualifier("generationAiProvider") ChatProviderPort provider, ObjectMapper json) {
        this.provider = provider;
        this.json = json;
    }

    /**
     * Judges {@code letter} against the brief's posting. Never throws for a model problem: an
     * unreadable answer or a provider error comes back as {@link LetterGateVerdict#NOT_COMPLETED}.
     */
    public LetterGateResult judge(String letter, LetterBrief brief) {
        PromptComposition prompt = prompt(letter, brief);
        try {
            for (int call = 1; call <= MAX_CALLS; call++) {
                Optional<LetterGateResult> verdict = LetterGateAnswer.read(
                        json, provider.generateJson(prompt, AiOperations.LETTER_GATE));
                if (verdict.isPresent()) {
                    return verdict.get();
                }
                log.warn("Letter gate: unreadable answer on call {} of {}", call, MAX_CALLS);
            }
        } catch (RuntimeException providerError) {
            log.warn("Letter gate: provider failed: {}", providerError.getMessage());
        }
        return new LetterGateResult(LetterGateVerdict.NOT_COMPLETED, List.of(), 0, LetterGateStop.NO_VERDICT);
    }

    private static PromptComposition prompt(String letter, LetterBrief brief) {
        String system = SYSTEM + "\n\n" + brief.guardrails().untrustedInputBlock();
        String user = CHECKLIST
                + "\n\n## Ansøgningen\n" + (letter != null ? letter : "")
                + "\n\n## Job Description\n" + brief.postingOr("(intet opslag)");
        return new PromptComposition(system, user, "", "", "", "", user);
    }

    /** What the gate failed, with its quotes: the rewriter's work list. */
    static String failures(LetterGateResult failed) {
        StringBuilder sb = new StringBuilder("## What The Gate Failed");
        for (LetterGatePoint point : failed.failedPoints()) {
            sb.append("\n- Point ").append(point.point()).append(": ")
              .append(point.reason() != null ? point.reason() : "failed");
            point.quotes().forEach(quote -> sb.append("\n  > ").append(quote));
        }
        return sb.toString();
    }
}
