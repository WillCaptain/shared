package org.twelve.aipp.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.twelve.aipp.AippAppSpec;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PassiveEvidenceControlTest {
    final ObjectMapper json=new ObjectMapper();
    ObjectNode tool(String role) {
        return json.valueToTree(Map.of("name","evidence_"+role,"description","Passive evidence control", "visibility",List.of("host"),
                "invocation_identity","verified-request-v1","side_effect",role.equals("status")?"none":"idempotent",
                "canvas",Map.of("triggers",false),"parameters",Map.of("type","object","properties",Map.of(),"required",List.of()),
                PassiveEvidence.CONTROL_DECLARATION,PassiveEvidence.controlDeclaration(role)));
    }
    @Test void explicitControlRolesPassTheGeneralManifestValidator() {
        for(String role:List.of("status","remove")) new AippAppSpec().assertValidSkillStructure(tool(role));
    }
    @Test void controlsCannotClaimAcceptanceLifecycleOrUnsupportedRoles() {
        var tool=tool("remove"); tool.set(PassiveEvidence.DECLARATION,json.valueToTree(PassiveEvidence.declaration()));
        assertThatThrownBy(() -> PassiveEvidence.validateConsumer(tool)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PassiveEvidence.validateConsumer(tool("status").put("lifecycle","post_turn"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PassiveEvidence.controlDeclaration("reset")).isInstanceOf(IllegalArgumentException.class);
        var malformed=tool("remove"); ((ObjectNode)malformed.path(PassiveEvidence.CONTROL_DECLARATION)).put("extra",true);
        assertThatThrownBy(() -> PassiveEvidence.validateConsumer(malformed)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void controlsRequireVerifiedHostOnlyNontriggeringAppropriateEffects() {
        for(String role:List.of("status","remove")) {
            for(String field:List.of("visibility","invocation_identity","side_effect","canvas"))
                assertThatThrownBy(() -> PassiveEvidence.validateConsumer(tool(role).put(field,"invalid"))).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void subscriptionAndEnvelopeUseTheSameExactExecutionScopeRules() {
        var scope=json.valueToTree(Map.of("contextKind","workspace","taskKind","main","workspaceId","w","workspaceOwnerAppId","sample-app"));
        assertThatCode(() -> PassiveEvidence.validateExecutionScope(scope)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PassiveEvidence.validateExecutionScope(json.valueToTree(Map.of("contextKind","main"))))
                .isInstanceOf(IllegalArgumentException.class);
        ((ObjectNode)scope).put("workspaceId","");
        assertThatThrownBy(() -> PassiveEvidence.validateExecutionScope(scope)).isInstanceOf(IllegalArgumentException.class);
    }
}
