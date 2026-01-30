# Exec Summary

동아시아(ADMIN1) 행정구역을 대상으로 한 **턴제 지역 점령 시뮬레이터**입니다.

## 평가 포인트(기업 관점)
- 규칙 문서: `docs/01_RULEBOOK.md`
- 자동 검증: `./mvnw test`
- 콘솔 데모: `./mvnw exec:java`

## 핵심 룰(요약)
- **시간**: 1턴 = 10일
- **지상 공격**: 인접 지역만 공격 가능 (`static/data/adjacency.json`)
- **해상 이동**: 항구(PORT) + 해안 지역에서만 가능, 해상 권역 hop 수에 따라 여러 턴 소요
- **중립 점령**: 확률/민병대 없이 **즉시 점령**
- **전투 판정**: `ratio = Attack / EffectiveDefense`
  - `ratio >= 2.0` → 점령 성공
  - `ratio < 2.0` → 수비 성공
- **성벽(WALL)**: 방어력 +40%
- **시설**: 레벨 없음(있/없), 세력별 **턴당 1개**만 건설 가능
- **자원/인구**: 지역당 대표 자원 1종 + 인구 스탯을 보유하며, 점령지에서 턴마다 자원 생산

## 빠른 확인 방법
1) 테스트로 규칙 확인: `./mvnw test`
2) 콘솔 데모 실행: `./mvnw exec:java`
3) 웹 UI 확인(선택): `./mvnw spring-boot:run` 후 `http://localhost:8081/map`
