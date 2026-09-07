package com.autoapplicant.domain.document.structured;

public record AtsCheck(
        String code,
        String label,
        String status,
        String detail
) {}
