package org.twelve.llmgateway.contract;

import java.util.List;

public record EmbeddingInvocationResponse(String invocationId, List<List<Float>> embeddings, GatewayUsage usage) {
    public EmbeddingInvocationResponse { embeddings = embeddings == null ? List.of() : List.copyOf(embeddings); }
}
