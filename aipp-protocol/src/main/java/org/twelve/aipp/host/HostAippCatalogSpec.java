package org.twelve.aipp.host;

/** Provider-neutral contract for the Host's aggregate AIPP catalog. */
public final class HostAippCatalogSpec {
    public static final int SCHEMA_VERSION = 1;
    public static final String PATH = "/api/host/aipps";
    public static final String FIELD_SCHEMA_VERSION = "schema_version";
    public static final String FIELD_APPS = "apps";
    public static final String FIELD_HELP_CONTRIBUTIONS = "help_contributions";

    private HostAippCatalogSpec() {}
}
