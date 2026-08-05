package org.twelve.shared.retrieval;

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
import java.util.Comparator;
import java.util.List;

/** Minimal OpenAI-compatible {@code /embeddings} client with bounded request batching. */
public final class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {
    private final HttpClient http;
    private final ObjectMapper json;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final int batchSize;
    private final Duration timeout;

    public OpenAiCompatibleEmbeddingProvider(
            URI endpoint, String apiKey, String model, int batchSize) {
        this(HttpClient.newHttpClient(), new ObjectMapper(), endpoint, apiKey, model, batchSize,
                Duration.ofSeconds(30));
    }

    public OpenAiCompatibleEmbeddingProvider(
            HttpClient http,
            ObjectMapper json,
            URI endpoint,
            String apiKey,
            String model,
            int batchSize,
            Duration timeout) {
        if (endpoint == null) throw new IllegalArgumentException("endpoint is required");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        this.http = http;
        this.json = json;
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model.trim();
        this.batchSize = batchSize;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public List<float[]> embed(List<String> inputs) throws EmbeddingException {
        if (inputs == null || inputs.isEmpty()) return List.of();
        List<float[]> result = new ArrayList<>(inputs.size());
        for (int from = 0; from < inputs.size(); from += batchSize) {
            int to = Math.min(inputs.size(), from + batchSize);
            result.addAll(requestBatch(inputs.subList(from, to)));
        }
        return List.copyOf(result);
    }

    private List<float[]> requestBatch(List<String> inputs) throws EmbeddingException {
        ObjectNode payload = json.createObjectNode();
        payload.put("model", model);
        ArrayNode input = payload.putArray("input");
        inputs.forEach(value -> input.add(value == null ? "" : value));

        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
            if (!apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EmbeddingException("embedding endpoint returned HTTP " + response.statusCode());
            }
            JsonNode data = json.readTree(response.body()).path("data");
            if (!data.isArray() || data.size() != inputs.size()) {
                throw new EmbeddingException("embedding response count did not match request");
            }
            List<IndexedVector> vectors = new ArrayList<>(data.size());
            for (int fallbackIndex = 0; fallbackIndex < data.size(); fallbackIndex++) {
                JsonNode item = data.get(fallbackIndex);
                JsonNode values = item.path("embedding");
                if (!values.isArray() || values.isEmpty()) {
                    throw new EmbeddingException("embedding response contained an empty vector");
                }
                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) vector[i] = values.get(i).floatValue();
                vectors.add(new IndexedVector(item.path("index").asInt(fallbackIndex), vector));
            }
            vectors.sort(Comparator.comparingInt(IndexedVector::index));
            return vectors.stream().map(IndexedVector::vector).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("embedding request interrupted", e);
        } catch (IOException | RuntimeException e) {
            throw new EmbeddingException("embedding request failed: " + e.getMessage(), e);
        }
    }

    private record IndexedVector(int index, float[] vector) {}
}
