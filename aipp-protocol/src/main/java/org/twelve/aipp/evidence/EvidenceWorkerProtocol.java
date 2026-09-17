package org.twelve.aipp.evidence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.twelve.aipp.identity.InvocationRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Bounded structural contract. Neither parsing nor a digest establishes authority. */
public final class EvidenceWorkerProtocol {
    public static final String CHECK_SCHEMA="aipp.evidence-worker-check/v1", GRANT_SCHEMA="aipp.evidence-delegation/v1",
            DECISION_SCHEMA="aipp.evidence-worker-decision/v1", CHECK_PATH="/api/evidence/authorize",
            GRANTS_PATH="/api/evidence/delegations", RESPONSE_HEADER="X-Aipp-Authority-Evidence";
    public static final int MAX_BYTES=16384;
    private static final ObjectMapper JSON=new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder().streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(10000).maxNumberLength(20).build()).build()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private EvidenceWorkerProtocol() {}

    public record Check(String canonicalJson) {
        public JsonNode value() { return read(bytes()); }
        public byte[] bytes() { return canonicalJson.getBytes(StandardCharsets.UTF_8); }
        public String bindingHash() {
            ObjectNode binding=(ObjectNode)value(); binding.remove(List.of("schema","workload"));
            return PassiveEvidence.digest(PassiveEvidence.canonical(binding));
        }
        public String requestHash() { return new InvocationRequest("twelfth-users","evidence_worker_authorize","POST",CHECK_PATH,bytes()).bodySha256(); }
        public JsonNode source() { return read(text(value(),"source",8192).getBytes(StandardCharsets.UTF_8)); }
        @Override public String toString() { return "WorkerCheck[redacted]"; }
    }
    public static Check check(byte[] bytes) {
        JsonNode n=read(bytes);
        fields(n,"schema","workload","attempt_id","issuer","subject","origin","subscription_id","subscription_revision",
                "removal","source","audience","operation","method","target","body_sha256");
        equal(n,"schema",CHECK_SCHEMA); id(n,"attempt_id"); id(n,"subject");
        text(n,"workload",512); text(n,"issuer",512); hash(n,"subscription_id"); positive(n,"subscription_revision");
        if(!n.path("removal").isBoolean()) throw invalid();
        PassiveEvidence.validateOrigin(text(n,"origin",512)); hash(n,"body_sha256");
        String app=text(n,"audience",128),op=text(n,"operation",128);
        equal(n,"method","POST"); equal(n,"target","/api/tools/"+op);
        new InvocationRequest(app,op,"POST",text(n,"target",256),new byte[0]);
        JsonNode source=read(text(n,"source",8192).getBytes(StandardCharsets.UTF_8));
        var root=JSON.createObjectNode(); var context=root.putObject("_context");
        for(var pair:Map.of("userId","user_id","agentId","agent_id","sessionId","session_id","uiSessionId","ui_session_id","executionScope","execution_scope").entrySet())
            context.set(pair.getKey(),source.path(pair.getValue()));
        var evidence=root.putObject("evidence"); evidence.put("schema",PassiveEvidence.SCHEMA);
        evidence.putObject("recipient").put("app_id",app).put("origin",n.path("origin").textValue()); evidence.set("source",source);
        PassiveEvidence.control(encode(root),app);
        if(!source.path("user_id").asText().equals(n.path("subject").asText())
                || !PassiveEvidence.canonical(source).equals(n.path("source").textValue())
                || !subscriptionId(n.path("subject").asText(),source.path("agent_id").asText(),source.path("execution_scope"),app,n.path("origin").asText())
                        .equals(n.path("subscription_id").asText())) throw invalid();
        return new Check(PassiveEvidence.canonical(n));
    }
    public static JsonNode grant(byte[] bytes) {
        if(bytes==null || bytes.length>8192) throw invalid();
        JsonNode n=read(bytes);
        fields(n,"schema","issuer","workload","agent_id","execution_scope","recipient_app","recipient_origin","subscription_id",
                "subscription_revision","accept_operation","remove_operation","organization","expires_at","expected_revision");
        equal(n,"schema",GRANT_SCHEMA); text(n,"issuer",512); text(n,"workload",512); text(n,"agent_id",128);
        text(n,"organization",512); PassiveEvidence.validateExecutionScope(n.path("execution_scope"));
        String app=text(n,"recipient_app",128); PassiveEvidence.validateOrigin(text(n,"recipient_origin",512));
        for(String f:List.of("accept_operation","remove_operation"))
            new InvocationRequest(app,text(n,f,128),"POST","/api/tools/"+text(n,f,128),new byte[0]);
        if(n.path("accept_operation").equals(n.path("remove_operation"))) throw invalid();
        hash(n,"subscription_id"); positive(n,"subscription_revision"); number(n,"expected_revision",0); instant(n,"expires_at");
        return n;
    }
    public static JsonNode revocation(byte[] bytes) {
        JsonNode n=read(bytes); fields(n,"delegation_id","expected_revision"); hash(n,"delegation_id"); positive(n,"expected_revision"); return n;
    }
    public static JsonNode decision(byte[] bytes) {
        JsonNode n=read(bytes);
        fields(n,"schema","binding_sha256","request_sha256","delegation_id","delegation_revision","subscription_revision","checked_at","expires_at","organization");
        equal(n,"schema",DECISION_SCHEMA);
        for(String f:List.of("binding_sha256","request_sha256","delegation_id")) hash(n,f);
        positive(n,"delegation_revision"); positive(n,"subscription_revision"); text(n,"organization",512);
        Instant at=instant(n,"checked_at"),expiry=instant(n,"expires_at");
        if(!expiry.equals(at.plusSeconds(90))) throw invalid(); return n;
    }
    /** The only query is a user-authenticated delegation-list cursor; signed checks never have a query. */
    public static String route(String method,String path,String query) {
        if(CHECK_PATH.equals(path)) {
            if(!"POST".equals(method) || query!=null) throw invalid();
        } else if(GRANTS_PATH.equals(path)) {
            if(!Set.of("GET","POST","DELETE").contains(method)) throw invalid();
            if(query!=null && (!"GET".equals(method) || !query.matches("after=[a-f0-9]{64}"))) throw invalid();
        } else throw invalid();
        return path+(query==null?"":"?"+query);
    }
    public static InvocationRequest checkInvocation(byte[] body) { return new InvocationRequest("twelfth-users","evidence_worker_authorize","POST",CHECK_PATH,body); }
    public static InvocationRequest decisionInvocation(byte[] body) { return new InvocationRequest("world-one","evidence_worker_decision","POST","/api/evidence/decision",body); }
    public static String subscriptionId(String user,String agent,JsonNode scope,String app,String origin) {
        return PassiveEvidence.digest(PassiveEvidence.canonical(JSON.valueToTree(List.of(user,agent,PassiveEvidence.canonical(scope),app,origin))));
    }
    public static String delegationId(String user,String issuer,String workload,String subscription,long revision) {
        return PassiveEvidence.digest(PassiveEvidence.canonical(JSON.valueToTree(List.of(user,issuer,workload,subscription,revision))));
    }
    public static byte[] encode(Object value) { try { return JSON.writeValueAsBytes(value); } catch(Exception e) { throw invalid(); } }
    public static JsonNode read(byte[] bytes) {
        try {
            if(bytes==null || bytes.length==0 || bytes.length>MAX_BYTES) throw invalid();
            JsonNode n=JSON.readTree(bytes); if(n==null) throw invalid(); return n;
        } catch(Exception e) { throw invalid(); }
    }
    public static String text(JsonNode n,String field,int max) {
        JsonNode v=n.path(field); if(!v.isTextual() || v.textValue().isBlank() || v.textValue().length()>max
                || v.textValue().chars().anyMatch(c -> c<32 || c==127)) throw invalid(); return v.textValue();
    }
    public static long positive(JsonNode n,String field) { return number(n,field,1); }
    private static long number(JsonNode n,String field,long min) {
        JsonNode v=n.path(field); if(!v.isIntegralNumber() || !v.canConvertToLong() || v.longValue()<min) throw invalid(); return v.longValue();
    }
    public static Instant instant(JsonNode n,String field) { try { return Instant.parse(text(n,field,40)); } catch(Exception e) { throw invalid(); } }
    private static void fields(JsonNode n,String... names) {
        var actual=new HashSet<String>(); n.fieldNames().forEachRemaining(actual::add);
        if(!n.isObject() || !actual.equals(Set.of(names))) throw invalid();
    }
    private static void equal(JsonNode n,String f,String v) { if(!v.equals(text(n,f,256))) throw invalid(); }
    private static void hash(JsonNode n,String f) { if(!text(n,f,64).matches("[a-f0-9]{64}")) throw invalid(); }
    private static void id(JsonNode n,String f) { String s=text(n,f,36); if(!UUID.fromString(s).toString().equals(s)) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("invalid_evidence_worker_message"); }
}
