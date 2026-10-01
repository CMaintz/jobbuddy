package com.autoapplicant.domain.user;

import java.util.List;

/**
 * A user-authored CV section that isn't one of the fixed types (e.g. "Publications", "Volunteering").
 * Master-profile data with a stable {@code id}; each item carries its own stable id so AI tailoring
 * can rewrite item text while the assembler validates it back to source (no invented items).
 */
public record CustomSection(String id, String heading, List<CustomSectionItem> items) {}
