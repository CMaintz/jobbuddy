package com.autoapplicant.usecase.matching;

import java.util.List;

/**
 * The skill labels a match is scored on.
 *
 * @param held      the profile's own skills: what the user wrote or confirmed
 * @param suggested skills proposed to the user and not yet answered. They earn a little loose
 *                  credit in matching and are never evidence anywhere else.
 */
record MatchSkills(List<String> held, List<String> suggested) {

    MatchSkills {
        held = List.copyOf(held);
        suggested = List.copyOf(suggested);
    }
}
