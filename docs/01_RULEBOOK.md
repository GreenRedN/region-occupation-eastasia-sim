# Rulebook

본 문서는 **현재 코드에 존재하는 규칙만**을 적습니다.

## 1) 엔티티

### 지역(Region)
- 데이터: `src/main/resources/static/data/regions.json`
- 키: `regionKey` (예: `CN:Province:Anhui:6738f8d1`)

### 점령자(Owner)
- `A` = USER
- `B..F` = AI (시작 시 `aiCount`만큼 생성)
- `NEUTRAL` = 미점령

### 유닛(Unit)
- 필드
  - `ownerId`: 소속 세력
  - `soldiers`: 병력 수치(최소 1)
  - `regionKey`: 주둔 지역(해상 이동중이면 `null`)
  - `transitFromKey`, `transitToKey`, `remainingDays`: 해상 이동 상태값

### 시설(BuildingType)
- `ADMIN`: 행정 (자원 생산 +20% 보너스)
- `MILITARY`: 군사 (공격/방어 +10% 보너스, 모집 가능)
- `PORT`: 항구 (해상 이동을 가능하게 함)
- `WALL`: 성벽 (방어 +40% 보너스)

## 2) 턴 / 시간
- `TURN_DAYS = 10`
- `turn`이 1 증가할 때마다 `day += 10`

## 3) 액션(턴 중 선택)

### PASS
- 아무 행동 없이 턴 종료.

### BUILD
- 내 점령지(`fromKey`)에 시설 1개 건설.
- 제한: **세력별 턴당 1개**.
- `PORT`는 **해안 지역에서만** 건설 가능.

### RECRUIT
- 내 점령지(`fromKey`)에서 병력 모집.
- 전제: 해당 지역에 `MILITARY` 시설이 있어야 함.
- 비용: 총 자원 합계가 `RECRUIT_COST(50)` 이상 필요.
- 효과: 해당 지역의 내 유닛 1개에 병력 `+RECRUIT_GAIN(80)`.

### ATTACK (지상 공격)
- 조건
  - `fromKey`는 내 점령지
  - `toKey`는 `fromKey`의 인접 지역
  - `fromKey`에 내 유닛이 있어야 함
- 결과
  - **중립(`NEUTRAL`) 지역**: 즉시 점령. (확률/민병대/전투 손실 없음)
  - 타 세력 점령지: 아래 “전투 판정” 규칙 적용

### NAVAL_MOVE (해상 이동)
- 조건
  - 출항지 `fromKey`: 내 점령지 + 해안 + `PORT` 보유
  - 도착지 `toKey`: 해안 (세력 소유 여부 무관)
- 이동 시간(일)
  - 해상 권역 hop 수로 계산(최단거리 BFS)
  - `days = (hop + 1) * daysPerHop` (현재 daysPerHop=10)
- 도착 처리
  - 도착지가 중립이면 즉시 점령.
  - 도착지가 타 세력이면 “상륙 전투”가 즉시 발생:
    - 성공: 점령
    - 실패: 유닛 파괴(간소 처리)

## 4) 전투 판정

### 공격력(Attack)
- 기본: `attacker.soldiers`
- 출발지에 `MILITARY`가 있으면 `+10%` (곱연산)

### 방어력(Defense)
- 해당 지역에 방어 세력 유닛이 있으면: 그 병력 합
- 방어 유닛이 없고, 지역이 “점령지”이면: 기본 수비대 `BASE_GARRISON = 60`
- 방어 유닛이 없고, 지역이 “중립”이면: 0 (중립은 전투 없이 즉시 점령이 기본)

### 방어 보정
- 방어측 지역에 `MILITARY`가 있으면 `+10%` (곱연산)
- 방어측 지역에 `WALL`이 있으면 `+40%` (곱연산)

### 승패
- `ratio = Attack / EffectiveDefense`
- `ratio >= 2.0` → 점령 성공
- `ratio < 2.0` → 수비 성공

### 손실(간소)
- 점령 성공(타 세력 상대로): 공격 유닛 병력 10% 감소
- 지상 공격 실패: 공격 유닛 병력 25% 감소
- 상륙 공격 실패: 유닛 파괴(제거)

### 유닛 위치
- 점령 성공 시 공격 유닛은 대상 지역으로 이동.

## 5) 자원 생산
- 각 점령지는 “대표 자원 1종”을 생산함 (`static/data/region_stats.json`).
- 턴 종료 시, 점령지마다 `yieldPerTurn`만큼 해당 자원을 세력 자원 풀에 누적.
- `ADMIN` 시설이 있으면 해당 지역 생산량 +20% 추가.

## 6) 게임 종료
- 모든 지역이 중립이 아니게 되면(전 지역 점령) 종료.
- 종료 시: 영토 수가 가장 많은 세력이 승자. 동률이면 무승부.
