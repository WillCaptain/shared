package org.twelve.aipp.frontend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    @Test
    void scanWidgetLocalCss_flagsCssFilesInWidgetTree(@TempDir Path widgetsRoot) throws Exception {
        Path widgetDir = widgetsRoot.resolve("demo");
        Files.createDirectories(widgetDir);
        Files.writeString(widgetDir.resolve("demo.css"), ".demo { color: red; }");
        Files.writeString(widgetDir.resolve("demo.js"), "export function mount() {}");

        List<String> hits = WidgetGuardSupport.scanWidgetLocalCss(widgetsRoot);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).contains("demo.css"));
    }

    @Test
    void scanWidgetLocalCss_flagsInlineHardcodedColors(@TempDir Path widgetsRoot) throws Exception {
        Path widgetDir = widgetsRoot.resolve("demo");
        Files.createDirectories(widgetDir);
        Files.writeString(widgetDir.resolve("demo.js"), """
                export function mount() {
                  Object.assign(btn.style, { color: '#9aa4b2' });
                }
                """);

        List<String> hits = WidgetGuardSupport.scanWidgetLocalCss(widgetsRoot);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(h -> h.contains("inline styled colors")));
    }
}
