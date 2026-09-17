package org.twelve.aipp.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PassiveEvidenceReceiptTest {
    private final ObjectMapper json=new ObjectMapper();
    private final String delivery=UUID.randomUUID().toString(),receipt=UUID.randomUUID().toString(),hash="a".repeat(64);
    ObjectNode valid() {
        return json.valueToTree(Map.of("schema",PassiveEvidence.ACK_SCHEMA,"delivery_id",delivery,"receipt_id",receipt,
                "content_sha256",hash,"accepted_at","2026-09-14T00:00:00.123456789Z","status","accepted",
                "processing_state","receiving","applied",false));
    }
    PassiveEvidence.Receipt read(ObjectNode body) { return PassiveEvidence.receipt(body.toString().getBytes(StandardCharsets.UTF_8),delivery,hash); }
    @Test void acceptsOnlyDurableAcceptanceAndPreservesNanosecondReceiptIdentity() {
        var accepted=read(valid());
        assertThat(accepted.receiptId()).isEqualTo(receipt);
        assertThat(accepted.acceptedAt()).isEqualTo(Instant.parse("2026-09-14T00:00:00.123456789Z"));
        assertThat(read(valid().put("processing_state","pending")).processingState()).isEqualTo("pending");
    }
    @Test void requiresExactDeliveryHashSchemaStateAndFieldTypes() {
        for(String field:List.of("schema","delivery_id","receipt_id","content_sha256","accepted_at","status","processing_state","applied"))
            assertThatThrownBy(() -> read(valid().put(field,"invalid"))).hasMessage("invalid_passive_evidence");
        assertThatThrownBy(() -> read(valid().put("delivery_id",UUID.randomUUID().toString()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> read(valid().put("content_sha256","b".repeat(64)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> read(valid().put("applied",true))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> read(valid().put("extra",1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsMissingFieldsDuplicateKeysTrailingValuesAndOversizedBodies() {
        for(String field:List.of("schema","delivery_id","receipt_id","content_sha256","accepted_at","status","processing_state","applied")) {
            var body=valid(); body.remove(field);
            assertThatThrownBy(() -> read(body)).hasMessage("invalid_passive_evidence");
        }
        for(byte[] body:List.of((valid()+" {}").getBytes(StandardCharsets.UTF_8),
                valid().toString().replace("\"applied\":false","\"applied\":false,\"applied\":false").getBytes(StandardCharsets.UTF_8),new byte[4097]))
            assertThatThrownBy(() -> PassiveEvidence.receipt(body,delivery,hash)).hasMessage("invalid_passive_evidence");
    }
}
