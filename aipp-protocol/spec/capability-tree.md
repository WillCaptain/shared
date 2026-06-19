# Capability Tree — 协议规格（Host 运行时）

**受众**：AIPP 应用开发者、AI 编排开发者。

**发现路径**：[`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → 本文。

**归属**：Capability tree 描述 **Host 如何发现与编排可执行能力**（tool / skill / route）。它与 AIPP 的 `GET /api/tools`、`GET /api/skills` 对齐，但是 **Ones（world-one）Host 侧的导航图**。

**与 widget 的关系**：

| 概念 | 作用 |
|------|------|
| **Capability tree**（`tool` / `skill` / `route`） | Router / Planner **执行发现** |
| **`widgets/` 文件夹**（`kind: widget`） | **Widget 目录**，浏览与预览，**不参与** Router 文本匹配 |
| **`sys.*` widget** | Host 展示协议，见 [`system-widgets.md`](system-widgets.md) |

---

## 1. AIPP 开发者要做什么

1. **暴露能力**：`GET /api/tools`、`GET /api/skills`（及 playbook）— 这是权威清单。
2. **暴露 UI**：`GET /api/widgets` — 自有 widget manifest。
3. **（推荐）组织语义**：在 Host 上维护一棵 **route → tool/skill** 树，让 Router 能渐进发现你的领域，而不是只靠扁平 tool 描述。
4. **验证**：安装到 Host 后调用下文 **Host API** 检查 `your-app-id` 的树与 `widgets/` 目录。

你 **不需要** 把 `sys.selection` 写成可执行叶节点；互斥多选由 Host Planner 在匹配多个 **tool 叶** 后自动发出 `sys.selection`。

---

## 2. 文档 schema

```json
{
  "schema_version": "aipp.capability_tree/v1",
  "app_id": "recipe-one",
  "app_name": "菜谱管理",
  "customized": false,
  "root": { }
}
```

| 字段 | 说明 |
|------|------|
| `schema_version` | 固定 `aipp.capability_tree/v1` |
| `app_id` | 与 `GET /api/app.app_id` 一致 |
| `customized` | `true` = 用户或应用在 Host 上保存过覆盖 |
| `root` | 树根节点 |

---

## 3. 节点类型（`kind`）

| kind | 角色 | Router 可发现？ | 典型 `ref` |
|------|------|-----------------|------------|
| `route` | 分组 / 导航干 | 展开子节点 | 无 |
| `folder` | 纯 UI 容器 | 否 | 无 |
| `widget` | Widget **目录项** | **否** | `{ "type": "widget", "name": "recipe-board" }` |
| `tool` | 可执行叶 | **是** | `{ "type": "tool", "name": "recipe_list" }` |
| `skill` | 可执行叶 | **是** | `{ "type": "skill", "name": "recipe_import" }` |

### 3.1 节点 ID

```
{app_id}::{local_id}
```

示例：

- `recipe-one::root`
- `recipe-one::folder-widgets`
- `recipe-one::widget-recipe-board`
- `recipe-one::tool-recipe-list`

`local_id` 在 app 内唯一。

**Virtual forest `imported`:** Host overlay community packs (`~/.world-one/imported/`). Same node kinds; not a registered HTTP AIPP. See [`imported-overlay.md`](imported-overlay.md). Discovery searches `imported` **last**.

### 3.2 公共字段

| 字段 | 说明 |
|------|------|
| `id` | 全局节点 id |
| `kind` | 见上表 |
| `title` | 展示标题 |
| `description` | 路由节点唯一正文：发现期召回摘要（列表中截断展示）+ 非 root 路由命中后注入上下文（统一模型 2026-06，原 `aap_pre` 字段已移除，旧树加载时折叠进 description） |
| `entry_prompt` | 进入该路由后注入的操作手册（原 `aap_post`；通常仅在 `root`，root 缺省回落到注册层 `prompt_contributions[layer=entry_prompt]`） |
| `org_hint` | 企业约定 overlay（Host 实例级，管理员编辑；不覆盖 AIPP 设计） |
| `ref` | `tool` / `skill` / `widget` 叶的引用 |
| `children` | 子节点数组 |

### 3.3 示例：业务 app 树

```json
{
  "id": "recipe-one::root",
  "kind": "route",
  "title": "菜谱管理",
  "description": "菜谱、食材、烹饪步骤。用户提到菜、菜谱、食材、烹饪时使用本应用。",
  "children": [
    {
      "id": "recipe-one::folder-widgets",
      "kind": "folder",
      "title": "widgets",
      "description": "UI widgets registered for this AIPP.",
      "children": [
        {
          "id": "recipe-one::widget-recipe-board",
          "kind": "widget",
          "title": "Recipe Board",
          "description": "菜谱看板",
          "ref": {
            "type": "widget",
            "name": "recipe-board",
            "display_mode": "canvas"
          },
          "children": []
        }
      ]
    },
    {
      "id": "recipe-one::route-browse",
      "kind": "route",
      "title": "浏览与搜索",
      "children": [
        {
          "id": "recipe-one::tool-recipe-list",
          "kind": "tool",
          "title": "列出菜谱",
          "description": "按关键词列出菜谱卡片。",
          "ref": { "type": "tool", "name": "recipe_list" },
          "children": []
        }
      ]
    }
  ]
}
```

### 3.4 `worldone-system` 树

Host 内置 app `worldone-system` 的 `widgets/` 下列出 **所有 `sys.*` catalog 项**（Confirm、Selection、Capability Map 等）。这是给人类和 AI 查 Host 能力的 **电话簿**，不是你的 AIPP 需要实现的节点。

---

## 4. 树的数据来源（Ones host）

| 来源 | 说明 |
|------|------|
| **自动生成** | 首次访问：root + `widgets/`（来自 `/api/widgets`）+ 扁平 tool/skill 叶 |
| **持久化覆盖** | `~/.worldone/capability-trees/{app_id}.json` |
| **应用种子** | 部分 demo app 在 install 时写入 classpath JSON（如 `coop-plan-demo`） |
| **Manifest（规划）** | 未来可在 `GET /api/app` 增加 `capability_tree` 出厂默认；当前以 Host 侧文件为准 |

AIPP 作者在开发阶段应：

1. 保证 `ref.name` 与 `/api/tools` / `/api/skills` 中的 **name 完全一致**。
2. 用 Host API 拉取 `GET /api/capability-trees/{app_id}` 核对树是否反映预期语义分组。

---

## 5. Host API（Ones host）

Base URL 为 Host 地址（如 `http://localhost:8090`）。以下路径在 **Host** 上，不在 AIPP app 上。

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/capability-trees` | 整片森林（所有 app） |
| `GET` | `/api/capability-trees/{app_id}` | 单 app 完整树 |
| `GET` | `/api/capability-trees/capabilities?parent=` | 通用遍历；`parent` 空 = 各 app 根 |
| `GET` | `/api/capability-trees/capabilities?parent={node_id}` | 某节点的直接子节点 |
| `GET` | `/api/capability-trees/roots/summary` | Router 注入用根摘要（Markdown 文本） |
| `GET` | `/api/capability-trees/nodes/{node_id}/children` | 子节点摘要列表 |
| `PUT` | `/api/capability-trees/{app_id}` | 保存整棵树（维护 UI） |
| `POST` | `/api/capability-trees/{app_id}/reset` | 重置为自动生成 |
| `PATCH` | `/api/capability-trees/nodes/{node_id}` | 更新单节点 |
| `POST` | `/api/capability-trees/nodes/{parent_id}/children` | 添加子节点 |
| `DELETE` | `/api/capability-trees/nodes/{node_id}` | 删除节点 |

### 5.1 开发时自检

```bash
# 你的 app 整棵树
curl -s http://localhost:8090/api/capability-trees/recipe-one | jq .

# 仅看 widgets 目录
curl -s 'http://localhost:8090/api/capability-trees/capabilities?parent=recipe-one::folder-widgets' | jq .

# 合并 widget 目录（含 sys.*）
curl -s http://localhost:8090/api/widgets | jq '[.widgets[] | {type, app_id, title: .title // .type}]'
```

---

## 6. Router 如何使用树（概要）

```
用户消息
  → Host 注入各 AIPP 根摘要（roots/summary）
  → LLM 调用 target_capability_nodes([node_ids…])
  → 遇到 route：展开 children，继续发现
  → 遇到多个互斥 tool 叶：Host Free Planner → sys.selection
  → 遇到多个协作步骤：Host Free Planner → sys.plan
  → 单个 tool/skill 叶：执行 tool / load skill
```

**要点**：

- 匹配目标是 **`kind: tool` / `kind: skill`**，不是 `kind: widget`。
- `sys.selection` 的 options 引用的是 **tool 名** + 可选 `node_id`，不是 widget catalog id。

---

## 7. 与 AIPP 三端点的对应关系

```
┌─────────────────────┬──────────────────────────┬─────────────────────────┐
│ AIPP 端点           │ Capability tree          │ 用途                    │
├─────────────────────┼──────────────────────────┼─────────────────────────┤
│ GET /api/tools      │ kind=tool 叶的 ref.name  │ LLM 原子调用            │
│ GET /api/skills     │ kind=skill 叶的 ref.name │ Playbook 渐进发现       │
│ GET /api/widgets    │ widgets/ 下 kind=widget  │ UI manifest + 目录预览  │
└─────────────────────┴──────────────────────────┴─────────────────────────┘
```

Tool 响应里的 `widget_type`（自有或 `sys.*`）**不**出现在树的执行叶上；树只回答「调用哪个 tool/skill」。

---

## 8. 反模式

- ❌ 在自家 `/api/widgets` 注册 `sys.selection`
- ❌ 把 `sys.*` 做成 `kind: tool` 叶（应用没有名为 `sys.selection` 的 tool）
- ❌ 期望 Router 搜索 `widgets/` 文件夹来执行业务
- ❌ `ref.name` 与 `/api/tools` 名称不一致
- ❌ 只在 tool description 里写流程，树上无可发现叶（应补 route 分组 + skill playbook）

---

## 9. 相关文档

- [`system-widgets.md`](system-widgets.md) — `sys.*` 类型与 tool 响应 JSON
- [`../README.md`](../README.md) §3 Tool、§4 Skill、§5 Widget
- Host 实现参考：Ones（world-one）`CapabilityTreeService`、`CapabilityTreeController`
