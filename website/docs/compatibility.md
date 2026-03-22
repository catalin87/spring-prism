# Compatibility Matrix

Spring Prism currently validates the following production-facing baseline:

| Surface | Supported baseline |
| --- | --- |
| Java | `21` |
| Spring Boot | `3.4.x` |
| Spring AI | `1.0.0-M5` |
| LangChain4j | `1.0.1` |

These versions match the current build and CI matrix. If you move outside this range, treat it as
untested until the library publishes an updated compatibility statement.

## Module Support

| Module | Status |
| --- | --- |
| `prism-core` | Production-ready core engine |
| `prism-spring-ai` | Supported |
| `prism-langchain4j` | Supported |
| `prism-spring-boot-starter` | Supported |
| `prism-dashboard` | Deferred |
| MCP support | Deferred |
