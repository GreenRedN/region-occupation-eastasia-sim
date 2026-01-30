# 🚩 Region Occupation Simulator (East Asia)

> [cite_start]**"지도 데이터 + 규칙 엔진"이 결합된 웹 기반 지역 점령 시뮬레이터**

[cite_start]동아시아 6개국(한국, 북한, 일본, 중국, 대만, 몽골) 154개 지역을 배경으로 펼쳐지는 땅따먹기 시뮬레이션입니다.
[cite_start]가볍게 판도를 그리는 **자유 점령 모드**와, AI와 경쟁하며 천하통일을 노리는 **턴제 게임 모드**를 제공합니다.

## 🛠 Tech Stack

* [cite_start]**Server:** Spring Boot (Port: 8081)
* [cite_start]**Client:** HTML5 Canvas + Pure JS (단일 화면 지도 UI)
* [cite_start]**Database:** None (In-memory 상태 관리, 서버 재시작 시 초기화)

---

## 🎮 Game Modes

### 1. 자유 점령 (Paint Mode)
[cite_start]전략 시뮬레이션의 판도 구상이나 설정을 위한 샌드박스 모드입니다.
* [cite_start]**기능:** 지도 클릭 시 선택한 세력 색상으로 즉시 변경
* [cite_start]**특징:** 복잡한 룰 없이 현황판처럼 사용 가능

### 2. 턴제 게임 (Turn-based Strategy)
[cite_start]유저 1명과 AI(1~5 세력)가 경쟁하는 전략 게임 모드입니다.
* [cite_start]**목표:** 154개 전체 지역 100% 점령 (중립 지역이 없어야 승리)
* **진행:** 1턴 = 10일 경과. [cite_start]`PASS` 버튼으로 턴을 넘기면 생산/전투/AI 행동이 처리됩니다.

---

## ⚔️ Core Mechanics (Rules)

기획서에 명시된 핵심 게임 규칙입니다.

### 🗺️ Action (액션)
* [cite_start]**이동/점령 (MOVE):** 인접한 지역으로 이동하며, 중립 지역은 즉시 점령합니다.
* **공격 (ATTACK):** 적 지역으로 이동 시 전투가 발생합니다. (공격력/방어력 비율 2.0 이상 시 승리)
* [cite_start]**해상 이동 (NAVAL):** 항구(PORT)가 있는 해안 지역끼리 이동 가능하며, 상륙전 실패 시 유닛은 파괴됩니다.
* **시설 건설 (BUILD):** 금화를 소모하여 내정/군사 시설을 짓습니다. (턴당 1개 제한)
* [cite_start]**징병 (RECRUIT):** MILITARY 시설이 있는 곳에서 인구 비례로 병력을 모집합니다.
* [cite_start]**첩보 (SPY):** 전장의 안개(Fog of War) 지역을 정찰하여 정보를 갱신합니다.

### 🏗️ Facilities (시설)
* [cite_start]**ADMIN:** 민심 대폭 상승 (+10)
* [cite_start]**ECONOMY:** 민심/안정 상승 및 금화 생산량 보정
* [cite_start]**MILITARY:** 안정도 상승, 징병 가능, 전투 시 공/방 보정
* [cite_start]**PORT:** 해상 이동 가능
* [cite_start]**WALL:** 방어력 1.4배 증가

### 💰 Economy & Tech (경제/기술)
* **자원:** `GOLD` 단일 자원. [cite_start]지역 생산량과 민심에 따라 수급됩니다.
* **기술:** 점령지 평균 민심 90 이상일 때 기술 포인트 획득. (TAX, STEEL, CRYPTO 연구 가능)

---

## 🚀 How to Run

1.  Repository를 Clone 합니다.
2.  Spring Boot 프로젝트를 실행합니다.
3.  [cite_start]브라우저에서 `http://localhost:8081/map` 로 접속합니다.
4.  **자유 점령** 혹은 **턴제 게임** 모드를 선택하여 시작합니다.

> **Note:** 별도의 DB 설치가 필요 없습니다. [cite_start]모든 데이터는 서버 메모리에서 관리되므로 서버 종료 시 진행 상황은 리셋됩니다. [cite: 8, 232]
