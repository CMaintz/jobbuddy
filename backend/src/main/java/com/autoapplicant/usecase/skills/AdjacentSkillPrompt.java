package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.document.PromptComposition;
import java.util.List;

/**
 * The prompt that asks which skills a profile very likely holds without listing them.
 *
 * <p>Only the skill labels are sent: they are all the question needs, and nothing that
 * identifies the person leaves the server for it.
 */
final class AdjacentSkillPrompt {

    /** More than this per run is a catalogue, not a question worth answering. */
    static final int MAX_SUGGESTIONS = 8;

    static final String SYSTEM_PROMPT = """
            You suggest skills a job seeker very likely has but did not write on their profile.
            You get the skills they listed. Each suggestion is put to them as a yes/no question,
            "You have <listed skill>. Do you also have <suggestion>?", and only a yes adds it.

            Suggest a skill when nearly everyone who does a listed skill in their job also does
            the suggested one, so it is simply missing from the list: someone with event planning
            almost certainly does supplier contact and event budgets; someone with bookkeeping
            almost certainly does reconciliations; someone with Django almost certainly writes
            Python. Prefer the wording a job posting uses for the skill, since the point is to
            match the postings they would fit.

            Rules:
            - "because" is the one listed skill, copied exactly, that the suggestion follows from.
            - Never a skill already in the list, nor a spelling or translation of one.
            - Never a degree, a certification, a named tool or product, a language, a seniority
              or a leadership claim: those are learnt or earned separately, not implied.
            - Not a step up or a neighbouring field ("Java" does not imply "Kotlin", "press work"
              does not imply "crisis communication").
            - Write each skill in the same language as the listed skill it follows from, Danish
              or English, never any other.
            - At most %d entries, the surest first. Return an empty list rather than guess.

            Return ONLY JSON: {"suggestions": [{"skill": "string", "because": "string"}]}
            """.formatted(MAX_SUGGESTIONS);

    private AdjacentSkillPrompt() {}

    static PromptComposition forSkills(List<String> heldSkills) {
        String user = "Listed skills:\n- " + String.join("\n- ", heldSkills);
        return new PromptComposition(SYSTEM_PROMPT, user, null, null, null, null, user);
    }
}
