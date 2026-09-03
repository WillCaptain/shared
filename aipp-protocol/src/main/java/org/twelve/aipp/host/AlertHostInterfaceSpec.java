package org.twelve.aipp.host;

/** Generic Host runtime contract supplied by whichever AIPP owns alert projection. */
public interface AlertHostInterfaceSpec {
    String EFFECT_TYPE = "shared.alert.runtime/v1";

    String OP_HYDRATE = "hydrate";
    String OP_SUBSCRIBE = "subscribe";
    String OP_ACTIVE_ALERTS = "activeAlerts";
    String OP_DISMISS_ALERT = "dismissAlert";
    String OP_TOGGLE_TRACKING = "toggleTracking";
    String OP_ADD_ITEM_COMMENT = "addItemComment";
    String OP_CLASSIFY_ITEM = "classifyItem";
    String OP_APPLY_ITEMS = "applyItems";
    String OP_IS_TRACKED = "isTracked";
    String OP_TRACK_LIVE_ITEM = "trackLiveItem";
    String OP_OPEN_PRIMARY_VIEW = "openPrimaryView";
    String OP_FOCUS_ITEM = "focusItem";

    String FIELD_ITEM_ID = "item_id";
    String FIELD_PRIMARY_WIDGET_TYPE = "primary_widget_type";
    String FIELD_ITEM_WIDGET_TYPE = "item_widget_type";
    String FIELD_ITEM_WIDGET_MODULE_URL = "item_widget_module_url";
}
