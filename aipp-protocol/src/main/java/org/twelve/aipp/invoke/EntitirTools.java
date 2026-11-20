package org.twelve.aipp.invoke;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical tool names for the <b>Entitir capability</b>: the headless,
 * {@code world_id}-keyed surface an AIPP uses to build a data application on
 * an ontology world. Consumers declare the bundles they need in their
 * {@code /api/tools} {@code requires[]} and call the names through
 * {@link org.twelve.aipp.host.HostToolClient}; they never hold the provider's
 * app id or base URL.
 *
 * <p>The names are the contract. Which of them a deployed provider already
 * serves is listed in {@code spec/entitir-capability.md} §9; a name that is not
 * served yet is unresolved on the Host like any other missing capability.
 *
 * @see OntologyWorldTools the earlier {@code wiki_*} / {@code ontology_*} names
 */
public final class EntitirTools {

    private EntitirTools() {}

    /** Namespace of the capability. Other tools may share the prefix; only {@link #ALL} is the contract. */
    public static final String PREFIX = "entitir_";

    // ── Worlds ──────────────────────────────────────────────────────────────
    public static final String WORLD_LIST   = "entitir_world_list";
    public static final String WORLD_GET    = "entitir_world_get";
    public static final String WORLD_CREATE = "entitir_world_create";
    public static final String WORLD_UPDATE = "entitir_world_update";
    public static final String WORLD_DELETE = "entitir_world_delete";

    // ── Model (types), build and release ────────────────────────────────────
    public static final String MODEL_GET     = "entitir_model_get";
    public static final String MODEL_APPLY   = "entitir_model_apply";
    public static final String WORLD_BUILD   = "entitir_world_build";
    public static final String WORLD_RELEASE = "entitir_world_release";

    // ── Data (walk through and change a built world) ────────────────────────
    public static final String DATA_SCHEMA       = "entitir_data_schema";
    public static final String DATA_SEARCH       = "entitir_data_search";
    public static final String DATA_NEIGHBORHOOD = "entitir_data_neighborhood";
    public static final String DATA_EVAL         = "entitir_data_eval";
    public static final String DATA_ASK          = "entitir_data_ask";
    public static final String DATA_CHANGE       = "entitir_data_change";

    // ── Decisions and actions ───────────────────────────────────────────────
    public static final String CAPABILITY_LIST   = "entitir_capability_list";
    public static final String CAPABILITY_SAVE   = "entitir_capability_save";
    public static final String CAPABILITY_REMOVE = "entitir_capability_remove";
    public static final String DECISION_RUN      = "entitir_decision_run";
    public static final String ACTION_RUN        = "entitir_action_run";
    public static final String EXECUTION_LIST    = "entitir_execution_list";
    public static final String EXECUTION_GET     = "entitir_execution_get";

    // ── Analysis topics ─────────────────────────────────────────────────────
    public static final String TOPIC_LIST    = "entitir_topic_list";
    public static final String TOPIC_SAVE    = "entitir_topic_save";
    public static final String TOPIC_DELETE  = "entitir_topic_delete";
    public static final String TOPIC_PUBLISH = "entitir_topic_publish";

    /** Find, create, rename and delete worlds. */
    public static final List<String> WORLD_REQUIRES = List.of(
            WORLD_LIST, WORLD_GET, WORLD_CREATE, WORLD_UPDATE, WORLD_DELETE);

    /** Change a world's types, build staging and release it to production. */
    public static final List<String> MODEL_REQUIRES = List.of(
            MODEL_GET, MODEL_APPLY, WORLD_BUILD, WORLD_RELEASE);

    /** Read a built world: schema, type graph, expressions and questions. */
    public static final List<String> DATA_READ_REQUIRES = List.of(
            DATA_SCHEMA, DATA_SEARCH, DATA_NEIGHBORHOOD, DATA_EVAL, DATA_ASK);

    /** Write entity rows into a built world. */
    public static final List<String> DATA_WRITE_REQUIRES = List.of(DATA_CHANGE);

