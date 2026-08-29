package org.twelve.llmgateway.client;

import org.twelve.llmgateway.contract.GatewayOperation;
import org.twelve.llmgateway.contract.GatewayRequestMetadata;

/** Returns an opaque credential already issued by a trusted authorization center. Never signs tokens. */
@FunctionalInterface
public interface GatewayCredentialProvider {
    String credentialFor(GatewayOperation operation, GatewayRequestMetadata metadata);
}
