# Data Dictionary

## 1) 정적 데이터(JSON)

### regions.json
- 경로: `src/main/resources/static/data/regions.json`
- 설명: 지역(ADMIN1) 메타 데이터
- 용도: 지도 표시, 지역 이름 표시, regionKey 기준으로 다른 데이터와 조인

### region_stats.json
- 경로: `src/main/resources/static/data/region_stats.json`
- 설명: 지역별 스탯(인구/대표 자원/턴당 생산량)
- 용도: 턴 종료 시 자원 생산 계산

### owners.json
- 경로: `src/main/resources/static/data/owners.json`
- 설명: 점령자(세력) 메타(표시 이름/색)

### adjacency.json
- 경로: `src/main/resources/static/data/adjacency.json`
- 설명: 지상 인접 그래프
- 용도: ATTACK 시 fromKey→toKey 인접성 검증

### sea_zones.json
- 경로: `src/main/resources/static/data/sea_zones.json`
- 설명: 해상 권역(표기용)
- 용도: 이동 규칙(해상 hop)은 `GeoRuleService`의 권역/그래프 상수로 계산

## 2) 런타임 상태(인메모리)

- `OccupationStateRepository`: regionKey → ownerId
- `BuildingStateRepository`: regionKey → 건설된 시설 집합
- `TurnGameService.resourcesByOwner`: ownerId → 자원 보유량(Map)
- `UnitStateRepository`: unitId → 유닛 상태
- `TurnGameService`: 턴(day/turn) 진행 및 액션 처리
