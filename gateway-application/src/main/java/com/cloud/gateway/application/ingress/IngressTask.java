package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.util.NullSafe;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Per-use-case cancellation ownership; never holds protocol or executor state. */
final class IngressTask<T> extends CompletableFuture<T> {
    private final List<CompletableFuture<?>> children = new ArrayList<>();

    synchronized <V> CompletionStage<V> track(CompletionStage<V> stage) {
        CompletableFuture<V> child = stage.toCompletableFuture();
        if (isDone()) {
            child.cancel(true);
        } else {
            children.add(child);
            child.whenComplete((value, failure) -> {
                if (failure != null && completeExceptionally(failure)) {
                    cancelChildren();
                }
            });
        }
        return child;
    }

    void finish(CompletionStage<T> stage) {
        track(stage).whenComplete((value, failure) -> {
            if (failure == null) {
                complete(value);
            } else {
                completeExceptionally(failure);
            }
        });
    }

    @Override
    public synchronized boolean cancel(boolean interrupt) {
        boolean cancelled = super.cancel(interrupt);
        if (cancelled) {
            cancelChildren();
        }
        return cancelled;
    }

    private synchronized void cancelChildren() {
        NullSafe.copyOf(children).forEach(child -> child.cancel(true));
    }
}
