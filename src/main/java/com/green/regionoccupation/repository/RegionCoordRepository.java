package com.green.regionoccupation.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.regionoccupation.domain.RegionCoord;
import com.green.regionoccupation.util.ResourceUtil;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 지도 좌표(라벨 포인트) 로더/조회.
 * - static/data/region_coords.json
 */
@Repository
public class RegionCoordRepository {

    private final Map<String, RegionCoord> byKey;

    public RegionCoordRepository(ObjectMapper objectMapper) {
        String json = ResourceUtil.readClasspath("static/data/region_coords.json");
        try {
            List<RegionCoord> list = objectMapper.readValue(json, new TypeReference<List<RegionCoord>>() {});
            this.byKey = list.stream().collect(Collectors.toUnmodifiableMap(RegionCoord::key, c -> c));
        } catch (Exception e) {
            throw new IllegalStateException("region_coords.json 로딩 실패: " + e.getMessage(), e);
        }
    }

    public Optional<RegionCoord> find(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public boolean has(String key) {
        return byKey.containsKey(key);
    }

    public int count() {
        return byKey.size();
    }
}
