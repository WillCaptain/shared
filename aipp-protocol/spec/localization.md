# Localization — 用户可见文案必须本地化

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.
> Java helper: {@code org.twelve.aipp.AippLocales}. Soft shape check: {@code AippAppSpec#assertValidLocalizedLabels}.

**所有 AIPP 与 Host 必须把 localization 当作一等契约**——任何会展示给终端用户的字符串都不得只硬编码一种语言。

---

## 1. 动机

Host UI、tool 标签、Host 拒绝文案（如「需要 Once 客户端」）、widget 文案、错误提示都会直接被用户看到。单语硬编码（尤其只写中文或只写英文）会在切换语言后失效，并让跨区域部署不可用。

| 角色 | 责任 |
|------|------|
| **Host** | Session **language** 的 SSOT；解析 LocalizedString；对 Host 自有用户文案做本地化后再 SSE 下发 |
| **AIPP** | Manifest / tool 响应 / widget UI 中用户可见字符串提供多语言；消费 Host 的 `language` |
| **Once / 桌面** | 与 Host UI 同一 language（或显式同步） |

---

## 2. Session language（SSOT）

请求（`POST /api/chat`、`POST /api/apps/{appId}/open` 等）携带：

```json
{
  "session_id": "main",
  "message": "pwd",
  "language": "zh"
}
```

| 规则 | 说明 |
|------|------|
| 字段名 | `language`（IETF 主语言标签，小写推荐：`zh`、`en`） |
| 归一化 | `zh-CN` / `zh_Hans` → `zh`；`en-US` → `en`（见 `AippLocales.normalize`） |
| 缺省 | Host 默认 `en`（或产品策略等价物）；**不得**假定永远是中文 |
| Widget | Host 通过 `data-aipp-language` / `hostApi.getLanguage()` 暴露同一值（见 [`widgets.md`](widgets.md)） |

Host **不得**用服务端 OS locale 代替用户 UI 语言。

完整 chat 契约见 [`host-runtime.md`](host-runtime.md) §1。

### 2.1 Session language vs reply language

Ones follows the same split as ChatGPT / DeepSeek — **UI language ≠ reply language**:

| 用途 | 跟随什么 |
|------|----------|
| Host **system prompt 变体**、widget/chrome、`display_labels` | Session `language`（Chat UI） |
| **助手自然语言回复** | 默认匹配用户正在写的语言；用户明确要求其它语言时按其要求；不把 UI language 当成回复锁 |
| Host 本轮发出的 chat `text`（如 invalid-instruction） | 尽量匹配用户消息语言；信号不清（如短 shell 指令 `pwd`）时回退 session `language` |

Prompt 侧只给**轻量约定**（一两句），不要写成“强制 / 违反即错误”的铁律段落——模型本就会跟用户语言走。  
Java 辅助：`AippLocales.replyLanguage(sessionLanguage, userMessage)` 仅用于 Host 自有 LocalizedString，不是给 LLM 硬锁语言。

---

## 3. LocalizedString 映射（规范形态）

用户可见文案用 **语言 → 字符串** 对象表示：

```json
{
  "en": "Invalid instruction: Once desktop client is required.",
  "zh": "无效指令：需要 Once 桌面客户端。"
}
```

| 规则 | 说明 |
|------|------|
| **`en` 必填** | 英语为协议回退语言；缺省解析最终落到 `en` |
| 其它键 | 至少覆盖产品支持的 UI 语言（当前 Host：`zh`、`en`） |
| 解析顺序 | 精确标签 → 主语言子标签 → `en` → 任意非空值 |
| 机器码 | `error` / `code` 等枚举保持语言中立（如 `no_client_executor`）；**人类可读 text 另附 LocalizedString** |

Java：`AippLocales.resolve(labels, language)`。

---

## 4. Manifest 字段

### 4.1 Tool UI 标签（推荐）

```json
{
  "name": "recipe_create",
  "display_labels": {
    "en": "New recipe",
    "zh": "新建菜谱"
  }
}
```

| 字段 | 状态 | 说明 |
|------|------|------|
| `display_labels` | **推荐（新代码必用）** | LocalizedString；Host `/api/tool-labels` 按 session language 解析 |
| `display_label_zh` | **Legacy** | 仅中文；Host 兼容读取，等价于 `display_labels.zh` 缺失时的回退 |
| `display_name` | **禁用** | 见 [`verify.md`](verify.md) / `assertValidSkillStructure` |

详见 [`host-decoupling.md`](host-decoupling.md) §5。

### 4.2 其它用户可见 manifest 文案

`welcome_message`、配置表单项 label/hint、widget 标题等：**新字段一律用 LocalizedString**（或成对的 `*_labels`），不得只提供单一语言标量。

---

## 5. Tool 响应与 Host 文案

| 表面 | 要求 |
|------|------|
| Tool 返回给用户看的 `message` / `error_message` / 卡片标题 | LocalizedString，或按请求 `language`（及 `_context`）在服务端已解析的单一字符串 |
| Host 拒绝 / 无效指令 / 能力不可用 | Host 在发出 `text` / `error` **之前**按 **reply language**（§2.1）解析 LocalizedString；禁止只发一种语言 |
| SSE `ChatEvent.text` | content 已是最终展示语言（Host 或 AIPP 已 resolve） |

Client-only 工具在无 Once 时的 Host 拒绝示例（逻辑码 + 本地化正文）见 [`client-execution.md`](client-execution.md) § Host user-facing refusals。

---

## 6. Widget ESM

- 使用 `hostApi.getLanguage()` / `data-aipp-language`，**不要**写死中文或英文。
- 共享 UI 文案放在 widget 内的 labels 表，或经 Host i18n；切换语言后应随 `worldone:languagechange`（或等价事件）刷新。

---

## 7. 合规

| 检查 | 说明 |
|------|------|
| `AippLocales` | 解析 / 归一化单元测试 |
| `assertValidLocalizedLabels(node)` | 若存在 `display_labels`（或其它约定 labels 对象），形状合法且含非空 `en` |
| 人工 / review | 新增用户可见硬编码单语 → 阻断 |

**最低门禁（本版本）：** 形状校验 + Host 关键路径本地化。不强制存量 `display_label_zh` 立即迁移，但**新字符串禁止只加 `*_zh`**。

---

## 8. 反例

| 反例 | 为何不行 |
|------|----------|
| Host 写死 `"无效指令：…"` 不分语言 | 英文 UI 仍显示中文 |
| 把回复语言写成铁律“强制 / 违反即错误” | ChatGPT/DeepSeek 只是默认跟用户语言；过严的锁会干扰混语与显式“用××回复” |
| Tool 只声明 `display_label_zh`（新代码） | 英文 UI 退回 tool `name` |
| 用服务器 `Locale.getDefault()` | 与用户 UI 无关 |
| 只返回 `error: "no_client_executor"` 且无本地化说明，又让 LLM 自由发挥 | 模型会编造 stdout（见 client-execution） |
