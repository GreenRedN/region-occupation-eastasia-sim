package com.green.regionoccupation.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.regionoccupation.domain.RegionStat;
import com.green.regionoccupation.util.ResourceUtil;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 지역 스탯 로더/조회.
 *
 * static/data/region_stats.json 을 로딩한다.
 */
@Repository
public class RegionStatRepository {

    private final Map<String, RegionStat> byKey;

    public RegionStatRepository(ObjectMapper objectMapper) {
        String json = ResourceUtil.readClasspath("static/data/region_stats.json");
        try {
            List<RegionStat> list = objectMapper.readValue(json, new TypeReference<List<RegionStat>>() {});
            Map<String, RegionStat> m = new HashMap<>();
            for (RegionStat s : list) {
                m.put(s.key(), s);
            }
            this.byKey = Collections.unmodifiableMap(m);
        } catch (Exception e) {
            throw new IllegalStateException("region_stats.json 로딩 실패: " + e.getMessage(), e);
        }
    }

    public RegionStat getByKey(String key) {
        RegionStat s = byKey.get(key);
        if (s == null) throw new IllegalArgumentException("RegionStat not found: " + key);
        return s;
    }

    public Map<String, RegionStat> snapshot() {
        return byKey;
    }
}
