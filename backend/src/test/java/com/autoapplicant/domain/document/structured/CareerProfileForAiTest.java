package com.autoapplicant.domain.document.structured;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CareerProfileForAiTest {

    /** Components whose items carry links; withoutLinks rebuilds these and nothing else. */
    private static final Set<String> LINK_BEARING = Set.of(
            "experience", "projects", "education", "certifications", "customSections");

    /** Identity fields CLAUDE.md's privacy invariant keeps out of the AI payload. */
    private static final Pattern IDENTITY_NAME =
            Pattern.compile("(?i)^(full)?name$|email|phone|photo|linkedin|github|website");

    /**
     * A tripwire for the privacy invariant: adding an identity field anywhere in the payload's
     * record tree should be a deliberate decision, not a quiet addition. The behavioural guard
     * (no identity value in the serialized payload) lives in CareerProfileContextServiceTest.
     */
    @Test
    void no_component_in_the_payload_tree_is_an_identity_field() {
        List<String> identityLike = new ArrayList<>();
        collectIdentityLike(CareerProfileForAi.class, identityLike);

        assertThat(identityLike).isEmpty();
    }

    private static void collectIdentityLike(Class<?> recordType, List<String> found) {
        for (RecordComponent component : recordType.getRecordComponents()) {
            if (IDENTITY_NAME.matcher(component.getName()).find()) {
                found.add(recordType.getSimpleName() + "." + component.getName());
            }
            nestedRecords(component.getGenericType())
                    .forEach(nested -> collectIdentityLike(nested, found));
        }
    }

    private static List<Class<?>> nestedRecords(Type type) {
        if (type instanceof Class<?> cls && cls.isRecord()) {
            return List.of(cls);
        }
        if (type instanceof ParameterizedType parameterized) {
            return Arrays.stream(parameterized.getActualTypeArguments())
                    .flatMap(arg -> nestedRecords(arg).stream())
                    .toList();
        }
        return List.of();
    }

    /**
     * withoutLinks re-lists every component positionally, so a swapped or dropped argument would
     * compile and quietly send the AI the wrong field. Each component gets a distinct value here
     * and must come back as that same instance.
     */
    @Test
    void without_links_keeps_every_other_component_in_place() throws Exception {
        RecordComponent[] components = CareerProfileForAi.class.getRecordComponents();
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            values[i] = distinctValue(components[i], i);
        }
        Constructor<CareerProfileForAi> ctor = CareerProfileForAi.class.getDeclaredConstructor(
                Arrays.stream(components).map(RecordComponent::getType)
                        .toArray(Class<?>[]::new));
        CareerProfileForAi original = ctor.newInstance(values);

        CareerProfileForAi stripped = original.withoutLinks();

        for (RecordComponent component : components) {
            Object before = component.getAccessor().invoke(original);
            Object after = component.getAccessor().invoke(stripped);
            if (LINK_BEARING.contains(component.getName())) {
                assertThat(after).as(component.getName()).isEqualTo(before);
            } else {
                assertThat(after).as(component.getName()).isSameAs(before);
            }
        }
    }

    private static Object distinctValue(RecordComponent component, int index) {
        Class<?> type = component.getType();
        String marker = "value-" + index;
        if (type == String.class) {
            return marker;
        }
        if (type == Map.class) {
            return Map.of(marker, marker);
        }
        if (type == List.class) {
            return LINK_BEARING.contains(component.getName())
                    ? List.of()
                    : new ArrayList<>(List.of(marker));
        }
        throw new IllegalStateException("Add a value for " + component.getName());
    }
}
