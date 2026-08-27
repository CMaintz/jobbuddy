package com.autoapplicant.usecase.document;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Labels and explanations for the ATS report's checks, in the language of the document they
 * describe.
 *
 * <p>A Danish user reading a Danish letter was previously handed English diagnostics. The document
 * already knows what language it is in — the CV path from its resolved generation language, the
 * letter path by detecting its own body — so the report follows it.
 *
 * <p>Check {@code code}s stay stable and English: the frontend keys off them, and they are the
 * identifier, not the copy.
 */
final class AtsCheckMessages {

    /** At most this many findings are named before the rest are summarised as a count. */
    private static final int MAX_LISTED = 5;

    private final boolean danish;

    private AtsCheckMessages(boolean danish) {
        this.danish = danish;
    }

    static AtsCheckMessages forLanguage(String language) {
        return new AtsCheckMessages(JobLanguageDetector.isDanish(language));
    }

    // ── Static checks ─────────────────────────────────────────────────────────────────────

    String realTextLabel()   { return danish ? "Rigtig tekst" : "Real text rendering"; }
    String realTextDetail()  {
        return danish ? "Gengivet som markerbar tekst, ikke som billede."
                      : "Rendered as selectable text rather than an image.";
    }

    String headingsLabel()   { return danish ? "Standardoverskrifter" : "Standard CV headings"; }
    String headingsDetail()  {
        return danish ? "Bruger forventelige sektionsoverskrifter, som CV-læsere kan tolke."
                      : "Uses predictable section headings for CV parsing.";
    }

    String privacyLabel()    { return danish ? "Kontaktoplysninger" : "Contact privacy"; }
    String privacyDetail()   {
        return danish ? "Navn og kontaktoplysninger indsættes efter AI-tilpasningen."
                      : "Name and contact details are inserted after AI tailoring.";
    }

    String designedLabel()   { return danish ? "Designet layout" : "Designed layout"; }
    String designedDetail()  {
        return danish ? "Designede skabeloner tolkes mindre pålideligt end ATS-tilstand."
                      : "Designed templates may parse less reliably than ATS mode.";
    }

    String noteLabel()       { return danish ? "Bemærkning" : "Tailoring note"; }

    // ── Guard checks ──────────────────────────────────────────────────────────────────────

    String metricsLabel()    { return danish ? "Talpåstande" : "Metric claims"; }
    String metricsPass()     {
        return danish ? "Alle tal i dokumentet kan spores til din profil."
                      : "Every number in the document traces back to your profile.";
    }
    String metricsFail(List<String> findings) {
        return danish
                ? "Ikke dækket af din profil: " + join(findings)
                  + ". Ret eller fjern dem, inden du sender — du skal kunne stå inde for dem til samtalen."
                : "Not supported by your profile: " + join(findings)
                  + ". Correct or remove before sending — you would have to defend these in an interview.";
    }

    String unverifiedLabel() { return danish ? "Tal at tjekke" : "Figures to check"; }
    String unverifiedWarn(List<String> findings) {
        return danish
                ? "Tallet står i din profil, men om noget andet: " + join(findings)
                  + ". Tjek at det tæller det rigtige."
                : "The number is in your profile, but counting something else: " + join(findings)
                  + ". Check it refers to the right thing.";
    }

    String fillerLabel()     { return danish ? "Floskler" : "Filler phrases"; }
    String fillerPass()      {
        return danish ? "Ingen kendte floskler eller AI-typiske vendinger."
                      : "No known application clichés or AI-tell phrasing.";
    }
    String fillerWarn(List<String> findings) {
        return danish
                ? "Læses som fyld: " + join(findings) + ". Erstat med noget konkret om netop denne stilling."
                : "Reads as boilerplate: " + join(findings) + ". Replace with something concrete to this role.";
    }

    String retractedLabel()  { return danish ? "Tilbagetrukne påstande" : "Retracted claims"; }
    String retractedFail(List<String> findings) {
        return danish
                ? "Påstande, du tidligere har trukket tilbage, er dukket op igen: " + join(findings) + "."
                : "Claims you previously disowned reappeared: " + join(findings) + ".";
    }

    /** Quotes and joins findings; caps the list so one bad generation can't flood the panel. */
    private String join(List<String> values) {
        String joined = values.stream().limit(MAX_LISTED).map(v -> "\"" + v + "\"")
                .collect(Collectors.joining(", "));
        if (values.size() <= MAX_LISTED) return joined;
        int rest = values.size() - MAX_LISTED;
        return joined + (danish ? " (+" + rest + " mere)" : " (+" + rest + " more)");
    }
}
