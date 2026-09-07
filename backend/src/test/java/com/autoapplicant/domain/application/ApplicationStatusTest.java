package com.autoapplicant.domain.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStatusTest {

    // ── Valid forward transitions ─────────────────────────────────────────────

    @Test
    void saved_can_transition_to_preparing() {
        assertThat(SAVED.canTransitionTo(PREPARING)).isTrue();
    }

    @Test
    void saved_can_transition_to_archived() {
        assertThat(SAVED.canTransitionTo(ARCHIVED)).isTrue();
    }

    @Test
    void preparing_can_transition_to_applied() {
        assertThat(PREPARING.canTransitionTo(APPLIED)).isTrue();
    }

    @Test
    void preparing_can_go_back_to_saved() {
        assertThat(PREPARING.canTransitionTo(SAVED)).isTrue();
    }

    @Test
    void applied_can_transition_to_recruiter_contact() {
        assertThat(APPLIED.canTransitionTo(RECRUITER_CONTACT)).isTrue();
    }

    @Test
    void applied_can_transition_to_rejected() {
        assertThat(APPLIED.canTransitionTo(REJECTED)).isTrue();
    }

    @Test
    void recruiter_contact_can_transition_to_interview() {
        assertThat(RECRUITER_CONTACT.canTransitionTo(INTERVIEW)).isTrue();
    }

    @Test
    void interview_can_branch_to_technical_test_final_round_offer_rejected() {
        assertThat(INTERVIEW.canTransitionTo(TECHNICAL_TEST)).isTrue();
        assertThat(INTERVIEW.canTransitionTo(FINAL_ROUND)).isTrue();
        assertThat(INTERVIEW.canTransitionTo(OFFER)).isTrue();
        assertThat(INTERVIEW.canTransitionTo(REJECTED)).isTrue();
    }

    @Test
    void technical_test_can_transition_to_final_round() {
        assertThat(TECHNICAL_TEST.canTransitionTo(FINAL_ROUND)).isTrue();
    }

    @Test
    void final_round_can_transition_to_offer() {
        assertThat(FINAL_ROUND.canTransitionTo(OFFER)).isTrue();
    }

    @Test
    void offer_can_only_be_archived() {
        assertThat(OFFER.canTransitionTo(ARCHIVED)).isTrue();
    }

    @Test
    void any_non_terminal_status_can_be_archived() {
        for (ApplicationStatus s : new ApplicationStatus[]{
                SAVED, PREPARING, APPLIED, RECRUITER_CONTACT,
                INTERVIEW, TECHNICAL_TEST, FINAL_ROUND}) {
            assertThat(s.canTransitionTo(ARCHIVED))
                    .as("Expected %s → ARCHIVED to be valid", s)
                    .isTrue();
        }
    }

    // ── Invalid transitions ───────────────────────────────────────────────────

    @Test
    void rejected_is_terminal() {
        for (ApplicationStatus next : ApplicationStatus.values()) {
            assertThat(REJECTED.canTransitionTo(next))
                    .as("REJECTED should not transition to %s", next)
                    .isFalse();
        }
    }

    @Test
    void archived_is_terminal() {
        for (ApplicationStatus next : ApplicationStatus.values()) {
            assertThat(ARCHIVED.canTransitionTo(next))
                    .as("ARCHIVED should not transition to %s", next)
                    .isFalse();
        }
    }

    @Test
    void saved_cannot_skip_to_applied() {
        assertThat(SAVED.canTransitionTo(APPLIED)).isFalse();
    }

    @Test
    void saved_cannot_jump_to_offer() {
        assertThat(SAVED.canTransitionTo(OFFER)).isFalse();
    }

    @Test
    void applied_cannot_go_backwards_to_saved() {
        assertThat(APPLIED.canTransitionTo(SAVED)).isFalse();
    }

    @Test
    void offer_cannot_transition_to_rejected() {
        assertThat(OFFER.canTransitionTo(REJECTED)).isFalse();
    }

    @Test
    void offer_cannot_transition_to_interview() {
        assertThat(OFFER.canTransitionTo(INTERVIEW)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class,
            names = {"RECRUITER_CONTACT", "INTERVIEW", "TECHNICAL_TEST", "FINAL_ROUND", "OFFER"})
    void saved_cannot_jump_to_late_stages(ApplicationStatus lateStage) {
        assertThat(SAVED.canTransitionTo(lateStage)).isFalse();
    }

    @Test
    void no_self_transition_allowed() {
        for (ApplicationStatus s : ApplicationStatus.values()) {
            assertThat(s.canTransitionTo(s))
                    .as("Self-transition on %s should be false", s)
                    .isFalse();
        }
    }
}
