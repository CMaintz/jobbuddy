package com.autoapplicant.adapter.ai;

import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemini embeddings through the native batchEmbedContents endpoint.
 *
 * <p>Not the OpenAI-compatible endpoint GeminiAdapter uses for chat: that one documents only
 * model and input for embeddings, and gemini-embedding-001 answers with 3072 dimensions by
 * default, which the vector(1536) columns and their index refuse. The native endpoint takes
 * outputDimensionality. See https://ai.google.dev/api/embeddings.
 */
public class GeminiEmbeddingClient implements EmbeddingProviderPort {

    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int HTTP_OK = 200;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public GeminiEmbeddingClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        JsonNode response = post(requestBody(texts));
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode embedding : response.path("embeddings")) {
            vectors.add(toVector(embedding.path("values")));
        }
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException("Gemini returned " + vectors.size() + " embeddings for "
                    + texts.size() + " texts");
        }
        return vectors;
    }

    @Override
    public String embeddingModelName() {
        return model;
    }

    private String requestBody(List<String> texts) {
        ObjectNode body = json.createObjectNode();
        ArrayNode requests = body.putArray("requests");
        for (String text : texts) {
            ObjectNode request = requests.addObject();
            request.put("model", "models/" + model);
            request.putObject("content").putArray("parts").addObject().put("text", text);
            request.put("outputDimensionality", EmbeddingChoice.DIMENSIONS);
        }
        return body.toString();
    }

    private JsonNode post(String body) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/v1beta/models/" + model + ":batchEmbedContents"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HTTP_OK) {
                throw new IllegalStateException("Gemini embeddings answered " + response.statusCode()
                        + ": " + response.body());
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Gemini embeddings request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Gemini embeddings", e);
        }
    }

    // A wrong width would otherwise fail at the database insert, far from its cause.
    private static float[] toVector(JsonNode values) {
        if (values.size() != EmbeddingChoice.DIMENSIONS) {
            throw new IllegalStateException("Gemini returned " + values.size() + " dimensions, expected "
                    + EmbeddingChoice.DIMENSIONS);
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) vector[i] = (float) values.get(i).asDouble();
        return vector;
    }
}
