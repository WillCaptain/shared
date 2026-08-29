package org.twelve.llmgateway.client;

import org.twelve.llmgateway.contract.*;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface GatewayClient {
    ChatInvocationResponse chat(ChatInvocationRequest request, GatewayRequestMetadata metadata) throws Exception;
    EmbeddingInvocationResponse embedding(EmbeddingInvocationRequest request, GatewayRequestMetadata metadata) throws Exception;
    StreamHandle streamChat(ChatInvocationRequest request, GatewayRequestMetadata metadata,
                            Consumer<GatewayStreamEvent> events) throws Exception;

    record StreamHandle(String invocationId, CompletionStage<ChatInvocationResponse> completion,
                        Runnable cancel) implements AutoCloseable {
        @Override public void close() { if (cancel != null) cancel.run(); }
    }
}
