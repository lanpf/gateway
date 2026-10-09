package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyAssembler;
import com.cloud.gateway.domain.capability.policy.EffectiveCapabilityPolicies;
import java.util.ArrayList;
import com.cloud.framework.core.validation.Require;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.cloud.gateway.domain.capability.runtime.CapabilityContext;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecutionKey;
import com.cloud.gateway.domain.capability.runtime.CapabilityOutcome;
import com.cloud.gateway.domain.capability.runtime.CapabilityPlan;
import com.cloud.gateway.domain.capability.runtime.CapabilityPlanner;
import com.cloud.gateway.domain.capability.runtime.CapabilityPlanningEntry;

/** Owns resolve -> plan -> execute for each slot, including denial and cancellation. */
public final class CapabilityExecutionCommandService {
    private final CapabilityPolicyAssembler assembler;
    private final CapabilityPlanner planner = new CapabilityPlanner();
    private final List<CapabilityRegistration<?>> registrations;
    private final Map<CapabilityId, CapabilityExecutor<?>> executors;

    public CapabilityExecutionCommandService(CapabilityPolicyAssembler assembler, List<CapabilityRegistration<?>> registrations,
            List<CapabilityExecutor<?>> executors) {
        Require.allNotNull(assembler, registrations, executors);
        this.assembler = assembler;
        this.registrations = NullSafe.copyOf(registrations);
        CapabilityRegistration.validate(this.registrations, executors);
        this.executors = NullSafe.copyOf(Require.noDuplicates(executors, CapabilityExecutor::capability));
    }

    /** Resolve against this slot's facts, then plan and execute. */
    public CompletionStage<CapabilityExecutionOutput> execute(CapabilityExecutionCommand command) {
        Require.notNull(command);
        return executeAgainst(assembler.assemble(command.policyConfig(), command.context().endpoint(),
                command.context().granted()), command.context(),
                command.executed());
    }

    /**
     * Plan and execute against an already-resolved result.
     *
     * <p>Resolution reads only the endpoint and the granted identity -- not the slot and not the
     * transport lifecycle. So the forward slot, which sees the same snapshot, endpoint and
     * granted identity as admission did, would resolve to exactly the same thing; only the execution
     * point differs, and that belongs to planning. Re-resolving there repeats work that
     * cannot produce a different answer, so the caller hands the earlier result back in. A slot whose
     * facts <em>did</em> change (the pre-authentication slot has no granted identity at all) must still
     * call {@link #execute}.
     */
    public CompletionStage<CapabilityExecutionOutput> executeAgainst(EffectiveCapabilityPolicies resolved,
            CapabilityContext context, Set<CapabilityExecutionKey> executed) {
        IngressTask<CapabilityExecutionOutput> task = new IngressTask<>();
        try {
            Require.allNotNull(resolved, context, executed);
            List<CapabilityPlanningEntry<?>> entries = new ArrayList<>();
            registrations.forEach(registration -> entries.add(registration.bind(resolved)));
            CapabilityPlan plan = planner.plan(entries, context, executed);
            // Validate the entire plan before the first side effect.
            for (CapabilityPlan.Step step : plan.steps()) {
                CapabilityExecutor<?> executor = executors.get(step.capability());
                Objects.requireNonNull(executor, "unregistered capability: " + step.capability());
                step.units().forEach(executor.unitType()::cast);
            }
            CompletionStage<CapabilityExecution> chain = CompletableFuture.completedFuture(
                    new CapabilityExecution(executed, List.of()));
            for (CapabilityPlan.Step step : plan.steps()) {
                chain = chain.thenCompose(report -> {
                    if (task.isDone()) {
                        return CompletableFuture.failedFuture(new CancellationException());
                    }
                    if (!report.continues()) {
                        return CompletableFuture.completedFuture(report);
                    }
                    return task.track(executors.get(step.capability()).executeUnits(step.units())).thenApply(outcome -> {
                        Objects.requireNonNull(outcome, "capability outcome");
                        Set<CapabilityExecutionKey> keys = new HashSet<>(report.executed());
                        keys.addAll(step.keys());
                        List<CapabilityOutcome> outcomes = new ArrayList<>(report.outcomes());
                        outcomes.add(outcome);
                        return new CapabilityExecution(keys, outcomes);
                    });
                });
            }
            task.finish(chain.thenApply(report -> new CapabilityExecutionOutput(resolved, report)));
        } catch (RuntimeException failure) {
            task.completeExceptionally(failure);
        }
        return task;
    }
}
