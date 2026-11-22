package com.autoapplicant.domain.document;

import java.util.regex.Pattern;

/**
 * Her settings for generating documents, as the app's quick apply passes them: each may be null
 * for the default. They come from the browser, so anything not shaped like one becomes the
 * default instead of reaching a prompt.
 *
 * @param lengthPreference such as {@code STANDARD}
 * @param cvTemplate the CV template id
 * @param voice such as {@code Warm}
 * @param language her default document language, {@code Dansk} or {@code English}
 */
public record GenerationSettings(String lengthPreference, String cvTemplate, String voice,
                                 String language) {

    private static final Pattern LENGTH = Pattern.compile("[A-Z_]{1,20}");
    private static final Pattern TEMPLATE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern VOICE = Pattern.compile("[A-Za-z]{1,20}");
    private static final Pattern LANGUAGE = Pattern.compile("Dansk|English");

    public static final GenerationSettings DEFAULTS =
            new GenerationSettings(null, null, null, null);

    public GenerationSettings {
        lengthPreference = shaped(lengthPreference, LENGTH);
        cvTemplate = shaped(cvTemplate, TEMPLATE);
        voice = shaped(voice, VOICE);
        language = shaped(language, LANGUAGE);
    }

    /** The instruction quick apply sends with her voice, or null for none. */
    public String instructions() {
        return voice == null ? null : "Voice: " + voice + ".";
    }

    private static String shaped(String value, Pattern shape) {
        return value != null && shape.matcher(value).matches() ? value : null;
    }
}
