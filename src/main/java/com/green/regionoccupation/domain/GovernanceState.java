package com.green.regionoccupation.domain;

/**
 * 통치 상태(가변).
 * - RegionStat의 초기 support/stability 값을 복사해 게임 중 변화한다.
 */
public class GovernanceState {
    private int support;   // 0~100
    private int stability; // 0~100

    public GovernanceState(int support, int stability) {
        this.support = clamp0_100(support);
        this.stability = clamp0_100(stability);
    }

    public int support() { return support; }
    public int stability() { return stability; }

    public void setSupport(int v) { this.support = clamp0_100(v); }
    public void setStability(int v) { this.stability = clamp0_100(v); }

    private static int clamp0_100(int v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }
}
