package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GeminiEmbeddingClientTest {

    private static final String KEY = "test-gemini-key";
    private static final String MODEL = "gemini-embedding-001";
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> keyHeader = new AtomicReference<>();
    private final AtomicReference<JsonNode> body = new AtomicReference<>();
    private HttpServer server;

    /** Answers like Gemini's batchEmbedContents, with vectors of the given width. */
    private GeminiEmbeddingClient clientAnsweringWith(int dimensions) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            keyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            JsonNode request = json.readTree(exchange.getRequestBody());
            body.set(request);
            StringBuilder out = new StringBuilder("{\"embeddings\":[");
            for (int i = 0; i < request.get("requests").size(); i++) {
                if (i > 0) out.append(',');
                out.append("{\"values\":[");
                for (int d = 0; d < dimensions; d++) out.append(d == 0 ? "" : ",").append(i + 1);
                out.append("]}");
            }
            byte[] bytes = out.append("]}").toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return new GeminiEmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(), KEY, MODEL);
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void asks_gemini_for_1536_dimensions_so_vectors_fit_the_column_and_its_index() throws IOException {
        // Left to its default, gemini-embedding-001 answers with 3072 dimensions, which the
        // vector(1536) columns refuse.
        List<float[]> vectors = clientAnsweringWith(1536).embedAll(List.of("første", "anden"));

        assertThat(path.get()).isEqualTo("/v1beta/models/" + MODEL + ":batchEmbedContents");
        assertThat(keyHeader.get()).isEqualTo(KEY);
        JsonNode requests = body.get().get("requests");
        assertThat(requests).hasSize(2);
        for (JsonNode request : requests) {
            assertThat(request.get("outputDimensionality").asInt()).isEqualTo(1536);
            assertThat(request.get("model").asText()).isEqualTo("models/" + MODEL);
        }
        assertThat(requests.get(1).at("/content/parts/0/text").asText()).isEqualTo("anden");
        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(1)).hasSize(1536).startsWith(2f);
    }

    @Test
    void a_vector_of_any_other_width_fails_loudly_instead_of_at_the_database() throws IOException {
        GeminiEmbeddingClient client = clientAnsweringWith(3072);

        assertThatThrownBy(() -> client.embed("tekst"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3072")
                .hasMessageContaining("1536");
    }

    @Test
    void names_the_model_it_embeds_with() throws IOException {
        assertThat(clientAnsweringWith(1536).embeddingModelName()).isEqualTo(MODEL);
    }
}
