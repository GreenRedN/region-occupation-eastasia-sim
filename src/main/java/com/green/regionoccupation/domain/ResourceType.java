package com.green.regionoccupation.domain;

/**
 * 지역 대표 자원 타입.
 * - "너무 많으면" 부담이 커지므로, 지역당 대표 자원 1개만 둔다.
 */
public enum ResourceType {
    FOOD,
    ENERGY,
    METAL,
    WOOD,
    GOLD
}
