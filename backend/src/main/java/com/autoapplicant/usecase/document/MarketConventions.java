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
     * Conventions for short outreach — a recruiter message or a follow-up. Separate from the letter
     * rules because the medium is different: there is no page to fill and no structure to follow,
     * and the failure mode is sounding like sales rather than sounding generic.
     */
    public static String outreachRules(Market market) {
        return market == Market.DENMARK ? DANISH_OUTREACH_RULES : "";
    }

    /** Conventions for interview preparation and mock interviews. */
    public static String interviewRules(Market market) {
        return market == Market.DENMARK ? DANISH_INTERVIEW_RULES : "";
    }

    /**
     * How to read a posting from this market when judging fit. Danish postings separate what is
     * required from what is merely welcome, in language that is easy to flatten into one list —
     * and flattening it produces both false hope and false despair.
     */
    public static String jobReadingRules(Market market) {
        return market == Market.DENMARK ? DANISH_JOB_READING_RULES : "";
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
     * How a Danish posting encodes its requirements. The distinction is grammatical, not sectional:
     * the must-haves and the nice-to-haves are often in the same bullet list.
     */
    private static final String DANISH_JOB_READING_RULES = """
            ## Reading a Danish Posting
            Separate what the posting requires from what it merely welcomes before judging fit:
            - Requirements are phrased as "du skal", "det er et krav", "du har X års erfaring med", \
            or a bare "du har".
            - Preferences are phrased as "det er en fordel", "gerne", "vi ser gerne at", \
            "erfaring med X er et plus", "kendskab til". These are not requirements, and a missing \
            one is not a gap worth reporting as though it were.
            Weight the score on the requirements. Report a missing preference as an opportunity, not \
            a failure — treating both alike either scares a qualified candidate off or reassures an \
            unqualified one.
            Danish postings also frequently state the working language explicitly ("dansk er et krav", \
            "we work in English"). When they do, treat it as a hard requirement and say so plainly.""";

    /**
     * Danish outreach conventions. The register that works in a Danish inbox is closer to a
     * colleague's note than to a pitch: a Dane reads enthusiasm as selling and discounts it.
     */
    private static final String DANISH_OUTREACH_RULES = """
            ## Danish Market Conventions
            - Plain, direct, and short enough to read on a phone without scrolling. A Danish reader \
            discounts enthusiasm and responds to something concrete.
            - No opening pleasantries ("I hope this finds you well"), no apology for writing, and no \
            praise of the company. Say why you are writing in the first line.
            - Informal "du" — first names are normal at every level, and formality reads as distance.
            - One concrete thing the candidate has done, with its result, beats a summary of their \
            background. Where there is no result to give, say what became different.
            - Propose a small, specific next step (a short call), not an open offer to "connect".
            - Danish practice for unsolicited contact starts on the phone: ring the company, ask \
            whether you may send an application, ask who to send it to and how they prefer to \
            receive it. A letter that follows such a call should open by referring to it ("som \
            aftalt") — but only when the candidate says the call happened. Never invent one.
            - The candidate is expected to be the active party afterwards. Following up within a few \
            working days is normal here, not pushy.""";

    /**
     * Danish interview conventions.
     *
     * <p>Sourced from Ballisager's recruitment analysis and the union career services (Djof, HK,
     * Lederne): what employers say they weigh, how the rounds are structured, and when pay comes
     * up. The headline finding is that motivation and authenticity outrank polish — "vi ansaetter
     * mennesker, ikke perfekte profiler" — close to the opposite of what a candidate rehearsing for
     * a US-style interview prepares for.
     */
    private static final String DANISH_INTERVIEW_RULES = """
            ## Danish Interview Conventions
            - What employers say they weigh most: being clear about your motivation (63% of firms), \
            letting your personality show (52%), and visible preparation (45%). Authenticity beats a \
            polished answer here — a rehearsed-sounding candidate loses to a real one.
            - The process usually runs in rounds: a short screening, a first conversation with HR and \
            the hiring manager about background and motivation, a second with a case or the team, and \
            a final one where pay and terms are settled. Prepare for the round at hand, not all four.
            - A Danish interview is a conversation between near-equals, not an examination. The \
            candidate is expected to ask real questions back — about the work, the team, the \
            department's goals, how decisions get made — and asking none reads as disinterest. Asking \
            about the process and timeline at the end is normal.
            - Flat hierarchy means disagreement is expected. A question about handling a manager or a \
            colleague being wrong is testing whether the candidate would say so, not whether they comply.
            - Answers should be concrete and modest. Claiming sole credit for team work is a warning \
            sign here; "we", with a clear account of the candidate's own part, is the register that works.
            - The weakness question wants a real development area plus what the candidate is doing \
            about it. A strength in disguise ("I work too hard") is read as evasion.
            - Pay can come up at any point and is arriving earlier in the process than it used to. Be \
            ready with a researched range from the start; leave the actual negotiation until the job \
            has been offered.
            - Everything in the application will be probed. Prepare the candidate to defend each \
            specific claim they made, and to say plainly when something was a team result or a gap.""";

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
            reorder the reader's expectations for effect.
            - Danish employers screen through applicant tracking systems before a human reads \
            anything, and those systems match the posting's literal must-have terms. Mirror the \
            posting's own wording for every requirement the profile genuinely supports, and include \
            the common synonym where one exists (both "Kubernetes" and "K8s"), since a system \
            matching on one term will not infer the other.""";
}
