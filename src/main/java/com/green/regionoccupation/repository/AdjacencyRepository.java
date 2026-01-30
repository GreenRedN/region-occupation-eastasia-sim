package com.green.regionoccupation.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.regionoccupation.domain.Adjacency;
import com.green.regionoccupation.util.ResourceUtil;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 인접 데이터 로더/조회.
 * - 동아시아 실습 버전에서는 adjacency.json이 비어있어도 정상 동작(이 경우 인접 규칙 미적용).
 */
@Repository
public class AdjacencyRepository {

    private final Map<String, Set<String>> neighborsByKey;

    public AdjacencyRepository(ObjectMapper objectMapper) {
        String json = ResourceUtil.readClasspath("static/data/adjacency.json");
        try {
            List<Adjacency> list = objectMapper.readValue(json, new TypeReference<List<Adjacency>>() {});
            Map<String, Set<String>> m = new HashMap<>();
            for (Adjacency a : list) {
                m.put(a.key(), a.neighbors() == null ? Set.of() : Set.copyOf(a.neighbors()));
            }
            this.neighborsByKey = Collections.unmodifiableMap(m);
        } catch (Exception e) {
            throw new IllegalStateException("adjacency.json 로딩 실패: " + e.getMessage(), e);
        }
    }

    public Set<String> neighborsOf(String key) {
        return neighborsByKey.getOrDefault(key, Set.of());
    }

    public boolean hasAnyData() {
        return !neighborsByKey.isEmpty();
    }
}

