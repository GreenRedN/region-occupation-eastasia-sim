package com.green.regionoccupation.dto;

import com.green.regionoccupation.domain.BuildingType;

import java.util.Map;
import java.util.Set;

/**
 * 클릭한 지역 정보(FOG 적용).
 * - visible=false 이면, units/facilities/support/stability는 '마지막 관측'이거나 null일 수 있다.
 */
public record RegionInspectDto(
        String regionKey,
        String iso2,
        String nameKo,
        String nameEn,

        String ownerId,

        String resource,     // ResourceType name
        int yieldPerTurn,
        long population,

        boolean visible,
        Integer lastSeenDay,

        Integer support,
        Integer stability,

        Map<String, Object> unitsByOwnerId,  // Integer or String ("???", "~60")
        Set<BuildingType> facilities
) {}
