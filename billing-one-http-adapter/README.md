# Billing-One HTTP Adapter

Java HTTP implementation of the ports defined by `billing-contract` for applications calling the
Billing-One service. It is a caller-side transport adapter, not Billing-One itself and not a
generic HTTP client module.

Build with `mvn test`; existing consumers use `org.example:billing-one-http-adapter:1.0-SNAPSHOT`.
New callers should depend on `billing-java-sdk` instead. This module remains during the
transition so current Ones / 12th installs keep compiling.
