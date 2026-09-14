package org.twelve.aipp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutionOutcomeTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void unknownTransportOutcomeCannotBeSuccessOrSafeToRetry() {
        var result = json.valueToTree(ExecutionOutcome.unknownResult("client_timeout", "Deadline expired"));
        assertThat(ExecutionOutcome.valid(result.path("execution"))).isTrue();
        assertThat(ExecutionOutcome.allowsSuccess(result)).isFalse();
        assertThat(result.path("execution").path("retry_safe").asBoolean()).isFalse();
    }
    @Test void onlyValidCompletedReceiptsAllowSuccess() throws Exception {
        assertThat(ExecutionOutcome.allowsSuccess(json.readTree("{\"ok\":true}"))).isTrue();
        for (String state : java.util.List.of("failed", "not_started", "unknown", "invented")) {
            var result = json.readTree("{\"ok\":true,\"execution\":{\"schema\":\"" + ExecutionOutcome.SCHEMA
                    + "\",\"state\":\"" + state + "\",\"retry_safe\":false}}");
            assertThat(ExecutionOutcome.allowsSuccess(result)).isFalse();
        }
        assertThat(ExecutionOutcome.allowsSuccess(json.readTree("{\"execution\":null}"))).isFalse();
    }
}
