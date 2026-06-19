# Host Runtime Injection — 协议规格

**归属**：运行环境（`env`）等 **Host 级运行时上下文** 由 **world-one（Host）** 拥有并注入；AIPP **不得** 在 `configuration` 中持久化 `env`，**不得** 轮询 Host 获取 env。

**Host 基址**：见 [`host-url.md`](host-url.md) — 由 `~/.ones/host.json` / env / 默认值解析，**不在** bindings 注入 payload 中重复发送。

**对比**：

| 机制 | 时机 | 用途 |
|------|------|------|
| **`PUT /api/host/bindings`**（本节） | install / reload / env 变更 | 写入 AIPP 进程内运行时绑定（`env` 等） |
| **Host URL 文件**（[`host-url.md`](host-url.md)） | AIPP 启动 / 首次需要访问 Host | 解析 `host_base_url` 与派生 callback URL |
| **`_context.env`**（[`tool-responses.md`](tool-responses.md) §1） | 每次 `POST /api/tools/{name}` | 单次 tool 调用的只读 env |
| **AIPP `configuration`** | 用户经 `sys.configuration` 保存 | 纯业务配置（world_id、listener 等） |

三者不得混用同一字段（尤其 **`env` 只属于 Host**）。

---

## 1. AIPP 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `PUT` | `/api/host/bindings` | Host 注入 / 更新运行时绑定（幂等 upsert） |
| `GET` | `/api/host/bindings` | （可选）调试：返回当前生效绑定 |

需要 Host 上下文的外部 AIPP（listener、callback 消费者、长期 worker）**必须** 实现 `PUT`。  
纯 request/response 型 AIPP 可返回 `{ "ok": true, "ignored": true }`。

---

## 2. Host 何时调用

| 触发 | Host 行为 |
|------|-----------|
| `POST /api/registry/install` 成功、`loadApp` 完成 | 对目标 AIPP `PUT /api/host/bindings`（全量） |
| Host 启动后补加载缺失 app（runtime refresh） | 同上 |
| Host **runtime env** 变更（settings 中 `envVars` / `runtimeEnv`） | 对**所有已注册** AIPP 再次 `PUT`（至少更新 `env`） |
| （预留）Host 其他全局策略变更 | 扩展 bindings 字段后全量或增量 `PUT` |

**禁止**：AIPP 定时 pull Host env；**禁止** 在 `configuration.values` 中保存 `env`。

---

## 3. `PUT /api/host/bindings` 请求体

```json
{
  "host_id": "worldone",
  "app_id": "decision-reactor",
  "env": "production"
}
```

### 3.1 必选字段（v1）

| 字段 | 类型 | 说明 |
|------|------|------|
| `host_id` | string | Host 标识（如 `worldone`） |
| `app_id` | string | 本次绑定的 AIPP id，须与 install 一致 |
| `env` | string | 当前运行环境：`production` \| `staging` \| `draft`（Host 规范化） |

### 3.2 已移除字段（v1.1+）

以下字段 **不再** 由 Host 注入；AIPP 按 [`host-url.md`](host-url.md) 本地解析：

| 字段 | 替代 |
|------|------|
| `host_base_url` | `~/.ones/host.json` / env / 默认 `http://127.0.0.1:8090` |
| `host_event_callback_url` | 派生：`{host_base_url}/api/host/event-callbacks/{app_id}` |

### 3.3 扩展字段（预留）

Host 可增加顶层字段；AIPP **必须忽略**未知键。未来可能包括：

- `host_proxy_prefix` — widget 经 Host 代理访问 AIPP 的路径前缀
- `user_id` / `tenant_id` — 多租户（预留）
- `injected_at` — ISO-8601 时间戳

---

## 4. 响应

成功：

```json
{ "ok": true }
```

无 worker 的 app 可：

```json
{ "ok": true, "ignored": true }
```

失败：

```json
{ "ok": false, "error": "..." }
```

HTTP 建议：成功 `200`；入参错误 `400`；AIPP 暂不可接收 `503`（Host 可重试 install 阶段）。

---

## 5. env 变更

Host 修改 runtime env 后：

1. 更新 Host 内部 `runtimeEnv`（如 `~/.worldone-config.json`）。
2. 对每个已注册 `app_id` 调用 `PUT /api/host/bindings`，**至少**更新 `env`（推荐全量重发）。
3. AIPP 收到后更新内存绑定；若 listener 依赖 env，**在同一 PUT 处理内** 切换 env 并重置相关 cursor（**不** 启动定时 refresh）。

与 `_context.env` 的关系：bindings 供 **常驻 worker**；tool 调用仍读 `_context.env`，二者值应一致。

---

## 6. AIPP 实现要点

- **存储位置**：进程内存；**不要** 写入 `aipp-configuration.json` 的 `values`。
- **不要** 在 `configuration.ui` 中提供 `env` 或 Host URL 表单项。
- **不要** poll Host / entitir 做「及时刷新」；仅 react **push** 事件与 **inject** 更新。
- `GET /api/host/bindings`（可选）仅用于运维调试，Host 不依赖。
- Host URL 与 callback 基址：见 [`host-url.md`](host-url.md)。

---

## 7. Host（world-one）实现要点

- install / loadApp 成功后调用目标 AIPP `PUT /api/host/bindings`（`host_id`, `app_id`, `env`）。
- settings 保存且 `runtimeEnv` 变化时，遍历 registry 重注入。
- **不要** 在 bindings payload 中发送 `host_base_url` / `host_event_callback_url`。
- tool 调用时在 `_context`（及必要时 `args`）注入相同 `env`。

---

## 8. Java 校验

```java
AippHostInjectionSpec spec = new AippHostInjectionSpec();
spec.assertValidHostBindingsPutRequest(putBody);
spec.assertValidHostBindingsPutResponse(putResponse);
```

见 `org.twelve.aipp.AippHostInjectionSpec`。
