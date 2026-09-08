package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobContact;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contact extraction driven with the shapes a model actually returns, including the ones the
 * prompt forbids — the rules are strict, and a model will still disobey them sometimes. A letter
 * addressed to "jobs@" is worse than one addressed to nobody.
 */
class JobEnrichmentContactTest {

    private final JobEnrichmentService service = new JobEnrichmentService(
            Mockito.mock(com.autoapplicant.port.out.ai.AiProviderPort.class), new ObjectMapper());

    private static Job bareJob() {
        return Job.builder().id(UUID.randomUUID()).title("Backend-udvikler").companyName("Acme").build();
    }

    private JobContact contactFrom(String contactJson) {
        String response = """
                {"descriptionClean":"Vi søger en backend-udvikler.","aiSummary":"s","aiTags":[],
                 "technologies":[],"skills":[],"contact":%s}""".formatted(contactJson);
        return service.applyEnrichment(bareJob(), response).contact();
    }

    @Test
    void aNamedContactIsKeptWithEveryChannelThePostingGave() {
        JobContact contact = contactFrom("""
                {"name":"Mette Hansen","title":"afdelingsleder","email":"mh@acme.dk","phone":"12 34 56 78"}""");
        assertThat(contact).isNotNull();
        assertThat(contact.display()).isEqualTo("Mette Hansen, afdelingsleder");
        assertThat(contact.phone()).isEqualTo("12 34 56 78");
    }

    @Test
    void noContactObjectMeansNoContact() {
        assertThat(contactFrom("null")).isNull();
        assertThat(contactFrom("""
                {"name":null,"title":null,"email":null,"phone":null}""")).isNull();
    }

    @Test
    void aSharedInboxIsNotAContactPerson() {
        // The prompt forbids these; the mapper enforces it, because the model sometimes complies
        // with the letter of the instruction and not its intent.
        assertThat(contactFrom("{\"name\":null,\"email\":\"jobs@acme.dk\"}")).isNull();
        assertThat(contactFrom("{\"name\":null,\"email\":\"HR@acme.dk\"}")).isNull();
        assertThat(contactFrom("{\"name\":null,\"email\":\"rekruttering@acme.dk\"}")).isNull();
        assertThat(contactFrom("{\"name\":null,\"email\":\"job.ansoegning@acme.dk\"}")).isNull();
        assertThat(contactFrom("{\"name\":null,\"email\":\"karriere-dk@acme.dk\"}")).isNull();
    }

    @Test
    void aNamedPersonAtASharedInboxIsStillAPerson() {
        // The name is what makes it addressable; the address it routes through is not the test.
        JobContact contact = contactFrom("""
                {"name":"Mette Hansen","email":"jobs@acme.dk"}""");
        assertThat(contact).isNotNull();
        assertThat(contact.hasName()).isTrue();
    }

    @Test
    void aDirectPhoneNumberReachesAPersonEvenWithoutAName() {
        JobContact contact = contactFrom("{\"name\":null,\"phone\":\"12 34 56 78\"}");
        assertThat(contact).isNotNull();
        assertThat(contact.phone()).isEqualTo("12 34 56 78");
        assertThat(contact.hasName()).isFalse();
    }

    @Test
    void aPersonalAddressWithoutANameIsKept() {
        assertThat(contactFrom("{\"name\":null,\"email\":\"mette.hansen@acme.dk\"}")).isNotNull();
    }

    @Test
    void aCrawlerSuppliedContactIsNotOverwrittenByExtraction() {
        Job withContact = Job.builder().id(UUID.randomUUID()).title("Backend-udvikler")
                .contact(JobContact.ofNullable("Crawled Person", null, null, null)).build();
        Job enriched = service.applyEnrichment(withContact, """
                {"descriptionClean":"d","aiSummary":"s","aiTags":[],"technologies":[],"skills":[],
                 "contact":{"name":"Model Guess","email":"mg@acme.dk"}}""");
        assertThat(enriched.contact().name()).isEqualTo("Crawled Person");
    }

    @Test
    void aMalformedContactBlockDoesNotBreakTheRestOfEnrichment() {
        Job enriched = service.applyEnrichment(bareJob(), """
                {"descriptionClean":"Vi søger en backend-udvikler.","aiSummary":"Summary",
                 "aiTags":["backend"],"technologies":["Java"],"skills":[],"contact":"Mette Hansen"}""");
        assertThat(enriched.contact()).isNull();
        assertThat(enriched.aiSummary()).isEqualTo("Summary");
        assertThat(enriched.technologies()).containsExactly("Java");
    }
}
