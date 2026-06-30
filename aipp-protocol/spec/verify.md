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
| `is_canvas_mode` on widgets | `display_mode: canvas` \| `chat` \| `pop` | `assertWidgetDeclaresDisplayMode`; removed (v2.8) — rejected by `assertWidgetUsesCompressedFields` |
| `prompt` / `tools[]` / `resources` on tool entries | Skill playbook (`/api/skills`) | `assertValidSkillStructure` |
| `display_name` on tool entries | `display_label_zh` | `assertValidSkillStructure` |
| Root `system_prompt` on `/api/tools` | `prompt_contributions[layer=ambient_prompt]` | `assertValidToolsApiStructure` |
| `renders_output_of_skill` on widget | `entry_tool` | `assertWidgetUsesCompressedFields` (host: warn + ignore) |
| `context_prompt` / root `system_prompt` on widget | `widget_prompt` | `assertWidgetUsesCompressedFields` (host: warn + ignore) |
| Redundant `output_widget_rules` when `canvas.triggers` + `entry_tool` suffice | `canvas` on tool entry only | — |
| Nested `scope.level` / `visible_when` on tools | `visibility` + `owner_widget` / `router_shortcut` | Removed — Host no longer reads nested `scope` |
| `refresh_skill` on widget | `refresh_tool` | Removed (v2.8) — rejected by `assertWidgetUsesCompressedFields`; Host ignores + warns |
| `mutating_tools` on widget | `mutates_display: true` on each write tool | Removed (v2.8) — rejected by `assertWidgetUsesCompressedFields`; Host ignores + warns |
| `supports: { disable, theme }` on widget | Nothing — Host never read it; `--aipp-*` CSS vars are always injected | — (asserts removed) |
| `source` (`external`/`builtin`) on widget | Nothing — Host never read it | — |

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
| `assertValidRuntimeEventCallbacks` | `/api/tools` root: `runtime_event_callbacks` array (or legacy single object) |
| `assertValidRuntimeEventCallback` | Single `runtime_event_callback` object on a skill/tool entry |
| `assertValidEventSubscriptions` | `event_subscriptions` |
| `assertValidParametersSchema` | Tool `parameters` object |
| `assertValidSkillCanvasDeclaration` | Tool `canvas` block |
| `assertValidClientExecutionFields` | `execution_surface` / `client_capability` / `requires_confirmation` on tool (auto-run by `assertValidToolsApiStructure`; see `client-execution.md` §5 invariants) |
| `assertValidSideEffectField` | `side_effect` retry-safety enum on tool (auto-run by `assertValidToolsApiStructure`; see `tool-manifest.md` §3.1) |
| `assertValidPromptContributions` | `/api/tools` root `prompt_contributions`: layer/content/priority + `ambient_prompt` ≤ `MAX_AMBIENT_PROMPT_CHARS` budget (auto-run by `assertValidToolsApiStructure`; see `host-decoupling.md` §6.1) |
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
| `assertThemeCssVarsComplete` | Injected `--aipp-*` CSS vars |
| `assertWidgetDeclaresViews` | Multi-tab `views` |
| `assertWidgetDeclaresRefreshTool` | `refresh_tool` declared (v2.8: legacy `refresh_skill` no longer accepted) |
| `assertWidgetUsesCompressedFields` | Rejects removed fields: `renders_output_of_skill`, `context_prompt`, root `system_prompt`, `refresh_skill`, `mutating_tools`, `is_canvas_mode` |
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

## Rules quick table（合规规则速查）

