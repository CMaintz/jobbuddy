package com.autoapplicant.usecase.document;

import static com.autoapplicant.usecase.document.IdentityLadenProfile.CREDENTIAL_URL;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.IDENTITY_VALUES;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.PROJECT_GITHUB_URL;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.PROJECT_LIVE_URL;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.PROJECT_NAME;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import org.junit.jupiter.api.Test;

/** The privacy invariant at the source: the JSON every AI caller sends carries no identity. */
class CareerProfileContextServiceTest {

    private final CareerProfileContextService service = IdentityLadenProfile.contextService();

    @Test
    void aiPayloadCarriesNoIdentityValues() {
        String json = service.buildJson(USER_ID);

        assertThat(json).contains(PROJECT_NAME); // the payload is populated, not just empty
        IDENTITY_VALUES.forEach(value -> assertThat(json).doesNotContain(value));
    }

    @Test
    void serverSideProfileKeepsLinksForTheDocument() {
        CareerProfileForAi full = service.build(USER_ID);

        assertThat(full.projects().get(0).links())
                .containsExactly(PROJECT_GITHUB_URL, PROJECT_LIVE_URL);
        assertThat(full.certifications().get(0).links()).containsExactly(CREDENTIAL_URL);
        assertThat(full.withoutLinks().projects().get(0).links()).isEmpty();
        assertThat(full.withoutLinks().certifications().get(0).links()).isEmpty();
    }
}
