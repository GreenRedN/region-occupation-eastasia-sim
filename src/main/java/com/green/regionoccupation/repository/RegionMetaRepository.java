package com.green.regionoccupation.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.regionoccupation.domain.Region;
import com.green.regionoccupation.exception.NotFoundException;
import com.green.regionoccupation.util.ResourceUtil;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 지역 메타(동아시아 ADMIN1) 로더/조회.
 */
@Repository
public class RegionMetaRepository {

    private final Map<String, Region> byKey;
    private final List<Region> all;

    public RegionMetaRepository(ObjectMapper objectMapper) {
        String json = ResourceUtil.readClasspath("static/data/regions.json");
        try {
            List<Region> list = objectMapper.readValue(json, new TypeReference<List<Region>>() {});
            this.byKey = list.stream().collect(Collectors.toUnmodifiableMap(Region::key, r -> r));
            this.all = List.copyOf(list);
        } catch (Exception e) {
            throw new IllegalStateException("regions.json 로딩 실패: " + e.getMessage(), e);
        }
    }

    public List<Region> findAll() {
        return all;
    }

    public int count() {
        return all.size();
    }

    public boolean exists(String key) {
        return byKey.containsKey(key);
    }

    public Region getByKey(String key) {
        Region r = byKey.get(key);
        if (r == null) throw new NotFoundException("존재하지 않는 regionKey: " + key);
        return r;
    }
    public Set<String> keys() {
        return byKey.keySet();
    }

}

