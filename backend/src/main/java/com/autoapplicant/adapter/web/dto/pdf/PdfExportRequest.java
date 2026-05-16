package com.autoapplicant.adapter.web.dto.pdf;

import java.util.UUID;

public record PdfExportRequest(String content, UUID pdfTemplateId) {}
