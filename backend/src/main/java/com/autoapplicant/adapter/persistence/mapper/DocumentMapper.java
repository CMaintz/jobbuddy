package com.autoapplicant.adapter.persistence.mapper;

import com.autoapplicant.adapter.persistence.entity.CvVersionEntity;
import com.autoapplicant.adapter.persistence.entity.GeneratedDocumentEntity;
import com.autoapplicant.adapter.persistence.entity.PromptTemplateEntity;
import com.autoapplicant.adapter.persistence.entity.WritingProfileEntity;
import com.autoapplicant.domain.document.*;

import java.util.Arrays;
import java.util.List;

public final class DocumentMapper {

    private DocumentMapper() {}

    public static CvVersion toDomain(CvVersionEntity e) {
        return new CvVersion(e.getId(), e.getUserId(), e.getName(), e.getContent(),
                e.getFormat(), e.getFileUrl(), e.isPrimary(), e.getVersionNumber(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    public static CvVersionEntity toEntity(CvVersion d) {
        CvVersionEntity e = new CvVersionEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setName(d.name());
        e.setContent(d.content());
        e.setFormat(d.format());
        e.setFileUrl(d.fileUrl());
        e.setPrimary(d.isPrimary());
        e.setVersionNumber(d.versionNumber());
        return e;
    }

    public static PromptTemplate toDomain(PromptTemplateEntity e) {
        PromptCategory cat = e.getCategory() != null
                ? PromptCategory.valueOf(e.getCategory()) : null;
        return new PromptTemplate(e.getId(), e.getUserId(), e.getName(), cat,
                e.getDescription(), e.getSystemPrompt(), e.getUserPrompt(),
                e.getOutputConstraints(), e.isPublic(), e.getParentTemplateId(),
                e.getVersionNumber(), e.getCreatedAt(), e.getUpdatedAt(), e.isSystem());
    }

    public static PromptTemplateEntity toEntity(PromptTemplate d) {
        PromptTemplateEntity e = new PromptTemplateEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setName(d.name());
        e.setCategory(d.category() != null ? d.category().name() : null);
        e.setDescription(d.description());
        e.setSystemPrompt(d.systemPrompt());
        e.setUserPrompt(d.userPrompt());
        e.setOutputConstraints(d.outputConstraints());
        e.setPublic(d.isPublic());
        e.setSystem(d.isSystem());
        e.setParentTemplateId(d.parentTemplateId());
        e.setVersionNumber(d.versionNumber());
        return e;
    }

    public static GeneratedDocument toDomain(GeneratedDocumentEntity e) {
        return new GeneratedDocument(e.getId(), e.getUserId(), e.getApplicationId(),
                e.getJobId(), DocumentType.valueOf(e.getDocumentType()), e.getContent(),
                e.getPromptTemplateId(), e.getCvVersionId(), e.getModelUsed(),
                e.getTokensUsed(), e.getCreatedAt());
    }

    public static GeneratedDocumentEntity toEntity(GeneratedDocument d) {
        GeneratedDocumentEntity e = new GeneratedDocumentEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setApplicationId(d.applicationId());
        e.setJobId(d.jobId());
        e.setDocumentType(d.documentType().name());
        e.setContent(d.content());
        e.setPromptTemplateId(d.promptTemplateId());
        e.setCvVersionId(d.cvVersionId());
        e.setModelUsed(d.modelUsed());
        e.setTokensUsed(d.tokensUsed());
        return e;
    }

    public static WritingProfile toDomain(WritingProfileEntity e) {
        List<String> patterns = e.getPhrasing_patterns() != null
                ? Arrays.asList(e.getPhrasing_patterns()) : List.of();
        List<String> excerpts = e.getExampleExcerpts() != null
                ? Arrays.asList(e.getExampleExcerpts()) : List.of();
        return new WritingProfile(e.getId(), e.getUserId(), e.getTone(),
                e.getVocabularyNotes(), patterns, excerpts,
                e.getLastAnalyzedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public static WritingProfileEntity toEntity(WritingProfile d) {
        WritingProfileEntity e = new WritingProfileEntity();
        e.setId(d.id());
        e.setUserId(d.userId());
        e.setTone(d.tone());
        e.setVocabularyNotes(d.vocabularyNotes());
        e.setPhrasing_patterns(d.phrasingPatterns() != null
                ? d.phrasingPatterns().toArray(String[]::new) : new String[0]);
        e.setExampleExcerpts(d.exampleExcerpts() != null
                ? d.exampleExcerpts().toArray(String[]::new) : new String[0]);
        e.setLastAnalyzedAt(d.lastAnalyzedAt());
        return e;
    }
}
