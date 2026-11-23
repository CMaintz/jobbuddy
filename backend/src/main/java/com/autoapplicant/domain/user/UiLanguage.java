package com.autoapplicant.domain.user;

import java.util.Locale;
import java.util.Optional;

/** A language the app's UI and emails are written in. */
public enum UiLanguage {
    DA(Locale.forLanguageTag("da-DK")),
    EN(Locale.ENGLISH);

    private final Locale locale;

    UiLanguage(Locale locale) {
        this.locale = locale;
    }

    public Locale locale() {
        return locale;
    }

    /** The two-letter code the frontend and the database use, e.g. "da". */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<UiLanguage> fromCode(String code) {
        if (code == null) return Optional.empty();
        for (UiLanguage language : values()) {
            if (language.code().equalsIgnoreCase(code.trim())) return Optional.of(language);
        }
        return Optional.empty();
    }
}
