# AIPP Protocol — AI Plugin Program 协议规范

> 版本：2.8
> 最后更新：2026-06
> 受众：**开发者 / LLM**。
>
> **本文件只是 changelog + 章节存根。** 规范正文唯一所在：[`spec/*.md`](spec/INDEX.md)（wiki，one-copy）。
> 宪章（charter）：[`skills/aipp-development/SKILL.md`](skills/aipp-development/SKILL.md)；[`AGENTS.md`](AGENTS.md) 是指向它的指针。
> 任务路由：[`spec/INDEX.md`](spec/INDEX.md)；Tier 0 粘贴块见 [`docs/tier0-bootstrap.prompt.md`](docs/tier0-bootstrap.prompt.md)。
> 下方"章节存根"保留旧版 §0–§17 编号，便于旧引用落点；每节只给一段定位 + spec 链接。

## Changelog

### 2.4–2.7 压缩摘要（2026-06）

- **Tool lifecycle：** 只用 `lifecycle`（`on_demand` / `pre_turn` / `post_turn`）；`auto_pre_turn` / `background` 已从 manifest 移除。
- **Widget 展示：** 只用 `display_mode`（`canvas` / `chat` / `pop`）；`is_canvas_mode` 已从 manifest 移除（Host 仅打日志，不索引）。
- **Tool 条目：** 禁止 `prompt` / `tools[]` / `resources` / `display_name`；UI 标签用 `display_label_zh`；编排在 Skill playbook。
- **领域提示：** `/api/tools` 根级只用 `prompt_contributions`（`layer: ambient_prompt` / `entry_prompt`）；根级 `system_prompt` 已移除（Host 仅打日志，不注入）。
- **Widget 入口：** `entry_tool` 替代 `renders_output_of_skill`；`widget_prompt` 替代 `context_prompt` + 根级 `system_prompt`；`welcome_message` 保留（UI）。
- **Canvas 路由：** 优先 `tool.canvas.triggers` + `entry_tool` 映射；`output_widget_rules` 仅用于无 `canvas` 块时的字段强制规则。
- **Tool placement（v3）：** 用顶层 `visibility` + 可选 `owner_widget` / `router_shortcut` / `mutates_display`；嵌套 `scope.level` / `visible_when` 已移除（2026-06），Host 不再读取。
- **Widget 刷新：** widget 声明 `refresh_tool`；会改变画布展示的工具在 `/api/tools` 上声明 `mutates_display: true`；不再在 widget 上维护 `mutating_tools` 列表。

### 2.8 压缩摘要（2026-06）

- **Widget `supports` 移除：** manifest 级 `supports: { disable, theme }` 及 disable 行为契约整体移除——Host 从未读取；`--aipp-*` 主题 CSS 变量由 Host 页面无条件注入（[`spec/widgets.md`](spec/widgets.md) §4）。
- **Widget `source` 移除：** `external` / `builtin` 标记 Host 从未读取，从 manifest 移除。
- **Host bindings v1.1：** `PUT /api/host/bindings` 仅注入 `host_id` / `app_id` / `env`；`host_base_url` / `host_event_callback_url` 不再注入（[`spec/host-url.md`](spec/host-url.md) 本地解析 + 派生）。
- **Runtime 回调验证：** 顶层数组 `runtime_event_callbacks` 用 `assertValidRuntimeEventCallbacks(toolsRoot)`；skill 级单对象仍用 `assertValidRuntimeEventCallback`。
- **DB 访问标准化：** 有持久化的 AIPP 一律使用 `shared/db-ops` SDK（`AtomicDbOps`），每 app 一个 PostgreSQL schema —— [`spec/db-operations.md`](spec/db-operations.md)。
- **Spec 合并：** `decision-reactor-invoke.md` + `ontology-world-catalog.md` → [`spec/decision-reactor-integration.md`](spec/decision-reactor-integration.md)。
- **Skill 服务标准化：** 所有 app 的 `/api/skills`（index / playbook / files）一律经 `AippSkillPackages` 从 classpath `skills/{id}/SKILL.md` 提供，索引字段只允许出现在 frontmatter —— [`spec/skills.md`](spec/skills.md) §2.1。skill index 不再输出 `visible_when`（随 nested-scope 模型移除，Host 从不读取）。
- **文档架构（one skill + wiki）：** 协议宪章迁入 [`skills/aipp-development/SKILL.md`](skills/aipp-development/SKILL.md)（开发用 harness skill，自动触发；与 Host 运行时 skills 无关）；`AGENTS.md` 降为指针；`spec/` 为唯一规范正文（wiki），本 README 仅作 changelog + 章节存根。原 §2/§3/§7 正文迁入 [`spec/app-manifest.md`](spec/app-manifest.md) 与 [`spec/tool-manifest.md`](spec/tool-manifest.md)；§5.7 命名迁入 [`spec/display-titles.md`](spec/display-titles.md)；§10/§13 迁入 [`spec/host-runtime.md`](spec/host-runtime.md)；§14/§16 迁入 [`spec/verify.md`](spec/verify.md)；§17 模板迁入 [`docs/quickstart-checklist.md`](docs/quickstart-checklist.md) 附录。
- **Legacy 兼容垫整体移除：** `refresh_skill` / `mutating_tools` / `is_canvas_mode` 的兼容读取从 `ToolPlacement`、`AippWidgetSpec`、Host（`AppRegistry` 索引 + 前端 fallback）全部删除；`assertWidgetUsesCompressedFields` 现拒绝这三个字段，Host 对存量 manifest 仅警告 + 忽略。`{refresh_skill}` hint 占位符与 `display_mode` 缺省推断同步收敛（缺省 = `canvas`）。每个设置只剩一种写法。

