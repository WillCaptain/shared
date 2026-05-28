/**
 * Test fixture providing the canonical {@link #SYSTEM_OUTLINES} constant for outline-editor module tests.
 *
 * <p><strong>Single source of truth:</strong> the authoritative definition lives in
 * {@code entitir/ontology/src/main/java/org/twelve/entitir/ontology/world/OntologyWorld.java}
 * — field {@code SYSTEM_OUTLINES}.  The string below is a <em>verbatim copy</em>;
 * whenever {@code OntologyWorld.SYSTEM_OUTLINES} changes, this constant <strong>must</strong>
 * be updated to match.
 *
 * <p>The this module cannot import from entitir (circular dependency), so this file acts
 * as the bridge for tests that need the real VirtualSet / Aggregator / GroupBy type system.
 */
public final class EditorTestFixtures {

    private EditorTestFixtures() {}

    /**
     * System-level outline declarations: Aggregator, GroupBy, VirtualSet.
     *
     * <p><strong>⚠ Keep in sync with:</strong>
     * {@code entitir/ontology/src/main/java/org/twelve/entitir/ontology/world/OntologyWorld.java}
     * — field {@code public static final String SYSTEM_OUTLINES}.
     *
     * <p>This is a read-only copy for outline-editor module tests.
     * The entitir OntologyWorld is the single source of truth.
     */
    public static final String SYSTEM_OUTLINES = """
            outline Aggregator = <a>{
                  // Count elements matching the current predicate; returns self for chaining
                  count: Unit -> ~this,
                  // Apply projection f:(a→Number) to each element and accumulate the sum; returns self for chaining
                  sum: (a -> Number) -> ~this,
                  // Apply projection f:(a→Number) to each element and compute the average; returns self for chaining
                  avg: (a -> Number) -> ~this,
                  // Apply projection f:(a→Number) to each element and find the minimum; returns self for chaining
                  min: (a -> Number) -> ~this,
                  // Apply projection f:(a→Number) to each element and find the maximum; returns self for chaining
                  max: (a -> Number) -> ~this,
                  // Execute and return all configured aggregation results as Map<aggregation-name, value>
                  compute: Unit -> [String:Number]
            };
            
            outline GroupBy = <k, a>{
                  // Filter elements within each group by predicate (a→Bool), preserving group structure; returns self
                  filter: (a -> Bool) -> ~this,
                  // Count elements in each group; returns Map<k, Int>
                  count: Unit -> [k:Int],
                  // Run aggregation f:(Aggregator<a>→b) on each group; returns Map<k, b>
                  aggregate: <b>(Aggregator<a> -> b) -> [k:b],
                  // Convert grouped result to Map<k, VirtualSet<a>>, each key mapping to its original subset
                  to_map: Unit -> [k:VirtualSet<a>]
            };
            
            outline VirtualSet = <a>{
                  // Keep elements satisfying predicate (a→Bool); returns same-type subset (~this covariant self-type)
                  filter: (a->Bool) -> ~this,
                  // Sort ascending by projection field (? matches any orderable type); returns sorted new collection (~this)
                  order_by: (a -> ?) -> ~this,
                  // Sort descending by projection field (? matches any orderable type); returns sorted new collection (~this)
                  order_desc_by: (a -> ?) -> ~this,
                  // Take the first n records; returns a subset of at most n elements (~this)
                  take: Int -> ~this,
                  // Transform each element a via f:(a→b) into b; returns VirtualSet<b> (fn<b> is generic constraint)
                  map: fn<b> (a->b) -> VirtualSet<b>,
                  type:#"Virtual Set",
                  // Trigger database query and materialise results as an ordered list [a]
                  to_list: Unit -> [a],
                  // Return the first record a in the collection (throws if empty)
                  first: Unit -> a,
                  // Return the last record a in the collection (throws if empty)
                  last: Unit -> a,
                  // Count the number of elements in the collection; returns Int
                  count: Unit -> Int,
                  // Check whether the collection is non-empty; returns Bool
                  exists: Unit -> Bool,
                  // Apply projection (a→Number) to each element and return the sum as Number
                  sum: (a->Number) -> Number,
                  // Apply projection (a→Number) to each element and return the average as Number
                  avg: (a->Number) -> Number,
                  // Apply projection (a→b) and return the minimum value (b must be comparable)
                  min: fn<b>(a -> b) -> b,
                  // Apply projection (a→b) and return the maximum value (b must be comparable)
                  max: fn<b>(a -> b) -> b,
                  // Fold reduction: starting from init:b, apply f:(a→b) to accumulate over each element, returning b
                  reduce: fn<b>b->(a->b)->b,
                  // Iterate each element applying side-effect function f:(a→Unit); produces no new collection, returns Unit
                  each: (a -> Unit) -> Unit,
                  // Run multi-aggregation on the collection via an Aggregator pipeline; call compute() to get results
                  aggregate: <b>(Aggregator<a> -> b) -> b,
                  // Group elements by projection (a→b); returns GroupBy<b,a> supporting per-group aggregation and filtering
                  group_by: <b>(a->b)->GroupBy<b,a>
            };
            """;
}
