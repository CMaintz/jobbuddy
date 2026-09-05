package com.autoapplicant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Ai ai = new Ai();
    private Openai openai = new Openai();
    private Gemini gemini = new Gemini();
    private LinkedIn linkedin = new LinkedIn();
    private Careerjet careerjet = new Careerjet();
    private Greenhouse greenhouse = new Greenhouse();
    private Lever lever = new Lever();
    private Teamtailor teamtailor = new Teamtailor();
    private CornerstoneOnDemand cornerstoneOnDemand = new CornerstoneOnDemand();

    public Ai getAi() { return ai; }
    public Openai getOpenai() { return openai; }
    public Gemini getGemini() { return gemini; }
    public LinkedIn getLinkedin() { return linkedin; }
    public Careerjet getCareerjet() { return careerjet; }
    public Greenhouse getGreenhouse() { return greenhouse; }
    public Lever getLever() { return lever; }
    public Teamtailor getTeamtailor() { return teamtailor; }
    public CornerstoneOnDemand getCornerstoneOnDemand() { return cornerstoneOnDemand; }

    public static class Ai {
        private String enrichmentProvider = "gemini";
        private String generationProvider = "openai";
        /** Spend tier per operation class: economy | standard | premium. Resolves to a model via the provider's tier map. */
        private String enrichmentTier = "economy";
        private String generationTier = "standard";
        private Cli cli = new Cli();
        public String getEnrichmentProvider() { return enrichmentProvider; }
        public void setEnrichmentProvider(String enrichmentProvider) { this.enrichmentProvider = enrichmentProvider; }
        public String getGenerationProvider() { return generationProvider; }
        public void setGenerationProvider(String generationProvider) { this.generationProvider = generationProvider; }
        public String getEnrichmentTier() { return enrichmentTier; }
        public void setEnrichmentTier(String enrichmentTier) { this.enrichmentTier = enrichmentTier; }
        public String getGenerationTier() { return generationTier; }
        public void setGenerationTier(String generationTier) { this.generationTier = generationTier; }
        public Cli getCli() { return cli; }
        public void setCli(Cli cli) { this.cli = cli; }
    }

    /**
     * Local CLI-agent generation provider (Claude Code / Codex). Used when
     * {@code app.ai.generation-provider} is {@code claude-cli}/{@code codex}/{@code cli}: prompts are
     * piped to the agent's stdin so generation runs on a flat-fee subscription instead of API calls.
     * Embeddings are never produced here — keep a real API provider for enrichment.
     */
    public static class Cli {
        /** Command + fixed args; the prompt is piped to stdin. e.g. "claude -p" or "codex exec". */
        private String command = "claude -p";
        private int timeoutSeconds = 120;
        /** Label reported as the model name for provenance in saved documents. */
        private String modelLabel = "cli-agent";
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public String getModelLabel() { return modelLabel; }
        public void setModelLabel(String modelLabel) { this.modelLabel = modelLabel; }
    }

    public static class Openai {
        private String apiKey;
        private String model;
        private String embeddingModel;
        // Per-tier chat models; blank falls back to `model`.
        private String economyModel;
        private String standardModel;
        private String premiumModel;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
        public String getEconomyModel() { return economyModel; }
        public void setEconomyModel(String economyModel) { this.economyModel = economyModel; }
        public String getStandardModel() { return standardModel; }
        public void setStandardModel(String standardModel) { this.standardModel = standardModel; }
        public String getPremiumModel() { return premiumModel; }
        public void setPremiumModel(String premiumModel) { this.premiumModel = premiumModel; }
    }

    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-2.5-flash";
        private String embeddingModel = "gemini-embedding-001";
        private String economyModel;
        private String standardModel;
        private String premiumModel;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
        public String getEconomyModel() { return economyModel; }
        public void setEconomyModel(String economyModel) { this.economyModel = economyModel; }
        public String getStandardModel() { return standardModel; }
        public void setStandardModel(String standardModel) { this.standardModel = standardModel; }
        public String getPremiumModel() { return premiumModel; }
        public void setPremiumModel(String premiumModel) { this.premiumModel = premiumModel; }
    }

    public static class LinkedIn {
        private String clientId;
        private String clientSecret;
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }

    public static class Careerjet {
        private String affiliateId = "";
        public String getAffiliateId() { return affiliateId; }
        public void setAffiliateId(String affiliateId) { this.affiliateId = affiliateId; }
    }

    public static class Greenhouse {
        private java.util.List<String> companies = new java.util.ArrayList<>();
        public java.util.List<String> getCompanies() { return companies; }
        public void setCompanies(java.util.List<String> companies) { this.companies = companies; }
    }

    public static class Lever {
        private java.util.List<String> companies = new java.util.ArrayList<>();
        public java.util.List<String> getCompanies() { return companies; }
        public void setCompanies(java.util.List<String> companies) { this.companies = companies; }
    }

    public static class Teamtailor {
        /** List of career-page base URLs, e.g. https://jobs.example.com */
        private java.util.List<String> careerPageUrls = new java.util.ArrayList<>();
        public java.util.List<String> getCareerPageUrls() { return careerPageUrls; }
        public void setCareerPageUrls(java.util.List<String> urls) { this.careerPageUrls = urls; }
    }

    public static class CornerstoneOnDemand {
        public static class Tenant {
            private String name;
            private int siteId;
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public int getSiteId() { return siteId; }
            public void setSiteId(int siteId) { this.siteId = siteId; }
        }
        private java.util.List<Tenant> tenants = new java.util.ArrayList<>();
        public java.util.List<Tenant> getTenants() { return tenants; }
        public void setTenants(java.util.List<Tenant> tenants) { this.tenants = tenants; }
    }
}
