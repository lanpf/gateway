---
name: develop-distributed
description: Use when implementing or reviewing cross-instance locking, reliable event publication, message consumption, partitioning, dead letters, delayed messages, or related failure handling.
---

# Develop Distributed Capabilities

## Enforcement vocabulary

`required` rules are release-blocking. `default` rules apply unless a concrete deviation reason is recorded. `advisory` rules are optional guidance. `baseline` rules are preloaded for every change; `topic` rules are loaded when the affected capability matches.

## Workflow

1. Use `$develop-service` first to load and enforce the baseline rules for every code change.
2. Inspect the project guidance, affected modules, POMs, nearby ports, adapters, and runtime configuration.
3. Read `references/distributed-lock.md` before implementing cross-instance mutual exclusion or concurrent writes.
4. Read `references/distributed-messaging.md` when publishing or consuming events, using outbox, partitioning, dead letters, retries, or delayed messages.
5. Depend on framework ports from inner layers and keep starter-specific APIs and configuration in technical implementation or boot modules.
6. Design for duplicate execution, partial failure, retry, timeout, and observability before considering the happy path complete.
7. During development, run the narrowest unit tests that verify the affected capability. Record real-infrastructure scenarios and use `$test-integration` to implement and run them in the smoke-test phase.
8. Recheck the final diff against the `$develop-service` baseline checklist and the loaded distributed rules before completion.
