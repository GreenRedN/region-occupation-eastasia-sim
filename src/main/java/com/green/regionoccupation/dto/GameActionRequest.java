package com.green.regionoccupation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 턴제 게임 액션 요청(유저 전용).
 *
 * type:
 * - "MOVE"          : 육상 이동(fromKey,toKey)  (인접)
 * - "NAVAL_MOVE"    : 해상 이동(fromKey,toKey)  (항구 필요 / 날짜 소모)
 * - "ATTACK"        : 공격(fromKey,toKey)        (유닛 필요)
 * - "BUILD_FACILITY": 시설 건설(fromKey, facility)
 * - "RECRUIT"       : 병력 증원(fromKey)
 * - "PASS"          : 턴 넘김
 *
 * unitId:
 * - 해당 지역에 유저 유닛이 여러 개 있을 때 지정(없으면 첫 유닛 자동 선택)
 */
public record GameActionRequest(
        @NotBlank String type,
        String fromKey,
        String toKey,
        String facility,
        String unitId,
        Integer soldiers
) {}
