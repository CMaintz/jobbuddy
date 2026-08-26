package com.autoapplicant.adapter.web.dto.company;

import java.util.UUID;

/** Start tracking a company as an unsolicited-outreach target. */
public record TrackOutreachRequest(UUID companyId, String companyName, String contactName) {}
