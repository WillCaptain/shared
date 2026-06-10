# AIPP Compliance Verification

> **Audience:** coding agents and app developers.  
> **Rule:** If this doc disagrees with `AippAppSpec` / `AippWidgetSpec` / other `assert*` methods, **the Java methods win**.

---

## Minimum gate (every change)

Run these against **fixture JSON** captured from your HTTP handlers (or golden files in your app’s `src/test/resources`):

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.twelve.aipp.AippAppSpec;
import org.twelve.aipp.AippHostInjectionSpec;
import org.twelve.aipp.AippConfigurationSpec;
import org.twelve.aipp.widget.AippWidgetSpec;

AippAppSpec spec = new AippAppSpec();
ObjectMapper json = new ObjectMapper();

JsonNode app     = json.readTree(appJson);
JsonNode tools   = json.readTree(toolsJson);
JsonNode widgets = json.readTree(widgetsJson);

spec.assertValidAppManifest(app);
spec.assertValidToolsApiStructure(tools);
spec.assertValidWidgetsApiStructure(widgets);
spec.assertAppIdConsistency(app, tools);
spec.assertAppIdConsistency(app, widgets);
spec.assertWidgetsHaveAppIdentityFields(widgets);
spec.assertExactlyOneMainWidget(widgets, List.of("your-app-id"));
```

Optional when skills exist:

```java
JsonNode skills = json.readTree(skillsJson);
spec.assertValidSkillsApiStructure(skills);
spec.assertWidgetTypesRegistered(tools, widgets); // reads tools[] first, falls back to skills[]
```

Per tool entry with session/decoupling fields:

```java
spec.assertValidSkillSessionExtension(toolEntry);
spec.assertValidOutputWidgetRules(toolEntry);
spec.assertValidLifecycle(toolEntry);
```

---

## Protocol compression (2.4–2.7)

Validators and canonical manifests only. Host **ignores** legacy fields and logs `[AIPP legacy manifest]` warnings until all apps migrate.

| Removed from manifests | Use instead | Validator |
|------------------------|-------------|-----------|
| `auto_pre_turn` / `background` on tools | `lifecycle: pre_turn` / `post_turn` | `assertValidLifecycle` |
| `is_canvas_mode` on widgets | `display_mode: canvas` \| `chat` \| `pop` | `assertWidgetDeclaresDisplayMode` |
| `prompt` / `tools[]` / `resources` on tool entries | Skill playbook (`/api/skills`) | `assertValidSkillStructure` |
| `display_name` on tool entries | `display_label_zh` | `assertValidSkillStructure` |
| Root `system_prompt` on `/api/tools` | `prompt_contributions[layer=aap_pre]` | `assertValidToolsApiStructure` |
| `renders_output_of_skill` on widget | `entry_tool` | `assertWidgetUsesCompressedFields` (host: warn + ignore) |
| `context_prompt` / root `system_prompt` on widget | `widget_prompt` | `assertWidgetUsesCompressedFields` (host: warn + ignore) |
| Redundant `output_widget_rules` when `canvas.triggers` + `entry_tool` suffice | `canvas` on tool entry only | — |
| Nested `scope.level` / `visible_when` on tools | `visibility` + `owner_widget` / `router_shortcut` | Host normalizes via `ToolPlacement` |
| `refresh_skill` on widget | `refresh_tool` | `assertWidgetDeclaresRefreshTool` |
| `mutating_tools` on widget | `mutates_display: true` on each write tool | — (optional legacy format check) |

---

## Assert catalog

### App & endpoints (`AippAppSpec`)

| Method | Use when |
|--------|----------|
| `assertValidAppManifest` | `GET /api/app` |
| `assertValidToolsApiStructure` | `GET /api/tools` |
| `assertValidSkillsApiStructure` | `GET /api/skills` |
| `assertValidSkillStructure` | Single **tool** entry (same shape validator) |
| `assertValidWidgetsApiStructure` | `GET /api/widgets` |
| `assertValidWidgetStructure` | Single widget entry |
| `assertAppIdConsistency` | Cross-check `app_id` |
| `assertWidgetsHaveAppIdentityFields` | Widget identity fields |
| `assertExactlyOneMainWidget` | Widget list |
| `assertWidgetTypesRegistered` | Tool `canvas.widget_type` registered in widgets |
| `assertValidSkillSessionExtension` | `session` / `session_policy` on tool |
| `assertValidLifecycle` | `lifecycle` on tool |
| `assertValidOutputWidgetRules` | `output_widget_rules` |
| `assertValidRuntimeEventCallback` | `runtime_event_callbacks` |
| `assertValidEventSubscriptions` | `event_subscriptions` |
| `assertValidParametersSchema` | Tool `parameters` object |
| `assertValidSkillCanvasDeclaration` | Tool `canvas` block |
| `assertSystemWidgetExempt` | Confirm type is registered Host `sys.*` |

### Tool responses (`AippAppSpec`)

| Method | Use when |
|--------|----------|
| `assertChatModeResponse` | Chat-inline `html_widget` |
| `assertInlineWidgetResponseHasNoCanvas` | Chat-only response |
| `assertValidToolResponseCanvas` | `canvas` envelope |
| `assertCanvasOpenWithNewSession` | Canvas opens widget session |
| `assertCanvasPatchResponse` | Canvas patch/update |
| `assertToolResponseMatchesSkillCanvas` | Declared canvas vs response |

### Widgets (`AippWidgetSpec`)

| Method | Use when |
|--------|----------|
| `assertHtmlWidgetResponse` | Tool returns your widget type |
| `assertWidgetHasFullAppIdentity` | Widget manifest fields |
| `assertWidgetHasAppId` | `app_id` present |
| `assertWidgetDeclaresIsMain` | `is_main` boolean |
| `assertWidgetDeclaresDisplayMode` | `display_mode` (`canvas` \| `chat` \| `pop`) |
| `assertWidgetDeclaresAppOwnedRenderer` | `render` for external widgets |
| `assertWidgetSupportsDisable` | `disable` support |
| `assertWidgetThemeCoversProperties` | Theme keys |
| `assertThemeCssVarsComplete` | Injected CSS vars |
| `assertMutatingToolBlockedWhenDisabled` | Disabled widget + mutating tool |
| `assertReadToolWorksWhenDisabled` | Disabled widget + read tool |
| `assertWidgetDeclaresViews` | Multi-tab `views` |
| `assertWidgetDeclaresRefreshTool` | `refresh_tool` (legacy `refresh_skill` accepted) |
| `assertWidgetDeclareMutatingTools` | Legacy `mutating_tools` format only (optional; prefer `mutates_display` on tools) |
| `assertWidgetHasViews` | Specific view ids |
| `assertWidgetSupportsUpload` | Upload contract |
| `assertUploadAccepts` / `assertUploadTools` | Upload extensions/tools |

### Configuration (`AippConfigurationSpec`)

| Method | Use when |
|--------|----------|
| `assertValidConfigurationInAppManifest` | `configuration.ui` on `/api/app` |
| `assertValidConfigurationGetResponse` | `GET /api/configuration` |
| `assertValidConfigurationPutRequest` | `PUT /api/configuration` body |
| `assertValidConfigurationPutResponse` | `PUT` response |

### Host injection (`AippHostInjectionSpec`)

| Method | Use when |
|--------|----------|
| `assertValidHostBindingsPutRequest` | Host → app `PUT /api/host/bindings` |
| `assertValidHostBindingsPutResponse` | App response to bindings |
| `assertValidHostBindingsGetResponse` | Optional `GET` bindings debug |
| `assertValidEnv` | `env` string enum |

### System widgets

| Resource | Use when |
|----------|----------|
| `AippSystemWidget` constants | Valid `sys.*` type strings |
| `AippSystemWidgetSpecTest` | Example shapes for `sys.*` payloads |

---

## Maven commands

From `aipp-protocol/`:

```bash
mvn test
mvn test -Dtest=AippAppManifestTest
mvn test -Dtest=AippSystemWidgetSpecTest
mvn test -Dtest=AippConfigurationSpecTest
mvn test -Dtest=AippHostInjectionSpecTest
mvn test -Dtest=AippAppSpecSessionExtensionTest
mvn test -Dtest=HostDecouplingProtocolFieldsTest
mvn test -Dtest=ToolPlacementTest
```

From **your AIPP app** (test-scoped dependency):

```bash
mvn test -Dtest=YourAppAippComplianceTest
```

---

## Recommended app test pattern

1. Start app in test (or use static JSON fixtures).
2. `GET` each endpoint → store under `src/test/resources/aipp-fixtures/`.
3. One JUnit class runs all applicable `assert*` for your `app_id`.
4. Add per-tool response fixtures for `html_widget` / `canvas` / `awaiting_*`.
5. Editable widgets: `assertWidgetDeclaresRefreshTool` + assert write tools have `mutates_display` in `/api/tools`.

Example dependency:

```xml
<dependency>
  <groupId>org.example</groupId>
  <artifactId>aipp-protocol</artifactId>
  <version>1.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

---

## Failure handling

1. Read the `AssertionError` message — it names the field/rule.
2. Fix JSON or handler; **do not** weaken asserts.
3. If the assert is wrong, fix `aipp-protocol` first, then docs.

---

## Related

- Rules table: README §14
- Anti-patterns: README §16
- Discovery: [`INDEX.md`](INDEX.md)
