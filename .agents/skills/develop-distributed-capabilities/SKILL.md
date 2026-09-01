---
name: develop-distributed-capabilities
description: Use when implementing or reviewing cross-instance locking, reliable event publication, message consumption, partitioning, dead letters, delayed messages, or related failure handling.
---

# Develop Distributed Capabilities

## Workflow

1. Inspect the project guidance, affected modules, POMs, nearby ports, adapters, and runtime configuration.
2. Read `references/distributed-lock.md` before implementing cross-instance mutual exclusion or concurrent writes.
3. Read `references/distributed-messaging.md` when publishing or consuming events, using outbox, partitioning, dead letters, retries, or delayed messages.
4. Depend on framework ports from inner layers and keep starter-specific APIs and configuration in technical implementation or boot modules.
5. Design for duplicate execution, partial failure, retry, timeout, and observability before considering the happy path complete.
6. During development, run the narrowest unit tests that verify the affected capability. Record real-infrastructure scenarios and use `$test-integration` to implement and run them in the smoke-test phase.
