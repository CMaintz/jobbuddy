package com.autoapplicant.domain.document.structured;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CareerProfileForAiTest {

    /** Components whose items carry links; withoutLinks rebuilds these and nothing else. */
    private static final Set<String> LINK_BEARING = Set.of(
            "experience", "projects", "education", "certifications", "customSections");

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
