package com.green.regionoccupation.domain;

/**
 * 시설(건물) 타입.
 *
 * ✅ 레벨 시스템은 사용하지 않는다. (시설은 '있다/없다'만)
 * ✅ 한 턴에 한 번만 건설 가능(턴 서비스에서 제어)
 */
public enum BuildingType {
    /** 행정(민심 + 자원 계열) */
    ADMIN,
    /** 경제(금화 생산 + 민심 보정) */
    ECONOMY,
    /** 군사(모병/주둔, 전투 보정) */
    MILITARY,
    /** 항구(해상 이동/상륙의 전제) */
    PORT,
    /** 성벽(방어 보정) */
    WALL
}
