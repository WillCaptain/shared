package org.twelve.aipp.evidence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Versioned passive source evidence. Parsing validates structure, never authenticates a caller. */
public final class PassiveEvidence {
    public static final String SCHEMA="aipp.turn-evidence/v1", ACK_SCHEMA="aipp.evidence-receipt/v1";
    public static final String DECLARATION="passive_evidence";
    public static final String CONTROL_DECLARATION="passive_evidence_control";
    public static final int MAX_BODY_BYTES=262144, MAX_EVENTS=32;
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private PassiveEvidence() {}

    public record Source(String userId,String agentId,String sessionId,String uiSessionId,String turnId,
                         long sequence,Instant expiresAt,String outcome,String canonicalJson) {
        @Override public String toString() { return "EvidenceSource[redacted]"; }
    }
    public record Delivery(Source source,String recipientOrigin,String intentId,String kind,long from,long through,
                           long pageFrom,long pageThrough,String deliveryId,String contentHash,String canonicalJson) {
        @Override public String toString() { return "EvidenceDelivery[redacted]"; }
    }
    public record Control(Source source,String recipientOrigin) {}
    public record Receipt(String deliveryId,String receiptId,String contentHash,Instant acceptedAt,String processingState) {}

    /** Structural acceptance only. The Host must bind the response to its trusted request transport. */
    public static Receipt receipt(byte[] body,String expectedDeliveryId,String expectedContentHash) {
        if(body==null || body.length>4096) throw invalid();
        JsonNode value=read(body);
        fields(value,"schema","delivery_id","receipt_id","content_sha256","accepted_at","status","processing_state","applied");
        String delivery=uuid(value,"delivery_id"),id=uuid(value,"receipt_id"),hash=text(value,"content_sha256",64);
        String processing=text(value,"processing_state",16);
        if(!ACK_SCHEMA.equals(text(value,"schema",48)) || !"accepted".equals(text(value,"status",16))
                || !delivery.equals(expectedDeliveryId) || !hash.matches("[a-f0-9]{64}") || !hash.equals(expectedContentHash)
                || !Set.of("receiving","pending").contains(processing) || !value.path("applied").isBoolean()
                || value.path("applied").asBoolean()) throw invalid();
        Instant at;
        try { at=Instant.parse(text(value,"accepted_at",40)); } catch(RuntimeException e) { throw invalid(); }
        return new Receipt(delivery,id,hash,at,processing);
    }

    public static Map<String,Object> declaration() {
        return Map.of("schema",SCHEMA,"acknowledgement","durable_acceptance","max_events",MAX_EVENTS,"max_body_bytes",MAX_BODY_BYTES);
    }
    public static Map<String,Object> controlDeclaration(String operation) {
        if(!Set.of("status","remove").contains(operation)) throw invalid();
        return Map.of("schema",SCHEMA,"operation",operation);
    }
    public static void validateConsumer(JsonNode tool) {
        if(tool.has(CONTROL_DECLARATION)) {
            String operation=tool.path(CONTROL_DECLARATION).path("operation").asText();
            if(tool.has(DECLARATION) || !JSON.valueToTree(controlDeclaration(operation)).equals(tool.get(CONTROL_DECLARATION))
                    || !JSON.valueToTree(List.of("host")).equals(tool.get("visibility"))
                    || !"verified-request-v1".equals(tool.path("invocation_identity").asText())
                    || !(operation.equals("status")?"none":"idempotent").equals(tool.path("side_effect").asText())
                    || tool.has("lifecycle") || tool.path("canvas").path("triggers").asBoolean(true)) throw invalid();
        }
        if (!tool.has(DECLARATION)) return;
        if (!JSON.valueToTree(declaration()).equals(tool.get(DECLARATION))
                || !JSON.valueToTree(List.of("host")).equals(tool.get("visibility"))
                || !"verified-request-v1".equals(tool.path("invocation_identity").asText())
                || !"idempotent".equals(tool.path("side_effect").asText())
                || tool.has("lifecycle") || tool.path("canvas").path("triggers").asBoolean(true)) throw invalid();
    }

