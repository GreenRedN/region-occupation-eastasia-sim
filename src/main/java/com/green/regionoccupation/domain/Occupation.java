package com.green.regionoccupation.domain;

/**
 * 점령 상태(저장 단위는 regionKey).
 */
public record Occupation(String regionKey, String ownerId) {
}

