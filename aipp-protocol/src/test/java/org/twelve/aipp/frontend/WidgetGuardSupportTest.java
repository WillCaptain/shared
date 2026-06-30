package org.twelve.aipp.frontend;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WidgetGuardSupportTest {

    @Test
    void validateEsm_acceptsMountInTemplateCss() {
        String src = """
                function inject() {
                  const el = document.createElement('style');
                  el.textContent = `.foo { color: red; }`;
                }
                export function mount(targetEl, hostApi, data) {
                  inject();
                  targetEl.textContent = 'ok';
                }
                """;
        assertTrue(WidgetGuardSupport.validateEsmWidgetSource(src).isEmpty());
    }

    @Test
    void validateEsm_rejectsBareCssOutsideStrings() {
        String src = """
                .entity-graph-root {
                  display: flex;
                }
                export function mount(targetEl) {
                  targetEl.textContent = 'ok';
                }
                """;
        Optional<String> err = WidgetGuardSupport.validateEsmWidgetSource(src);
        assertTrue(err.isPresent());
        assertTrue(err.get().contains("bare CSS"));
    }

    @Test
    void validateEsm_rejectsMissingMount() {
        assertTrue(WidgetGuardSupport.validateEsmWidgetSource("export const x = 1;").isPresent());
    }

    @Test
    void findBareCss_ignoresCssInsideComments() {
        String src = """
                // .foo { color: red; }
                export function mount() {}
                """;
        assertTrue(WidgetGuardSupport.findBareCssOutsideStrings(src).isEmpty());
    }
}
