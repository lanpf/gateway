package com.cloud.gateway.application.ingress;

/** Observability port for prepare-stage signals; adapters map them to low-cardinality metrics. */
public interface PreparationObserver {

    /** Configuration defect: several endpoint definitions overlap on the requested path. */
    void ambiguousMatch();
}
