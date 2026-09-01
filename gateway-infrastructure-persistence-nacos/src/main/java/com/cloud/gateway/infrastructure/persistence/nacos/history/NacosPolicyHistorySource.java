package com.cloud.gateway.infrastructure.persistence.nacos.history;

import java.util.Optional;
import java.util.function.Predicate;

public interface NacosPolicyHistorySource {
    Optional<String> findFirst(Predicate<String> contentPredicate);
}
