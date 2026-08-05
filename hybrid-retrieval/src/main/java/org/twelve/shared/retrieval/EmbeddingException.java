package org.twelve.shared.retrieval;

/** Signals an unavailable or invalid embedding response. */
public class EmbeddingException extends Exception {
    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