详见 [`spec/verify.md`](spec/verify.md) § Protocol compression (2.4–2.7)。

### 2.8.1 — `side_effect` retry-safety 轴（新增，2026-06）

- **新增可选 tool 字段 `side_effect`：** `none` / `idempotent` / `mutating`，回答“多步协作计划失败后能否自动重试这一步”。独立于 placement / `mutates_display` / `requires_confirmation` 的第四正交轴 —— `mutates_display` 是 UI 过期，`side_effect` 是世界副作用，二者不互相蕴含（[`spec/field-semantics.md`](spec/field-semantics.md) §1）。
- **校验：** `AippAppSpec.assertValidSideEffectField`，由 `assertValidToolsApiStructure` 逐 tool 自动调用；只校验枚举合法性。未声明合法 —— Host 按最不安全（`mutating`）fail-closed，绝不对 post-send 失败自动重试（[`spec/tool-manifest.md`](spec/tool-manifest.md) §3.1）。
- **消费方：** world-one 协作计划编排器（`FreePlanExecutor` 修复循环）—— pre-send 失败任意可重试，`mutating` 的 post-send 失败停下交还用户。

---

## 章节存根（正文已迁入 spec/）

### 0. 阅读指南（给 LLM 的）

你要做的事是：**实现一个独立 HTTP 服务，让任意 AIPP Agent（Host）能发现你、调用你的能力、挂载你的 UI**。一切**能力**走 tools、**多步流程**走 skills（progressive disclosure）、**展示**走 widgets。
入口与分层：[`skills/aipp-development/SKILL.md`](skills/aipp-development/SKILL.md)（宪章，先读）→ [`spec/INDEX.md`](spec/INDEX.md)（按任务路由到单篇 spec）→ [`spec/verify.md`](spec/verify.md)（`assert*` 合规门禁）。不要整篇加载任何大文档。

### 1. Quickstart — 一个最小合规 AIPP

按 [`docs/quickstart-checklist.md`](docs/quickstart-checklist.md) 勾选执行（含可直接抄的最小模板附录）。各端点的 JSON 范例在对应 spec 页：[`spec/app-manifest.md`](spec/app-manifest.md)、[`spec/tool-manifest.md`](spec/tool-manifest.md)、[`spec/widgets.md`](spec/widgets.md)、[`spec/skills.md`](spec/skills.md)。注册到 Host：[`spec/host-registration.md`](spec/host-registration.md)。

### 2. 协议总览

端点地图（`/api/app`、`/api/tools`、`/api/skills`、`/api/widgets`、`/api/events`、bindings）与 AIPP / AIP / Host 三层分工 → [`spec/app-manifest.md`](spec/app-manifest.md) §1。

### 3. Tool — 原子能力（`/api/tools`）

Tool 是对 LLM"一次 call → 一次 response"的原子黑盒；entry = OpenAI function-calling 兼容层 + AIPP 扩展层 → [`spec/tool-manifest.md`](spec/tool-manifest.md)。
Tool placement（`visibility` / `owner_widget` / `router_shortcut` / `mutates_display`）→ [`spec/field-semantics.md`](spec/field-semantics.md) + [`spec/host-decoupling.md`](spec/host-decoupling.md) §7。

### 4. Skill — 渐进式发现的多步说明书（`/api/skills`）

索引 4 必选字段、description lint（WHAT + WHEN）、SKILL.md playbook 格式、`AippSkillPackages` 服务规则、Tool / Skill 判定准则（编排责任在哪一侧就归哪一侧）→ [`spec/skills.md`](spec/skills.md)。

### 5. Widget Manifest

