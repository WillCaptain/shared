package org.twelve.aipp.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.twelve.aipp.AippAppSpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PassiveEvidenceTest {
    private final ObjectMapper json=new ObjectMapper();
    ObjectNode tool() {
        return json.valueToTree(Map.of("name","receive_evidence","description","Accept passive evidence", "visibility",List.of("host"),
                "invocation_identity","verified-request-v1","side_effect","idempotent","canvas",Map.of("triggers",false),
                "parameters",Map.of("type","object","properties",Map.of(),"required",List.of()),"passive_evidence",PassiveEvidence.declaration()));
    }
    @Test void declarationPassesTheGeneralAppValidator() { new AippAppSpec().assertValidSkillStructure(tool()); }
    @Test void ordinaryLifecycleCannotAccidentallySubscribeToPassiveEvidence() {
        var tool=tool(); tool.put("lifecycle","post_turn");
        assertThatThrownBy(() -> new AippAppSpec().assertValidSkillStructure(tool)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void declarationRequiresHostVisibilityVerifiedIdentityAndDurableSemantics() {
        for(String field:List.of("visibility","invocation_identity","side_effect","passive_evidence")) {
            var tool=tool(); tool.put(field,"invalid");
            assertThatThrownBy(() -> PassiveEvidence.validateConsumer(tool)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void existingToolsHaveNoImpliedPassiveSubscription() {
        var tool=tool(); tool.remove("passive_evidence"); tool.put("lifecycle","post_turn");
        assertThatCode(() -> PassiveEvidence.validateConsumer(tool)).doesNotThrowAnyException();
    }
    @Test void canonicalHashIgnoresObjectOrderButPreservesArrayOrder() throws Exception {
        var a=json.readTree("{\"b\":2,\"a\":[1,2]}"); var b=json.readTree("{\"a\":[1,2],\"b\":2}");
        assertThat(PassiveEvidence.canonical(a)).isEqualTo(PassiveEvidence.canonical(b));
        assertThat(PassiveEvidence.digest(PassiveEvidence.canonical(a)))
                .isNotEqualTo(PassiveEvidence.digest(PassiveEvidence.canonical(json.readTree("{\"b\":2,\"a\":[2,1]}"))));
    }
    @Test void originsCannotSmugglePathsQueriesCredentialsOrFragments() {
        for(String value:List.of("https://example/path","https://user:secret@example","https://example?key=value","https://example#fragment","file:///tmp/example"))
            assertThatThrownBy(() -> PassiveEvidence.validateOrigin(value)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> PassiveEvidence.validateOrigin("http://localhost:8091")).doesNotThrowAnyException();
    }
    @Test void malformedAndOversizedRequestsHaveSafeErrors() {
        for(byte[] body:List.of(new byte[0],new byte[PassiveEvidence.MAX_BODY_BYTES+1],"{\"schema\":1,\"schema\":2}".getBytes(StandardCharsets.UTF_8)))
            assertThatThrownBy(() -> PassiveEvidence.delivery(body,"sample-app")).hasMessage("invalid_passive_evidence");
    }
}
