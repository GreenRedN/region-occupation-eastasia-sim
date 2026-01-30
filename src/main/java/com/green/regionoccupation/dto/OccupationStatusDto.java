package com.green.regionoccupation.dto;

import java.util.Map;

/**
 * 현재 점령 상태(단순).
 * - occupation: { regionKey -> ownerId }
 */
public record OccupationStatusDto(
        Map<String, String> occupation,
        int totalRegions,
        int occupiedRegions
) {
}

