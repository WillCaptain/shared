# LLM Shared — legacy Java runtime library

Legacy in-process Java LLM runtime/configuration library used by the Ones host and existing sibling
services. It includes provider calling, model pools, tool-call parsing, embeddings helpers and
usage hooks.

It is not the cross-process LLM Gateway wire contract and is not the caller SDK; those live in
`llm-gateway-contract` and `llm-gateway-java-sdk`. The historical artifact name is retained for
consumer compatibility and must not be copied as a naming pattern for new modules.

Build with `mvn test`; consumers use `org.example:llm-shared:1.0-SNAPSHOT`.
