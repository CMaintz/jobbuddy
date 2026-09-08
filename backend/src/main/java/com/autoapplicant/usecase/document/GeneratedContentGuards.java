package com.autoapplicant.usecase.document;

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
 * the model-free {@link DocumentFactGuard} (invented/inflated metrics) and the
 * {@link RetractedClaimsGuard} (disowned claims resurfacing). Config-gated per guard.
 */
@Service
public class GeneratedContentGuards {

    private static final Logger log = LoggerFactory.getLogger(GeneratedContentGuards.class);

    private final DocumentFactGuard factGuard;
    private final RetractedClaimsGuard retractedClaimsGuard;

    @Value("${app.ai.fact-guard.enabled:true}")
    private boolean factGuardEnabled;

    @Value("${app.ai.fact-guard.mode:warn}")
    private String factGuardMode;

    @Value("${app.ai.retracted-claims.mode:warn}")
    private String retractedClaimsMode;

    public GeneratedContentGuards(DocumentFactGuard factGuard, RetractedClaimsGuard retractedClaimsGuard) {
        this.factGuard = factGuard;
        this.retractedClaimsGuard = retractedClaimsGuard;
    }

    /** Runs the fact gate and retracted-claims gate over generated text. Warns or blocks per config. */
    public void verify(UUID userId, String body, String sourceProfileJson, String documentType) {
        applyFactGuard(body, sourceProfileJson, documentType);
        applyRetractedClaimsGuard(userId, body, documentType);
    }

    private void applyFactGuard(String body, String sourceProfileJson, String documentType) {
        if (!factGuardEnabled) return;
        DocumentFactGuard.FactAudit audit = factGuard.audit(body, sourceProfileJson);
        if (audit.clean()) return;
        String msg = "Fact guard: metric claim(s) not supported by the profile in "
                + label(documentType) + ": " + audit.inventedMetrics();
        if ("block".equalsIgnoreCase(factGuardMode)) {
            throw new IllegalStateException(msg + " — generation blocked (app.ai.fact-guard.mode=block)");
        }
        log.warn(msg);
    }

    private void applyRetractedClaimsGuard(UUID userId, String body, String documentType) {
        List<String> violations = retractedClaimsGuard.findViolations(userId, body);
        if (violations.isEmpty()) return;
        String msg = "Retracted claim(s) resurfaced in " + label(documentType) + ": " + violations;
        if ("block".equalsIgnoreCase(retractedClaimsMode)) {
            throw new IllegalStateException(msg + " — generation blocked (app.ai.retracted-claims.mode=block)");
        }
        log.warn(msg);
    }

    private static String label(String documentType) {
        return documentType != null ? documentType : "document";
    }
}
