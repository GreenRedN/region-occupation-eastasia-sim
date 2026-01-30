package com.green.regionoccupation.repository;

import com.green.regionoccupation.domain.GovernanceState;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class GovernanceStateRepository {
    private final Map<String, GovernanceState> byRegionKey = new ConcurrentHashMap<>();

    public void clear() {
        byRegionKey.clear();
    }

    public GovernanceState getOrCreate(String regionKey, int supportInit, int stabilityInit) {
        return byRegionKey.computeIfAbsent(regionKey, k -> new GovernanceState(supportInit, stabilityInit));
    }

    public GovernanceState get(String regionKey) {
        return byRegionKey.get(regionKey);
    }

    public Map<String, GovernanceState> snapshot() {
        return Map.copyOf(byRegionKey);
    }
}
