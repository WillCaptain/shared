package org.twelve.aipp.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Compact Ed25519 assertion codec. The payload is signed as transmitted, not re-serialized. */
public final class ResourceGrantAssertions {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final int MAX_ASSERTION_CHARS = 16_384;

    private ResourceGrantAssertions() {}

    public static String sign(ResourceGrantAssertion claims, PrivateKey key) {
        if (claims == null || key == null) throw new IllegalArgumentException("claims and private key are required");
        try {
            byte[] payload = JSON.writeValueAsBytes(claims);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(payload);
            return B64.encodeToString(payload) + "." + B64.encodeToString(signer.sign());
        } catch (Exception error) {
            throw new IllegalArgumentException("resource grant assertion could not be signed", error);
        }
    }

    public static ResourceGrantAssertion verify(String assertion, PublicKey key) {
        if (assertion == null || assertion.isBlank() || assertion.length() > MAX_ASSERTION_CHARS || key == null) {
            throw new IllegalArgumentException("resource grant assertion is unavailable");
        }
        try {
            String[] parts = assertion.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException("resource grant assertion is malformed");
            byte[] payload = B64D.decode(parts[0].getBytes(StandardCharsets.US_ASCII));
            byte[] signature = B64D.decode(parts[1].getBytes(StandardCharsets.US_ASCII));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(payload);
            if (!verifier.verify(signature)) throw new IllegalArgumentException("resource grant assertion signature is invalid");
            return JSON.readValue(payload, ResourceGrantAssertion.class);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("resource grant assertion is invalid", error);
        }
    }

    public static PrivateKey privateKey(String encoded) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(clean(encoded))));
        } catch (Exception error) {
            throw new IllegalArgumentException("resource grant private key is invalid", error);
        }
    }

    public static PublicKey publicKey(String encoded) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(clean(encoded))));
        } catch (Exception error) {
            throw new IllegalArgumentException("resource grant public key is invalid", error);
        }
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("resource grant key is required");
        return value.replaceAll("\\s+", "");
    }
}
