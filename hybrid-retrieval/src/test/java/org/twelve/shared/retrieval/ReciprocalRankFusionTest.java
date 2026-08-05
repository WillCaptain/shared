package org.twelve.shared.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReciprocalRankFusionTest {
    @Test
    void usesK60AndRewardsDocumentsPresentInBothLists() {
        RetrievalHit firstLexical = hit("a", 0.9, "lexical");
        RetrievalHit secondLexical = hit("b", 0.8, "lexical");
        RetrievalHit firstVector = hit("b", 0.95, "vector");
        RetrievalHit secondVector = hit("c", 0.7, "vector");

        List<RetrievalHit> fused = ReciprocalRankFusion.fuse(
                3, List.of(firstLexical, secondLexical), List.of(firstVector, secondVector));

        assertEquals("b", fused.getFirst().document().id());
        assertEquals(1.0 / 62 + 1.0 / 61, fused.getFirst().score(), 1e-12);
        assertEquals(Set.of("lexical", "vector"), fused.getFirst().sources());
    }

    private static RetrievalHit hit(String id, double score, String source) {
        RetrievalDocument document = new RetrievalDocument(
                "ns", id, "/" + id, "document", id, id, "", Map.of());
        return "lexical".equals(source)
                ? new RetrievalHit(document, score, score, null, Set.of(source))
                : new RetrievalHit(document, score, null, score, Set.of(source));
    }
}
