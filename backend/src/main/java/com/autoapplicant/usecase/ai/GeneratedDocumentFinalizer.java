package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.domain.document.structured.JobKeywords;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.port.out.document.BuildApplicationDocumentPort;
import com.autoapplicant.port.out.document.PersistGeneratedDocumentPort;
import com.autoapplicant.usecase.document.GeneratedContentGuards;
import java.util.Arrays;
import org.springframework.stereotype.Service;

/**
 * Turns a reviewed document body into a saved {@link StructuredDocument}: runs the deterministic
 * content guards (fact gate + retracted claims + filler), assembles the structured document with the
 * findings riding along into its ATS report, and persists it. The guards run here, on the way out —
 * the property {@code GuardedPathsTest} protects.
 */
@Service
public class GeneratedDocumentFinalizer {

    private final GeneratedContentGuards contentGuards;
    private final BuildApplicationDocumentPort buildApplicationDocument;
    private final PersistGeneratedDocumentPort persistGeneratedDocument;

    public GeneratedDocumentFinalizer(GeneratedContentGuards contentGuards,
                                      BuildApplicationDocumentPort buildApplicationDocument,
                                      PersistGeneratedDocumentPort persistGeneratedDocument) {
        this.contentGuards = contentGuards;
        this.buildApplicationDocument = buildApplicationDocument;
        this.persistGeneratedDocument = persistGeneratedDocument;
    }

    public StructuredDocument finalizeDocument(GenerateDocumentCommand cmd, GenerationInputs in,
                                               String body, String modelName) {
        // Deterministic backstops (fact gate + retracted claims + filler), shared with the CV path.
        // The findings ride along into the ATS report so the user sees them.
        ContentGuardFindings guardFindings =
                contentGuards.verify(cmd.userId(), body, in.contactFreeJson(), cmd.documentType());
        // Keyword coverage is measured downstream against the delivered text and the posting's own
        // tiered asks — the model is no longer asked to grade itself.
        StructuredDocument doc = buildApplicationDocument.buildApplicationDocument(
                cmd.userId(), parseDocumentType(cmd.documentType()), body, cmd.templateId(),
                JobKeywords.of(in.job()), cmd.showProfileImage(), cmd.theme(), guardFindings);
        return persistGeneratedDocument.save(cmd.userId(), cmd.jobId(), doc, modelName);
    }

    private static DocumentType parseDocumentType(String type) {
        if (type == null) {
            return DocumentType.COVER_LETTER;
        }
        try {
            return DocumentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid document type: " + type
                    + ". Valid types: " + Arrays.toString(DocumentType.values()));
        }
    }
}
