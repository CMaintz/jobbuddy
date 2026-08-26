package com.autoapplicant.domain.company;

import java.util.Map;

/**
 * One reason a company was ranked where it was, as a code plus its arguments rather than a
 * finished sentence.
 *
 * <p>The alternative — English prose built server-side — put English into a Danish UI. The server
 * has no reliable way to know the user's interface language (unlike a generated document, which
 * knows its own), so the reason travels as data and the frontend renders it through the
 * translation it already has.
 *
 * @param code stable key, e.g. {@code skillOverlap}; the frontend maps it to
 *             {@code companies.targets.reason.<code>}
 * @param args interpolation values for that message, e.g. {@code {skills: "Java, Kubernetes"}}
 */
public record OutreachReason(String code, Map<String, String> args) {

    public OutreachReason {
        args = args != null ? Map.copyOf(args) : Map.of();
    }

    public static OutreachReason of(String code) {
        return new OutreachReason(code, Map.of());
    }

    public static OutreachReason of(String code, String argName, Object argValue) {
        return new OutreachReason(code, Map.of(argName, String.valueOf(argValue)));
    }
}
