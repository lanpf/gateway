---
name: test-integration
description: Use when writing, running, or reviewing integration tests in the smoke-test phase, including the standalone integration-tests module, Testcontainers scenarios, and integration-test documentation.
---

# Test Integration

## Enforcement vocabulary

`required` rules are release-blocking. `default` rules apply unless a concrete deviation reason is recorded. `advisory` rules are optional guidance. `baseline` rules are preloaded for every change; `topic` rules are loaded when the affected capability matches.

## Workflow

1. Confirm the work belongs to the smoke-test phase; during development only unit tests are allowed.
2. Use `$develop-service` to load and enforce the baseline rules before changing integration-test code or configuration.
3. Read `references/integration-testing.md` before creating or changing any `*IT` scenario.
4. Read `references/logging-verification.md`, `references/persistence-verification.md`, `references/id-verification.md`, `references/lock-verification.md`, `references/messaging-verification.md`, or `references/compensation-verification.md` when the scenario covers that topic.
5. Keep integration tests in the standalone integration-tests module and manage real infrastructure with Testcontainers.
6. Maintain the same-named scenario documentation and manual observation steps for every integration test.
7. Run the integration tests through the Maven Failsafe `integration-test` and `verify` phases.
8. Recheck the final diff against the `$develop-service` baseline checklist and every loaded integration topic before completion.

Required rules are release-blocking; default-rule deviations must have a concrete reported reason.
