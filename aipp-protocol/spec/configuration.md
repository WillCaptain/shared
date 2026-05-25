# AIPP Configuration — 协议规格

**归属**：配置元数据与配置值均属于 **AIPP 应用**；Host（world-one）只提供通用 `sys.configuration` 渲染器，通过 HTTP 代理读写 AIPP，**不存储、不解析、不注入** 配置语义。

---

## 1. 端点

| 端点 | 必选 | 说明 |
|------|------|------|
| `GET /api/app` → `configuration.ui` | 可选 | 配置界面元数据（layout） |
| `GET /api/configuration` | 有 UI 时必选 | 当前配置值 |
| `PUT /api/configuration` | 有 UI 时必选 | 保存配置值 |

无配置需求的 AIPP **省略** `configuration` 块，无需实现 `/api/configuration`。

---

## 2. `GET /api/app` — `configuration.ui`

```json
{
  "app_id": "decision-exec",
  "app_name": "决策执行",
  "...": "...",
  "configuration": {
    "ui": {
      "layout": {
        "type": "group",
        "title": "监听",
        "width": "fill",
        "children": [
          {
            "type": "panel",
            "width": "fill",
            "children": [
              {
                "type": "input",
                "bind": "entitir.base_url",
                "label": "Entitir URL",
                "input_type": "text"
              }
            ]
          }
        ]
      }
    }
  }
}
```

| 字段 | 必选 | 说明 |
|------|------|------|
| `configuration` | 可选 | 存在则必须含 `ui` |
| `configuration.ui` | ✅ | 仅含 `layout`（根节点） |
| `configuration.ui.layout` | ✅ | 布局树根节点 |

---

## 3. 布局节点

### 3.1 容器

| `type` | 说明 |
|--------|------|
| `group` | 带标题的分组；`title` 可为空字符串 |
| `panel` | 无标题面板（等价于 `group` 且 `title` 省略或 `""`） |

容器公共字段：

| 字段 | 必选 | 默认 | 说明 |
|------|------|------|------|
| `type` | ✅ | — | `group` \| `panel` |
| `children` | ✅ | — | 子节点数组，至少 1 项 |
| `title` | group 推荐 | `""` | 分组标题 |
| `width` | 可选 | `fill` | `fill` 或正整数（px）；**根下第一层** group/panel 默认 `fill` |
| `layout_mode` | 可选 | `stream` | `stream`：纵向流式排布子项（Host widget 默认） |

### 3.2 控件（叶子）

所有控件必须有 `type` 与 `bind`（`bind` 为 values 中的路径，点分嵌套，如 `listener.poll_interval_ms`）。

| `type` | 额外字段 |
|--------|----------|
| `label` | `text`（必选） |
| `input` | `label`（推荐）, `input_type`：`text` \| `number` \| `password`（默认 `text`）, `placeholder`, `required` |
| `combobox` | `label`, `options`: `[{ "value", "label" }]`（≥1） |
| `list` | `label`, `item_type`: `string`（默认） |
| `radiobox` | `label`, `options`（同 combobox） |
| `checkbox` | `label` |
| `rich_text` | `label` |

---

## 4. `GET /api/configuration`

```json
{
  "ok": true,
  "values": {
    "entitir": { "base_url": "http://localhost:8093" },
    "listener": { "poll_interval_ms": 5000 }
  }
}
```

| 字段 | 必选 | 说明 |
|------|------|------|
| `ok` | ✅ | `true` |
| `values` | ✅ | JSON 对象；键与 layout 中 `bind` 路径对应 |

失败：`{ "ok": false, "error": "..." }`，HTTP 4xx/5xx 由实现决定。

---

## 5. `PUT /api/configuration`

请求：

```json
{
  "values": {
    "entitir": { "base_url": "http://localhost:8093" }
  }
}
```

响应：

```json
{ "ok": true }
```

- AIPP **拥有**校验与持久化；可接受完整 `values` 或按实现 merge。
- Host / `sys.configuration` **只转发** body，不缓存。

---

## 6. Host `sys.configuration`（world-one）

非 AIPP 端点；Host 内置 widget。

**展示模式**：widget manifest 声明 `"display_mode": "pop"`（浮窗，不占 chat/canvas）。工具/skill 响应使用 `pop_widget` 字段（形状同 `html_widget`）。

**打开时 data**（由 Host skill `aipp_configuration_view` 组装）：

```json
{
  "app_id": "decision-exec",
  "app_name": "决策执行",
  "configuration_ui": { "layout": { "...": "..." } },
  "values": { "...": "..." }
}
```

**保存**：`PUT` 经 `hostApi.appProxyUrl('/api/configuration')` 写回目标 AIPP。

**入口**（Host 实现，协议只约定行为）：

1. LLM / skill：`aipp_configuration_view(app_id)` — 用户可说 “open X configuration”
2. `sys.app-list` 每行配置图标
3. 左下角 Apps 面板列表配置图标

---

## 7. 与 Host 运行时注入区分

| | AIPP `configuration` | Host `PUT /api/host/bindings` | `_context.env`（tool 调用） |
|--|------------------------|--------------------------------|-----------------------------|
| 所有者 | AIPP | world-one | world-one |
| 用途 | 业务配置（world_id、Entitir URL 等） | 常驻 worker 运行时绑定 | 单次 tool 只读 env |
| 存储 | AIPP 持久化（`GET/PUT /api/configuration`） | AIPP 内存 / runtime 文件 | 不存储 |
| 典型字段 | `world.world_id`, `listener.enabled` | `env`, `host_event_callback_url` | `env` |

**铁律**：

- `configuration.values` **不得**含 `env`、Host 基址、事件 callback URL。
- `env` 仅由 Host 注入（bindings + `_context`）；详见 [`host-injection.md`](host-injection.md)。
- Host settings 变更 env 时，Host 对所有已注册 AIPP **再次** `PUT /api/host/bindings`，AIPP **不得** poll Host。

---

## 8. Java 校验

```java
AippConfigurationSpec cfg = new AippConfigurationSpec();
cfg.assertValidConfigurationInAppManifest(appNode);
cfg.assertValidConfigurationGetResponse(getNode);
cfg.assertValidConfigurationPutRequest(putBody);
```

见 `AippAppSpec`：manifest 含 `configuration` 时自动调用 layout 校验。
