package com.green.regionoccupation.domain;

/**
 * 지도 좌표(라벨 기준점).
 *
 * - 실제 km가 아니라, 프론트 캔버스(라벨 포인트) 좌표계 기준.
 * - 해상 이동 소요일을 "거리 기반"으로 근사 계산할 때 사용.
 */
public record RegionCoord(String key, double x, double y) {
}
