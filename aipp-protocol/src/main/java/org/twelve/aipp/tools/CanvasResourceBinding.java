package org.twelve.aipp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared Host/provider rule for resource arguments bound to the active canvas. */
public final class CanvasResourceBinding {
    public static final String FIELD = "canvas_resource_parameters";

    private CanvasResourceBinding() {}

    public static Map<String, Object> bind(Map<String, Object> tool,
                                          Map<String, Object> arguments, String workspaceId) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        if (workspaceId == null || workspaceId.isBlank() || tool == null
                || !(tool.get(FIELD) instanceof List<?> parameters) || parameters.isEmpty()) return args;
        String bound = workspaceId.strip();
        Map<String, Object> result = new LinkedHashMap<>(args);
        for (Object parameter : parameters) {
            if (!(parameter instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("invalid canvas resource parameter declaration");
            }
            Object value = args.get(name);
            if (value != null && (!(value instanceof String text)
                    || (!text.isBlank() && !bound.equals(text.strip())))) {
                throw new Conflict(name);
            }
            result.put(name, bound);
        }
        return result;
    }

    public static final class Conflict extends IllegalArgumentException {
        public Conflict(String parameter) {
            super("canvas_resource_conflict: " + parameter + " must target the current canvas resource");
        }
    }
}
