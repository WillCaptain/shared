package org.twelve.aipp.evidence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceWorkerProtocolTest {
    static final ObjectMapper JSON=new ObjectMapper();
    ObjectNode valid() {
        String user="11111111-1111-1111-1111-111111111111";
        var scope=JSON.valueToTree(Map.of("contextKind","main","taskKind","main","workspaceId","","workspaceOwnerAppId",""));
        var source=JSON.createObjectNode().put("user_id",user).put("agent_id","agent").put("session_id","s").put("ui_session_id","ui")
                .put("turn_id",UUID.randomUUID().toString()).put("turn_sequence",1).put("expires_at","2026-10-01T00:00:00Z").put("outcome","complete");
        source.set("execution_scope",scope);
        return JSON.createObjectNode().put("schema",EvidenceWorkerProtocol.CHECK_SCHEMA).put("workload","worker")
                .put("attempt_id",UUID.randomUUID().toString()).put("issuer","host").put("subject",user).put("origin","https://memory.example")
                .put("subscription_id",EvidenceWorkerProtocol.subscriptionId(user,"agent",scope,"memory-one","https://memory.example"))
                .put("subscription_revision",1).put("removal",false).put("source",PassiveEvidence.canonical(source))
                .put("audience","memory-one").put("operation","memory_evidence_accept").put("method","POST")
                .put("target","/api/tools/memory_evidence_accept").put("body_sha256","a".repeat(64));
    }
    @Test void canonicalRoundTripPreservesHostBindingAndRedactsPrinting() {
        var n=valid(); var check=EvidenceWorkerProtocol.check(EvidenceWorkerProtocol.encode(n));
        n.remove(List.of("schema","workload"));
        assertEquals(PassiveEvidence.digest(PassiveEvidence.canonical(n)),check.bindingHash());
        assertEquals(check,EvidenceWorkerProtocol.check(check.bytes()));
        assertFalse(check.toString().contains("agent")); assertNotNull(check.requestHash());
    }
    @Test void rejectsUnknownMissingDuplicateAndTrailingFields() {
        var n=valid(); n.put("events","private text"); assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.check(EvidenceWorkerProtocol.encode(n)));
        n.remove("events"); n.remove("workload"); assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.check(EvidenceWorkerProtocol.encode(n)));
        String encoded=PassiveEvidence.canonical(valid());
        assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.check((encoded+" {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.check(("{\"schema\":\"x\","+encoded.substring(1)).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void rejectsScopeOwnerDigestTargetAndNumericWidening() {
        for(String field:List.of("subject","subscription_id","subscription_revision","target","body_sha256","removal")) {
            var n=valid(); n.put(field,"invalid");
            assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.check(EvidenceWorkerProtocol.encode(n)),field);
        }
        var n=valid(); n.put("subscription_revision",1.5);
        assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.check(EvidenceWorkerProtocol.encode(n)));
    }
    @Test void sourceCannotCarryEventsOrNoncanonicalMetadata() throws Exception {
        var n=valid(); var source=(ObjectNode)JSON.readTree(n.path("source").asText()); source.put("events","hidden");
        n.put("source",PassiveEvidence.canonical(source)); var withEvents=n; assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.check(EvidenceWorkerProtocol.encode(withEvents)));
        n=valid(); n.put("source"," "+n.path("source").asText()); var altered=n;
        assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.check(EvidenceWorkerProtocol.encode(altered)));
    }
    @Test void onlyOwnerListAllowsAnExactCursor() {
        String query="after="+"a".repeat(64);
        assertEquals(EvidenceWorkerProtocol.GRANTS_PATH+"?"+query,EvidenceWorkerProtocol.route("GET",EvidenceWorkerProtocol.GRANTS_PATH,query));
        for(String method:List.of("POST","DELETE")) assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.route(method,EvidenceWorkerProtocol.GRANTS_PATH,query));
        assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.route("GET",EvidenceWorkerProtocol.GRANTS_PATH,query+"&after=b"));
        assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.route("POST",EvidenceWorkerProtocol.CHECK_PATH,query));
    }
    @Test void enforcesBodyDepthAndDecisionBounds() {
        assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.read(new byte[16385]));
        assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.read(("[".repeat(21)+"0"+"]".repeat(21)).getBytes(StandardCharsets.UTF_8)));
        var decision=JSON.createObjectNode().put("schema",EvidenceWorkerProtocol.DECISION_SCHEMA).put("binding_sha256","a".repeat(64))
                .put("request_sha256","b".repeat(64)).put("delegation_id","c".repeat(64)).put("delegation_revision",1).put("subscription_revision",1)
                .put("checked_at","2026-09-15T00:00:00Z").put("expires_at","2026-09-15T00:01:30Z").put("organization","12th");
        assertNotNull(EvidenceWorkerProtocol.decision(EvidenceWorkerProtocol.encode(decision)));
        decision.put("expires_at","2026-09-15T00:10:00Z"); assertThrows(IllegalArgumentException.class,() -> EvidenceWorkerProtocol.decision(EvidenceWorkerProtocol.encode(decision)));
    }
}
