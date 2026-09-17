package org.twelve.aipp.evidence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceRemovalReceiptTest {
    final ObjectMapper json=new ObjectMapper();
    byte[] request() {
        var scope=json.valueToTree(Map.of("contextKind","main","taskKind","main","workspaceId","","workspaceOwnerAppId",""));
        var source=json.createObjectNode().put("user_id","11111111-1111-1111-1111-111111111111").put("agent_id","agent").put("session_id","s").put("ui_session_id","ui")
                .put("turn_id","22222222-2222-2222-2222-222222222222").put("turn_sequence",1).put("expires_at","2026-09-15T00:00:00Z").put("outcome","complete"); source.set("execution_scope",scope);
        var root=json.createObjectNode(); var context=root.putObject("_context");
        for(var entry:Map.of("userId","user_id","agentId","agent_id","sessionId","session_id","uiSessionId","ui_session_id","executionScope","execution_scope").entrySet()) context.set(entry.getKey(),source.path(entry.getValue()));
        var evidence=root.putObject("evidence"); evidence.put("schema",PassiveEvidence.SCHEMA); evidence.putObject("recipient").put("app_id","sample-app").put("origin","https://sample.example"); evidence.set("source",source);
        return EvidenceWorkerProtocol.encode(root);
    }
    @Test void exactReceiptRoundTripAndStableRemovalIdentity() {
        byte[] body=request(); String id=UUID.randomUUID().toString(); Instant time=Instant.parse("2026-09-15T01:00:00.123456789Z");
        var wire=EvidenceRemovalReceipt.wire("host","sample-app",body,id,time);
        var parsed=EvidenceRemovalReceipt.parse(EvidenceWorkerProtocol.encode(wire),"host","sample-app",body);
        assertEquals(id,parsed.receiptId()); assertEquals(time,parsed.removedAt());
        assertEquals(wire.get("removal_id"),EvidenceRemovalReceipt.wire("host","sample-app",body,UUID.randomUUID().toString(),time.plusSeconds(1)).get("removal_id"));
    }
    @Test void wrongIssuerRequestRecipientHashOrAppliedFlagFails() {
        byte[] body=request(); var wire=json.valueToTree(EvidenceRemovalReceipt.wire("host","sample-app",body,UUID.randomUUID().toString(),Instant.now()));
        for(String field:List.of("issuer","request_sha256","source_sha256","removal_id","source_state","applied")) {
            ObjectNode altered=wire.deepCopy(); altered.put(field,"wrong");
            assertThrows(IllegalArgumentException.class,() -> EvidenceRemovalReceipt.parse(EvidenceWorkerProtocol.encode(altered),"host","sample-app",body));
        }
        assertThrows(IllegalArgumentException.class,() -> EvidenceRemovalReceipt.parse(EvidenceWorkerProtocol.encode(wire),"other","sample-app",body));
        assertThrows(IllegalArgumentException.class,() -> EvidenceRemovalReceipt.parse(EvidenceWorkerProtocol.encode(wire),"host","other-app",body));
    }
    @Test void strictReceiptRejectsExtraDuplicateTrailingAndOversize() {
        byte[] request=request(); String body=new String(EvidenceWorkerProtocol.encode(EvidenceRemovalReceipt.wire("host","sample-app",request,UUID.randomUUID().toString(),Instant.now())),java.nio.charset.StandardCharsets.UTF_8);
        for(String value:List.of(body+"{}","{\"applied\":false,"+body.substring(1),"{\"extra\":1,"+body.substring(1)))
            assertThrows(IllegalArgumentException.class,() -> EvidenceRemovalReceipt.parse(value.getBytes(java.nio.charset.StandardCharsets.UTF_8),"host","sample-app",request));
        assertThrows(IllegalArgumentException.class,() -> EvidenceRemovalReceipt.parse(new byte[4097],"host","sample-app",request));
    }
}
