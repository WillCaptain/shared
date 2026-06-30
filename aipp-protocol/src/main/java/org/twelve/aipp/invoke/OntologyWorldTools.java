package org.twelve.aipp.invoke;

import java.util.List;

/**
 * Canonical tool names for the <b>ontology-world capability</b> (Host-brokered).
 * Consumers ({@code note-one}, {@code decision-reactor}) declare these in their
 * {@code /api/tools} {@code requires[]} and call them via {@link org.twelve.aipp.host.HostToolClient}.
 *
 * @see spec/ontology-world-capability.md
 */
public final class OntologyWorldTools {

    private OntologyWorldTools() {}

    // ── Wiki ops (build & read a user's wiki world) ─────────────────────────
    public static final String WIKI_ENSURE      = "wiki_ensure";
    public static final String WIKI_UPSERT_NODE = "wiki_upsert_node";
    public static final String WIKI_LEAVES      = "wiki_leaves";
    public static final String WIKI_DOCUMENT    = "wiki_document";
    public static final String WIKI_EVAL        = "wiki_eval";
    public static final String WIKI_SCHEMA      = "wiki_schema";
    public static final String WIKI_ADD_TYPES   = "wiki_add_types";
    public static final String WIKI_BUILD       = "wiki_build";
    public static final String WIKI_PROMOTE     = "wiki_promote";
    public static final String WIKI_HISTORY     = "wiki_history";

    // ── Catalog + subscription ops (decision reactor) ───────────────────────
    public static final String ONTOLOGY_LIST_WORLDS     = "ontology_list_worlds";
    public static final String ONTOLOGY_ENTRY_TEMPLATES = "ontology_entry_templates";
    public static final String ONTOLOGY_ENTITY_OUTLINES = "ontology_entity_outlines";
    public static final String ONTOLOGY_SUBSCRIBE       = "ontology_subscribe";
    public static final String ONTOLOGY_UNSUBSCRIBE     = "ontology_unsubscribe";

    /** Wiki tool names a wiki consumer (note-one) should declare in {@code requires[]}. */
    public static final List<String> WIKI_REQUIRES = List.of(
            WIKI_ENSURE, WIKI_UPSERT_NODE, WIKI_LEAVES, WIKI_DOCUMENT, WIKI_EVAL,
            WIKI_SCHEMA, WIKI_ADD_TYPES, WIKI_BUILD, WIKI_PROMOTE, WIKI_HISTORY);

    /** Catalog/subscription tool names a reactor consumer should declare in {@code requires[]}. */
    public static final List<String> CATALOG_REQUIRES = List.of(
            ONTOLOGY_LIST_WORLDS, ONTOLOGY_ENTRY_TEMPLATES, ONTOLOGY_ENTITY_OUTLINES,
            ONTOLOGY_SUBSCRIBE, ONTOLOGY_UNSUBSCRIBE);
}
