package com.green.regionoccupation.dto;

public record RegionDto(
        String key,
        String iso2,
        String type,
        String nameKo,
        String nameEn,
        String displayKo,
        String displayEn
) {
}

