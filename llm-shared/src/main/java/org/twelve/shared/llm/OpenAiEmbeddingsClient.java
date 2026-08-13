package org.twelve.shared.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI-compatible {@code POST /embeddings} client.
 *
 * <p>This is the shared invoke leaf for dense retrieval. Domain stores (hybrid-retrieval,
 * note-one, capability index) should call this instead of owning their own HTTP clients.
 */
public final class OpenAiEmbeddingsClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_MAX_CHARS = 12_000;

    private final LLMConfig config;
    private final String model;
    private final int batchSize;
    private final HttpClient http;

    public OpenAiEmbeddingsClient(LLMConfig config, String model) {
        this(config, model, 32);
    }

    public OpenAiEmbeddingsClient(LLMConfig config, String model, int batchSize) {
        this.config = Objects.requireNonNull(config, "config");
        this.model = model == null || model.isBlank() ? "text-embedding-3-small" : model.trim();
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        this.batchSize = batchSize;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public String model() {
        return model;
    }

    public float[] embed(String text) throws Exception {
        if (text == null || text.isBlank()) return new float[0];
        List<float[]> out = embed(List.of(text));
        return out.isEmpty() ? new float[0] : out.getFirst();
    }

    public List<float[]> embed(List<String> inputs) throws Exception {
        if (inputs == null || inputs.isEmpty()) return List.of();
        List<float[]> result = new ArrayList<>(inputs.size());
        for (int from = 0; from < inputs.size(); from += batchSize) {
            int to = Math.min(inputs.size(), from + batchSize);
            result.addAll(requestBatch(inputs.subList(from, to)));
        }
        return List.copyOf(result);
    }

    private List<float[]> requestBatch(List<String> inputs) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<String> capped = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            String text = input == null ? "" : input;
            capped.add(text.length() > DEFAULT_MAX_CHARS ? text.substring(0, DEFAULT_MAX_CHARS) : text);
        }
        body.put("input", capped);

        HttpRequest req = HttpRequest.newBuilder(URI.create(config.embeddingsUrl()))
                .timeout(Duration.ofSeconds(Math.max(30, config.timeoutSeconds())))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            String err = resp.body() == null ? "" : resp.body();
            throw new IllegalStateException("embeddings HTTP " + resp.statusCode() + ": "
                    + err.substring(0, Math.min(200, err.length())));
        }
        JsonNode data = JSON.readTree(resp.body()).path("data");
        if (!data.isArray() || data.size() != inputs.size()) {
            throw new IllegalStateException("embedding response count did not match request");
        }
        List<IndexedVector> vectors = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            JsonNode item = data.get(i);
            JsonNode values = item.path("embedding");
            if (!values.isArray() || values.isEmpty()) {
                throw new IllegalStateException("embedding response contained an empty vector");
            }
            float[] vector = new float[values.size()];
            for (int j = 0; j < values.size(); j++) vector[j] = (float) values.get(j).asDouble();
            vectors.add(new IndexedVector(item.path("index").asInt(i), vector));
        }
        vectors.sort(Comparator.comparingInt(IndexedVector::index));
        return vectors.stream().map(IndexedVector::vector).toList();
    }

    private record IndexedVector(int index, float[] vector) {}
}
