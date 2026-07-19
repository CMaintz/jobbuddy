package com.autoapplicant.domain.interview;

/** One exchange in a mock-interview roleplay. Role is "interviewer" or "candidate". */
public record MockInterviewTurn(String role, String content) {}