    /** Author a world's decisions and actions. */
    public static final List<String> CAPABILITY_REQUIRES = List.of(
            CAPABILITY_LIST, CAPABILITY_SAVE, CAPABILITY_REMOVE);

    /** Run decisions and actions and read what the run did. */
    public static final List<String> RUN_REQUIRES = List.of(
            CAPABILITY_LIST, DECISION_RUN, ACTION_RUN, EXECUTION_LIST, EXECUTION_GET);

    /** Author and publish analysis topics. */
    public static final List<String> TOPIC_REQUIRES = List.of(
            TOPIC_LIST, TOPIC_SAVE, TOPIC_DELETE, TOPIC_PUBLISH);

    /** Every contract name, in spec order. */
    public static final List<String> ALL = List.of(
            WORLD_LIST, WORLD_GET, WORLD_CREATE, WORLD_UPDATE, WORLD_DELETE,
            MODEL_GET, MODEL_APPLY, WORLD_BUILD, WORLD_RELEASE,
            DATA_SCHEMA, DATA_SEARCH, DATA_NEIGHBORHOOD, DATA_EVAL, DATA_ASK, DATA_CHANGE,
            CAPABILITY_LIST, CAPABILITY_SAVE, CAPABILITY_REMOVE,
            DECISION_RUN, ACTION_RUN, EXECUTION_LIST, EXECUTION_GET,
            TOPIC_LIST, TOPIC_SAVE, TOPIC_DELETE, TOPIC_PUBLISH);

    /** Names that take no {@code world_id}: they locate or create a world, or address a run by its own id. */
    public static final Set<String> WITHOUT_WORLD_ID = Set.of(WORLD_LIST, WORLD_CREATE, EXECUTION_GET);

    /** Names that change durable state; a provider declares {@code side_effect} other than {@code none}. */
    public static final Set<String> MUTATING = Set.of(
            WORLD_CREATE, WORLD_UPDATE, WORLD_DELETE,
            MODEL_APPLY, WORLD_BUILD, WORLD_RELEASE,
            DATA_CHANGE,
            CAPABILITY_SAVE, CAPABILITY_REMOVE, DECISION_RUN, ACTION_RUN,
            TOPIC_SAVE, TOPIC_DELETE, TOPIC_PUBLISH);

    /**
     * Contract name → the earlier name that accepts the contract's arguments and runs the
     * same operation. A provider keeps serving the earlier name, with its earlier response
     * shape, so existing consumers keep working.
     */
    public static final Map<String, String> EARLIER_NAMES = earlierNames();

    /** The union of the given bundles without duplicates, for a consumer's {@code requires[]}. */
    @SafeVarargs
    public static List<String> requires(List<String>... bundles) {
        List<String> names = new ArrayList<>();
        for (List<String> bundle : bundles) {
            for (String name : bundle) {
                if (!names.contains(name)) names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static Map<String, String> earlierNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(WORLD_BUILD, OntologyWorldTools.WIKI_BUILD);
        names.put(WORLD_RELEASE, OntologyWorldTools.WIKI_PROMOTE);
        names.put(DATA_SCHEMA, "analysis_world_schema");
        names.put(DATA_SEARCH, "analysis_world_search");
        names.put(DATA_NEIGHBORHOOD, "analysis_world_neighborhood");
        names.put(DATA_EVAL, "analysis_world_eval");
        names.put(DATA_ASK, "ontology_data_query");
        names.put(DATA_CHANGE, "world_entity_change_batch");
        names.put(DECISION_RUN, "invoke_decision");
        names.put(EXECUTION_LIST, "runtime_execution_list");
        names.put(EXECUTION_GET, "runtime_execution_get");
        names.put(TOPIC_LIST, "analysis_topic_list");
        names.put(TOPIC_SAVE, "analysis_topic_save");
        names.put(TOPIC_DELETE, "analysis_topic_delete");
        names.put(TOPIC_PUBLISH, "analysis_topic_publish");
        return Collections.unmodifiableMap(names);
    }
}
