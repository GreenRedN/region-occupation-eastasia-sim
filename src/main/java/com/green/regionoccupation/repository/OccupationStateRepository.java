package com.green.regionoccupation.repository;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 점령 상태(인메모리 저장소).
 * - regionKey -> ownerId
 */
@Repository
public class OccupationStateRepository {

    private final ConcurrentHashMap<String, String> state = new ConcurrentHashMap<>();

    public Map<String, String> snapshot() {
        return Collections.unmodifiableMap(state);
    }

    public Optional<String> findOwnerId(String regionKey) {
        return Optional.ofNullable(state.get(regionKey));
    }

    // TurnGameService 호환용(기존 코드가 getOwnerId를 호출)
    public String getOwnerId(String regionKey) {
        return state.get(regionKey);
    }

    public void setOwnerId(String regionKey, String ownerId) {
        state.put(regionKey, ownerId);
    }

    public void occupy(String regionKey, String ownerId) {
        setOwnerId(regionKey, ownerId);
    }

    public void remove(String regionKey) {
        state.remove(regionKey);
    }

    public void reset() {
        state.clear();
    }
}

