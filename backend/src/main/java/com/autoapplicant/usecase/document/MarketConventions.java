package com.autoapplicant.usecase.document;

import java.util.Locale;

/**
 * Hiring-market conventions injected into generation prompts.
 *
 * <p>Honesty, targeting, and length rules say how to argue; these say what the reader on the
 * other end actually expects. Danish recruiters screen fast and reject formulaic openers, letters
 * that merely retell the CV, and American-style self-promotion — none of which the model avoids
 * on its own, because its priors are trained on the US market.
 *
 * <p>Open/closed by design: a new market is a new constant plus a branch in {@link #resolve},
 * with no change to the prompt builder. Denmark is the only market with rules today; everything
 * else resolves to {@link Market#GENERIC} and contributes no block.
 */
public final class MarketConventions {

    public enum Market { DENMARK, GENERIC }

    private MarketConventions() {}

    /**
     * The market whose conventions apply. The posting's country wins when known — a Copenhagen
     * employer posting in English is still hiring into the Danish market — with the resolved
     * output language as the fallback signal.
     */
    public static Market resolve(String resolvedLanguage, String jobCountry) {
        if (jobCountry != null && !jobCountry.isBlank()) {
            String c = jobCountry.strip().toLowerCase(Locale.ROOT);
            if (c.startsWith("danmark") || c.startsWith("denmark") || c.equals("dk")) return Market.DENMARK;
            // A known non-Danish country is an explicit signal; don't let the language override it.
            return Market.GENERIC;
        }
        return JobLanguageDetector.isDanish(resolvedLanguage) ? Market.DENMARK : Market.GENERIC;
    }

    /** Market conventions for a prose application letter; empty string when the market has none. */
    public static String letterRules(Market market) {
        return market == Market.DENMARK ? DANISH_LETTER_RULES : "";
    }

    /** Market conventions for a CV; empty string when the market has none. */
    public static String cvRules(Market market) {
        return market == Market.DENMARK ? DANISH_CV_RULES : "";
    }

    /**
     * Danish letter conventions. Sourced from Danish career-advisory guidance (Ase, Djøf, HK,
     * Ballisager, WorkinDenmark): the screening is brutally fast, the opener and the
     * CV-retelling trap are the two most-cited rejection reasons, and the register is plainer
     * and more modest than the model's default.
     */
    private static final String DANISH_LETTER_RULES = """
            ## Danish Market Conventions
            The reader is a Danish recruiter or hiring manager who decides in well under a minute. \
            Write for that reader:
            - The opening sentence must carry information, not announce the application. Never open \
            with a formulaic line ("Jeg søger hermed stillingen som…", "Jeg skriver for at søge…", \
            "I am writing to apply for…"). Open on the concrete hook: the result, the domain overlap, \
            or the specific thing about this employer that made the candidate write.
            - The letter must NOT retell the CV in prose. Its job is the argument the CV cannot make: \
            why these facts mean the candidate can solve THIS employer's problem. If a paragraph could \
            be replaced by reading the CV, it is wasted.
            - Prove, don't assert. Tie each claimed competence to the posting's own wording and back it \
            with one concrete example ("Fordi jeg har arbejdet med X, kan jeg …"). An unproven adjective \
            is the single most common Danish rejection reason.
            - Write about what the candidate contributes, not what the job would give them. Motivation is \
            demonstrated through specific knowledge of the company and its work — never through \
            enthusiasm adjectives or flattery.
            - Danish professional register: plain, direct, informal "du"/"I"-form, modest. No American \
            superlatives, no self-praise without evidence, no eagerness that reads as desperation. \
            Confidence is shown by being concrete and brief.
            - Close calmly and briefly: one forward-looking sentence, no pleading, no apology for \
            writing. When writing in Danish, sign off "Med venlig hilsen".
            - Hard ceiling: one A4 page (~350–400 words, about 2.500 characters). Danish readers treat \
            a longer letter as a failure to prioritise.""";

    /**
     * Danish CV conventions. Danish CVs are shorter, plainer, and more literal than the US resumes
     * the model defaults to; overdesign and inflated titles actively count against the candidate.
     */
    private static final String DANISH_CV_RULES = """
            ## Danish Market Conventions
            - Danish CVs are short and factual: two A4 pages maximum, one page for students and new \
            graduates. Straightforwardness is rewarded; inflated claims and padding are penalised.
            - Open with a short profile text ("profiltekst") of 3–5 lines written FOR this posting: what \
            the candidate is, what they are strongest at, and where they are heading. Not a generic bio.
            - Reverse-chronological in every section, newest first, and keep month-level date ranges — \
            Danish readers expect months and notice unexplained gaps.
            - State titles plainly, exactly as the profile has them. Do not translate a role into a \
            grander-sounding title, and do not use English hype words for work that was ordinary.
            - Keep the spoken-language entries and their proficiency levels when the profile has them: \
            Danish-language ability is frequently a real requirement and is read closely.
            - Use the ordinary section names a Danish reader expects; do not invent unusual headings or \
            reorder the reader's expectations for effect.""";
}
