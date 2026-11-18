# LLM Gateway Contract

Pure Java request, response, error, stream-event and usage types for the Ones LLM Gateway wire
boundary. It has no HTTP transport, provider credentials, or executable service.

Build with `mvn test`; consumers use `org.example:llm-gateway-contract:1.0-SNAPSHOT`.

For a tool manifest's `gateway_delegation` in `job` mode, `operations` is an optional
explicit list of `chat` and/or `embedding`. A Host that implements this extension rejects
empty, duplicate or unknown operations; when the field is absent it preserves the existing
chat-only job grant. A job grant is also bound to the user, calling app, feature and job ID,
and has a bounded lifetime. The consuming service must present matching request metadata.
Consumers requesting embedding access need a Host version that understands `operations`;
an older Host's legacy chat-only grant cannot authorize embedding calls.
