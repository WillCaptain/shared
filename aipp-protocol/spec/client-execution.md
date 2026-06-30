# Client execution — 协议规格（Host + Desktop Shell）

**受众**：AIPP 开发者、world-one Host 开发者、ones-shell 开发者。

**发现路径**：[`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → 本文。

---

## 1. 动机

部分 tool 必须在用户本机执行（终端、本地文件、打开应用）。浏览器沙箱无法完成这些操作。

**模型**："think in server, act in client" —— tool 元数据（contract）在 Host registry；**执行**由已连接的 client executor（如 ones-shell）完成。LLM 提供知识（命令内容），声明提供契约（哪里可执行、参数 schema），executor 提供执行（OS 细节、安全策略）。

| 概念 | 说明 |
|------|------|
| `execution_surface: server` | 默认；Host HTTP 代理到 AIPP |
| `execution_surface: client` | Host 通过 SSE 下发 `client_tool_call`，等待 `POST /api/client-results` |
| Client executor | 与 UI session 绑定的本机执行器（Electron main process） |

**仅 tool 声明 `execution_surface`**。Skill 的本地性由 `allowed_tools` 推导；Widget 通过 `hostApi.callTool` 走同一分发路径。

### 两层模型（Tier 1 / Tier 2）

| Tier | 谁声明 tool | 例子 | 理由 |
|------|------------|------|------|
| **Tier 1 — 核心原语** | Host 内置（`local-client` / `LocalClientBuiltins`） | 见下表 | 通用、schema 极薄；LLM 已知 shell / UX；executor 侧少量固定 handler |
| **Tier 2 — 领域扩展** | 外部 AIPP 的 `GET /api/tools` | 罕见：仅当 **新 execution shape** 无法由 Tier-1 组合表达时 | 大多数本地操作应用 `terminal_run` + skill/description 即可，无需新 capability |

**Tier-1 原语（`local-client` app）** — ones-shell 注册七个 capability：

| Capability | Tools | 用途 |
|------------|-------|------|
| `terminal` | `terminal_run` | 一次性 shell（install/test/deploy/git/clipboard via `pbpaste`/`pbcopy`/open/osascript） |
| `terminal_session` | `terminal_session_start` / `_read` / `_stop` | 后台长驻进程（dev server、watch） |
| `user_dialog` | `user_confirm` / `user_pick_folder` / `user_pick_file` / `user_prompt` | 原生确认与文件/目录选择（`user_prompt` 全平台原生文本输入：mac=osascript，win/linux=临时 BrowserWindow） |
| `filesystem` | `filesystem_read` / `_write` / `_list` / `_search` | 结构化读写与搜索（相对路径基于 ones-shell `workspaceRoot`） |
| `app` | `app_open` / `app_list` / `app_activate` / `app_applescript` | 打开 URL/文件/应用、列出与激活前台应用、AppleScript 自动化（`app_applescript` 仅 macOS） |
| `browser` | `browser_open` / `_read` / `_click` / `_fill` / `_eval` / `_close` | 受控 BrowserWindow 的 DOM 级网页自动化（selector/JS 级，非像素；纯 Electron 无原生依赖） |
| `screen` | `screen_capture` / `screen_capture_window` | 桌面/窗口截图，返回图片。**vision-gated**：仅当模型支持图像输入时 Host 才向 LLM 暴露（`VISION_GATED_CAPABILITIES`）；结果作为后续 user message 注入 |
| `clipboard` | `clipboard_read` / `clipboard_write` | 跨平台结构化读写系统剪贴板（Electron `clipboard`）。优先于 `pbpaste`/`pbcopy`：全平台可用且免去 shell 转义 |
| `notify` | `notify` | 原生 OS 通知（toast，Electron `Notification`），用于异步进度/结果提示，不抢焦点 |
| `input` | `input_cursor` / `input_move` / `input_click` / `input_type` / `input_key` / `input_scroll` | **Tier-2，默认关闭、强门控**：OS 级鼠标/键盘注入。仅当用户在 ones-shell 设置中开启“Allow OS input control”后 executor 才上报 `input` capability（否则 INV-2 隐藏全部 input_*）。所有写操作 `requires_confirmation`；需可选原生后端（`@nut-tree-fork/nut-js` 或 `robotjs`）+ macOS 辅助功能权限 |

上述除 `input` 外都是 `local-client` 上的 Tier-1 capability，**绝不**单独建 AIPP。`clipboard`/`notify` 之外的大多数本地资源仍由 `terminal_run` 覆盖。

**Tier-2 `input`（OS 级输入注入）门控链**：①默认关闭，用户须在 ones-shell 设置显式开启；②开启后 executor 才在握手 capabilities 中上报 `input`，Host 的 INV-2 过滤据此显隐 input_* tool；③所有写操作 `requires_confirmation=true`，shell 侧逐次弹窗确认；④需可选原生后端（`@nut-tree-fork/nut-js` 或 `robotjs`，非硬依赖，缺失时返回 `input_unavailable`）；⑤macOS 还需授予辅助功能（Accessibility）权限。优先使用 `browser_*`（selector 级）/`app_*`/`screen_capture` 等更精确、低风险的能力，`input` 仅用于无法通过它们表达的场景。

**平台差异**：`app_applescript` 仅 macOS；`app_list` / `app_activate` 在 macOS（osascript）与 Windows（PowerShell）实现，Linux 返回 `unsupported_platform`。executor 在握手时上报 `platform`，Host 可据此隐藏不适用的 tool。

两层共用完全相同的运行时（§4）：注册 → 过滤 → SSE 分发 → 结果回传。差别只在 tool 声明的来源。

**禁止**：为 Tier-1 原语建独立 AIPP 服务（如已废弃的 terminal-one）；给 LLM 提供 OS playbook / 命令清单类 skill —— 模型已知 shell 命令，executor 已知 OS。

---

## 2. Tool manifest 扩展字段

在 `GET /api/tools` 的每个 tool entry 上（可选）：

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `execution_surface` | `"server"` \| `"client"` \| `["server","client"]` | `"server"` | 执行位置。数组形态 = **dual-surface**（见 §8） |
| `client_capability` | `string` | — | surface 含 `client` 时必填；如 `terminal`, `filesystem`, `std.file.parse.v1` |
| `client_package` | `object` | — | dual-surface tool 的本机安装包（见 §8.3）；声明后 Host 可在 capability 缺失时发起安装协商 |
| `requires_confirmation` | `boolean` | `false` | 执行前需用户确认（shell 侧弹窗） |

**两种 surface 形态**：

- **单面（scalar）**：`"server"`（默认，纯 HTTP 代理）或 `"client"`（纯本机执行，**client-only**）。client-only tool 在无 executor 的 session 中对 LLM 完全隐藏（INV-2），且**绝不**走 server（INV-3）。
- **双面（array）**：`["server","client"]` —— 同一 tool 既可在 server 执行，也可在 client 执行。这类 tool **永不隐藏**：有 executor 且广告了对应 capability 时优先 client，否则回退 server。详见 §8。

示例：

```json
{
  "name": "terminal_run",
  "description": "Run a shell command on the user's machine.",
  "parameters": {
    "type": "object",
    "properties": {
      "command": { "type": "string", "description": "Shell command, e.g. pwd" }
    },
    "required": ["command"]
  },
  "execution_surface": "client",
  "client_capability": "terminal",
  "requires_confirmation": false,
  "visibility": ["llm"],
  "router_shortcut": true
}
```

---

## 3. Session 级 executor 握手

### `POST /api/client-executor/register`

Browser / ones-shell 在 session 激活时调用：

```json
{
  "session_id": "main",
  "executor": {
    "id": "ones-shell",
    "capabilities": ["terminal", "filesystem", "open_app"],
    "platform": "mac"
  }
}
```

`platform` is normalized by Host to `mac` | `windows` | `linux` and injected into `_context.platform` for server AIPPs that opt in via `inject_context`. Client tools normally do **not** need platform on the LLM side — the executor owns OS specifics.

`capabilities` 必须与 executor 实际注册的 handler 一一对应（不要广告没有 handler 的 capability）。Host 按 capability 过滤 tool 可见性：session 无对应 capability ⇒ 该 client tool 不进入 LLM 工具列表。

响应：`{ "ok": true }`

断开或切换 session 时客户端应重新注册。无 executor 的 session：**不向 LLM 暴露** `execution_surface=client` 的 tool。

### `DELETE /api/client-executor/register?session_id=main`

可选；清除 session 的 executor 绑定。

---

## 4. 运行时循环

```
LLM → tool_call(terminal_run)
  → Host parks agent loop
  → SSE: { "type": "client_tool_call", "content": "{...}" }
  → ones-shell executes locally
  → POST /api/client-results
  → Host resumes loop with tool result JSON string
```

### SSE `client_tool_call` content

```json
{
  "session_id": "main",
  "call_id": "uuid",
  "tool": "terminal_run",
  "args": { "command": "pwd" },
  "client_capability": "terminal",
  "requires_confirmation": false,
  "result_token": "one-time-secret",
  "timeout_seconds": 30
}
```

### `POST /api/client-results`

```json
{
  "session_id": "main",
  "call_id": "uuid",
  "result_token": "one-time-secret",
  "result": {
    "ok": true,
    "stdout": "/Users/me",
    "stderr": "",
    "exit_code": 0
  }
}
```

响应：`{ "ok": true }`

Host 将 `result` 序列化为 tool result 字符串注入 history，继续下一轮 LLM。

错误形态：`{ "ok": false, "error": "user_denied" }` 或 `{ "ok": false, "error": "client timeout" }`。

---

## 5. 安全与三条硬性不变式（normative）

**前提**：LLM 没有"用户本机"的天然概念——对模型而言，server 就是它的 local。
因此本地执行的边界必须由协议字段显式声明，且 Host 必须强制执行以下三条不变式：

| # | 不变式 | 强制点 |
|---|--------|--------|
| **INV-1 标签** | 本地能力 tool **必须**声明 `execution_surface: client` + 非空 `client_capability`。缺失 capability 的 client tool 是非法 manifest——Host 拒绝将其暴露给 LLM，也拒绝分发 | `AippAppSpec.assertValidClientExecutionFields`（合规门）+ Host 注册期校验 |
| **INV-2 隐藏** | session 无已连接 executor、或 executor 未广告对应 capability 时，**client-only** tool 对 LLM 完全不可见（工具列表、fast-leaf 路由一律过滤）。不允许"暴露但报错"。**dual-surface tool 不受此约束**——它永远可见，因为始终有 server 兜底（§8） | Host 工具列表 / 路由过滤 |
| **INV-3 禁止服务端执行** | **client-only**（`execution_surface="client"`）的 tool **绝不**通过任何 server 路径执行：agent loop HTTP 路由、`/api/proxy/tools/*`、skill handle 路由全部硬拒绝（`client_tool_must_not_run_on_server`）。AIPP HTTP 端即使实现了同名 POST handler，Host 也不得调用。**dual-surface tool 不在此列**——它显式声明了 `server` surface，server 执行是合法回退 | Host 所有 server-side tool 调用入口 |

> **dual-surface 与三条不变式**：dual-surface（`["server","client"]`）是对 INV-2/INV-3 的<b>显式豁免</b>，而非违反——开发者通过同时声明两个 surface，主动承诺该 tool 在 server 与 client 上语义等价（如 `parse_file` 解析同一份字节返回同样的文本）。INV-1 仍然适用：声明了 `client` surface 就必须有非空 `client_capability`。

其余安全要求：

- `result_token` 一次性，完成或超时后作废
- Executor 仅通过 localhost 注册（生产环境可加固）
- `requires_confirmation` 对破坏性操作默认 `true`
- Shell 侧对 `terminal` capability 维护命令 allowlist（产品策略，非协议）

**术语纪律**：文档与代码中避免裸用 "local tool"——
`server` = world-one / AIPP HTTP 服务（LLM 视角的 local）；
`client` = 用户本机上的 ones-shell。唯一的本地标签就是 `execution_surface: client`。

---

## 6. AIPP 开发者清单

- [ ] 本地资源 tool 标记 `execution_surface: client` + `client_capability`
- [ ] 不在 AIPP HTTP 端实现 client tool 的 POST handler（Host 拦截，不走代理）
- [ ] Skill `allowed_tools` 列出 client tool 时，接受 browser-only session 会裁剪这些 tool
- [ ] 文档中说明需要 Ones Desktop（ones-shell）

---

## 7. Tier-2 扩展标准流程（新增一个本地能力）

新本地操作 = **一个新 capability**。三步，全部走既有协议，Host 核心零修改：

> **Tier-2 何时需要**：仅当 Tier-1 三个 capability 的组合仍无法表达（例如未来的 `browser`
> 有状态页面自动化）。大多数场景用 `terminal_run` + AIPP skill 描述即可，**不要**为
> 剪贴板等 shell 可完成的事新建 capability。

### Step 1 — 声明 contract（server 侧）

AIPP 在自己的 `GET /api/tools` 中声明 tool：

```json
{
  "name": "obs_scene_switch",
  "description": "Switch the active scene in the user's local OBS Studio.",
  "parameters": { "type": "object", "properties": { "scene": { "type": "string" } }, "required": ["scene"] },
  "execution_surface": "client",
  "client_capability": "obs_control",
  "requires_confirmation": false,
  "visibility": ["llm"]
}
```

注册到 Host（`POST /api/registry/install`）后，Host 自动索引 `execution_surface` / `client_capability`，分发路径与 Tier-1 builtin 完全一致。

### Step 2 — 实现 handler（client 侧）

ones-shell `src/capabilities.js` 注册同名 capability handler：

```javascript
registerCapability('obs_control', async (args, ctx) => {
  // OS / 本地进程细节全部在这里；必要时 await ctx.confirm("...")
  return { ok: true, scene: args.scene };
});
```

`ones-shell:capabilities` 自动返回 `listCapabilities()`，executor 注册时即广告新 capability。

### Step 3 — 自动可见性

- Desktop session（executor 带 `obs_control`）→ tool 进入 LLM 工具列表
- Browser-only session → tool 自动隐藏
- executor 在线但 capability 缺失 → 同样隐藏（按 capability 过滤，非按 executor 在线）

### 验收清单（每个新 capability 必查）

- [ ] `client_capability` 与 handler key 完全一致（kebab/snake 不混用）
- [ ] handler 不在（不能在）AIPP HTTP 端实现
- [ ] 破坏性操作 `requires_confirmation: true` 或 handler 内 `ctx.confirm`
- [ ] 不给 LLM 提供"操作手册" skill——description 一句话说清 WHAT + 前置条件即可
- [ ] browser-only session 验证 tool 不可见；desktop session 验证 round-trip 结果真实返回

---

## 8. Dual-surface tool 与 Client AIPP 安装协商（normative）

### 8.1 动机：一个 tool，两处执行

§1–§7 的 Tier-1/Tier-2 都是 **client-only** 模型：tool 只能在 client 跑（终端、本地文件），server 不能代劳。但有一类能力是 **位置无关、结果等价** 的——例如把一份文档字节解析成文本（`parse_file`）：

- 在 **web 浏览器** session 里没有 executor → 应当在 **server** 跑（AIPP 上传字节、服务端解析）；
- 在 **ones-shell 桌面** session 里 → 应当在 **client** 跑（用户文件不离开本机，隐私优先、零上传）。

为此引入 **dual-surface tool**：`execution_surface: ["server","client"]`。我们**不**再区分"server AIPP / client AIPP"——AIPP 永远是被部署的 HTTP 服务，只是它的 **tool** 声明了可在哪些 surface 执行。

### 8.2 解析顺序（Host，每次分发时求值）

给定一个被 LLM 选中的 tool：

```
surfaces = parse(execution_surface)            // {} → {server}（默认）

1. client-only（surfaces == {client}）：
     a. executor 在线且广告 client_capability → client 执行
     b. 否则 → INV-2 早已隐藏该 tool；不应到达此处

2. dual（surfaces ⊇ {server, client}）：
     a. executor 在线且广告 client_capability        → client 执行（隐私优先）
     b. executor 在线、未广告，但 tool 有 client_package
        且 (machine_id, app_id) 不在黑名单            → 发起安装协商（§8.4），
                                                         协商期间 server 兜底执行，不阻塞
     c. 其他（web session / 已拒绝 / 无 package）       → server 执行

3. server-only（surfaces == {server}）：server 执行
```

**关键**：dual tool 永远能完成（server 兜底），安装协商是**异步的能力升级**，绝不阻塞当前调用。

### 8.3 `client_package` —— tool 自带的本机安装包

dual-surface tool 若希望在桌面端本地执行，需声明如何把这份能力**安装到 ones-shell**。包由 AIPP 自己托管与提供（开闭原则：ones-shell 通用，不内置任何具体 AIPP 的 handler）。

```json
{
  "name": "parse_file",
  "execution_surface": ["server", "client"],
  "client_capability": "std.file.parse.v1",
  "client_package": {
    "app_id":     "note-one",
    "capability": "std.file.parse.v1",
    "runtime":    "jar",
    "version":    "0.1.0",
    "url":        "https://note-one.example.com/api/client-package/std.file.parse.v1.jar",
    "sha256":     "…",
    "launch":     { "args": ["--port", "${PORT}"] },
    "health":     "/health"
  }
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `app_id` | ✓ | 提供方 AIPP，黑名单按 `(machine_id, app_id)` 维度记账 |
| `capability` | ✓ | 安装成功后 executor 应广告的 capability，须等于 tool 的 `client_capability` |
| `runtime` | ✓ | `jar`（首版唯一支持）/ 未来 `node` / `binary` |
| `version` | ✓ | 用于增量升级与去重 |
| `url` | ✓ | 包下载地址（AIPP 自身的 HTTP 端点） |
| `sha256` | 推荐 | 完整性校验，下载后比对 |
| `launch` | — | 启动参数；`${PORT}` 由 ones-shell 注入（本机回环端口） |
| `health` | — | 本机进程健康检查路径（默认 `/health`） |

**runtime=jar 契约**：ones-shell 用 `java -jar <pkg> --port <PORT>` 启动；该进程必须在 `127.0.0.1:<PORT>` 暴露：
- `GET <health>` → `200` 表示就绪；
- `POST /invoke`（body `{ "tool", "args" }`）→ 返回 tool result JSON（与 server 端 `POST /api/tools/{name}` 同形）。

ones-shell 据此注册一个**通用 capability handler**（capability 名 = `client_capability`），把 `client_tool_call` 透传到本机进程的 `/invoke`。整个机制是 generic 的：ones-shell 不认识 `parse_file`，只认识"某 capability 由某本机进程提供"。

### 8.4 安装协商状态机（per-machine）

```
                 ┌─────────────┐
                 │ NOT_OFFERED │   executor 在线、未广告 capability、tool 有 client_package
                 └──────┬──────┘
                        │ Host SSE: client_install_offer
                        ▼
                 ┌─────────────┐  用户「安装」
        ┌────────│  OFFERED    │──────────────┐
        │ 用户拒绝 └─────────────┘              ▼
        ▼                              下载 client_package → 启动本机进程
 ┌─────────────┐                       → 注册 capability → 重新握手
 │ BLACKLISTED │                              │
 │ (machine,   │                              ▼
 │  app_id)    │                       ┌─────────────┐
 └──────┬──────┘                       │  INSTALLED  │  capability 已广告，后续直接 client 执行
        │                              └─────────────┘
        │ 用户在 plugin 页手动安装
        └──────────────────────────────────►（清除黑名单）
```

| 状态 | 触发 | 后续 |
|------|------|------|
| **OFFERED** | dual tool 命中 §8.2-2b | Host 发 `client_install_offer` SSE；当前调用**仍走 server 兜底** |
| **INSTALLED** | 用户接受 → ones-shell 下载+启动+注册 capability → 重新 `register` 握手 | 下一次该 capability 的 dual tool 走 client |
| **BLACKLISTED** | 用户拒绝 | Host 记 `(machine_id, app_id)`；**该机器**上不再为此 app 弹安装；dual tool 继续 server 兜底 |
| **清除黑名单** | 用户在 ones-shell plugin 页手动安装 | 删除 `(machine_id, app_id)` 黑名单项，恢复可协商 |

**黑名单作用域 = per-machine**：键为 `(machine_id, app_id)`。`machine_id` 是 ones-shell 在本机生成并持久化的稳定标识（如 `userData/machine-id`），握手时随 executor 一起上报。同一用户在另一台机器仍会被询问；同一机器上拒绝一次后不再打扰。

### 8.5 握手扩展（§3 增量）

`POST /api/client-executor/register` 的 `executor` 对象新增可选字段：

```json
{
  "session_id": "main",
  "executor": {
    "id": "ones-shell",
    "machine_id": "m-7f3a…",
    "capabilities": ["terminal", "filesystem", "std.file.parse.v1"],
    "installed_client_apps": [{ "app_id": "note-one", "capability": "std.file.parse.v1", "version": "0.1.0" }],
    "platform": "mac"
  }
}
```

- `machine_id`：黑名单与安装记账的稳定键。缺省时 Host 退化为 per-session 行为（不持久）。
- `installed_client_apps`：已安装的 client package 清单（用于 Host 显示状态与去重）；其 capability 也应出现在 `capabilities` 中。

### 8.6 安装协商 HTTP 端点（Host）

| 端点 | 用途 |
|------|------|
| `POST /api/client-install/offer` | （内部）agent loop 触发，登记一次 offer，返回 `client_package` + 当前状态（`offered`/`blacklisted`/`installed`） |
| `POST /api/client-install/reject` | ones-shell 上报用户拒绝：`{ machine_id, app_id }` → 加入黑名单 |
| `POST /api/client-install/installed` | ones-shell 上报安装完成：`{ machine_id, app_id, capability, version }`（随后重新握手广告 capability） |
| `POST /api/client-install/clear-blacklist` | plugin 页手动安装时清除：`{ machine_id, app_id }` |
| `GET  /api/client-install/state?machine_id=…` | plugin 页查询：已安装 / 黑名单 / 可安装 列表 |
| `GET  /api/client-install/catalog?machine_id=…&installed_capabilities=…` | Once 启动 bootstrap：缺失的 client package 清单（[`client-bootstrap.md`](client-bootstrap.md)） |

`client_install_offer` SSE content：

```json
{
  "type": "client_install_offer",
  "content": {
    "session_id": "main",
    "app_id": "note-one",
    "capability": "std.file.parse.v1",
    "reason": "本地解析可让文件不离开你的电脑",
    "client_package": { "...": "见 §8.3" }
  }
}
```

### 8.7 ones-shell 职责（client 侧）

1. **machine_id**：首次启动生成并持久化（`userData/machine-id`），握手随 executor 上报。
2. **响应 `client_install_offer`**：弹原生确认窗。
   - 接受 → 下载 `client_package.url`（校验 `sha256`）→ 按 runtime 启动本机进程（jar：`java -jar … --port <free>`）→ 轮询 `health` → 用 `registerCapability(capability, proxyHandler)` 注册通用代理 handler → POST `/api/client-install/installed` → 重新握手（capabilities 增加该项）。
   - 拒绝 → POST `/api/client-install/reject`。
3. **通用代理 handler**：收到 `client_tool_call`（capability=已安装项）→ `POST 127.0.0.1:<port>/invoke {tool,args}` → 原样返回 result。**ones-shell 不含任何 AIPP 专属逻辑**。
4. **生命周期**：app 退出时杀掉本机进程；重启时按 `installed_client_apps` 记录重新拉起。
5. **plugin 页**：列出可安装 / 已安装 / 黑名单；手动安装清除黑名单（`clear-blacklist`）。
6. **Launch bootstrap**（[`client-bootstrap.md`](client-bootstrap.md)）：Once 启动时 `GET /api/client-install/catalog`，对 `missing` 包批量确认安装；见 §8.9。

### 8.9 Launch bootstrap（Once）

见 [`client-bootstrap.md`](client-bootstrap.md)。摘要：

- Host：`GET /api/client-install/catalog?machine_id=…&installed_capabilities=…`
- Once：`restoreInstalledPackages` → catalog → 确认 → install/reject → 握手
- Agent 可见性：client-only 无 capability 则隐藏；dual-surface 仍可见并 server 兜底

### 8.8 AIPP 开发者清单（dual-surface）

- [ ] tool 声明 `execution_surface: ["server","client"]` + 非空 `client_capability`
- [ ] **同时**在 AIPP HTTP 端实现 `POST /api/tools/{name}`（server 兜底，dual tool 必须可在 server 跑）
- [ ] 提供 `client_package`（runtime=jar）+ 包下载端点 + `sha256`
- [ ] 本机进程实现 `GET /health` + `POST /invoke`，且 `/invoke` 与 server handler **语义等价**
- [ ] capability 用反向域名风格的稳定 ID（如 `std.file.parse.v1`），便于跨 AIPP 复用（见 `capability-providers.md`）
