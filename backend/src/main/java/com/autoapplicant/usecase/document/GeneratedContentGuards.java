package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.usecase.ai.RetractedClaimsGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Runtime integrity checks applied to EVERY generated candidate-facing document — cover letters,
 * application texts, AND tailored CVs — so the deterministic backstops are identical across paths:
 * the model-free {@link DocumentFactGuard} (invented/inflated metrics), the
 * {@link RetractedClaimsGuard} (disowned claims resurfacing), and the {@link ClicheGuard}
 * (application filler and AI-tell phrasing). Config-gated per guard.
 */
@Service
public class GeneratedContentGuards {

    private static final Logger log = LoggerFactory.getLogger(GeneratedContentGuards.class);

    private final DocumentFactGuard factGuard;
    private final RetractedClaimsGuard retractedClaimsGuard;
    private final ClicheGuard clicheGuard;

    @Value("${app.ai.fact-guard.enabled:true}")
    private boolean factGuardEnabled;

    @Value("${app.ai.fact-guard.mode:warn}")
    private String factGuardMode;

    @Value("${app.ai.retracted-claims.mode:warn}")
    private String retractedClaimsMode;

    @Value("${app.ai.cliche-guard.enabled:true}")
    private boolean clicheGuardEnabled;

    @Value("${app.ai.cliche-guard.mode:warn}")
    private String clicheGuardMode;

    public GeneratedContentGuards(DocumentFactGuard factGuard, RetractedClaimsGuard retractedClaimsGuard,
                                  ClicheGuard clicheGuard) {
        this.factGuard = factGuard;
        this.retractedClaimsGuard = retractedClaimsGuard;
        this.clicheGuard = clicheGuard;
    }

    /**
     * Runs the fact, retracted-claims, and filler gates over generated text. Warns or blocks per
     * config, and returns what was found so the caller can put it in the document's ATS report —
     * a finding the user never sees is a finding that never gets fixed.
     */
    public ContentGuardFindings verify(UUID userId, String body, String sourceProfileJson, String documentType) {
        DocumentFactGuard.FactAudit factAudit = auditFacts(body, sourceProfileJson, documentType);
        return new ContentGuardFindings(
                factAudit.inventedMetrics(),
                applyRetractedClaimsGuard(userId, body, documentType),
                applyClicheGuard(body, documentType),
                factAudit.unverifiedMetrics());
    }

    /**
     * Runs the fact gate. Only the invented tier can block: a figure the profile contains under a
     * different noun is nearly always a rewording, and failing generation on one would make the
     * strict mode unusable.
     */
    private DocumentFactGuard.FactAudit auditFacts(String body, String sourceProfileJson, String documentType) {
        if (!factGuardEnabled) return new DocumentFactGuard.FactAudit(List.of(), List.of());
        DocumentFactGuard.FactAudit audit = factGuard.audit(body, sourceProfileJson);
        if (!audit.unverifiedMetrics().isEmpty()) {
            log.info("Fact guard: figure(s) in {} counting something the profile attaches elsewhere: {}",
                    label(documentType), audit.unverifiedMetrics());
        }
        if (audit.inventedMetrics().isEmpty()) return audit;
        String msg = "Fact guard: metric claim(s) not supported by the profile in "
                + label(documentType) + ": " + audit.inventedMetrics();
        if ("block".equalsIgnoreCase(factGuardMode)) {
            throw new IllegalStateException(msg + " — generation blocked (app.ai.fact-guard.mode=block)");
        }
        log.warn(msg);
        return audit;
    }

    private List<String> applyRetractedClaimsGuard(UUID userId, String body, String documentType) {
        List<String> violations = retractedClaimsGuard.findViolations(userId, body);
        if (violations.isEmpty()) return List.of();
        String msg = "Retracted claim(s) resurfaced in " + label(documentType) + ": " + violations;
        if ("block".equalsIgnoreCase(retractedClaimsMode)) {
            throw new IllegalStateException(msg + " — generation blocked (app.ai.retracted-claims.mode=block)");
        }
        log.warn(msg);
        return violations;
    }

    /**
     * Filler/AI-tell phrases that survived generation and review. Warn-only by default: a cliché is
     * a quality signal, not a correctness failure, and blocking on one would fail a document the
     * user could still edit.
     */
    private List<String> applyClicheGuard(String body, String documentType) {
        if (!clicheGuardEnabled) return List.of();
        ClicheGuard.ClicheAudit audit = clicheGuard.audit(body);
        if (audit.clean()) return List.of();
        String msg = "Cliche guard: filler phrase(s) survived review in "
                + label(documentType) + ": " + audit.phrases();
        if ("block".equalsIgnoreCase(clicheGuardMode)) {
            throw new IllegalStateException(msg + " — generation blocked (app.ai.cliche-guard.mode=block)");
        }
        log.warn(msg);
        return audit.phrases();
    }

    private static String label(String documentType) {
        return documentType != null ? documentType : "document";
    }
}
