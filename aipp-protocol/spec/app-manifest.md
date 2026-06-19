# App Manifest & Protocol Overview（`GET /api/app`）

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.
> **Verify:** `assertValidAppManifest` — [`verify.md`](verify.md)。
> 配置 UI（`configuration.ui`）详见 [`configuration.md`](configuration.md)。

---

## 1. 协议总览（端点地图）

```
LLM / Agent (Host)
    ↕  GET  /api/app                       ← 应用身份（icon/name/color）
    ↕  GET  /api/tools                     ← 原子 Tool 清单（权威）— tool-manifest.md
    ↕  GET  /api/skills                    ← Skill Playbook 索引（progressive disclosure，可选）— skills.md
    ↕  GET  /api/skills/{id}/playbook      ← Skill 正文 SKILL.md
    ↕  GET  /api/widgets                   ← Widget Manifest 清单 — widgets.md
    ↕  POST /api/tools/{n}                 ← 执行 tool — tool-responses.md
    ↕  PUT  /api/host/bindings             ← Host 注入运行时绑定（install & env 变更）— host-injection.md
    ↕  POST /api/events                    ← 接收 Host 派发的事件（仅订阅方需实现）— events.md
AIPP App（独立进程）
```

### AIPP vs AIP

| 层 | 模块 | 职责 | 含 UI？ |
|----|------|------|--------|
| **AIP** | 纯能力库 | 原子工具，任意 LLM 可调用 | ❌ |
| **AIPP App** | 你正在写的 | 把 AIP 工具组合为 Skill + 持有自己的 Widget UI | ✅ |
| **AIPP Agent / Host** | 任意实现协议的 Agent | 发现 / 调度 / 挂载 / 路由 | ✅（仅 Host UI） |

---

## 2. `GET /api/app` 字段

```json
{
  "app_id":          "recipe-one",
  "app_name":        "菜谱管理",
  "app_icon":        "<svg viewBox='0 0 24 24'>...</svg>",
  "app_description": "管理菜谱、食材、烹饪步骤",
  "app_color":       "#ff8a65",
  "is_active":       true,
  "version":         "1.0"
}
```

| 字段 | 必选 | 说明 |
|------|------|------|
| `app_id` | ✅ | kebab-case；必须与 `/api/tools.app`、`/api/widgets.app` 一致 |
| `app_name` | ✅ | 显示名 |
| `app_icon` | ✅ | 内嵌 SVG 字符串（推荐）或公网 URL |
| `app_description` | ✅ | 一行描述 |
| `app_color` | ✅ | hex 主题色 |
| `is_active` | ✅ | boolean |
| `version` | ✅ | 字符串 |
| `app_author` | 可选 | 作者 / 维护方（`sys.app-info` 展示在 Author 行） |
| `main_widget_type` | 推荐 | 主入口 widget type；无专属 UI 时用 `sys.app-info`（§3） |
| `configuration` | 可选 | 含 `ui.layout`；有则须实现 `GET/PUT /api/configuration` — [`configuration.md`](configuration.md) |

**Classpath 源文件：** 每个 AIPP 在 `src/main/resources/aipp-app.json` 维护上述字段；`GET /api/app` 应返回同一内容（可合并运行时 `configuration`）。详见 §4。

---

## 3. `main_widget_type` 与标准主入口 `sys.app-info`

**问题**：Listener / 后台型 AIPP 没有专属 Canvas，但仍需要在 Apps 面板被「打开」时有合理默认 UI；同时 manifest 元数据（名称、版本、作者）应有一处标准展示，而不是每个 app 各写一套。

**分工**：

| 部分 | 所有者 | 说明 |
|------|--------|------|
| `main_widget_type` | AIPP | 在 `GET /api/app` 声明主入口 widget type |
| `app_author` | AIPP | （可选）作者 / 维护方，供信息页展示 |
| `sys.app-info` widget | Host | 内置 ESM，读 registry 缓存的 manifest |
| `aipp_app_info_view` skill | Host（`worldone-system`） | 组装 `html_widget` 并返回 |

**`GET /api/app` 片段（无专属 UI 的 app）**：

```json
{
  "app_id": "decision-reactor",
  "app_name": "决策执行引擎",
  "is_active": true,
  "version": "0.1.0",
  "app_author": "Twelve / Entitir",
  "main_widget_type": "sys.app-info",
  "configuration": { "ui": { "layout": { "...": "..." } } }
}
```

