package com.green.regionoccupation.repository;

import com.green.regionoccupation.domain.Unit;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 유닛 상태 저장소(인메모리).
 */
@Repository
public class UnitStateRepository {

    private final Map<String, Unit> byId = new ConcurrentHashMap<>();

    public void clear() {
        byId.clear();
    }

    public void add(Unit u) {
        byId.put(u.id(), u);
    }

    public void remove(String id) {
        byId.remove(id);
    }

    public Unit get(String id) {
        Unit u = byId.get(id);
        if (u == null) throw new IllegalArgumentException("Unit not found: " + id);
        return u;
    }

    public List<Unit> all() {
        return new ArrayList<>(byId.values());
    }

    public List<Unit> byOwner(String ownerId) {
        List<Unit> out = new ArrayList<>();
        for (Unit u : byId.values()) {
            if (ownerId.equals(u.ownerId())) out.add(u);
        }
        return out;
    }

    public List<Unit> stationedAt(String regionKey) {
        List<Unit> out = new ArrayList<>();
        for (Unit u : byId.values()) {
            if (!u.isInTransit() && regionKey.equals(u.regionKey())) out.add(u);
        }
        return out;
    }

    public Optional<Unit> firstStationedAtOwned(String regionKey, String ownerId) {
        for (Unit u : byId.values()) {
            if (!u.isInTransit() && regionKey.equals(u.regionKey()) && ownerId.equals(u.ownerId())) return Optional.of(u);
        }
        return Optional.empty();
    }

    /**
     * 턴 종료 처리: 이동중 유닛의 남은 일수 감소, 도착 처리.
     */
    public List<Unit> tickAndArrive(int daysPerTurn) {
        List<Unit> arrived = new ArrayList<>();
        for (Unit u : byId.values()) {
            if (!u.isInTransit()) continue;
            boolean ready = u.tickDays(daysPerTurn);
            if (ready) {
                u.arrive();
                arrived.add(u);
            }
        }
        return arrived;
    }
}
