# Region Occupation Simulator — East Asia (ADMIN1) 🗺️⚔️  
## v3 (Fog/첩보/민심)


동아시아(중국/일본/한국/북한/대만/몽골) **광역 행정구역(ADMIN1)** 지도를 캔버스로 띄우고,
지역을 클릭해 **점령(소유자 변경)** 을 시각화하거나 **턴제 점령 게임(유저 vs AI)** 을 진행하는 로컬 웹 앱입니다.

- **Tech:** Java 21 · Spring Boot · Thymeleaf · Vanilla JS(Canvas)
- **Port:** `8081` (기본값, `application.properties`)

---

## 기능 요약 ✨

### 1) 자유 점령 모드 (Free Occupation)
- 지도에서 지역 클릭 → 점령 주체 선택 → 해당 색으로 점령 표시
- 점령 상태는 서버 인메모리로 유지(서버 재시작 시 초기화)

### 2) 턴제 모드 (Turn-based: USER(A) vs AI)
- 시작 지역 1개를 선택해 게임 시작
- 주요 액션: 공격/이동/해상 이동/시설 건설/모집/턴 넘김
- 공격/이동은 **인접 데이터(`adjacency.json`)** 를 기반으로 제한

### 3) 클릭 판정 방식 (Pixel ID Map)
폴리곤(GIS) 대신 **픽셀 ID 맵(PNG)** 을 사용합니다.
- 클릭한 픽셀의 RGB → ID 변환 → 지역 키 매핑 → 해당 지역을 “선택/점령” 처리

---

## 빠른 실행 🚀

### 요구사항
- Java 21+

### 실행
```bash
./mvnw spring-boot:run
```

> 만약 GitHub 웹 업로드 때문에 `.mvn/` 폴더가 누락되어 `mvnw`가 동작하지 않으면, 로컬에 Maven이 설치된 경우 아래로도 실행 가능합니다:

```bash
mvn spring-boot:run
```

브라우저 접속:
- `http://localhost:8081/`
- `http://localhost:8081/map`

> 참고: `server.port`는 `src/main/resources/application.properties`에서 변경할 수 있습니다.

---

## 콘솔 데모 & 테스트 🧪

### 1) 콘솔 데모(서버 없이 규칙 빠르게 확인)
```bash
./mvnw -q exec:java
```

(대체)
```bash
mvn -q exec:java
```

### 2) JUnit 테스트
```bash
./mvnw -q test
```

(대체)
```bash
mvn -q test
```

---

## 데이터 파일 🗂️
정적 데이터: `src/main/resources/static/data/`
- `regions.json` : 지역 메타(키/표시명/국가 등)
- `owners.json` : 점령 주체(색상 포함)
- `adjacency.json` : 지상 인접 그래프(공격/이동 전제)
- `region_stats.json` : 지역 스탯(자원/생산 등)
- `sea_zones.json` : 해상 권역(표기/규칙 보조)

---

## 문서 📄
- `docs/00_EXEC_SUMMARY.md` : 실행/요약
- `docs/01_RULEBOOK.md` : 규칙서(코드와 대응)
- `docs/02_DATA_DICTIONARY.md` : 데이터 사전
- `docs/03_SCENARIOS.md` : 시나리오
- `docs/04_DECISION_TRACE.md` : 의사결정 근거

---

## 폴더 구조 한눈에 보기 👀
- `src/main/java/com/green/regionoccupation/`
  - `controller/` : 점령/인접/게임 API
  - `service/` : 턴제 규칙/지리 규칙/점령 로직
  - `repository/` : JSON 로딩 + 인메모리 상태
  - `runner/ConsoleRunner.java` : 콘솔 데모 엔트리
- `src/main/resources/templates/` : `map.html`
- `src/main/resources/static/` : `js/`, `css/`, `data/`

---

## GitHub 웹 업로드 팁 💡
웹 업로드를 쓸 경우, `.mvn/` 같은 **점(.) 폴더가 숨김 처리**되어 누락될 수 있습니다.
- Windows 탐색기에서 **숨김 항목 표시**를 켠 뒤 업로드하거나,
- 가장 확실하게는 **Git push**로 올리면 누락 없이 업로드됩니다.

---

## License
MIT License. See `LICENSE`.


---

## v3에서 적용된 UI/규칙 ✅

- **Bottom Info Panel**: 지도 클릭 시 하단 패널에 지역 정보 표시(FOG 적용)
- **Fog of War(간소)**: 내 영토 + 인접 지역만 '보임', 그 외는 **마지막 관측**(있으면 `~값`) 또는 `???`
- **첩보(SPY)**: 선택한 target 지역을 관측해 **마지막 관측 정보**로 저장
- **민심/반란(간소)**: support에 따라 생산량 보정(v2.1 표) + support<20이면 25% 확률로 중립화
