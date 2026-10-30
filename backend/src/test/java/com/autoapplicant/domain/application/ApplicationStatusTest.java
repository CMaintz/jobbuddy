package com.autoapplicant.domain.application;

import static com.autoapplicant.domain.application.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApplicationStatusTest {

    // The whole pipeline as a table: one row per source state with its exact allowed targets,
    // so widening a set (e.g. letting APPLIED skip straight to INTERVIEW) fails as surely as
    // narrowing one.
    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(SAVED, EnumSet.of(PREPARING, ARCHIVED)),
                Arguments.of(PREPARING, EnumSet.of(APPLIED, SAVED, ARCHIVED)),
                Arguments.of(APPLIED, EnumSet.of(RECRUITER_CONTACT, REJECTED, ARCHIVED)),
                Arguments.of(RECRUITER_CONTACT, EnumSet.of(INTERVIEW, REJECTED, ARCHIVED)),
                Arguments.of(
                        INTERVIEW, EnumSet.of(TECHNICAL_TEST, FINAL_ROUND, OFFER, REJECTED, ARCHIVED)),
                Arguments.of(TECHNICAL_TEST, EnumSet.of(FINAL_ROUND, REJECTED, ARCHIVED)),
                Arguments.of(FINAL_ROUND, EnumSet.of(OFFER, REJECTED, ARCHIVED)),
                Arguments.of(OFFER, EnumSet.of(ARCHIVED)),
                Arguments.of(REJECTED, EnumSet.noneOf(ApplicationStatus.class)),
                Arguments.of(ARCHIVED, EnumSet.noneOf(ApplicationStatus.class)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allowedTransitions")
    void allows_exactly_the_listed_transitions(
            ApplicationStatus from, Set<ApplicationStatus> expected) {
        Set<ApplicationStatus> actual =
                Arrays.stream(ApplicationStatus.values())
                        .filter(from::canTransitionTo)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApplicationStatus.class)));

        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> employerDriven() {
        return Stream.of(
                Arguments.of(SAVED, false),
                Arguments.of(PREPARING, false),
                Arguments.of(APPLIED, false),
                Arguments.of(RECRUITER_CONTACT, true),
                Arguments.of(INTERVIEW, true),
                Arguments.of(TECHNICAL_TEST, true),
                Arguments.of(FINAL_ROUND, true),
                Arguments.of(OFFER, true),
                Arguments.of(REJECTED, true),
                Arguments.of(ARCHIVED, false));
    }

    @ParameterizedTest(name = "{0} employer-driven = {1}")
    @MethodSource("employerDriven")
    void marks_only_the_employers_moves_as_employer_driven(
            ApplicationStatus status, boolean expected) {
        assertThat(status.isEmployerDriven()).isEqualTo(expected);
    }
}
