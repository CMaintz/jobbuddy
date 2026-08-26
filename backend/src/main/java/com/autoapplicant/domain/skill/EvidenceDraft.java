package com.autoapplicant.domain.skill;

import java.util.List;

/**
 * A free-text answer restructured into the three parts a letter can cite — offered to the user for
 * confirmation, never saved directly.
 *
 * @param unsupportedFigures numbers in the draft that do not appear in what the user actually
 *                           wrote. Should always be empty; when it is not, the model embellished
 *                           and the UI must show the user what it added before they accept it.
 */
public record EvidenceDraft(String skillName, String situation, String action, String result,
                            List<String> unsupportedFigures) {

    public boolean isFaithful() {
        return unsupportedFigures.isEmpty();
    }
}
