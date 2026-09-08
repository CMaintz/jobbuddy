package com.autoapplicant.domain.document;

public enum DocumentType {
    CV,
    COVER_LETTER,
    APPLICATION_TEXT,
    /** Speculative application to a company with no posted vacancy ("uopfordret ansøgning"). */
    UNSOLICITED_APPLICATION,
    RECRUITER_MESSAGE,
    FOLLOW_UP_MESSAGE,
    CV_ANALYSIS_REPORT
}
