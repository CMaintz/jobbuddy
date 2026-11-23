package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A posting's asks in its three tiers, each skill once. An ask that is both tiered and in the
 * flat lists counts only in its tier; one in both tiers counts as required.
 */
record PostingAsks(List<String> required, List<String> preferred, List<String> untiered) {

    static PostingAsks of(Job job, SkillCanonicalizer canonicalizer) {
        Set<String> seen = new HashSet<>();
        List<String> required = distinct(job.requiredSkills(), canonicalizer, seen);
        List<String> preferred = distinct(job.preferredSkills(), canonicalizer, seen);
        List<String> flat = new ArrayList<>(nonNull(job.technologies()));
        flat.addAll(nonNull(job.skills()));
        return new PostingAsks(required, preferred, distinct(flat, canonicalizer, seen));
    }

    static PostingAsks untiered(List<String> asks) {
        return new PostingAsks(List.of(), List.of(), asks);
    }

    /** The same asks with every one {@code dropped} accepts left out of all three tiers. */
    PostingAsks without(Predicate<String> dropped) {
        return new PostingAsks(kept(required, dropped), kept(preferred, dropped),
                kept(untiered, dropped));
    }

    boolean isEmpty() {
        return required.isEmpty() && preferred.isEmpty() && untiered.isEmpty();
    }

    List<String> all() {
        List<String> all = new ArrayList<>(required);
        all.addAll(preferred);
        all.addAll(untiered);
        return all;
    }

    private static List<String> distinct(List<String> labels, SkillCanonicalizer canonicalizer,
                                         Set<String> seen) {
        List<String> out = new ArrayList<>();
        for (String label : nonNull(labels)) {
            String key = label == null ? "" : canonicalizer.canonical(label);
            if (!key.isEmpty() && seen.add(key)) out.add(label);
        }
        return out;
    }

    private static List<String> kept(List<String> asks, Predicate<String> dropped) {
        return asks.stream().filter(dropped.negate()).toList();
    }

    private static List<String> nonNull(List<String> list) {
        return list == null ? List.of() : list;
    }
}
