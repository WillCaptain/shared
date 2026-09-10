# Billing Java SDK

Java HTTP implementation of the ports defined by `billing-contract`. It is a reusable caller-side
SDK; Billing domain behavior remains in the provider service and the shared contract.

Build with `mvn test`; consumers use `org.example:billing-java-sdk:1.0-SNAPSHOT`.
The older `billing-one-http-adapter` module remains as a compatibility alias until Ones
callers finish switching.
