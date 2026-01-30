package com.green.regionoccupation.domain;

/**
 * 지역(ADMIN1) 스탯(간소 버전).
 *
 * - 지역당 대표 자원 1종 + 인구
 * - mobility/support/stability는 기본값으로 두되, 전투/이동/통치 확장에 바로 연결 가능
 */
public record RegionStat(
        String key,
        ResourceType resource,
        int yieldPerTurn,
        long population,
        int mobility,   // 1~10
        int support,    // 0~100
        int stability   // 0~100
) {}
