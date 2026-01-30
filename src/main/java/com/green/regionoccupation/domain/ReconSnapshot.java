package com.green.regionoccupation.domain;

import java.util.Map;
import java.util.Set;

/**
 * Fog of War / 첩보 결과로 저장되는 '마지막 관측 스냅샷'.
 * - viewerOwnerId 기준으로 저장된다.
 * - 수치(병력 등)는 방첩에 의해 '오염된 정보'가 될 수 있다.
 */
public record ReconSnapshot(
        int daySeen,
        Integer support,
        Integer stability,
        Map<String, Integer> unitsByOwnerId,
        Set<BuildingType> facilities
) {}
