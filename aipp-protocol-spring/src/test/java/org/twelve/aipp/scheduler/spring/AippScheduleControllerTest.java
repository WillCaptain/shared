package org.twelve.aipp.scheduler.spring;

import org.junit.jupiter.api.Test;
import org.twelve.aipp.scheduler.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AippScheduleControllerTest {

    @Test
    void exposesRegisteredHandlersAndDispatchesWithTrustedDeliveryContext() {
        DefaultAippScheduleRegistrar registrar = new DefaultAippScheduleRegistrar();
        AtomicReference<ScheduleDeliveryContext.Value> delivered = new AtomicReference<>();
        registrar.register(new AippScheduleHandler() {
            @Override public String name() { return "sting.fire"; }
            @Override public ScheduleFireResult onFire(ScheduleFireRequest request) {
                delivered.set(ScheduleDeliveryContext.require());
                return ScheduleFireResult.completed();
            }
        });
        AippScheduleController controller = new AippScheduleController(registrar, "sting-one");
        ScheduleFireRequest request = new ScheduleFireRequest(
                "job-1", "sting:card:start", "sting.fire", 100L, 1, Map.of());

        assertThat(controller.handlers().get("handlers").toString()).contains("sting.fire");
        assertThat(controller.fire("sting.fire", "sting-one", "user-1", "job-1:1", request)
                .getBody()).isEqualTo(ScheduleFireResult.completed());
        assertThat(delivered.get()).isEqualTo(
                new ScheduleDeliveryContext.Value("sting-one", "user-1", "job-1:1"));
    }

    @Test
    void rejectsWrongOwnerAndPathBodyHandlerMismatch() {
        DefaultAippScheduleRegistrar registrar = new DefaultAippScheduleRegistrar();
        registrar.register(new AippScheduleHandler() {
            @Override public String name() { return "sting.fire"; }
            @Override public ScheduleFireResult onFire(ScheduleFireRequest request) {
                return ScheduleFireResult.completed();
            }
        });
        AippScheduleController controller = new AippScheduleController(registrar, "sting-one");
        ScheduleFireRequest request = new ScheduleFireRequest(
                "job-1", "sting:card:start", "sting.fire", 100L, 1, Map.of());

        assertThat(controller.fire("sting.fire", "other-app", "user-1", "job-1:1", request)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(controller.fire("other.fire", "sting-one", "user-1", "job-1:1", request)
                .getStatusCode().value()).isEqualTo(400);
    }
}
