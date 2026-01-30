package com.green.regionoccupation.dto;

import com.green.regionoccupation.domain.BuildingType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 턴제 게임 상태 응답.
 *
 * winnerOwnerId: "A"|"B"|"C"|"D"|...|null(동률)
 *
 * turn: '완전한 1턴' = 유저 1회 행동 + (AI 수만큼) AI 행동
 */
public record GameStateDto(
        boolean started,
        boolean finished,
        String winnerOwnerId,
        int turn,
        String current, // "USER" (AI는 자동)

        int day,        // 날짜(0부터 누적), 1턴=10일
        int turnDays,

        String userOwnerId,
        List<String> aiOwnerIds,
        String userHomeKey,
        Map<String, String> aiHomeByOwnerId,

        boolean adjacencyEnabled,

        int totalRegions,
        int neutralRegions,
        Map<String, Integer> territoriesByOwnerId,

        // 기존 호환(남겨둠)
        List<String> portRegions,
        Map<String, String> occupation,
        List<String> log,

        // 신규
        Map<String, Set<BuildingType>> facilitiesByRegion,
        List<UnitDto> units,
        Map<String, Map<String, Integer>> resourcesByOwner,

        // 편의 필드: 금화/기술점수
        Map<String, Integer> goldByOwner,
        Map<String, Integer> techPointsByOwner,
        Map<String, Set<String>> unlockedTechByOwner
) {}
