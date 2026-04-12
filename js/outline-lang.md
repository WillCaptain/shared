# outline-lang.js — Outline 语言 SDK 完整文档

`outline-lang.js` 是所有 Monaco 编辑器宿主的 **Outline 语言服务唯一入口**。
凡需要 Outline 语法高亮、类型推导、自动补全、错误诊断的页面，均通过此 SDK 接入，不应在宿主代码中另行实现这些能力。

---

## 目录

1. [引入方式](#引入方式)
2. [API 总览](#api-总览)
3. [registerLanguage()](#1-registerlanguage)
4. [setTypeMap(data)](#2-settypemapdata)
5. [lookupType(word)](#3-lookuptypeword)
6. [registerHover(options?)](#4-registerhover)
7. [registerCompletions(options)](#5-registercompletions)
8. [createDiagnostics(options)](#6-creatediagnostics)
9. [后端契约](#后端契约)
10. [典型接入模式](#典型接入模式)
11. [类型推导范围与局限](#类型推导范围与局限)

---

## 引入方式

`outline-lang.js` 是普通 `<script>` 脚本，必须在 Monaco 加载完成后调用其 API：

```html
<script src="/js/outline-lang.js"></script>

<script>
require(['vs/editor/editor.main'], () => {
  OutlineLang.registerLanguage();
  // 之后调用其他 API ...
});
</script>
```

全局暴露为 `window.OutlineLang`，所有方法均挂载在此对象上。

---

## API 总览

| 方法 | 分类 | 是否全局唯一 | 说明 |
|---|---|---|---|
| `registerLanguage()` | 语言注册 | 是（幂等） | 注册 outline 语言、主题 |
| `setTypeMap(data)` | 类型推导 | — | 注入 session 类型数据 |
| `lookupType(word)` | 类型推导 | — | 同步查找任意标识符的类型信息 |
| `registerHover(options?)` | 类型推导 | 是（全局一次） | 注册 Monaco hover provider |
| `registerCompletions(options)` | 自动补全 | 是（全局一次） | 注册 Monaco completion provider |
| `createDiagnostics(options)` | 错误诊断 | 否（每个编辑器独立） | 创建防抖验证调度器 |

> **全局唯一**：Monaco 的语言 provider 是按 languageId 全局注册的。`registerLanguage`、`registerHover`、`registerCompletions` 内部各有 `_registered` 标志，重复调用无副作用。

---

## 1. registerLanguage()

```javascript
OutlineLang.registerLanguage();
```

**作用**：
- 向 Monaco 注册 `outline` 语言
- 配置 Monarch 语法高亮 tokenizer（关键字、类型、注释、字符串等）
- 配置括号匹配、自动闭合、行注释 `//` / 块注释 `/* */`
- 定义两套内置主题：`outline-dark`（深色）和 `outline-light`（浅色）

**必须最先调用**，其他 API 均依赖此注册。幂等，可安全重复调用。

### Token 类别

| Token 类型 | 典型内容 | 颜色（dark 主题） |
|---|---|---|
| `keyword` | `let`, `outline`, `if`, `match`, `module`… | 蓝色 |
| `type.identifier` | `Employee`, `Gender`（首字母大写） | 紫色 |
| `type.primitive` | `String`, `Int`, `Bool`, `Unit` | 青色 |
| `string` | `"hello"` | 绿色 |
| `string.literal-type` | `#"literal"` | 橙色 |
| `comment` | `// …` / `/* … */` | 灰色斜体 |
| `operator.special` | `->`, `=>`, `...` | 蓝色粗体 |
| `identifier` | `employees`, `name`（首字母小写） | 白色 |

---

## 2. setTypeMap(data)

```javascript
OutlineLang.setTypeMap(data);
```

**作用**：将 session 级符号数据注入 SDK 内部类型映射表，供 `lookupType` 和 hover provider 使用。

**参数**：
```javascript
data = {
  "Employee":  "**`entity Employee`**\n...",   // entity class
  "Employees": "**`VirtualSet<Employee>`**...", // collection type (派生)
  "employees": "**`let employees`** : ...",     // let variable binding (派生)
  "Gender":    "**`enum Gender`**\n...",         // enum class
  // ...
}
```

**规则**：
- 原始类型（`String`/`Int`/`Bool`/`Date`/`Unit`/`Nothing`…）内置于 SDK，**不可被覆盖**
- 每次调用完整替换 session 层数据（幂等，适合 session 切换时刷新）
- 调用后 hover 立即反映新数据，无需重新注册 provider

**何时调用**：
- Schema 标签页打开时，拉取 `GET /api/proxy/schema-types` 后调用一次
- Session 切换（进入不同的 world）时重新调用
- 无 session 时可调用 `OutlineLang.setTypeMap({})` 清空

---

## 3. lookupType(word)

```javascript
const markdown = OutlineLang.lookupType('Employee');
// → "**`entity Employee`** · `HR`\n\n```outline\n  name: String\n  ...\n```"

OutlineLang.lookupType('employees');
// → "**`let employees`** : `Employees`\n\n_集合入口，类型为 `VirtualSet<Employee>`_"

OutlineLang.lookupType('String');
// → "**`String`** — 基础类型 · 字符串"

OutlineLang.lookupType('unknownField');
// → null
```

**查找优先级**：
1. 内置原始类型（`_PRIMITIVES`）— 永不失效
2. Session 类型数据（`_sessionTypeMap`，由 `setTypeMap` 注入）

**返回值**：Markdown 字符串（Monaco 支持 `isTrusted: true`），或 `null`（未知标识符）。

**覆盖范围**：

| 标识符类型 | 示例 | 数据来源 |
|---|---|---|
| 内置原始类型 | `String`, `Int`, `Bool`, `Unit`, `Nothing` | SDK 内置 |
| Entity class | `Employee` | `setTypeMap` |
| Enum class | `Gender` | `setTypeMap` |
| Collection type | `Employees`（`VirtualSet<Employee>`） | `setTypeMap`（后端派生） |
| Let 变量 | `employees`（集合入口） | `setTypeMap`（后端派生） |

---

## 4. registerHover()

```javascript
OutlineLang.registerHover();               // 纯本地查找，无 HTTP
OutlineLang.registerHover({ hoverUrl }); // 本地查找 + HTTP fallback
```

**作用**：向 Monaco 注册 `outline` 语言的 hover provider。
鼠标悬停时调用 `lookupType(word)` 同步查找；仅当本地无结果且配置了 `hoverUrl` 时才发起 HTTP 请求。

**全局唯一**：整个页面生命周期只注册一次，重复调用无效。

### Options（均可选）

| 选项 | 类型 | 说明 |
|---|---|---|
| `hoverUrl` | `string \| (model) => string` | HTTP fallback 端点，`lookupType` 返回 null 时使用 |
| `getExtraBody` | `(code, offset) => object` | HTTP 请求附加字段（如 `session_id`） |

### Hover 流程

```
鼠标悬停
  └─ model.getWordAtPosition(position)  → word
       ├─ OutlineLang.lookupType(word)
       │    ├─ 命中 → 立即返回 hover（同步，零延迟）
       │    └─ 未命中
       │         ├─ 有 hoverUrl → POST { code, offset, ...extra } → 解析 { contents }
       │         └─ 无 hoverUrl → 不显示 hover
```

### HTTP fallback 端点契约

```
POST <hoverUrl>
Request:  { "code": "...", "offset": N, ...getExtraBody() }
Response: { "contents": "markdown string" }   // 有结果
          { "contents": null }                // 无结果（注意：不能用 Map.of() 返回 null）
```

### 使用示例

```javascript
// 最简：纯本地查找（schema 只读编辑器）
OutlineLang.registerHover();

// 带 HTTP fallback（可编辑的 query 编辑器）
OutlineLang.registerHover({
  hoverUrl: '/api/hover',
  getExtraBody: (code, offset) => ({ session_id: currentSessionId }),
});
```

---

## 5. registerCompletions(options)

```javascript
OutlineLang.registerCompletions({
  urlResolver: (model) => '/api/completions',
  getExtraBody: (code, offset) => ({ session_id: currentSessionId }),
  getSchemaMembers: () => schemaMap,  // 可选，客户端优先补全
});
```

**作用**：向 Monaco 注册 `outline` 语言的 completion provider，支持 `.` 触发的成员补全。

**全局唯一**，重复调用无效。

### Options

| 选项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `urlResolver` | `(model) => string` | `() => '/api/completions'` | 根据当前 model 选择补全 API |
| `getExtraBody` | `(code, offset) => object` | `null` | 附加请求字段 |
| `getSchemaMembers` | `() => object \| null` | `null` | 客户端 schema 优先补全（见下） |
| `triggerChars` | `string[]` | `['.']` | 触发字符 |

### schema-first 补全

`getSchemaMembers` 返回一个 `{ varName: CompletionItem[] }` 映射。
当代码中检测到 `varName.` 模式时，直接从本地 schema 返回成员列表，不发 HTTP。

```javascript
// 示例 schema map
{
  "employees": [
    { label: "filter",     kind: "method",    detail: "(predicate) => Employees" },
    { label: "count",      kind: "method",    detail: "() => Int" },
    { label: "create",     kind: "method",    detail: "({...}) => Employee" },
  ],
  "Employee": [
    { label: "name",       kind: "property",  detail: "String" },
    { label: "department", kind: "property",  detail: "String" },
  ]
}
```

### 补全缓存

内部使用 `stale-while-revalidate` 策略：命中缓存时立即返回旧数据，同时后台重新请求更新缓存，保证补全不阻塞用户输入。最大缓存条目数为 64。

### 补全端点契约

```
POST <urlResolver(model)>
Request:  { "code": "...", "offset": N, ...getExtraBody() }
Response: {
  "items": [
    { "label": "filter", "kind": "method", "detail": "...", "documentation": "..." },
    ...
  ]
}
```

`kind` 取值：`"property"` / `"method"` / `"outline"` / `"keyword"` / `"builtin"` / 其他（variable）

---

## 6. createDiagnostics(options)

```javascript
const schedule = OutlineLang.createDiagnostics({
  validateUrl:    '/api/validate',
  getRequestBody: (code) => ({ session_id: currentSessionId, code }),
  editor:         monacoEditorInstance,
  debounceMs:     600,
  markerOwner:    'outline-lint',
});

// 在编辑器内容变化时调用：
editor.onDidChangeModelContent(() => schedule());

// 也可手动触发（如页面初始化）：
schedule();
```

**作用**：创建一个防抖验证调度器，编辑器内容变化后延迟验证并将错误标注到 Monaco 编辑器（红/黄波浪线）。

**非全局唯一**：每个编辑器实例独立创建自己的 diagnostics 调度器。

### Options

| 选项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `validateUrl` | `string` | 必填 | 验证端点 URL |
| `getRequestBody` | `(code) => object` | `null` | 构造请求体（默认 `{ code }`） |
| `editor` | Monaco editor instance | 必填 | 目标编辑器 |
| `debounceMs` | `number` | `600` | 防抖延迟（毫秒） |
| `markerOwner` | `string` | `'outline-lint'` | Monaco marker owner，用于区分多个验证源 |

### 验证端点契约

```
POST <validateUrl>
Request:  { "code": "...", ...getRequestBody() }
Response: {
  "markers": [
    {
      "startLine":   1,      // 1-based
      "startColumn": 0,      // 0-based（SDK 内部 +1 转换为 Monaco 的 1-based）
      "endLine":     1,
      "endColumn":   10,
      "message":     "Unknown type: Foo",
      "severity":    8       // 8=Error, 4=Warning
    }
  ]
}
```

---

## 后端契约

以下端点由 `world-entitir` 实现，经 `world-one` 代理暴露：

### GET /api/proxy/schema-types?session_id=xxx

**用途**：Schema 标签页打开时全量获取类型数据，供 `OutlineLang.setTypeMap()` 使用。

**响应**：
```json
{
  "Employee":  "**`entity Employee`** · `HR`\n\n```outline\n  name: String\n  ...\n```",
  "Employees": "**`VirtualSet<Employee>`** · `Employees`\n\n```outline\n  create: {...} -> Employee\n```",
  "employees": "**`let employees`** : `Employees`\n\n_集合入口，类型为 `VirtualSet<Employee>`_",
  "Gender":    "**`enum Gender`**\n\n```outline\n  Male\n  Female\n```"
}
```

数据由 `CompletionsController.schemaTypes()` 生成，覆盖三类符号：
1. **Entity/Enum class**（直接来自 `session.classTypes()`）
2. **Collection type**（`VirtualSet<Entity>`，由 `pluralize(entityName)` 派生）
3. **Let 变量**（集合入口，由 `toLowerFirst(collectionName)` 派生）

### POST /api/proxy/schema-hover（已废弃，仅保留兼容）

原逐次 hover 实时推导端点。现已被 `setTypeMap` + `lookupType` 本地查找取代。
如仍需使用，可通过 `registerHover({ hoverUrl: '/api/proxy/schema-hover' })` 作为 fallback。

### POST /api/proxy/completions

自动补全端点，`registerCompletions` 的 `urlResolver` 配置指向此处。

### POST /api/proxy/validate / POST /api/proxy/schema-validate

语法 + 类型检查端点，`createDiagnostics` 的 `validateUrl` 配置指向此处。

---

## 典型接入模式

### 模式 A — Schema 只读编辑器（world-one ontology tab）

```javascript
// 1. 页面初始化时注册语言 + hover（全局一次）
require(['vs/editor/editor.main'], () => {
  OutlineLang.registerLanguage();
  OutlineLang.registerHover();   // 纯本地查找，无 HTTP fallback
});

// 2. Schema 标签页打开时并行拉取 outline + 类型数据
async function loadSchemaEditor() {
  const [outlineRes, typesRes] = await Promise.all([
    fetch(`/api/proxy/session-outline?session_id=${sessionId}`),
    fetch(`/api/proxy/schema-types?session_id=${sessionId}`),
  ]);
  // 注入类型数据到 SDK（primitives 自动内置，无需手动添加）
  if (typesRes.ok) OutlineLang.setTypeMap(await typesRes.json());

  const { outline } = await outlineRes.json();
  createOrUpdateMonacoEditor(outline);  // 只读编辑器
}
```

### 模式 B — 可编辑 Query 编辑器（world-one / playground）

```javascript
OutlineLang.registerLanguage();

// 补全：不同 model 可以路由到不同端点
OutlineLang.registerCompletions({
  urlResolver: (model) => {
    if (model === worldEditor.getModel()) return '/api/proxy/completions';
    return '/api/completions';
  },
  getExtraBody: (code, offset) => ({ session_id: currentSessionId }),
});

// Hover：session 类型由 setTypeMap 注入（schema 标签页加载时已注入）
OutlineLang.registerHover();

// Diagnostics：每个编辑器独立创建
const scheduleLint = OutlineLang.createDiagnostics({
  validateUrl:    '/api/proxy/validate',
  getRequestBody: (code) => ({ session_id: currentSessionId, code }),
  editor:         queryEditor,
  debounceMs:     400,
});
queryEditor.onDidChangeModelContent(() => scheduleLint());
```

### 模式 C — 嵌入式独立编辑器（第三方页面）

```javascript
// 不需要 session，只需要语言 + 诊断
OutlineLang.registerLanguage();

const scheduleLint = OutlineLang.createDiagnostics({
  validateUrl: '/my-api/validate',
  editor:       myEditor,
});
myEditor.onDidChangeModelContent(() => scheduleLint());
// hover 不注册，或使用自定义 hoverUrl
```

---

## 类型推导范围与局限

### 当前支持

| 场景 | 支持 | 方式 |
|---|---|---|
| 原始类型（`String`/`Int`/…） | ✅ 始终 | SDK 内置 |
| Entity class（`Employee`） | ✅ session 加载后 | `setTypeMap` |
| Enum class（`Gender`） | ✅ session 加载后 | `setTypeMap` |
| Collection type（`Employees`） | ✅ session 加载后 | `setTypeMap`（后端派生） |
| Let 变量（`employees`） | ✅ session 加载后 | `setTypeMap`（后端派生） |
| Nullable 修饰（`String?`） | ⚠️ 仅 hover 字面匹配 `String` | `?` 非 word 字符，被 Monaco 截断 |

### 当前局限

| 场景 | 支持 | 原因 |
|---|---|---|
| 任意表达式类型（`employees.filter(...)`） | ❌ | 需要完整 GCP 类型推导，SDK 不执行运行时推导 |
| 局部变量（`let x = employees.filter(...)`） | ❌ | 需要数据流分析 |
| 跨文件类型 | ❌ | 当前为单编辑器 scope |

> 如需表达式级类型推导，应在后端通过 GCP `OutlineInterpreter` 推导，结果作为 `setTypeMap` 数据或 `hoverUrl` fallback 返回。

---

## 版本说明

| 版本 | 变化 |
|---|---|
| 初版 | `registerHover({ hoverUrl })` — 每次 hover 发 HTTP |
| v2 | 增加 `localProvider` 选项，支持同步本地查找 |
| v3（当前） | 移除 `localProvider`，改为 SDK 内部维护类型映射表（`_PRIMITIVES` + `_sessionTypeMap`）；新增 `setTypeMap` / `lookupType` 公开 API；`registerHover` 无参数可用 |
