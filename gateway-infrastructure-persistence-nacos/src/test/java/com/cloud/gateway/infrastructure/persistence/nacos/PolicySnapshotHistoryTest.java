package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicyHistorySource;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicySnapshotHistory;
import org.junit.jupiter.api.Test;

class PolicySnapshotHistoryTest {
    private final StoredPolicySnapshotCodec codec = PolicyTestFixtures.codec();

    @Test
    void shouldFindHistoricalSnapshotByBusinessVersion() {
        NacosPolicySnapshotHistory history = new NacosPolicySnapshotHistory(
                new FakeHistorySource(List.of(
                        codec.encodeSnapshot(PolicyTestFixtures.snapshot(3, false)),
                        codec.encodeSnapshot(PolicyTestFixtures.snapshot(2, true)))), codec);

        assertEquals(2, history.find(2).orElseThrow().snapshotVersion());
        assertTrue(history.find(1).isEmpty());
    }

    private record FakeHistorySource(List<String> contents) implements NacosPolicyHistorySource {
        @Override
        public Optional<String> findFirst(Predicate<String> contentPredicate) {
            return contents.stream().filter(contentPredicate).findFirst();
        }
    }
}
