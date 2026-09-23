package com.autoapplicant.adapter.web.dto.company;

/** Payload for saving a company's research notes; a blank value clears them. */
public record SaveCompanyResearchRequest(String notes) {}
