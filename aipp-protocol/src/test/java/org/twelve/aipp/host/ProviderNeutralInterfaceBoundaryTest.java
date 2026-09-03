package org.twelve.aipp.host;

import org.junit.jupiter.api.Test;
import org.twelve.aipp.session.AippExternalSessionSpec;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps cross-AIPP Host boundaries declarative: constants only, never provider behavior. */
class ProviderNeutralInterfaceBoundaryTest {

    @Test
    void shared_provider_boundaries_are_interfaces_without_function_implementations() {
        List<Class<?>> boundaries = List.of(
                AlertHostInterfaceSpec.class,
                HostContributionInterfaceSpec.class,
                AippExternalSessionSpec.class);

        for (Class<?> boundary : boundaries) {
            assertThat(boundary.isInterface()).as(boundary.getName()).isTrue();
            assertThat(List.of(boundary.getDeclaredMethods()))
                    .as("%s must contain constants only", boundary.getName())
                    .isEmpty();
            assertThat(List.of(boundary.getDeclaredFields()))
                    .allSatisfy(field -> assertThat(field.getModifiers())
                            .satisfies(modifiers -> {
                                assertThat(Modifier.isPublic(modifiers)).isTrue();
                                assertThat(Modifier.isStatic(modifiers)).isTrue();
                                assertThat(Modifier.isFinal(modifiers)).isTrue();
                            }));
        }
    }

    @Test
    void shared_provider_boundaries_expose_no_provider_named_methods() {
        List<Class<?>> boundaries = List.of(
                AlertHostInterfaceSpec.class,
                HostContributionInterfaceSpec.class,
                AippExternalSessionSpec.class);

        assertThat(boundaries.stream()
                .flatMap(boundary -> List.of(boundary.getDeclaredMethods()).stream())
                .map(Method::getName))
                .noneMatch(name -> name.matches("(?i).*(sting|chat|user).*"));
    }
}
