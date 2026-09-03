package org.twelve.llmgateway.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayDelegationContractTest {
    @Test
    void remainsAProviderNeutralInterfaceWithoutImplementationMethods() {
        assertTrue(GatewayDelegationContract.class.isInterface());
        assertEquals(0, GatewayDelegationContract.class.getDeclaredMethods().length);
        assertEquals("gateway_delegation", GatewayDelegationContract.POLICY_FIELD);
    }
}
