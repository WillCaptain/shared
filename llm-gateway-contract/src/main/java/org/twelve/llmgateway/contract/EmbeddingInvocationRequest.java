package org.twelve.llmgateway.contract;

import java.util.List;

public record EmbeddingInvocationRequest(String idempotencyKey, String modelAlias, List<String> input) {
    public EmbeddingInvocationRequest {
        input = input == null ? List.of() : List.copyOf(input);
        if (input.isEmpty()) throw new IllegalArgumentException("input is required");
    }
    public static EmbeddingInvocationRequest single(String input, String modelAlias) {
        return new EmbeddingInvocationRequest(null, modelAlias, List.of(input));
    }
}
