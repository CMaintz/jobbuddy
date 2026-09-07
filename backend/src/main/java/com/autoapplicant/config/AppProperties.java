package com.autoapplicant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Ai ai = new Ai();
    private Openai openai = new Openai();
    private Gemini gemini = new Gemini();
    private Typesense typesense = new Typesense();
    private LinkedIn linkedin = new LinkedIn();
    private Careerjet careerjet = new Careerjet();
    private Greenhouse greenhouse = new Greenhouse();
    private Lever lever = new Lever();
    private Teamtailor teamtailor = new Teamtailor();
    private CornerstoneOnDemand cornerstoneOnDemand = new CornerstoneOnDemand();

    public Ai getAi() { return ai; }
    public Openai getOpenai() { return openai; }
    public Gemini getGemini() { return gemini; }
    public Typesense getTypesense() { return typesense; }
    public LinkedIn getLinkedin() { return linkedin; }
    public Careerjet getCareerjet() { return careerjet; }
    public Greenhouse getGreenhouse() { return greenhouse; }
    public Lever getLever() { return lever; }
    public Teamtailor getTeamtailor() { return teamtailor; }
    public CornerstoneOnDemand getCornerstoneOnDemand() { return cornerstoneOnDemand; }

    public static class Ai {
        private String enrichmentProvider = "gemini";
        private String generationProvider = "openai";
        public String getEnrichmentProvider() { return enrichmentProvider; }
        public void setEnrichmentProvider(String enrichmentProvider) { this.enrichmentProvider = enrichmentProvider; }
        public String getGenerationProvider() { return generationProvider; }
        public void setGenerationProvider(String generationProvider) { this.generationProvider = generationProvider; }
    }

    public static class Openai {
        private String apiKey;
        private String model;
        private String embeddingModel;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    }

    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-2.5-flash";
        private String embeddingModel = "gemini-embedding-001";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    }

    public static class Typesense {
        private String apiKey;
        private String host;
        private int port;
        private String protocol;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
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