**与 `/api/widgets` 的关系**：

- `main_widget_type` **优先来自** `GET /api/app`，Host 在 install 时写入 `appMainWidgetIndex`。
- 传统 app 仍可在 `/api/widgets` 里声明 `is_main:true` 的自有 widget；两者并存时 **`/api/app.main_widget_type` 覆盖** widget 清单推导出的 main widget。
- 声明 `main_widget_type: "sys.app-info"` 的 app **不必**在 `/api/widgets` 注册 `sys.app-info`（`sys.*` 为 Host 保留前缀，AIPP 禁止注册 — [`system-widgets.md`](system-widgets.md)）。

**openApp 链路（Host 行为）**：

```
用户点击 Apps 面板 / app_list_view 卡片
  → GET manifest.main_widget_type  （如 sys.app-info）
  → 反查 entry_tool  （worldone-system: aipp_app_info_view）
  → POST /api/tools/aipp_app_info_view { args: { app_id } }
  → { ok: true, html_widget: { widget_type: "sys.app-info", data: { ...manifest... } } }
  → Host 动态 import /widgets/system/app-info.js 并 mount
```

Host 在 `openApp` 时自动 `args.putIfAbsent("app_id", appId)`，AIPP 端无需实现 `aipp_app_info_view` —— 该 skill 由 Host 内置 app `worldone-system` 提供。

**`sys.app-info` 的 `data` 字段（Host 组装）**：

| 字段 | 来源 |
|------|------|
| `app_id` | 目标 app |
| `app_name` / `app_description` / `app_icon` / `app_color` / `version` / `app_author` | install 时缓存的 `GET /api/app` |
| `base_url` | registry 注册地址 |
| `has_configuration` | manifest 是否含 `configuration.ui` |

有配置 UI 时，widget 内「打开配置」按钮调用 Host 的 `hostApi.openConfiguration(app_id)`，行为与 `sys.app-list` 行内 ⚙ 一致。

**何时用 `sys.app-info`**：

- ✅ 后台 listener、纯配置型、尚未设计专属 Canvas 的 AIPP
- ✅ POC / onboarding 阶段占位主入口
- ❌ 已有完整 `is_main:true` 业务 widget 的 app —— 应把 `main_widget_type` 设为自有 type（如 `recipe-board`）

**Apps 列表可见性**：Host 展示 app 若满足 **`main_widget_type` 非空** 或 **`has_configuration`**（有 `configuration.ui`）即出现在列表；仅配置、无 main widget 的 app 仍可被打开配置，但卡片不可点击 openApp。

---

## 4. 元数据来源（禁止 Host 硬编码名称）

| 角色 | 元数据存放处 | Host 读取方式 |
|------|-------------|---------------|
| 普通 AIPP（HTTP 服务） | 该 app 仓库内 `src/main/resources/aipp-app.json` → 由 `GET /api/app` 返回 | install 时 `GET {base_url}/api/app`，缓存到 registry |
| Host 内置 app（`worldone-system`） | world-one 仓库 `aipp-app.json` → `GET /api/app` on Host port | 同上 |
| 无独立 HTTP 的 builtin（如 `local-client`） | `aipp-app-{app_id}.json` on Host classpath | `AippAppManifestLoader.loadClasspath` at register |

**规则：**

- `app_name`、`app_icon`、`app_color` 等展示字段 **只** 出现在 AIPP 元数据（`aipp-app.json` / `GET /api/app`），**不得** 在 Host Java 代码或 capability-tree 持久化文件里硬编码为权威来源。
- Host `registerBuiltin(appId, …)` 的 `name` 参数已废弃，仅作无法读取 manifest 时的回退；应传 `appId`。
- Capability forest / Apps 面板 / Router 一律通过 registry 缓存的 `GET /api/app` 解析 `app_name`（`AppRegistry.appDisplayName`）。
- 修改显示名：改对应 AIPP 的 `aipp-app.json` 并重新 deploy；**不要**改 world-one。

工具类：`org.twelve.aipp.AippAppManifestLoader`（`shared/aipp-protocol`）。

---

## Related

- [`tool-manifest.md`](tool-manifest.md) — `GET /api/tools`
- [`widgets.md`](widgets.md) — `GET /api/widgets`
- [`configuration.md`](configuration.md) — `configuration.ui` 与读写端点
- [`host-registration.md`](host-registration.md) — install 流程