必需字段、`render`（ESM mount/unmount/preview）、`hostApi`、主题 CSS 变量、views / `refresh_tool`、upload → [`spec/widgets.md`](spec/widgets.md)。
`sys.*` 系统 widget（AIPP 不注册，只在响应中引用）→ [`spec/system-widgets.md`](spec/system-widgets.md)；能力树 → [`spec/capability-tree.md`](spec/capability-tree.md)。

### 6. Host 解耦协议

**铁律**：Host 不为任何 AIPP 写一行特判代码；所有特化行为通过 manifest 字段自描述。
`output_widget_rules` / `lifecycle` / `runtime_event_callbacks` / `event_subscriptions` / `display_label_zh` / `prompt_contributions` / placement / 刷新 → [`spec/host-decoupling.md`](spec/host-decoupling.md)。
原 §6.7 三层标题命名 → [`spec/display-titles.md`](spec/display-titles.md)；§6.8 配置 → [`spec/configuration.md`](spec/configuration.md)；§6.9 `main_widget_type` / `sys.app-info` → [`spec/app-manifest.md`](spec/app-manifest.md) §3；§6.10 Host 运行时注入 → [`spec/host-injection.md`](spec/host-injection.md)。

### 7. 必备 HTTP 接口规范

| 端点 | Spec |
|------|------|
| 7.1 `GET /api/app` | [`spec/app-manifest.md`](spec/app-manifest.md) §2 |
| 7.2 `GET /api/tools` | [`spec/tool-manifest.md`](spec/tool-manifest.md) §4 |
| 7.3 `GET /api/skills`（+ playbook） | [`spec/skills.md`](spec/skills.md) |
| 7.4 `GET /api/widgets` | [`spec/widgets.md`](spec/widgets.md) |
| 7.5 `POST /api/tools/{name}`（`args` + `_context`） | [`spec/tool-responses.md`](spec/tool-responses.md) §1 |
| 7.6 `POST /api/events` | [`spec/events.md`](spec/events.md) |
| 7.7 `GET/PUT /api/configuration` | [`spec/configuration.md`](spec/configuration.md) |
| 7.8 `PUT /api/host/bindings` | [`spec/host-injection.md`](spec/host-injection.md) |

### 8. 响应约定

`html_widget` / `pop_widget` / `canvas` envelope、Host 分发优先级、`not_found` / `awaiting_confirmation` / `awaiting_selection`、状态枚举、`next_tool_recommended`、`entry_prompt_hit`、HTTP 状态码 → [`spec/tool-responses.md`](spec/tool-responses.md)。
`sys.*` 卡片的 `data` schema → [`spec/system-widgets.md`](spec/system-widgets.md)。

### 9. Session 类型规范

Session 类型表、`new_session`、`session_policy`（`singleton` / `keyed`）+ `session_instance_key`、归一原则 → [`spec/sessions.md`](spec/sessions.md)。

### 10. Host ↔ AIPP 运行时契约

`POST /api/chat` SSE 与 ChatEvent 类型清单、`POST /api/apps/{appId}/open`、全流程范例 → [`spec/host-runtime.md`](spec/host-runtime.md)。
原 §10.3–§10.4 ESM `hostApi` / canvas 数据传递 → [`spec/widgets.md`](spec/widgets.md) §2–§3；§10.5 entry prompt 运行时 → [`spec/tool-responses.md`](spec/tool-responses.md) §5.1；§10.6 widget upload → [`spec/widgets.md`](spec/widgets.md) §6。

### 11. inject_context 协议

`inject_context.request_context` / `turn_messages` → [`spec/skills.md`](spec/skills.md) §6。

### 12. memory_hints 协议

`memory_hints`（聚合进 Memory Agent system prompt）→ [`spec/skills.md`](spec/skills.md) §6。

### 13. LLM Context 多层架构（Host 视角）

Host 每轮 prompt 的 6 层叠加结构 → [`spec/host-runtime.md`](spec/host-runtime.md) §4。

### 14. 合规规则速查

→ [`spec/verify.md`](spec/verify.md) § Rules quick table。

### 15. 合规验证工具（Java/JUnit）

`AippAppSpec` / `AippWidgetSpec` / `AippConfigurationSpec` / `AippHostInjectionSpec` 的 `assert*` 目录与 Maven 命令 → [`spec/verify.md`](spec/verify.md)。文档与方法不一致时，**以方法为准**。

### 16. 不要做的事（反模式）

→ [`spec/verify.md`](spec/verify.md) § Anti-patterns。

### 17. 完整最小 AIPP 模板（可直接抄）

→ [`docs/quickstart-checklist.md`](docs/quickstart-checklist.md) 附录。
