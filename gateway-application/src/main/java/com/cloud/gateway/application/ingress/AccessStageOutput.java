package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.ingress.access.AccessDecision;
import java.util.Objects;

/**
 * The access stage's output: the evaluator's verdict, plus what an allowed verdict needs downstream.
 * Both variants hold the decision itself instead of restating it, so both carry the snapshot version
 * the request was evaluated against, and the relationship to {@link AccessDecision} is containment
 * rather than a parallel hierarchy.
 */
public sealed interface AccessStageOutput {
    AccessDecision decision();

    /** A denial adds nothing to the verdict; it is carried to the terminal output as-is. */
    record Denied(AccessDecision.Denied decision) implements AccessStageOutput { }

    /** Carries the original captured facts; admission resolves capabilities from this same snapshot. */
    record Allowed(AccessDecision.Allowed decision,
                   PreparationStageOutput.Prepared prepared) implements AccessStageOutput {
        public Allowed {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(prepared, "prepared");
        }
    }
}
