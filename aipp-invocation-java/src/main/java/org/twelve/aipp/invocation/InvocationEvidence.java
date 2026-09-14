package org.twelve.aipp.invocation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import org.twelve.aipp.identity.InvocationRequest;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;

/** Generic JWS adapter. No app rules, Host dependency, network key discovery or login bearer. */
public final class InvocationEvidence {
    private static final JOSEObjectType TYPE = new JOSEObjectType("aipp-invocation+jws");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final int MAX_TOKEN = 16384;
    private static final Set<String> FIELDS = Set.of("version", "issuer", "subject", "organization", "audience",
            "invocation_id", "issued_at", "expires_at", "operation", "method", "request_target", "body_sha256");

    public record Trust(String issuer, RSAPublicKey key) {
        public Trust {
            if (issuer == null || issuer.isBlank() || key == null || key.getModulus().bitLength() < 2048)
                throw new IllegalArgumentException("Invalid issuer trust");
        }
    }
    public record Identity(String issuer, String subject, String organization, String invocationId) {}

    /** Must atomically reject an existing issuer/audience/id across processes, through expiry. */
    @FunctionalInterface public interface ReplayStore {
        boolean claim(String issuer, String audience, String invocationId, Instant expiresAt);
    }

    private InvocationEvidence() {}

    /** Caller must obtain subject from its authenticated session, never model arguments. */
    public static String issue(String issuer, String keyId, RSAPrivateKey key, String subject,
                               String organization, InvocationRequest request, Clock clock) {
        if (key == null || key.getModulus().bitLength() < 2048) throw new IllegalArgumentException("RSA key must be at least 2048 bits");
        for (String value : List.of(issuer, keyId, subject))
            if (value.isBlank() || value.length() > 512) throw new IllegalArgumentException("Invalid issuer identity");
        try {
            long now = clock.instant().getEpochSecond();
            var claims = JSON.createObjectNode().put("version", "verified-request-v1")
                    .put("issuer", issuer).put("subject", subject).put("audience", request.audience())
                    .put("invocation_id", UUID.randomUUID().toString()).put("issued_at", now).put("expires_at", now + 60)
                    .put("operation", request.operation()).put("method", request.method())
                    .put("request_target", request.requestTarget()).put("body_sha256", request.bodySha256());
            if (organization != null) {
                if (organization.isBlank() || organization.length() > 512) throw new IllegalArgumentException("Invalid organization");
                claims.put("organization", organization);
            }
            var signed = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).type(TYPE).keyID(keyId).build(),
                    new Payload(JSON.writeValueAsString(claims)));
            signed.sign(new RSASSASigner(key));
            String result = signed.serialize();
            if (result.length() > MAX_TOKEN) throw new IllegalArgumentException("Invocation evidence too large");
            return result;
        } catch (Exception e) { throw new IllegalStateException("Invocation evidence issuance failed"); }
    }

    /** Verifies evidence and consumes replay ID before returning identity. All invocations are one-use. */
    public static Identity verify(String token, InvocationRequest actual, Map<String, Trust> configuredTrust,
                                  Clock clock, ReplayStore replays) {
        Objects.requireNonNull(replays, "Durable replay store required");
        try {
            if (token == null || token.length() > MAX_TOKEN) throw invalid();
            var jws = JWSObject.parse(token);
            var header = jws.getHeader();
            // Reject duplicate header members too; JOSE parsing alone is not sufficient.
            var headerJson = JSON.readTree(jws.getParsedParts()[0].decode());
            var headerFields = new HashSet<String>(); headerJson.fieldNames().forEachRemaining(headerFields::add);
            if (!headerFields.equals(Set.of("alg", "typ", "kid")) || !JWSAlgorithm.RS256.equals(header.getAlgorithm())
                    || !TYPE.equals(header.getType())) throw invalid();
            Trust trust = configuredTrust.get(header.getKeyID());
            if (trust == null || !jws.verify(new RSASSAVerifier(trust.key()))) throw invalid();
            JsonNode claims = JSON.readTree(jws.getPayload().toBytes());
            if (!claims.isObject()) throw invalid();
            var names = new HashSet<String>(); claims.fieldNames().forEachRemaining(names::add);
            if (!FIELDS.containsAll(names) || names.size() < FIELDS.size() - 1) throw invalid();
            if (!"verified-request-v1".equals(text(claims, "version")) || !trust.issuer().equals(text(claims, "issuer"))
                    || !actual.audience().equals(text(claims, "audience")) || !actual.operation().equals(text(claims, "operation"))
                    || !actual.method().equals(text(claims, "method")) || !actual.requestTarget().equals(text(claims, "request_target"))
                    || !actual.bodySha256().equals(text(claims, "body_sha256"))) throw invalid();
            long issued = number(claims, "issued_at"), expiry = number(claims, "expires_at"), now = clock.instant().getEpochSecond();
            if (issued < 0 || expiry <= issued || expiry - issued > 60 || issued > now + 5 || expiry <= now) throw invalid();
            String subject = text(claims, "subject"), id = text(claims, "invocation_id");
            String organization = claims.has("organization") ? text(claims, "organization") : null;
            if (!replays.claim(trust.issuer(), actual.audience(), id, Instant.ofEpochSecond(expiry))) throw invalid();
            return new Identity(trust.issuer(), subject, organization, id);
        } catch (Exception e) { throw invalid(); }
    }

    private static String text(JsonNode claims, String field) {
        var value = claims.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 8192) throw invalid();
        return value.textValue();
    }
    private static long number(JsonNode claims, String field) {
        var value = claims.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw invalid();
        return value.longValue();
    }
    private static SecurityException invalid() { return new SecurityException("invocation_identity_invalid"); }
}
