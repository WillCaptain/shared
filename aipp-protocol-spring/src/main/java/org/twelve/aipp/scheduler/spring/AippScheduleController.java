package org.twelve.aipp.scheduler.spring;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.twelve.aipp.scheduler.*;
import java.util.*;

@RestController
public final class AippScheduleController {
    private final DefaultAippScheduleRegistrar registrar;
    private final String appId;

    AippScheduleController(DefaultAippScheduleRegistrar registrar, String appId) {
        this.registrar = registrar; this.appId = appId;
    }

    @GetMapping(AippScheduleSpec.HANDLERS_PATH)
    public Map<String,Object> handlers() {
        return Map.of("app", Map.of("app_id", appId), "handlers", registrar.registrations());
    }

    @PostMapping(AippScheduleSpec.FIRE_PATH_PREFIX + "{handler}")
    public ResponseEntity<ScheduleFireResult> fire(
            @PathVariable("handler") String handler,
            @RequestHeader(AippScheduleSpec.HOST_APP_ID_HEADER) String deliveredAppId,
            @RequestHeader(AippScheduleSpec.HOST_USER_ID_HEADER) String userId,
            @RequestHeader(AippScheduleSpec.DELIVERY_ID_HEADER) String deliveryId,
            @RequestBody ScheduleFireRequest request) {
        if (!appId.equals(deliveredAppId == null ? "" : deliveredAppId.trim())) return ResponseEntity.status(403).build();
        if (!AippScheduleSpec.requireHandler(handler).equals(request.handler())) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok(ScheduleDeliveryContext.callAs(appId, userId, deliveryId,
                    () -> registrar.require(handler).onFire(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
