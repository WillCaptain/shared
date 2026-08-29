package org.twelve.aipp.host;

/** Trusted headers added by a Host while proxying authenticated browser traffic to an AIPP. */
public final class AippProxyHeaders {

    public static final String USER_ID = "X-Ones-User-Id";
    public static final String ORG_ID = "X-Ones-Org-Id";

    private AippProxyHeaders() {}
}
