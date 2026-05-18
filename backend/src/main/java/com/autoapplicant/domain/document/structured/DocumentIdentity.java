package com.autoapplicant.domain.document.structured;

public record DocumentIdentity(
        String name,
        String headline,
        String email,
        String phone,
        String location,
        String linkedinUrl,
        String githubUrl,
        String websiteUrl,
        String profileImageUrl
) {}
