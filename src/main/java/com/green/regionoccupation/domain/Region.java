package com.green.regionoccupation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 점령 가능한 최소 단위(ADMIN1) 지역 메타.
 * - regions.json(동아시아)에서 로딩
 */
public record Region(
        @JsonProperty("key") String key,
        @JsonProperty("iso2") String iso2,
        @JsonProperty("type") String type,
        @JsonProperty("name_ko") String nameKo,
        @JsonProperty("name_en") String nameEn,
        @JsonProperty("display_ko") String displayKo,
        @JsonProperty("display_en") String displayEn,
        @JsonProperty("hex") String hex
) {
}