    public static Delivery delivery(byte[] body,String recipient) {
        JsonNode root=read(body); fields(root,"_context","evidence");
        JsonNode value=root.path("evidence");
        fields(value,"schema","recipient","source","intent","page","delivery_id","events");
        schema(value); String origin=recipient(value,recipient); Source source=source(value.path("source"));
        context(root.path("_context"),value.path("source"));
        JsonNode intent=value.path("intent"),page=value.path("page");
        fields(intent,"id","kind","from_ordinal","through_ordinal"); fields(page,"from_ordinal","through_ordinal");
        String intentId=uuid(intent,"id"),kind=text(intent,"kind",24);
        long from=ordinal(intent,"from_ordinal"),through=ordinal(intent,"through_ordinal");
        if (through<from || !(kind.equals("terminal") && from==1 && through>=2
                || kind.equals("late_observation") && from>1 && from==through)) throw invalid();
        long pageFrom=ordinal(page,"from_ordinal"),pageThrough=ordinal(page,"through_ordinal");
        if(pageFrom<from || pageThrough>through || pageThrough<pageFrom || pageThrough-pageFrom>=MAX_EVENTS) throw invalid();
        String id=uuid(value,"delivery_id");
        if(!id.equals(deliveryId(value))) throw invalid();
        JsonNode events=value.path("events");
        if(!events.isArray() || events.size()!=pageThrough-pageFrom+1) throw invalid();
        var ids=new HashSet<String>(); long next=pageFrom;
        for(JsonNode event:events) {
            fieldsOptional(event,Set.of("id","ordinal","kind","payload"),Set.of("source_links"));
            if(!ids.add(uuid(event,"id")) || ordinal(event,"ordinal")!=next++) throw invalid();
            String eventKind=text(event,"kind",48);
            if(!eventKind.matches("[a-z][a-z0-9_]{0,47}")) throw invalid();
            if(canonical(event.path("payload")).length()>65536) throw invalid();
            if(event.has("source_links")) {
                if(!Set.of("input","steering").contains(eventKind)) throw invalid();
                links(event.path("source_links"));
            }
            if("provider_context".equals(eventKind)) providerProjection(event.path("payload"),recipient,origin,value.path("source"));
            boolean terminalPosition=kind.equals("terminal") && event.path("ordinal").asLong()==through;
            if(terminalPosition!=eventKind.equals("terminal")) throw invalid();
            if(terminalPosition && !source.outcome().equals(event.path("payload").path("outcome").asText())) throw invalid();
        }
        String encoded=canonical(value);
        return new Delivery(source,origin,intentId,kind,from,through,pageFrom,pageThrough,id,digest(encoded),encoded);
    }

    public static Control control(byte[] body,String recipient) {
        JsonNode root=read(body); fields(root,"_context","evidence");
        JsonNode value=root.path("evidence"); fields(value,"schema","recipient","source"); schema(value);
        Source source=source(value.path("source")); context(root.path("_context"),value.path("source"));
        return new Control(source,recipient(value,recipient));
    }

