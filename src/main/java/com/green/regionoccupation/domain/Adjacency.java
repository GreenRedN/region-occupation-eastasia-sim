package com.green.regionoccupation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 인접 데이터.
 * - adjacency.json: [{ "key": "...", "neighbors": ["...", "..."] }, ...]
 * 현재 동아시아 실습 버전에서는 adjacency.json이 비어 있어도 동작하도록 설계.
 */
public record Adjacency(
        @JsonProperty("key") String key,
        @JsonProperty("neighbors") List<String> neighbors
) {
}

