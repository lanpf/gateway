package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.policy.execution.*;
import com.cloud.gateway.domain.policy.bundle.CapabilityResolver;
import com.cloud.gateway.domain.policy.bundle.ResolvedCapabilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.Validate;

/** Owns resolve -> plan -> execute for either slot, including denial and cancellation. */
public final class CapabilityExecutionCommandService {
    private final CapabilityResolver resolver;
    private final CapabilityPlanner planner;
    private final Map<String, CapabilityExecutor<?>> executors;

    public CapabilityExecutionCommandService(CapabilityResolver resolver, CapabilityPlanner planner,
            List<CapabilityExecutor<?>> executors) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.planner = Objects.requireNonNull(planner, "planner");
        Map<String, CapabilityExecutor<?>> registered = new HashMap<>();
        for (CapabilityExecutor<?> executor : Objects.requireNonNull(executors, "executors")) {
            Objects.requireNonNull(executor, "executor");
            Validate.isTrue(registered.putIfAbsent(executor.capability(), executor) == null,
                    "duplicate capability executor: %s", executor.capability());
        }
        this.executors = Map.copyOf(registered);
    }

    public CompletionStage<CapabilityExecutionOutput> execute(CapabilityExecutionCommand command) {
        IngressTask<CapabilityExecutionOutput> task = new IngressTask<>();
        try {
            Objects.requireNonNull(command, "command");
            ResolvedCapabilities resolved = resolver.resolve(command.composition(), command.context());
            CapabilityPlan plan = planner.plan(resolved, command.context(), command.executed());
            // Validate the entire plan before the first side effect.
            for (CapabilityPlan.Step step : plan.steps()) {
                CapabilityExecutor<?> executor = Objects.requireNonNull(executors.get(step.capability()),
                        "unregistered capability: " + step.capability());
                step.units().forEach(executor.unitType()::cast);
            }
            CompletionStage<CapabilityExecution> chain = CompletableFuture.completedFuture(
                    new CapabilityExecution(command.executed(), List.of()));
            for (CapabilityPlan.Step step : plan.steps()) {
                chain = chain.thenCompose(report -> {
                    if (task.isDone()) {
                        return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException());
                    }
                    if (!report.allowed()) {
                        return CompletableFuture.completedFuture(report);
                    }
                    return task.track(executors.get(step.capability()).executeUnits(step.units())).thenApply(outcome -> {
                        Set<CapabilityExecutionKey> keys = new HashSet<>(report.executed());
                        keys.addAll(step.keys());
                        List<CapabilityOutcome> outcomes = new ArrayList<>(report.outcomes());
                        outcomes.add(Objects.requireNonNull(outcome, "capability outcome"));
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
