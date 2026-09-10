package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillCanonicalizerTest {

    private SkillCanonicalizer canonicalizerOver(SkillTaxonomy... rows) {
        SkillTaxonomyRepositoryPort repo = mock(SkillTaxonomyRepositoryPort.class);
        when(repo.findAll()).thenReturn(List.of(rows));
        return new SkillCanonicalizer(repo);
    }

    private SkillTaxonomy row(String name, String normalized, List<String> aliases) {
        return new SkillTaxonomy(null, name, normalized, null, "DevOps", aliases);
    }

    @Test
    void an_alias_and_the_canonical_name_resolve_to_the_same_key() {
        SkillCanonicalizer c = canonicalizerOver(
                row("Kubernetes", "kubernetes", List.of("k8s", "kube")));

        assertThat(c.canonical("k8s")).isEqualTo("kubernetes");
        assertThat(c.canonical("K8S")).isEqualTo("kubernetes");
        assertThat(c.canonical("Kubernetes")).isEqualTo("kubernetes");
        assertThat(c.canonical("kube")).isEqualTo(c.canonical("Kubernetes"));
    }

    @Test
    void an_unknown_label_maps_to_itself_normalised() {
        SkillCanonicalizer c = canonicalizerOver(
                row("Kubernetes", "kubernetes", List.of("k8s")));

        // Nothing is silently merged onto the wrong skill.
        assertThat(c.canonical("Elixir")).isEqualTo("elixir");
        assertThat(c.canonical("  SomeTool ")).isEqualTo("sometool");
    }

    @Test
    void a_distinct_taxonomy_row_is_never_captured_as_another_rows_alias() {
        // "tf" is Terraform's alias; TensorFlow is its own row and must keep its own key even if a
        // careless alias tried to claim it.
        SkillCanonicalizer c = canonicalizerOver(
                row("Terraform", "terraform", List.of("tf", "tensorflow")),
                row("TensorFlow", "tensorflow", List.of()));

        assertThat(c.canonical("tf")).isEqualTo("terraform");
        assertThat(c.canonical("TensorFlow")).isEqualTo("tensorflow");
    }

    @Test
    void surface_forms_expand_a_known_label_and_always_include_the_label_itself() {
        SkillCanonicalizer c = canonicalizerOver(
                row("Kubernetes", "kubernetes", List.of("k8s", "kube")));

        assertThat(c.surfaceForms("Kubernetes"))
                .contains("Kubernetes", "kubernetes", "k8s", "kube");
        // Reached via an alias, we still get the whole group.
        assertThat(c.surfaceForms("k8s"))
                .contains("k8s", "Kubernetes", "kube");
        // An unknown label still yields itself, so it is searched for verbatim.
        assertThat(c.surfaceForms("Elixir")).containsExactly("Elixir");
    }

    @Test
    void blank_input_is_handled() {
        SkillCanonicalizer c = canonicalizerOver(row("Go", "go", List.of("golang")));

        assertThat(c.canonical(null)).isEmpty();
        assertThat(c.canonical("  ")).isEmpty();
        assertThat(c.surfaceForms(null)).isEmpty();
        assertThat(c.surfaceForms("")).isEmpty();
    }
}