    /** Stable logical page identity. Source content and mutable read-time views are not ID inputs. */
    public static String deliveryId(JsonNode value) {
        JsonNode source=value.path("source");
        var input=JSON.createArrayNode().add(SCHEMA);
        input.add(value.path("recipient"));
        for(String key:List.of("user_id","agent_id","session_id","ui_session_id","turn_id")) input.add(source.path(key));
        input.add(value.path("intent").path("id")); input.add(value.path("page"));
        return UUID.nameUUIDFromBytes(canonical(input).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Source source(JsonNode s) {
        fields(s,"user_id","agent_id","session_id","ui_session_id","turn_id","turn_sequence","execution_scope","expires_at","outcome");
        String user=uuid(s,"user_id"),agent=text(s,"agent_id",128),session=text(s,"session_id",200),ui=text(s,"ui_session_id",200),turn=uuid(s,"turn_id");
        long sequence=number(s,"turn_sequence"); if(sequence<1) throw invalid();
        String outcome=text(s,"outcome",24); if(!Set.of("complete","failed","cancelled","awaiting_user").contains(outcome)) throw invalid();
        Instant expiry; try { expiry=Instant.parse(text(s,"expires_at",40)); } catch(RuntimeException e) { throw invalid(); }
        validateExecutionScope(s.path("execution_scope"));
        return new Source(user,agent,session,ui,turn,sequence,expiry,outcome,canonical(s));
    }
    public static void validateExecutionScope(JsonNode scope) {
        fields(scope,"contextKind","taskKind","workspaceId","workspaceOwnerAppId");
        String kind=text(scope,"contextKind",24),task=text(scope,"taskKind",24);
        if(!Set.of("main","subtask","workspace").contains(kind) || !Set.of("main","subtask").contains(task)) throw invalid();
        String workspace=emptyText(scope,"workspaceId",200); emptyText(scope,"workspaceOwnerAppId",128);
        if(kind.equals("workspace")!=!workspace.isEmpty() || kind.equals("main")&&!task.equals("main")
                || kind.equals("subtask")&&!task.equals("subtask")) throw invalid();
    }
    private static void context(JsonNode c,JsonNode source) {
        fields(c,"userId","agentId","sessionId","uiSessionId","executionScope");
        for(var pair:Map.of("userId","user_id","agentId","agent_id","sessionId","session_id","uiSessionId","ui_session_id","executionScope","execution_scope").entrySet())
            if(!c.path(pair.getKey()).equals(source.path(pair.getValue()))) throw invalid();
    }
    private static String recipient(JsonNode value,String app) {
        JsonNode r=value.path("recipient"); fields(r,"app_id","origin");
        if(!app.equals(text(r,"app_id",128))) throw invalid();
        String origin=text(r,"origin",512); validateOrigin(origin); return origin;
    }
    public static void validateOrigin(String origin) {
        try {
            URI uri=URI.create(origin);
            if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getRawUserInfo()!=null
                    || uri.getRawQuery()!=null || uri.getRawFragment()!=null || !uri.getRawPath().isEmpty()) throw invalid();
        } catch(RuntimeException e) { throw invalid(); }
    }
    private static void schema(JsonNode value) { if(!SCHEMA.equals(text(value,"schema",48))) throw invalid(); }

    /** Capture-time pointer metadata only; excerpts and read-time availability are rejected. */
    private static void links(JsonNode links) {
        fields(links,"version","relationship","mode","entries");
        if(number(links,"version")!=1 || !"reference_only".equals(text(links,"relationship",32))
                || !Set.of("explicit","implicit_candidates").contains(text(links,"mode",32))) throw invalid();
        JsonNode entries=links.path("entries"); if(!entries.isArray() || entries.isEmpty() || entries.size()>4) throw invalid();
        for(JsonNode entry:entries) {
            fieldsOptional(entry,Set.of("status","candidates_truncated","targets"),Set.of("requested"));
            if(!Set.of("resolved","unavailable","ambiguous","no_candidate","unresolved").contains(text(entry,"status",24))
                    || !entry.path("candidates_truncated").isBoolean()) throw invalid();
            JsonNode targets=entry.path("targets"); if(!targets.isArray() || targets.size()>3) throw invalid();
            for(JsonNode target:targets) { fields(target,"source_event_id"); uuid(target,"source_event_id"); }
            if(entry.has("requested")) {
                JsonNode request=entry.path("requested"); String kind=text(request,"kind",24);
                if(kind.equals("tool_call")) { fields(request,"kind","id","turn_id"); uuid(request,"turn_id"); text(request,"id",200); }
                else {
                    fields(request,"kind","id");
                    if(kind.equals("event")) uuid(request,"id");
                    else if(!kind.equals("message") || !text(request,"id",19).matches("[1-9][0-9]{0,18}")) throw invalid();
                }
            }
        }
    }
    private static void providerProjection(JsonNode payload,String recipient,String origin,JsonNode source) {
        if(payload.isObject()) {
            fields(payload,"omitted","reason","source_sha256");
            if(!payload.path("omitted").isBoolean() || !payload.path("omitted").asBoolean()
                    || !"provider_projection_unavailable".equals(payload.path("reason").asText())
                    || !payload.path("source_sha256").asText().matches("[a-f0-9]{64}")) throw invalid();
            return;
        }
        if(!payload.isArray() || payload.size()>16) throw invalid();
        for(JsonNode receipt:payload) {
            fields(receipt,"provider","origin","receipt");
            if(!recipient.equals(receipt.path("provider").asText()) || !origin.equals(receipt.path("origin").asText())
                    || !receipt.path("receipt").isObject()) throw invalid();
            JsonNode scope=receipt.path("receipt").path("scope");
            for(var pair:Map.of("userId","user_id","agentId","agent_id","sessionId","session_id","uiSessionId","ui_session_id","executionScope","execution_scope").entrySet())
                if(!scope.path(pair.getKey()).equals(source.path(pair.getValue()))) throw invalid();
        }
    }
    private static JsonNode read(byte[] bytes) {
        if(bytes==null || bytes.length==0 || bytes.length>MAX_BODY_BYTES) throw invalid();
        try { JsonNode result=JSON.readTree(bytes); bounded(result,0); return result; }
        catch(Exception e) { throw invalid(); }
    }
    private static void bounded(JsonNode node,int depth) {
        if(node==null || depth>16 || node.isContainerNode()&&node.size()>128 || node.isTextual()&&node.textValue().length()>65536) throw invalid();
        if(node.isObject()) node.fields().forEachRemaining(field -> {
            String name=field.getKey().replaceAll("[_-]","").toLowerCase(Locale.ROOT);
            if(Set.of("authorization","cookie","setcookie","password","passwd","apikey","secret","credential","credentials","delegation","token","privatekey","reasoning","reasoningcontent","thinking").contains(name)
                    || name.endsWith("token") || name.endsWith("secret") || name.endsWith("privatekey") || name.endsWith("authorization")) {
                if(!field.getValue().isNull() && !"[redacted]".equals(field.getValue().asText())) throw invalid();
            }
        });
        if(node.isContainerNode()) for(JsonNode child:node) bounded(child,depth+1);
    }
    public static String canonical(JsonNode node) {
        try { return JSON.writeValueAsString(sorted(node)); } catch(Exception e) { throw invalid(); }
    }
    private static JsonNode sorted(JsonNode n) {
        if(n.isObject()) { ObjectNode result=JSON.createObjectNode(); var names=new TreeSet<String>(); n.fieldNames().forEachRemaining(names::add); names.forEach(k -> result.set(k,sorted(n.get(k)))); return result; }
        if(n.isArray()) { ArrayNode result=JSON.createArrayNode(); n.forEach(v -> result.add(sorted(v))); return result; }
        return n;
    }
    public static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static void fields(JsonNode node,String... fields) { fieldsOptional(node,Set.of(fields),Set.of()); }
    private static void fieldsOptional(JsonNode node,Set<String> required,Set<String> optional) {
        if(!node.isObject()) throw invalid(); var actual=new HashSet<String>(); node.fieldNames().forEachRemaining(actual::add);
        if(!actual.containsAll(required)) throw invalid(); actual.removeAll(required); if(!optional.containsAll(actual)) throw invalid();
    }
    private static String text(JsonNode n,String field,int max) { String s=emptyText(n,field,max); if(s.isBlank()) throw invalid(); return s; }
    private static String emptyText(JsonNode n,String field,int max) {
        JsonNode v=n.path(field); if(!v.isTextual() || v.textValue().length()>max || v.textValue().chars().anyMatch(c -> c<32)) throw invalid(); return v.textValue();
    }
    private static String uuid(JsonNode n,String field) {
        String value=text(n,field,36); try { if(!UUID.fromString(value).toString().equals(value)) throw invalid(); return value; } catch(RuntimeException e) { throw invalid(); }
    }
    private static long number(JsonNode n,String field) { JsonNode v=n.path(field); if(!v.isIntegralNumber() || !v.canConvertToLong()) throw invalid(); return v.longValue(); }
    private static long ordinal(JsonNode n,String field) { long value=number(n,field); if(value<1 || value>4096) throw invalid(); return value; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("invalid_passive_evidence"); }
}
