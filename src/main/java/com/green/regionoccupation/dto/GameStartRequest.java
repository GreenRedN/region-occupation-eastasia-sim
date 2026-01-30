package com.green.regionoccupation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 턴제 게임 시작 요청.
 *
 * - userHomeKey: 유저 시작 지역(필수)
 * - aiCount: AI 수(0~5). null이면 1.
 * - aiHomeKeys: AI 시작 지역(선택). 비워두면 서버가 랜덤 선택.
 */
public record GameStartRequest(
        @NotBlank String userHomeKey,
        @Min(0) @Max(5) Integer aiCount,
        List<String> aiHomeKeys
) {}
