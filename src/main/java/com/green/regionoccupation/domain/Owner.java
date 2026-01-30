package com.green.regionoccupation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 점령 세력(플레이어/AI/중립)
 */
public record Owner(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("color") int[] color
) {
}

