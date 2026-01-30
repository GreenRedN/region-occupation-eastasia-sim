package com.green.regionoccupation.repository;

import com.green.regionoccupation.domain.BuildingType;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지역별 시설 상태 저장소 (인메모리).
 *
 * - 시설은 "지역"에 붙는다 (점령자가 바뀌어도 시설은 남는다)
 * - 레벨 시스템은 사용하지 않는다(있/없).
 */
@Repository
public class BuildingStateRepository {

    private final Map<String, EnumSet<BuildingType>> buildingsByRegion = new ConcurrentHashMap<>();

    public boolean has(String regionKey, BuildingType type) {
        EnumSet<BuildingType> s = buildingsByRegion.get(regionKey);
        return s != null && s.contains(type);
    }

    public EnumSet<BuildingType> getSetOrEmpty(String regionKey) {
        EnumSet<BuildingType> s = buildingsByRegion.get(regionKey);
        return (s == null) ? EnumSet.noneOf(BuildingType.class) : EnumSet.copyOf(s);
    }

    public Map<String, Set<BuildingType>> snapshot() {
        Map<String, Set<BuildingType>> out = new HashMap<>();
        for (Map.Entry<String, EnumSet<BuildingType>> e : buildingsByRegion.entrySet()) {
            out.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return out;
    }

    public void add(String regionKey, BuildingType type) {
        buildingsByRegion.compute(regionKey, (k, v) -> {
            EnumSet<BuildingType> set = (v == null) ? EnumSet.noneOf(BuildingType.class) : EnumSet.copyOf(v);
            set.add(type);
            return set;
        });
    }

    public void clear() {
        buildingsByRegion.clear();
    }

    public Set<String> regionsHaving(BuildingType type) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, EnumSet<BuildingType>> e : buildingsByRegion.entrySet()) {
            if (e.getValue() != null && e.getValue().contains(type)) out.add(e.getKey());
        }
        return out;
    }
}
