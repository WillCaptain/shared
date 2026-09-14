package org.twelve.aipp.widget;

import java.net.URI;

/** Exact app-local static targets; never a wildcard or an API-route permission. */
public final class WidgetAssetPaths {
    private WidgetAssetPaths() {}

    public static boolean isStaticTarget(String target) {
        if (target == null || target.length() > 2048
                || target.chars().anyMatch(c -> c < 33 || c > 126)) return false;
        try {
            URI uri = URI.create(target);
            return !uri.isAbsolute() && uri.getRawAuthority() == null && uri.getRawFragment() == null
                    && uri.normalize().equals(uri) && uri.getRawPath() != null
                    && uri.getRawPath().matches("/widgets/(?:[A-Za-z0-9_-][A-Za-z0-9_.-]*/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.(?:js|css)");
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
