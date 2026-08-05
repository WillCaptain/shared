package org.twelve.shared.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reciprocal-rank fusion with the conventional constant {@value #K}. */
public final class ReciprocalRankFusion {
    public static final int K = 60;

    private ReciprocalRankFusion() {}

    @SafeVarargs
    public static List<RetrievalHit> fuse(int limit, List<RetrievalHit>... rankedLists) {
        if (limit <= 0) return List.of();
        Map<String, Accumulator> byDocument = new LinkedHashMap<>();
        for (List<RetrievalHit> ranked : rankedLists) {
            if (ranked == null) continue;
            for (int i = 0; i < ranked.size(); i++) {
                RetrievalHit hit = ranked.get(i);
                String key = hit.document().namespace() + "\u0000" + hit.document().id();
                Accumulator acc = byDocument.computeIfAbsent(key,
                        ignored -> new Accumulator(hit.document()));
                acc.rrf += 1.0 / (K + i + 1);
                if (hit.lexicalScore() != null) acc.lexical = hit.lexicalScore();
                if (hit.vectorScore() != null) acc.vector = hit.vectorScore();
                acc.sources.addAll(hit.sources());
            }
        }
        List<RetrievalHit> result = new ArrayList<>();
        for (Accumulator acc : byDocument.values()) {
            result.add(new RetrievalHit(acc.document, acc.rrf, acc.lexical, acc.vector, acc.sources));
        }
        result.sort(Comparator.comparingDouble(RetrievalHit::score).reversed()
                .thenComparing(hit -> hit.document().id()));
        return List.copyOf(result.subList(0, Math.min(result.size(), limit)));
    }

    private static final class Accumulator {
        private final RetrievalDocument document;
        private final Set<String> sources = new LinkedHashSet<>();
        private double rrf;
        private Double lexical;
        private Double vector;

        private Accumulator(RetrievalDocument document) {
            this.document = document;
        }
    }
}
