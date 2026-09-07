package com.autoapplicant.adapter.web.dto.application;

public record UpdateRecruiterInfoRequest(
        String recruiterName,
        String recruiterEmail,
        String recruiterMessage,
        String recruiterReply
) {}
