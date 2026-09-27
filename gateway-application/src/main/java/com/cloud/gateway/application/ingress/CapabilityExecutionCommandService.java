package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.configuration.CapabilityResolver;
import com.cloud.gateway.domain.capability.configuration.EffectiveCapabilityConfig;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.apache.commons.lang3.Validate;

/** Owns resolve -> plan -> execute for each slot, including denial and cancellation. */
public final class CapabilityExecutionCommandService {
    private final CapabilityResolver resolver;
    private final CapabilityPlanner planner = new CapabilityPlanner();
    private final List<CapabilityRegistration<?>> registrations;
    private final Map<String, CapabilityExecutor<?>> executors;

    public CapabilityExecutionCommandService(CapabilityResolver resolver, List<CapabilityRegistration<?>> registrations,
            List<CapabilityExecutor<?>> executors) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.registrations = List.copyOf(Objects.requireNonNull(registrations, "registrations"));
        CapabilityRegistration.validate(this.registrations, executors);
        Objects.requireNonNull(executors, "executors");
        Map<String, CapabilityExecutor<?>> registered = new HashMap<>();
        for (CapabilityExecutor<?> executor : executors) {
            Objects.requireNonNull(executor, "executor");
            Validate.isTrue(registered.putIfAbsent(executor.capability(), executor) == null,
                    "duplicate capability executor: %s", executor.capability());
        }
        this.executors = Map.copyOf(registered);
    }

    /** Resolve against this slot's facts, then plan and execute. */
    public CompletionStage<CapabilityExecutionOutput> execute(CapabilityExecutionCommand command) {
        Objects.requireNonNull(command, "command");
        return executeAgainst(resolver.resolve(command.composition(), command.context().endpoint(),
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
    public CompletionStage<CapabilityExecutionOutput> executeAgainst(EffectiveCapabilityConfig resolved,
            CapabilityContext context, Set<CapabilityExecutionKey> executed) {
        IngressTask<CapabilityExecutionOutput> task = new IngressTask<>();
        try {
            Objects.requireNonNull(resolved, "resolved");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(executed, "executed");
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
