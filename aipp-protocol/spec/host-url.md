# Host Base URL — 协议规格

**归属**：world-one（Host）对外根 URL 是 **部署配置**，由每个 AIPP 进程本地解析；**不是** AIPP `configuration` widget 字段，**不是** Host `PUT /api/host/bindings` 注入字段。

---

## 1. 配置文件

所有 AIPP 共享同一路径（用户可编辑）：

```
~/.ones/host.json
```

示例：

```json
{
  "host_base_url": "http://127.0.0.1:8090"
}
```

| 字段 | 必选 | 说明 |
|------|------|------|
| `host_base_url` | ✅ | Host 对外根 URL（推荐带 `http://` 或 `https://`） |

路径覆盖（可选）：

| 变量 | 说明 |
|------|------|
| `AIPP_HOST_CONFIG` | 指向替代 JSON 文件的绝对路径 |

---

## 2. 解析优先级

AIPP 在需要访问 Host 时按序解析：

1. **`~/.ones/host.json`**（或 `AIPP_HOST_CONFIG` 指向的文件）
2. 环境变量 **`AIPP_HOST_BASE_URL`**（别名 **`WORLD_ONE_BASE_URL`**）
3. 应用 `application.yml` 中的 `aipp.host.base-url` / 模块等价项
4. 默认值 **`http://127.0.0.1:8090`**

Host 注入的 legacy `host_base_url`（旧版 bindings）若仍存在，优先于文件（向后兼容）。

---

## 3. 派生 URL

AIPP **本地派生**，Host **不再注入**：

| 用途 | 公式 |
|------|------|
| World events | `{host_base_url}/api/world-events` |
| Event callback 注册 | `{host_base_url}/api/host/event-callbacks/{app_id}` |

`app_id` 来自本 AIPP 的 `GET /api/app`。

---

## 4. 与 Host 注入的关系

| 字段 | 来源 |
|------|------|
| `host_base_url` | **本规格**（文件 / env / 默认） |
| `host_event_callback_url` | **派生**（见 §3） |
| `env` | Host `PUT /api/host/bindings` |
| `host_id`, `app_id` | Host `PUT /api/host/bindings` |

详见 [`host-injection.md`](host-injection.md) 与 [`configuration.md`](configuration.md) §7。

---

## 5. Java 工具

```java
import org.twelve.aipp.host.HostUrlResolver;
import org.twelve.aipp.host.HostBindingsUrls;

String base = HostUrlResolver.resolve(appYamlFallback);
String events = HostBindingsUrls.worldEventsUrl(hostBindingsStore, appYamlFallback);
String callbacks = HostUrlResolver.eventCallbackBaseUrl(hostBindingsStore, appId, appYamlFallback);
```

---

## 6. 禁止事项

- **禁止** 在 `configuration.ui` 中提供 Host URL 表单项
- **禁止** 在 `configuration.values` 中持久化 `host.base_url`
- **禁止** 要求 Host 在 install 时注入 `host_base_url`（仅注入 `env` 等 Host 独有运行时字段）
