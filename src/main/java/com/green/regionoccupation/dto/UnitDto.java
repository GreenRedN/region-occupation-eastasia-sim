package com.green.regionoccupation.dto;

/**
 * UI 노출용 유닛 DTO.
 */
public record UnitDto(
        String id,
        String ownerId,
        String regionKey,        // 주둔 중이면 값, 이동 중이면 null
        String transitFromKey,
        String transitToKey,
        int remainingDays,
        int soldiers
) {}
