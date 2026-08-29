# Shared

Reusable code and cross-application contracts for the 12th workspace.

The directory name of each module states the capability and delivery form; a bare `client`
is not considered a sufficient module name.

| Path | Responsibility |
| --- | --- |
| `aipp-protocol/` | Core AIPP protocol contracts and specifications |
| `aipp-protocol-spring/` | Spring integration for the AIPP protocol |
| `billing-contract/` | Pure Java Billing API types and application ports shared by callers and Billing-One |
| `billing-one-http-adapter/` | HTTP implementation of the Billing ports for services calling Billing-One |
| `db-ops/` | Java/Spring JDBC atomic-operation and structured SQL logging library |
| `hybrid-retrieval/` | Java hybrid lexical/vector retrieval library built on `db-ops` |
| `llm-gateway-contract/` | Pure Java wire types shared by the LLM Gateway and its callers |
| `llm-gateway-java-sdk/` | Java HTTP/SSE SDK used by AIPPs to invoke the LLM Gateway |
| `llm-gateway-protocol/` | OpenAPI, SSE, compatibility rules and golden protocol fixtures |
| `llm-shared/` | Legacy Java in-process LLM runtime/configuration types; distinct from the Gateway SDK |
| `operational-trace-java/` | Java operational trace types and best-effort HTTP emitter |
| `outline-editor/` | Stateless Java editor-analysis backend shared by Outline hosts |
| `js/` | Browser-side Outline editor client distributed as static JavaScript |
| `theme/` | Theme source definitions, compiler scripts, runtime interfaces and package sources |
| `css/` | Stable shared CSS plus generated outputs from `theme/` |

Executable Ones applications and AIPPs belong in the sibling `ones/` repository. Shared modules
must not be placed at the top level of `ones/`.

`llm-shared` is an explicitly documented legacy name, not a naming template for new modules. New
modules must state both capability and delivery form, such as `*-java-sdk`, `*-protocol`, or
`*-http-adapter`. Theme source is edited under `theme/`; generated CSS is written under `css/`.
