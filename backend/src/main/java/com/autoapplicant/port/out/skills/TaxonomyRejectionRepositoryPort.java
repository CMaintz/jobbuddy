package com.autoapplicant.port.out.skills;

import java.util.Set;

/** The labels an admin has ruled out of the skill taxonomy. */
public interface TaxonomyRejectionRepositoryPort {

    /** Normalized names never to offer again. */
    Set<String> findRejectedNormalizedNames();

    /** Records a rejection; re-rejecting a label already ruled out changes nothing. */
    void reject(String normalizedName, String label, String reason);
}
