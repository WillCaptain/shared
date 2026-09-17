package org.twelve.aipp.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Durable source-removal acknowledgement. Parsing establishes binding, not response authentication. */
public final class EvidenceRemovalReceipt {
    public static final String SCHEMA="aipp.evidence-removal-receipt/v1";
    public static final int MAX_BYTES=4096;
    private EvidenceRemovalReceipt() {}
    public record Receipt(String removalId,String receiptId,Instant removedAt) {}
    public static Map<String,Object> wire(String issuer,String app,byte[] request,String receiptId,Instant removedAt) {
        var control=PassiveEvidence.control(request,app);
        if(issuer==null || issuer.isBlank() || issuer.length()>512 || issuer.chars().anyMatch(c -> c<32 || c==127)) throw invalid();
        uuid(receiptId); Objects.requireNonNull(removedAt);
        var recipient=Map.of("app_id",app,"origin",control.recipientOrigin());
        var input=List.of(SCHEMA,issuer,recipient,control.source().canonicalJson());
        String id=UUID.nameUUIDFromBytes(PassiveEvidence.canonical(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(input)).getBytes(StandardCharsets.UTF_8)).toString();
        return Map.of("schema",SCHEMA,"removal_id",id,"receipt_id",receiptId,"removed_at",removedAt.toString(),
                "issuer",issuer,"recipient",recipient,"source_sha256",PassiveEvidence.digest(control.source().canonicalJson()),
                "request_sha256",hash(request),"source_state","removed","applied",false);
    }
    public static Receipt parse(byte[] response,String issuer,String app,byte[] request) {
        if(response==null || response.length>MAX_BYTES) throw invalid();
        JsonNode actual=EvidenceWorkerProtocol.read(response);
        String receipt=EvidenceWorkerProtocol.text(actual,"receipt_id",36); uuid(receipt);
        Instant at=EvidenceWorkerProtocol.instant(actual,"removed_at");
        JsonNode expected=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(wire(issuer,app,request,receipt,at));
        if(!actual.equals(expected)) throw invalid();
        return new Receipt(actual.path("removal_id").asText(),receipt,at);
    }
    private static String hash(byte[] body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void uuid(String value) { if(!UUID.fromString(value).toString().equals(value)) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("invalid_evidence_removal_receipt"); }
}
