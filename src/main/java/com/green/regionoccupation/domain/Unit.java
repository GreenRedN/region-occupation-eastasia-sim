package com.green.regionoccupation.domain;

import java.util.Objects;

/**
 * 유닛(병력) 상태.
 *
 * - stationed: regionKey != null
 * - transit:   regionKey == null, transitToKey != null, remainingDays > 0
 */
public final class Unit {
    private final String id;
    private final String ownerId;

    private String regionKey;            // 주둔 지역(이동 중이면 null)
    private String transitFromKey;       // 이동 출발지
    private String transitToKey;         // 이동 목적지
    private int remainingDays;           // 남은 이동 일수

    private int soldiers;                // 병력 수(단순 수치)

    public Unit(String id, String ownerId, String regionKey, int soldiers) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.regionKey = Objects.requireNonNull(regionKey);
        // 시작 병력 0을 허용해야 '군사시설 → 모집 → 파견' 루프가 성립한다.
        // (기존에는 Math.max(1, ...)로 인해 0을 넣어도 1로 강제되어 버그처럼 보였음)
        this.soldiers = Math.max(0, soldiers);
    }

    public String id() { return id; }
    public String ownerId() { return ownerId; }

    public boolean isInTransit() { return regionKey == null; }

    public String regionKey() { return regionKey; }
    public String transitFromKey() { return transitFromKey; }
    public String transitToKey() { return transitToKey; }
    public int remainingDays() { return remainingDays; }

    public int soldiers() { return soldiers; }

    public void addSoldiers(int delta) {
        soldiers = Math.max(0, soldiers + delta);
    }

    public void moveTo(String newRegionKey) {
        this.regionKey = Objects.requireNonNull(newRegionKey);
        this.transitFromKey = null;
        this.transitToKey = null;
        this.remainingDays = 0;
    }

    public void startTransit(String fromKey, String toKey, int days) {
        this.regionKey = null;
        this.transitFromKey = Objects.requireNonNull(fromKey);
        this.transitToKey = Objects.requireNonNull(toKey);
        this.remainingDays = Math.max(1, days);
    }

    public boolean tickDays(int days) {
        if (!isInTransit()) return false;
        remainingDays -= Math.max(0, days);
        return remainingDays <= 0;
    }

    public void arrive() {
        if (!isInTransit()) return;
        this.regionKey = this.transitToKey;
        this.transitFromKey = null;
        this.transitToKey = null;
        this.remainingDays = 0;
    }
}
