# Operational Trace Java

Java contract and best-effort HTTP emitter for Ones operational trace events.

The module produces `org.example:operational-trace-java:1.0-SNAPSHOT` and is the only Java
source of truth for the cross-surface trace schema used by `world-one`, `note-one`, and other
AIPPs. Events use integer `schema_version: 0`, dotted action names such as `workspace.set`, and
the same snake-case wire format as `world-one/src/main/resources/static/trace-client.js`.
`PayloadSanitizer` redacts secret-bearing keys before events are serialized or posted to
`POST /api/trace/events`.

Build and install:

```bash
mvn clean install
```

The emitter deliberately never lets trace transport failures break the product
operation being traced.
