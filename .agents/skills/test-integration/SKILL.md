---
name: test-integration
description: Use when writing, running, or reviewing integration tests in the smoke-test phase, including the standalone integration-tests module, Testcontainers scenarios, and integration-test documentation.
---

# Test Integration

## Workflow

1. Confirm the work belongs to the smoke-test phase; during development only unit tests are allowed, and they are covered by `$develop-service-code`.
2. Read `references/integration-testing.md` before creating or changing any `*IT` scenario.
3. Keep integration tests in the standalone integration-tests module and manage real infrastructure with Testcontainers.
4. Maintain the same-named scenario documentation and manual observation steps for every integration test.
5. Run the integration tests through the Maven Failsafe `integration-test` and `verify` phases.

Treat every `required` rule as a release-blocking constraint.
