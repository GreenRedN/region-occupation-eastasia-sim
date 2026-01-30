package com.green.regionoccupation.repository;

import com.green.regionoccupation.domain.ReconSnapshot;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * viewerOwnerId -> (regionKey -> ReconSnapshot)
 */
@Repository
public class ReconStateRepository {

    private final Map<String, Map<String, ReconSnapshot>> store = new ConcurrentHashMap<>();

    public void clear() {
        store.clear();
    }

    public ReconSnapshot get(String viewerOwnerId, String regionKey) {
        Map<String, ReconSnapshot> m = store.get(viewerOwnerId);
        return (m == null) ? null : m.get(regionKey);
    }

    public void put(String viewerOwnerId, String regionKey, ReconSnapshot snapshot) {
        store.computeIfAbsent(viewerOwnerId, k -> new ConcurrentHashMap<>()).put(regionKey, snapshot);
    }

    public Map<String, ReconSnapshot> snapshotForViewer(String viewerOwnerId) {
        return store.getOrDefault(viewerOwnerId, Map.of());
    }
}
