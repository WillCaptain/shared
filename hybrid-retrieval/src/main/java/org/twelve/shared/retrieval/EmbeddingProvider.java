package org.twelve.shared.retrieval;

import java.util.List;

/** Produces one embedding per input, preserving input order. */
public interface EmbeddingProvider {
    String model();

    List<float[]> embed(List<String> inputs) throws EmbeddingException;

    default float[] embed(String input) throws EmbeddingException {
        List<float[]> result = embed(List.of(input));
        if (result.size() != 1) {
            throw new EmbeddingException("embedding provider returned " + result.size() + " vectors for one input");
        }
        return result.getFirst();
    }
}