| 项 | 必选 | 要求 |
|----|------|------|
| `GET /api/app` | ✅ | 7 个字段全 — [`app-manifest.md`](app-manifest.md) |
| `GET /api/tools` 顶层 | ✅ | `app`, `version`, `tools` |
| `GET /api/skills` 顶层 | ✅ | `app`, `version`, `skills`（数组，可空） |
| `GET /api/widgets` 顶层 | ✅ | `app`, `version`, `widgets` |
| Tool entry | ✅ | `name`(snake_case), `description`, `parameters`(type=object) |
| Tool entry | ✅ | `canvas`（含 `triggers` boolean） |
| Tool entry | ❌ 禁用 | **不得**含 `prompt` / `tools[]` / `resources`（编排在 Skill 里） |
| Tool 顶层 | ✅ | `visibility`；推荐 `owner_widget` / `router_shortcut` / `mutates_display`（v3 扁平字段） |
| Tool `side_effect` | 推荐（写工具）| `none` / `idempotent` / `mutating`；漏标 Host 按 `mutating` fail-closed，不自动重试 — `tool-manifest.md` §3.1 |
| Widget（多 Tab / 可编辑画布） | 推荐 | `refresh_tool` + 各 write tool 的 `mutates_display: true` |
| Skill 索引条目 | ✅ | `name`, `description`(40-1024 字符且含 WHEN), `allowed_tools`(非空), `playbook_url` |
| Skill 索引条目 | 推荐 | `level`(`app`/`widget`) + `owner_app`/`owner_widget` |
| Skill `allowed_tools` 元素 | ✅ | 每项都必须能在某个已注册 app 的 `/api/tools` 中找到 |
| `/api/skills/{name}/playbook` | ✅ | 返回 `text/markdown`；frontmatter 含 `name`/`description`/`allowed-tools`（注意连字符） |
| Widget | ✅ | `type`(非 `sys.*`), `app_id`, `is_main`, `display_mode`, `description` |
| Widget 非 `sys.*` | ✅ | `render` |
| **每个 app** | ✅ | **恰好一个 `is_main:true` widget** |
| `html_widget` | ✅ | `widget_type`, `title`, `data` |
| 跨端点一致性 | ✅ | `app_id` 在 `/api/app`、`/api/tools.app`、`/api/widgets.app` 必须相同 |
| `lifecycle` | 可选 | `on_demand` / `post_turn` / `pre_turn` |
| `output_widget_rules` | 可选 | `force_canvas_when`(数组) + `default_widget`(字符串) |
| `runtime_event_callbacks` | 可选 | 数组：`[{events:[…], path:"…"}]`（标准形态 — [`host-decoupling.md`](host-decoupling.md) §3） |
| `event_subscriptions` | 可选 | 字符串数组；订阅方必须实现 `POST /api/events` |
| `display_label_zh` | 推荐 | tool 在前端的中文显示名 |
| `prompt_contributions` | 推荐 | 提供领域路由提示，让 LLM 准确分流 |
| `session_summary` | 推荐 | 决策链 task session 标题 — [`display-titles.md`](display-titles.md) |
| `tags.event_label` / `widget.context_title` | 推荐 | NEED_INPUT pending event 与 widget 默认标题 — [`display-titles.md`](display-titles.md) |
| `configuration.ui` | 可选 | 有则须实现 `GET/PUT /api/configuration` — [`configuration.md`](configuration.md) |
| `GET/PUT /api/configuration` | 有 UI 时必选 | `values` 对象 |
| `app_author` | 可选 | 作者 / 维护方 |
| `main_widget_type` | 推荐 | 主入口；无 Canvas 时用 `sys.app-info` — [`app-manifest.md`](app-manifest.md) §3 |
| `PUT /api/host/bindings` | listener/callback 型必选 | 接收 Host 注入；`env` 不得进 configuration — [`host-injection.md`](host-injection.md) |
| `configuration.values` | ❌ 禁止 | **不得**含 `env` 或 Host 地址（用 bindings 注入） |
| `configuration.values` | ❌ 禁止 | **不得**含 LLM provider 凭证（用 Host [`llm-config.md`](llm-config.md)） |
| AIPP internal LLM (v2.9+) | ✅ | 须 `GET` Host `/api/llm-config`；不得长期依赖 `*.llm.*` yml / 共享 JSON |
| Host `GET /api/llm-config` | Host 必选 | 返回有效 tier（user → instance → env）— [`llm-config.md`](llm-config.md) §4.1 |
| `AippLlmConfigSpec` | Host LLM API | `assertValidLlmConfigResponse` 等 — [`llm-config.md`](llm-config.md) §8 |

> 注：旧规则表曾要求 widget 必带 `source` 字段——v2.8 起 `source` 已从 manifest 移除（Host 从未读取）。

---

## Anti-patterns（不要做的事）

- ❌ 在 AIPP `configuration` 或 env 中长期保存 LLM API key —— 用 Host [`llm-config.md`](llm-config.md)。
- ❌ 让 Host 知道你的 tool 名字。所有特化行为通过解耦字段自描述（[`host-decoupling.md`](host-decoupling.md)）。
- ❌ 让 Host 渲染你的业务 UI。Host 只调度，UI 必须由你 `render` 提供。
- ❌ 在 host system prompt 里加你的领域词。用 `prompt_contributions` 注入。
- ❌ 用 `sys.*` 作为 widget type 前缀（Host 系统保留）。
- ❌ 把多步事务硬塞成一个 Tool —— 应当拆出来写 SKILL.md，由 Host SkillRouter 渐进发现。
- ❌ 在 Skill description 里堆关键词列表"帮助召回"——纯靠 description 语义，关键词列表反而稀释信号。
- ❌ Skill description 不写 WHEN clause（"Use when ..." / "用于 ..." / "当用户 ..."）—— 写了 LLM 也召不到。
- ❌ Skill 的 `allowed_tools` 引用了未注册的 tool —— Host 会自动把 skill 从 catalog 移除。
- ❌ 在工具响应里同时返回 `html_widget` 和 `canvas` 而不声明 `output_widget_rules` —— Host 会优先 `html_widget`，你可能拿不到 canvas。
- ❌ `is_main` 多于一个或一个都没有 → 测试会红。
- ❌ 静默创建资源（找不到时直接 create 而不询问）→ 必须返回 `not_found` 让 LLM 转述。
- ❌ 在 AIPP `configuration` 中保存 `env` 或 Host 基址 → 用 `PUT /api/host/bindings`（[`host-injection.md`](host-injection.md)）。
- ❌ AIPP 轮询 Host / entitir 做 env 或业务事件 timely refresh → 用 bindings 注入 + Host push callback。
- ❌ 在 widget manifest 维护 `mutating_tools` 列表 → 在 `/api/tools` 上对 write 工具声明 `mutates_display: true`。
- ❌ 仍写 nested `scope.level` / `visible_when` → 用 `visibility` + `owner_widget` / `router_shortcut`（legacy `scope` 已移除，Host 不再读取）。
- ❌ `llm_hint` 硬编码 refresh 工具名 → 用 `{refresh_tool}` + widget `refresh_tool` 字段。

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

- Rules table: § Rules quick table (above)
- Anti-patterns: § Anti-patterns (above)
- Discovery: [`INDEX.md`](INDEX.md)
