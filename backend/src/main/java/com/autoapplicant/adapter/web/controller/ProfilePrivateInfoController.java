package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.port.in.user.ManageProfilePrivateInfoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile/private")
@Tag(name = "Profile Private Info")
public class ProfilePrivateInfoController {

    private final ManageProfilePrivateInfoUseCase useCase;
    private final SecurityContextHelper secCtx;

    public ProfilePrivateInfoController(ManageProfilePrivateInfoUseCase useCase,
                                        SecurityContextHelper secCtx) {
        this.useCase = useCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Get private profile info (PII)")
    @GetMapping
    public ProfilePrivateInfo getPrivateInfo() {
        return useCase.getPrivateInfo(secCtx.getCurrentUserId());
    }

    @Operation(summary = "Update private profile info (PII). Merge semantics: omitted/null fields keep their current value; send an empty string to clear a field.")
    @RequestMapping(method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ProfilePrivateInfo updatePrivateInfo(@RequestBody ProfilePrivateInfo info) {
        return useCase.updatePrivateInfo(secCtx.getCurrentUserId(), info);
    }
}
