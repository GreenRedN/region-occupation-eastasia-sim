package com.green.regionoccupation.service;

import com.green.regionoccupation.domain.BuildingType;
import com.green.regionoccupation.domain.RegionStat;
import com.green.regionoccupation.domain.ResourceType;
import com.green.regionoccupation.domain.Unit;
import com.green.regionoccupation.dto.GameActionRequest;
import com.green.regionoccupation.dto.GameStartRequest;
import com.green.regionoccupation.dto.GameStateDto;
import com.green.regionoccupation.dto.UnitDto;
import com.green.regionoccupation.repository.AdjacencyRepository;
import com.green.regionoccupation.repository.BuildingStateRepository;
import com.green.regionoccupation.repository.OccupationStateRepository;
import com.green.regionoccupation.repository.RegionMetaRepository;
import com.green.regionoccupation.repository.RegionStatRepository;
import com.green.regionoccupation.repository.UnitStateRepository;
import com.green.regionoccupation.domain.GovernanceState;
import com.green.regionoccupation.domain.ReconSnapshot;
import com.green.regionoccupation.dto.RegionInspectDto;
import com.green.regionoccupation.repository.GovernanceStateRepository;
import com.green.regionoccupation.repository.ReconStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 턴제 점령 시뮬레이터(간소 버전).
 *
 * ✅ 이번 버전의 확정 요구
 * - 레벨 시스템 없음: 시설은 "있/없"만
 * - 한 턴에 시설 1개만 건설 가능
 * - 유닛이 존재하며, 유닛은 턴마다 이동/공격
 * - 해상은 권역 hop + 날짜 시스템(1턴=10일)로 원거리 즉시 공격을 막는다
 * - 자원/인구 추가(지역당 대표 자원 1종)
 */
@Service
public class TurnGameService {

    // ✅ 유저 세력(요청: B)
    private static final String USER_OWNER = "B";
    private static final String NEUTRAL_OWNER = "NEUTRAL";
    private static final int TURN_DAYS = 10;
    // ===== 준비기간(내정 턴) =====
    // "시작하자마자 점령"을 막기 위해, 게임 시작 후 N턴 동안은 전투/해상 이동을 금지한다.
    // 기획서 기준: "준비턴" 없이 바로 진행
    private static final int PREP_TURNS = 0;


    // 전투/시설 수치(사용자 지정 범위 내)
    private static final double WALL_DEF_BONUS = 0.40; // 40% (요청 범위: 30~50)
    private static final double TAKEOVER_THRESHOLD = 2.0; // 공격이 방어의 2배면 점령
    // 시작 병력은 0 (요청 반영): 초반 확장은 '군사시설 + 모집' 이후에만 가능
    private static final int START_SOLDIERS = 0;
    // ===== 모집(인구 기반) =====
    // "지역 인구의 10%"를 컨셉으로 하되, 게임 밸런스를 위해 '병력 포인트'로 스케일링한다.
    private static final double RECRUIT_POP_RATIO = 0.10;   // 10%
    private static final int RECRUIT_SCALE = 10_000;        // 인구 1만당 1 포인트(10% 적용 후)
    private static final int RECRUIT_MIN = 15;
    private static final int RECRUIT_MAX = 120;
    private static final int RECRUIT_GOLD_PER_POINT = 2;    // 모집 1포인트당 금화 비용
    private static final int BASE_GARRISON = 60; // 주둔 유닛이 없을 때(점령지) 기본 수비대

    // ===== 금화 기반 건설비 =====
    private static final Map<BuildingType, Integer> BUILD_COST_GOLD = Map.of(
            // 유저 룰: 경제시설(30) → 군사시설(50)로 성장 곡선을 만든다.
            BuildingType.ADMIN, 30,
            BuildingType.ECONOMY, 30,
            BuildingType.MILITARY, 50,
            BuildingType.PORT, 30,
            BuildingType.WALL, 30
    );

    // ===== 기술(Tech Points) =====
    private static final int TECH_GAIN_SUPPORT_THRESHOLD = 90; // 민심 90% 이상이면 턴당 +1
    private static final int TECH_SUPPORT_THRESHOLD = TECH_GAIN_SUPPORT_THRESHOLD;
    private static final int TECH_PER_TURN = 1;
    private static final Map<String, TechDef> TECH_DEFS = Map.of(
            "TAX", new TechDef("TAX", "조세 제도", 5, "금화 생산 +10%"),
            "STEEL", new TechDef("STEEL", "강철 군수", 5, "공격력 +10%"),
            "CRYPTO", new TechDef("CRYPTO", "암호 통신", 5, "첩보 오차 제거")
    );

    private record TechDef(String code, String name, int cost, String description) {}


    private final OccupationStateRepository occupationStateRepository;
    private final BuildingStateRepository buildingStateRepository;
    private final UnitStateRepository unitStateRepository;
    private final RegionMetaRepository regionMetaRepository;
    private final RegionStatRepository regionStatRepository;
    private final AdjacencyRepository adjacencyRepository;
    private final GeoRuleService geoRuleService;
    private final GovernanceStateRepository governanceStateRepository;
    private final ReconStateRepository reconStateRepository;

    // ===== game state =====
    private boolean started = false;
    private boolean finished = false;
    private String winnerOwnerId = null;

    private int turn = 0; // full turns
    private int day = 0;  // accumulative day = turn*10

    private final List<String> aiOwnerIds = new ArrayList<>();

    // v3: 방첩 레벨(0~3). 문서에 기본값이 없어서, AI는 1, 유저는 0으로 초기화.
    private final Map<String, Integer> counterIntelByOwnerId = new HashMap<>();
    private String userHomeKey = null;
    private final Map<String, String> aiHomeByOwnerId = new LinkedHashMap<>();

    private final List<String> log = new ArrayList<>();

    // 턴당 시설 1개 제한(세력별)
    private final Set<String> builtThisTurnOwners = new HashSet<>();

    // 한 턴(유저 턴) 동안, 동일 유닛이 여러 번 행동하는 것을 방지
    private final Set<String> actedUnitIdsThisTurn = new HashSet<>();

    // 세력 자원 풀(대표 자원 1종이 지역마다 다르므로, 세력은 타입별로 누적)
    private final Map<String, EnumMap<ResourceType, Integer>> resourcesByOwner = new LinkedHashMap<>();

    // ===== 기술 상태 =====
    private final Map<String, Integer> techPointsByOwner = new LinkedHashMap<>();
    private final Map<String, Set<String>> unlockedTechByOwner = new LinkedHashMap<>();
    // 기술 효과(간소)
    private final Map<String, Double> goldBonusByOwner = new HashMap<>();   // TAX
    private final Map<String, Double> attackBonusByOwner = new HashMap<>(); // STEEL

    public TurnGameService(
            OccupationStateRepository occupationStateRepository,
            BuildingStateRepository buildingStateRepository,
            UnitStateRepository unitStateRepository,
            RegionMetaRepository regionMetaRepository,
            RegionStatRepository regionStatRepository,
            AdjacencyRepository adjacencyRepository,
            GeoRuleService geoRuleService,
            GovernanceStateRepository governanceStateRepository,
            ReconStateRepository reconStateRepository
    ) {
        this.occupationStateRepository = occupationStateRepository;
        this.buildingStateRepository = buildingStateRepository;
        this.unitStateRepository = unitStateRepository;
        this.regionMetaRepository = regionMetaRepository;
        this.regionStatRepository = regionStatRepository;
        this.adjacencyRepository = adjacencyRepository;
        this.geoRuleService = geoRuleService;
        this.governanceStateRepository = governanceStateRepository;
        this.reconStateRepository = reconStateRepository;
    }

    // =========================
    // Public API
    // =========================

    /**
     * 자유 점령(OccupationService)에서 "턴제 게임 진행 중" 여부를 판단할 때 사용.
     */
    public boolean isRunning() {
        return started && !finished;
    }

    public GameStateDto state() {
        return buildStateDto();
    }

    /**
     * 클릭한 지역의 정보(FOG 적용).
     * - visible=false 이면, 마지막 관측 스냅샷(있다면)만 제공한다.
     */
    public RegionInspectDto inspect(String regionKey) {
        if (!StringUtils.hasText(regionKey)) throw new IllegalArgumentException("regionKey required");

        var r = regionMetaRepository.getByKey(regionKey);
        if (r == null) throw new IllegalArgumentException("unknown regionKey: " + regionKey);

        String owner = occupationStateRepository.getOwnerId(regionKey);
        if (owner == null) owner = NEUTRAL_OWNER;

        boolean visible = isVisibleTo(USER_OWNER, regionKey);

        RegionStat stat = regionStatRepository.getByKey(regionKey);
        GovernanceState g = governanceStateRepository.getOrCreate(regionKey, stat.support(), stat.stability());

        Integer lastSeenDay = null;
        Integer support = null;
        Integer stability = null;
        Map<String, Object> unitsOut = null;
        Set<BuildingType> facilitiesOut = null;

        if (visible) {
            support = g.support();
            stability = g.stability();
            unitsOut = new LinkedHashMap<>();
            Map<String, Integer> units = unitsAt(regionKey);
            for (Map.Entry<String, Integer> e : units.entrySet()) unitsOut.put(e.getKey(), e.getValue());
            facilitiesOut = buildingStateRepository.getSetOrEmpty(regionKey);
            // visible 지역은 recon도 최신으로 갱신
            reconStateRepository.put(USER_OWNER, regionKey,
                    new ReconSnapshot(day, support, stability, units, facilitiesOut));
        } else {
            ReconSnapshot snap = reconStateRepository.get(USER_OWNER, regionKey);
            if (snap != null) {
                lastSeenDay = snap.daySeen();
                support = snap.support();
                stability = snap.stability();
                facilitiesOut = snap.facilities();
                unitsOut = new LinkedHashMap<>();
                if (snap.unitsByOwnerId() != null) {
                    for (Map.Entry<String, Integer> e : snap.unitsByOwnerId().entrySet()) {
                        // 마지막 관측값은 "~"로 표시 (UX: "마지막 관측")
                        unitsOut.put(e.getKey(), "~" + e.getValue());
                    }
                }
            } else {
                // 완전 미관측
                unitsOut = Map.of("???", "???");
                facilitiesOut = Set.of();
            }
        }

        return new RegionInspectDto(
                regionKey,
                r.iso2(),
                r.nameKo(),
                r.nameEn(),
                owner,
                stat.resource().name(),
                stat.yieldPerTurn(),
                stat.population(),
                visible,
                lastSeenDay,
                support,
                stability,
                unitsOut,
                facilitiesOut
        );
    }


    public GameStateDto start(GameStartRequest request) {
        Objects.requireNonNull(request);

        resetInternal();

        String home = request.userHomeKey();
        if (!StringUtils.hasText(home)) throw new IllegalArgumentException("userHomeKey is required");
        regionMetaRepository.getByKey(home); // validate exists

        // owners.json에 정의된 세력 풀(NEUTRAL 제외). 유저(B)를 제외한 나머지를 AI로 사용.
        List<String> ownerPool = new ArrayList<>(List.of("A", "B", "C", "D", "E", "F"));
        ownerPool.remove(USER_OWNER);
        int aiCount = Math.max(0, Math.min(ownerPool.size(), request.aiCount()));

        started = true;
        finished = false;
        winnerOwnerId = null;

        userHomeKey = home;
        occupationStateRepository.occupy(home, USER_OWNER);
        log.add("START: USER home=" + shortName(home));

        // AI owners: ownerPool 순서대로
        aiOwnerIds.clear();
        for (int i = 0; i < aiCount; i++) {
            aiOwnerIds.add(ownerPool.get(i));
        }

        // pick ai homes (neutral keys)
        Set<String> used = new HashSet<>();
        used.add(home);

        List<String> candidates = new ArrayList<>(regionMetaRepository.keys());
        Collections.shuffle(candidates, ThreadLocalRandom.current());

        for (String ai : aiOwnerIds) {
            String pick = null;
            for (String k : candidates) {
                if (used.contains(k)) continue;
                // 너무 작은 섬 같은 것보다, 일단 해안/내륙 상관 없이
                pick = k;
                break;
            }
            if (pick == null) throw new IllegalStateException("AI home pick failed");
            used.add(pick);

            occupationStateRepository.occupy(pick, ai);
            aiHomeByOwnerId.put(ai, pick);
            log.add("START: AI " + ai + " home=" + shortName(pick));
        }

        // init resources
        resourcesByOwner.clear();
        techPointsByOwner.clear();
        unlockedTechByOwner.clear();
        goldBonusByOwner.clear();
        attackBonusByOwner.clear();
        resourcesByOwner.put(USER_OWNER, new EnumMap<>(ResourceType.class));
        for (String ai : aiOwnerIds) resourcesByOwner.put(ai, new EnumMap<>(ResourceType.class));
        for (EnumMap<ResourceType, Integer> m : resourcesByOwner.values()) {
            for (ResourceType t : ResourceType.values()) m.put(t, 0);
        }

        // init tech
        techPointsByOwner.clear();
        unlockedTechByOwner.clear();
        goldBonusByOwner.clear();
        attackBonusByOwner.clear();
        techPointsByOwner.put(USER_OWNER, 0);
        unlockedTechByOwner.put(USER_OWNER, new LinkedHashSet<>());
        goldBonusByOwner.put(USER_OWNER, 0.0);
        attackBonusByOwner.put(USER_OWNER, 0.0);
        for (String ai : aiOwnerIds) {
            techPointsByOwner.put(ai, 0);
            unlockedTechByOwner.put(ai, new LinkedHashSet<>());
            goldBonusByOwner.put(ai, 0.0);
            attackBonusByOwner.put(ai, 0.0);
        }

        // units: 1 per owner at home
        unitStateRepository.clear();
        unitStateRepository.add(new Unit(newUnitId(USER_OWNER), USER_OWNER, home, START_SOLDIERS));
        for (String ai : aiOwnerIds) {
            String hk = aiHomeByOwnerId.get(ai);
            unitStateRepository.add(new Unit(newUnitId(ai), ai, hk, START_SOLDIERS));
        }

        // no facilities at start
        buildingStateRepository.clear();

        // v3: 통치/첩보 상태 초기화
        governanceStateRepository.clear();
        reconStateRepository.clear();

        // 방첩 레벨 초기화(가정: USER=0, AI=1)
        counterIntelByOwnerId.clear();
        counterIntelByOwnerId.put(USER_OWNER, 0);
        for (String ai : aiOwnerIds) counterIntelByOwnerId.put(ai, 1);

        turn = 0;
        day = 0;
        builtThisTurnOwners.clear();

        // PREP phase: block early expansion right after game start
        if (PREP_TURNS > 0) {
            log.add("PREP_PHASE: " + PREP_TURNS + " turn(s) (no ATTACK / NAVAL_MOVE)");
        }

        // ✅ 시작 직후에는 자원 생산을 한 번 더 돌리지 않는다.
        // - "금화가 쌓이면(30) 경제시설" 흐름을 만들기 위해, 초기 GOLD는 0에서 시작.
        // - 첫 PASS 이후 endTurn()에서 produceResources()가 수행되며 그때부터 누적된다.

        return buildStateDto();
    }

    public GameStateDto userAction(GameActionRequest request) {
        if (!started) throw new IllegalStateException("Game not started");
        if (finished) return buildStateDto();

        String type = String.valueOf(request.type()).trim().toUpperCase(Locale.ROOT);

        // ✅ 유저는 한 턴에 여러 액션 가능, 턴 종료는 PASS로만 수행
        switch (type) {
            case "PASS" -> {
                log.add("USER PASS");
                // AI actions
                for (String ai : aiOwnerIds) {
                    if (finished) break;
                    doAiTurn(ai);
                }
                // end of full-turn
                endTurn();
                return buildStateDto();
            }
            case "MOVE" -> doLandMove(USER_OWNER, request);
            case "NAVAL_MOVE" -> doNavalMove(USER_OWNER, request);
            case "ATTACK" -> doLandAttack(USER_OWNER, request);
            case "BUILD_FACILITY" -> doBuildFacility(USER_OWNER, request);
            case "RECRUIT" -> doRecruit(USER_OWNER, request);
            case "SPY" -> doSpy(USER_OWNER, request);
            default -> throw new IllegalArgumentException("Unknown type: " + request.type());
        }

        // 액션 후 즉시 승리 판정(100% 점령 등)
        checkWinner();
        return buildStateDto();
    }

    /**
     * 기술 연구(턴 소비 없음).
     * - techPoints(민심 90%+ 유지로 누적)로 비용을 지불해 기술을 해금한다.
     */
    public GameStateDto unlockTech(String techCode) {
        if (!started) throw new IllegalStateException("Game not started");
        if (finished) return buildStateDto();

        doUnlockTech(USER_OWNER, techCode);
        return buildStateDto();
    }

    public void reset() {
        resetInternal();
    }

    // =========================
    // Actions
    // =========================

    private boolean isPrepPhase() {
        return turn < PREP_TURNS;
    }

    private void ensureNotPrep(String actionName) {
        if (!isPrepPhase()) return;
        throw new IllegalArgumentException("준비기간(" + PREP_TURNS + "턴)에는 " + actionName + "할 수 없습니다.");
    }

    private void doLandMove(String ownerId, GameActionRequest req) {
        String from = requireKey(req.fromKey(), "fromKey");
        String to = requireKey(req.toKey(), "toKey");

        ensureOwned(from, ownerId);
        ensureAdjacent(from, to);

        String toOwner = occupationOwnerOfOrNeutral(to);
        if (!ownerId.equals(toOwner) && !NEUTRAL_OWNER.equals(toOwner)) {
            throw new IllegalArgumentException("적 지역은 MOVE로 이동할 수 없습니다. (공격: ATTACK)");
        }

        Unit u = pickUnitAtForAction(ownerId, from, req.unitId());

        int have = u.soldiers();
        int amount = (req.soldiers() == null) ? have : req.soldiers();
        if (amount <= 0) throw new IllegalArgumentException("이동 병력 수는 1 이상이어야 합니다.");
        if (amount > have) throw new IllegalArgumentException("이동 병력 수가 보유 병력보다 많습니다. (보유:" + have + ", 요청:" + amount + ")");

        if (NEUTRAL_OWNER.equals(toOwner)) {
            occupationStateRepository.occupy(to, ownerId);
            afterOccupy(ownerId, to);
            log.add(ownerId + " TAKE_NEUTRAL " + shortName(to));
        }

        // 목적지에 아군 유닛이 있으면 합쳐서 '지역당 1스택' 형태로 유지한다.
        Optional<Unit> dstOpt = unitStateRepository.firstStationedAtOwned(to, ownerId);

        if (amount == have) {
            // 전체 이동
            if (dstOpt.isPresent() && !dstOpt.get().id().equals(u.id())) {
                Unit dst = dstOpt.get();
                dst.addSoldiers(u.soldiers());
                unitStateRepository.remove(u.id());
                actedUnitIdsThisTurn.add(dst.id()); // 합쳐진 스택도 이미 이동을 수행한 것으로 처리
                log.add(ownerId + " MOVE " + shortName(from) + " -> " + shortName(to) + " (merge, +" + have + ")");
            } else {
                u.moveTo(to);
                log.add(ownerId + " MOVE " + shortName(from) + " -> " + shortName(to) + " (" + have + ")");
            }
            return;
        }

        // 부분 이동(파견): 출발지 유닛에서 병력을 떼어내 목적지로 보낸다.
        if (have <= 1) throw new IllegalArgumentException("병력이 1명인 유닛은 분할 이동할 수 없습니다.");

        u.addSoldiers(-amount);

        if (dstOpt.isPresent()) {
            Unit dst = dstOpt.get();
            dst.addSoldiers(amount);
            actedUnitIdsThisTurn.add(dst.id());
            log.add(ownerId + " MOVE " + shortName(from) + " -> " + shortName(to) + " (reinforce +" + amount + ")");
        } else {
            Unit moved = new Unit(newUnitId(ownerId), ownerId, to, amount);
            unitStateRepository.add(moved);
            actedUnitIdsThisTurn.add(moved.id());
            log.add(ownerId + " MOVE " + shortName(from) + " -> " + shortName(to) + " (send " + amount + ")");
        }
    }

    private void doNavalMove(String ownerId, GameActionRequest req) {
        ensureNotPrep("해상 이동/상륙");
        String from = requireKey(req.fromKey(), "fromKey");
        String to = requireKey(req.toKey(), "toKey");

        ensureOwned(from, ownerId);
        Unit u = pickUnitAtForAction(ownerId, from, req.unitId());

        if (!buildingStateRepository.has(from, BuildingType.PORT)) {
            throw new IllegalArgumentException("항구(PORT)가 필요합니다: " + shortName(from));
        }
        if (!geoRuleService.isCoastal(from) || !geoRuleService.isCoastal(to)) {
            throw new IllegalArgumentException("해상 이동은 해안 지역만 가능합니다.");
        }

        OptionalInt days = geoRuleService.travelDaysBetweenCoastalRegions(from, to, TURN_DAYS);
        if (days.isEmpty()) throw new IllegalArgumentException("해상 이동 경로/좌표 정보가 없습니다.");

        int have = u.soldiers();
        int amount = (req.soldiers() == null) ? have : req.soldiers();
        if (amount <= 0) throw new IllegalArgumentException("이동 병력 수는 1 이상이어야 합니다.");
        if (amount > have) throw new IllegalArgumentException("이동 병력 수가 보유 병력보다 많습니다. (보유:" + have + ", 요청:" + amount + ")");

        // 이동 시작: 즉시 점령/전투는 하지 않고, 도착 턴에 처리
        if (amount == have) {
            u.startTransit(from, to, days.getAsInt());
            log.add(ownerId + " NAVAL_MOVE " + shortName(from) + " -> " + shortName(to) + " (" + days.getAsInt() + "d, " + have + ")");
            return;
        }

        if (have <= 1) throw new IllegalArgumentException("병력이 1명인 유닛은 분할 이동할 수 없습니다.");
        u.addSoldiers(-amount);
        Unit moved = new Unit(newUnitId(ownerId), ownerId, from, amount);
        unitStateRepository.add(moved);
        actedUnitIdsThisTurn.add(moved.id());
        moved.startTransit(from, to, days.getAsInt());
        log.add(ownerId + " NAVAL_MOVE " + shortName(from) + " -> " + shortName(to) + " (" + days.getAsInt() + "d, send " + amount + ")");
    }

    private void doLandAttack(String ownerId, GameActionRequest req) {
        ensureNotPrep("공격/점령");
        String from = requireKey(req.fromKey(), "fromKey");
        String to = requireKey(req.toKey(), "toKey");

        ensureOwned(from, ownerId);
        ensureAdjacent(from, to);

        String toOwner = occupationOwnerOfOrNeutral(to);
        if (NEUTRAL_OWNER.equals(toOwner)) {
            throw new IllegalArgumentException("중립 지역은 ATTACK이 아니라 MOVE로 들어가면 즉시 점령됩니다.");
        }
        if (ownerId.equals(toOwner)) {
            throw new IllegalArgumentException("자기 지역은 공격할 수 없습니다.");
        }

        Unit attacker = pickUnitAtForAction(ownerId, from, req.unitId());
        resolveBattleAndMaybeOccupy(attacker, from, to, /*isImmediate=*/true);
    }

    private void doBuildFacility(String ownerId, GameActionRequest req) {
        String at = requireKey(req.fromKey(), "fromKey");
        ensureOwned(at, ownerId);

        if (builtThisTurnOwners.contains(ownerId)) {
            throw new IllegalArgumentException("한 턴에 시설은 1개만 건설할 수 있습니다.");
        }

        String fac = String.valueOf(req.facility()).trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(fac)) throw new IllegalArgumentException("facility is required");
        BuildingType type;
        try {
            type = BuildingType.valueOf(fac);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown facility: " + req.facility());
        }

        if (type == BuildingType.PORT && !geoRuleService.isCoastal(at)) {
            throw new IllegalArgumentException("내륙에는 항구(PORT)를 지을 수 없습니다.");
        }

        // 군사시설은 '금화 50' 조건만으로 건설 가능(유저 룰)

        if (buildingStateRepository.has(at, type)) {
            throw new IllegalArgumentException("이미 해당 시설이 있습니다: " + type);
        }

        int cost = BUILD_COST_GOLD.getOrDefault(type, 0);
        if (cost > 0) {
            ensureGold(ownerId, cost);
            consumeGold(ownerId, cost);
        }

        buildingStateRepository.add(at, type);

        // 시설 효과(간소):
        // - ADMIN: 민심 크게 상승
        // - ECONOMY: 민심/안정 소폭 상승
        // - MILITARY: 안정 소폭 상승
        RegionStat stat = regionStatRepository.getByKey(at);
        GovernanceState g = governanceStateRepository.getOrCreate(at, stat.support(), stat.stability());
        switch (type) {
            case ADMIN -> g.setSupport(g.support() + 10);
            case ECONOMY -> {
                g.setSupport(g.support() + 4);
                g.setStability(g.stability() + 2);
            }
            case MILITARY -> g.setStability(g.stability() + 3);
            default -> { /* no-op */ }
        }

        builtThisTurnOwners.add(ownerId);
        log.add(ownerId + " BUILD " + type + " @" + shortName(at) + " (gold -" + cost + ")");
    }

    private void doRecruit(String ownerId, GameActionRequest req) {
        String at = requireKey(req.fromKey(), "fromKey");
        ensureOwned(at, ownerId);

        if (!buildingStateRepository.has(at, BuildingType.MILITARY)) {
            throw new IllegalArgumentException("군사시설(MILITARY)이 필요합니다: " + shortName(at));
        }

        RegionStat stat = regionStatRepository.getByKey(at);
        int gain = calcRecruitGain(stat.population());
        int costGold = gain * RECRUIT_GOLD_PER_POINT;
        ensureGold(ownerId, costGold);
        consumeGold(ownerId, costGold);

        Unit u = unitStateRepository.firstStationedAtOwned(at, ownerId)
                .orElseGet(() -> {
                    Unit nu = new Unit(newUnitId(ownerId), ownerId, at, START_SOLDIERS);
                    unitStateRepository.add(nu);
                    return nu;
                });

        u.addSoldiers(gain);
        log.add(ownerId + " RECRUIT +" + gain + " @" + shortName(at) + " (gold -" + costGold + ", unit " + u.id() + ")");
    }

    // =========================
    // AI
    // =========================

        private void doAiTurn(String ai) {
        // 1) 이동 완료된 유닛만 대상으로 행동
        List<Unit> units = unitStateRepository.byOwner(ai);

        Unit u = null;
        for (Unit cand : units) {
            if (!cand.isInTransit()) { u = cand; break; }
        }

        // AI가 반란 등으로 유닛이 0개가 되면 '영구 멈춤'이 발생한다.
        // -> 최소 1개의 주둔 유닛(병력 0)으로 복구해서 건설/모집이라도 계속하도록 한다.
        if (u == null) {
            if (units.isEmpty()) {
                String spawn = aiHomeByOwnerId.get(ai);
                if (!StringUtils.hasText(spawn)) {
                    // home이 없으면, 점령 중인 아무 지역이라도 선택
                    for (Map.Entry<String, String> e : occupationStateRepository.snapshot().entrySet()) {
                        if (ai.equals(e.getValue())) { spawn = e.getKey(); break; }
                    }
                }
                if (StringUtils.hasText(spawn)) {
                    Unit nu = new Unit(UUID.randomUUID().toString(), ai, spawn, 0);
                    unitStateRepository.add(nu);
                    u = nu;
                    log.add(ai + " UNIT_REFORM @" + shortName(spawn));
                }
            }

            if (u == null) {
                log.add(ai + " PASS (all units in transit)");
                return;
            }
        }

        String from = u.regionKey();
        if (from == null) {
            log.add(ai + " PASS (no stationed unit)");
            return;
        }

        // PREP phase: block early expansion right after game start
        if (isPrepPhase()) {
            // During prep, AI does only internal actions (build/recruit), no capture.

            // Recruit if possible (MILITARY required)
            if (buildingStateRepository.has(from, BuildingType.MILITARY)) {
                RegionStat st = regionStatRepository.getByKey(from);
                int gain = calcRecruitGain(st.population());
                int costGold = gain * RECRUIT_GOLD_PER_POINT;
                if (goldOf(ai) >= costGold) {
                    consumeGold(ai, costGold);
                    u.addSoldiers(gain);
                    log.add(ai + " RECRUIT +" + gain + " @" + shortName(from) + " (gold -" + costGold + ", unit " + u.id() + ")");
                    return;
                }
            }

            // Build (one per turn): ECONOMY -> ADMIN -> MILITARY
            if (!builtThisTurnOwners.contains(ai)) {
                if (!buildingStateRepository.has(from, BuildingType.ECONOMY)) {
                    buildFacilityNoChecks(ai, from, BuildingType.ECONOMY);
                    if (builtThisTurnOwners.contains(ai)) return;
                }
                if (!buildingStateRepository.has(from, BuildingType.ADMIN)) {
                    buildFacilityNoChecks(ai, from, BuildingType.ADMIN);
                    if (builtThisTurnOwners.contains(ai)) return;
                }
                if (!buildingStateRepository.has(from, BuildingType.MILITARY)) {
                    buildFacilityNoChecks(ai, from, BuildingType.MILITARY);
                    if (builtThisTurnOwners.contains(ai)) return;
                }
            }

            log.add(ai + " PASS (prep phase)");
            return;
        }

        // 1.5) 현재 지역을 점령 상태로 "고정"하기 위해, 행정시설(ADMIN)이 없으면 우선 건설(가능할 때)
        // - 주둔군 규칙이 있는 상태에서 AI가 매턴 이동만 하면 땅이 계속 NEUTRAL로 돌아가서 게임이 의미 없어진다.
        // - ADMIN은 '점령 행정' 역할: 지어두면 주둔이 없어도 유지(유저와 동일 룰)
        if (!builtThisTurnOwners.contains(ai) && !buildingStateRepository.has(from, BuildingType.ADMIN)) {
            buildFacilityNoChecks(ai, from, BuildingType.ADMIN);
            if (builtThisTurnOwners.contains(ai)) return;
        }

        // 2) 확장/전투
        // - 중립은 ATTACK이 아니라 "MOVE로 진입하면 즉시 점령" 규칙을 AI도 따르게 한다.
        // - 병력이 0이면 확장/전투 시도 자체를 하지 않는다(예외 spam 방지).
        if (u.soldiers() > 0) {
            List<String> neighbors = new ArrayList<>(neighborsOf(from));
            Collections.sort(neighbors);

            // 2-A) 중립 우선: MOVE로 진입 -> 즉시 점령
            for (String to : neighbors) {
                String occ = occupationStateRepository.getOwnerId(to);
                if (occ == null || NEUTRAL_OWNER.equals(occ)) {
                    occupationStateRepository.occupy(to, ai);
                    afterOccupy(ai, to);
                    u.moveTo(to);
                    log.add(ai + " TAKE_NEUTRAL " + shortName(to));
                    return;
                }
            }

            // 2-B) 그 다음 적 지역 공격
            for (String to : neighbors) {
                String occ = occupationStateRepository.getOwnerId(to);
                if (occ != null && !NEUTRAL_OWNER.equals(occ) && !ai.equals(occ)) {
                    try {
                        resolveBattleAndMaybeOccupy(u, from, to, true);
                        return;
                    } catch (Exception ignore) {
                        // 다음 후보
                    }
                }
            }
        }

        // 3) 군사시설 있고 금화 충분하면 모집(인구 기반)
        if (buildingStateRepository.has(from, BuildingType.MILITARY)) {
            RegionStat st = regionStatRepository.getByKey(from);
            int gain = calcRecruitGain(st.population());
            int costGold = gain * RECRUIT_GOLD_PER_POINT;
            if (goldOf(ai) >= costGold) {
                consumeGold(ai, costGold);
                u.addSoldiers(gain);
                log.add(ai + " RECRUIT +" + gain + " @" + shortName(from) + " (gold -" + costGold + ", unit " + u.id() + ")");
                return;
            }
        }

        // 4) 시설 건설(한 턴 1개) - 경제 -> 군사 -> 행정/항구/성벽
        if (!builtThisTurnOwners.contains(ai)) {
            // ECONOMY 우선
            if (!buildingStateRepository.has(from, BuildingType.ECONOMY) && goldOf(ai) >= BUILD_COST_GOLD.get(BuildingType.ECONOMY)) {
                buildFacilityNoChecks(ai, from, BuildingType.ECONOMY);
                return;
            }
            // 항구(해안이면)
            if (geoRuleService.isCoastal(from) && !buildingStateRepository.has(from, BuildingType.PORT) && goldOf(ai) >= BUILD_COST_GOLD.get(BuildingType.PORT)) {
                buildFacilityNoChecks(ai, from, BuildingType.PORT);
                return;
            }
            // 군사(경제가 있어야)
            if (buildingStateRepository.has(from, BuildingType.ECONOMY) && !buildingStateRepository.has(from, BuildingType.MILITARY) && goldOf(ai) >= BUILD_COST_GOLD.get(BuildingType.MILITARY)) {
                buildFacilityNoChecks(ai, from, BuildingType.MILITARY);
                return;
            }
            if (!buildingStateRepository.has(from, BuildingType.ADMIN) && goldOf(ai) >= BUILD_COST_GOLD.get(BuildingType.ADMIN)) {
                buildFacilityNoChecks(ai, from, BuildingType.ADMIN);
                return;
            }
            if (!buildingStateRepository.has(from, BuildingType.WALL) && goldOf(ai) >= BUILD_COST_GOLD.get(BuildingType.WALL)) {
                buildFacilityNoChecks(ai, from, BuildingType.WALL);
                return;
            }
        }

        // 5) 이동(자기 영토 내 첫 인접지)
        List<String> ownNeighbors = new ArrayList<>();
        for (String n : neighborsOf(from)) {
            if (ai.equals(occupationStateRepository.getOwnerId(n))) ownNeighbors.add(n);
        }
        Collections.sort(ownNeighbors);
        if (!ownNeighbors.isEmpty()) {
            String to = ownNeighbors.getFirst();
            u.moveTo(to);
            log.add(ai + " MOVE " + shortName(from) + " -> " + shortName(to));
            return;
        }

        log.add(ai + " PASS");
    }

    // =========================
    // Turn end processing
    // =========================

    private void endTurn() {
        // 1) 날짜/턴 진행
        turn += 1;
        day += TURN_DAYS;

        // 2) 이동(해상) 진행 및 도착 처리
        // 도착 처리에서 "출항지"를 전투 보정(시설) 계산에 사용하기 위해, tick 전 transitFrom을 보관
        Map<String, String> transitFromByUnitId = new HashMap<>();
        for (Unit u : unitStateRepository.all()) {
            if (u.isInTransit()) {
                transitFromByUnitId.put(u.id(), u.transitFromKey());
            }
        }

        List<Unit> arrived = unitStateRepository.tickAndArrive(TURN_DAYS);
        for (Unit u : arrived) {
            // 도착한 지역이 내 땅이 아니면, 즉시 전투(상륙) 처리
            String to = u.regionKey();
            if (to == null) continue;
            String occ = occupationStateRepository.getOwnerId(to);
            if (occ == null || "NEUTRAL".equals(occ) || !u.ownerId().equals(occ)) {
                try {
                    String from = transitFromByUnitId.getOrDefault(u.id(), to);
                    resolveBattleAndMaybeOccupy(u, from, to, /*isImmediate=*/false);
                } catch (Exception e) {
                    // 방어 성공: 상륙 실패로 유닛 제거(간소 처리)
                    unitStateRepository.remove(u.id());
                    log.add(u.ownerId() + " LANDING FAILED @" + shortName(to) + " (unit destroyed)");
                }
            }
        }

        // 3) 자원 생산
        produceResources();

        // 3.5) (삭제) 주둔군(garrison) 기반 점령 유지 규칙은 적용하지 않는다.

        // 4) 통치 상태 갱신 + 반란 처리
        updateGovernanceEachTurn();
        processRebellion();

        // 4.5) 민심 90% 이상 유지 시 기술점수 +1 (세력 단위)
        accrueTechPoints();

        // 5) Fog(가시 지역) recon 갱신
        refreshReconForVisibleRegions();

        // 6) 승리 체크
        checkWinner();

        // 5) 턴 제한 초기화
        builtThisTurnOwners.clear();
        actedUnitIdsThisTurn.clear();


        trimLog();
    }

    private void updateGovernanceEachTurn() {
        Map<String, String> occ = occupationStateRepository.snapshot();
        for (Map.Entry<String, String> e : occ.entrySet()) {
            String key = e.getKey();
            String owner = e.getValue();
            RegionStat stat = regionStatRepository.getByKey(key);
            GovernanceState g = governanceStateRepository.getOrCreate(key, stat.support(), stat.stability());

            if (NEUTRAL_OWNER.equals(owner)) {
                // 중립은 50을 향해 서서히 수렴(가정)
                int s = g.support();
                if (s < 50) g.setSupport(s + 1);
                else if (s > 50) g.setSupport(s - 1);
            } else {
                // 점령 중이면 민심/안정이 조금씩 회복(가정)
                int supportDelta = 2;
                int stabilityDelta = 1;
                // 시설별 추가 회복(요청: 행정=민심, 경제=경제+민심, 군사=지역방어)
                if (buildingStateRepository.has(key, BuildingType.ADMIN)) supportDelta += 1;
                if (buildingStateRepository.has(key, BuildingType.ECONOMY)) {
                    supportDelta += 1;
                    stabilityDelta += 1;
                }
                if (buildingStateRepository.has(key, BuildingType.MILITARY)) stabilityDelta += 1;

                g.setSupport(g.support() + supportDelta);
                g.setStability(g.stability() + stabilityDelta);
            }
        }
    }

    private void processRebellion() {
        // v2.1 표 기준: support < 20 이면 25% 반란
        Map<String, String> occ = occupationStateRepository.snapshot();
        for (Map.Entry<String, String> e : occ.entrySet()) {
            String key = e.getKey();
            String owner = e.getValue();
            if (NEUTRAL_OWNER.equals(owner)) continue;

            RegionStat stat = regionStatRepository.getByKey(key);
            GovernanceState g = governanceStateRepository.getOrCreate(key, stat.support(), stat.stability());

            if (g.support() < 20) {
                double p = 0.25;
                if (ThreadLocalRandom.current().nextDouble() < p) {
                    // 소유권 상실: 중립화 + 주둔 유닛 제거(간소)
                    occupationStateRepository.occupy(key, NEUTRAL_OWNER);
                    for (Unit u : unitStateRepository.stationedAt(key)) {
                        if (owner.equals(u.ownerId())) unitStateRepository.remove(u.id());
                    }
                    // 반란 직후 민심은 50으로 리셋(가정)
                    g.setSupport(50);
                    g.setStability(Math.max(30, g.stability()));

                    log.add("REBELLION @" + shortName(key) + " -> NEUTRAL");
                }
            }
        }
    }

    private void produceResources() {
        Map<String, String> occ = occupationStateRepository.snapshot();
        for (Map.Entry<String, String> e : occ.entrySet()) {
            String key = e.getKey();
            String owner = e.getValue();
            if (!resourcesByOwner.containsKey(owner)) continue; // 안전

            // v3.1: 경제는 "금화(GOLD)"로 일원화.
            // - 지역 stat.yieldPerTurn을 금화 수입으로 취급
            // - ECONOMY 시설로 수입 증가
            // - 민심에 따라 생산 보정
            // - TAX 기술로 세력 단위 금화 보정
            RegionStat stat = regionStatRepository.getByKey(key);
            int base = stat.yieldPerTurn();

            if (buildingStateRepository.has(key, BuildingType.ECONOMY)) {
                base += Math.max(1, (int) Math.round(stat.yieldPerTurn() * 0.25));
            }

            GovernanceState g = governanceStateRepository.getOrCreate(key, stat.support(), stat.stability());
            int addGold = (int) Math.round(base * supportMultiplier(g.support()));
            addGold = (int) Math.round(addGold * (1.0 + goldBonusByOwner.getOrDefault(owner, 0.0)));

            EnumMap<ResourceType, Integer> pool = resourcesByOwner.get(owner);
            pool.put(ResourceType.GOLD, pool.get(ResourceType.GOLD) + addGold);
        }
        log.add("PRODUCE: +" + TURN_DAYS + "d");
    }

    
    private void afterOccupy(String newOwnerId, String regionKey) {
        // 점령 직후 민심/안정 하락(가정)
        RegionStat stat = regionStatRepository.getByKey(regionKey);
        GovernanceState g = governanceStateRepository.getOrCreate(regionKey, stat.support(), stat.stability());
        g.setSupport(Math.min(g.support(), 35));
        g.setStability(Math.min(g.stability(), 40));

        // 점령 직후는 recon을 갱신(보이는 지역이면) - USER 기준
        if (isVisibleTo(USER_OWNER, regionKey)) {
            reconStateRepository.put(USER_OWNER, regionKey,
                    new ReconSnapshot(day, g.support(), g.stability(), unitsAt(regionKey), buildingStateRepository.getSetOrEmpty(regionKey)));
        }

        // 점령이 발생한 즉시 승리 판정(100% 점령 등)
        checkWinner();
    }

// =========================
    // Battle
    // =========================

        private void resolveBattleAndMaybeOccupy(Unit attacker, String fromKey, String toKey, boolean isImmediate) {
        String target = toKey;

        // 병력이 0이면 점령/공격 불가
        if (attacker.soldiers() <= 0) {
            throw new IllegalArgumentException("병력이 없습니다. (0)\n모집/이동으로 병력을 확보하세요.");
        }

        String defenderOwner = occupationStateRepository.getOwnerId(target);
        if (defenderOwner == null) defenderOwner = NEUTRAL_OWNER;

        // ✅ 중립 지역: 기본은 "즉시 점령"(민병대/확률 없음)
        if (NEUTRAL_OWNER.equals(defenderOwner)) {
            occupationStateRepository.occupy(target, attacker.ownerId());
            afterOccupy(attacker.ownerId(), target);

            // 중립지에 남아있는 타 세력 유닛이 있다면 제거(안전장치)
            for (Unit du : unitStateRepository.stationedAt(target)) {
                if (!attacker.ownerId().equals(du.ownerId())) unitStateRepository.remove(du.id());
            }

            // 점령 성공 시 공격 유닛은 해당 지역으로 이동(전략 시뮬 감각)
            if (attacker.regionKey() == null || !target.equals(attacker.regionKey())) {
                attacker.moveTo(target);
            }

            log.add(attacker.ownerId() + " TAKE_NEUTRAL " + shortName(target));
            return;
        }

        int atk = attacker.soldiers();
        atk = (int) Math.round(atk * attackFacilityBonus(fromKey));
        // 기술(강철 군수): 공격력 보정
        atk = (int) Math.round(atk * (1.0 + attackBonusByOwner.getOrDefault(attacker.ownerId(), 0.0)));

        int def = defensePower(target, defenderOwner);
        def = (int) Math.round(def * defenseFacilityBonus(target));

        if (buildingStateRepository.has(target, BuildingType.WALL)) {
            def = (int) Math.round(def * (1.0 + WALL_DEF_BONUS));
        }

        double ratio = (def <= 0) ? 999.0 : ((double) atk / (double) def);

        if (ratio >= TAKEOVER_THRESHOLD) {
            // 점령 성공
            occupationStateRepository.occupy(target, attacker.ownerId());
            afterOccupy(attacker.ownerId(), target);

            // 방어측 유닛 제거(간소: 해당 지역에 있던 방어측 유닛 모두 제거)
            for (Unit du : unitStateRepository.stationedAt(target)) {
                if (!attacker.ownerId().equals(du.ownerId())) unitStateRepository.remove(du.id());
            }

            // 공격자 피해(간소)
            attacker.addSoldiers(-(int) Math.round(attacker.soldiers() * 0.10));

            // 점령 성공 시 유닛 이동
            if (attacker.regionKey() == null || !target.equals(attacker.regionKey())) {
                attacker.moveTo(target);
            }

            log.add(attacker.ownerId() + " TAKE " + shortName(target) + " (ratio " + fmt(ratio) + ")");
        } else {
            if (isImmediate) {
                // 지상 공격 실패: 일부 손실만
                attacker.addSoldiers(-(int) Math.round(attacker.soldiers() * 0.25));
                log.add(attacker.ownerId() + " FAIL " + shortName(target) + " (ratio " + fmt(ratio) + ")");
            } else {
                // 상륙 실패: 호출자가 유닛 파괴 처리(간소)
                throw new IllegalStateException("LANDING_FAILED ratio=" + fmt(ratio));
            }
        }
    }

        
    private int defensePower(String regionKey, String defenderOwnerId) {
        // 방어 유닛이 있으면 그 병력 합
        int sum = 0;
        for (Unit u : unitStateRepository.stationedAt(regionKey)) {
            if (defenderOwnerId.equals(u.ownerId())) sum += u.soldiers();
        }
        if (sum > 0) return sum;

        // 점령지는 "기본 수비대"가 있다고 간주(전략 시뮬 느낌). 중립은 0.
        if (NEUTRAL_OWNER.equals(defenderOwnerId)) return 0;
        return BASE_GARRISON;
    }

private double attackFacilityBonus(String fromKey) {
        double b = 1.0;
        if (buildingStateRepository.has(fromKey, BuildingType.MILITARY)) b += 0.10;
        return b;
    }

    private double defenseFacilityBonus(String regionKey) {
        double b = 1.0;
        if (buildingStateRepository.has(regionKey, BuildingType.MILITARY)) b += 0.10;
        return b;
    }

    // =========================
    // Helpers
    // =========================

    // v3: Fog of War - 내 영토 + 인접 영토만 "현재 정보"를 볼 수 있다.
    private boolean isVisibleTo(String viewerOwnerId, String regionKey) {
        String owner = occupationStateRepository.getOwnerId(regionKey);
        if (viewerOwnerId.equals(owner)) return true;

        // 내 유닛이 주둔 중이면(점령 직후/상륙 등) 현재 정보는 볼 수 있다고 가정
        for (Unit u : unitStateRepository.stationedAt(regionKey)) {
            if (viewerOwnerId.equals(u.ownerId())) return true;
        }

        // 인접 데이터가 없으면: 최소 가시성만(내 영토만) 적용
        if (!adjacencyRepository.hasAnyData()) return false;

        // regionKey가 내 영토와 인접하면 가시
        for (String n : adjacencyRepository.neighborsOf(regionKey)) {
            String nOwner = occupationStateRepository.getOwnerId(n);
            if (viewerOwnerId.equals(nOwner)) return true;
        }
        return false;
    }

    // 지역 주둔 유닛 현황(ownerId -> 병력 합)
    private Map<String, Integer> unitsAt(String regionKey) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Unit u : unitStateRepository.stationedAt(regionKey)) {
            out.merge(u.ownerId(), u.soldiers(), Integer::sum);
        }
        return out;
    }

    // v3: 첩보(간소) - target 지역을 관측하여 "마지막 관측" 스냅샷을 저장한다.
    private void doSpy(String ownerId, GameActionRequest req) {
        String target = requireKey(req.toKey(), "toKey");

        String targetOwner = occupationStateRepository.getOwnerId(target);
        if (targetOwner == null) targetOwner = NEUTRAL_OWNER;

        RegionStat stat = regionStatRepository.getByKey(target);
        GovernanceState g = governanceStateRepository.getOrCreate(target, stat.support(), stat.stability());

        // 방첩 레벨(0~3)이 높을수록 오차 범위를 크게(가정)
        int ci = counterIntelByOwnerId.getOrDefault(targetOwner, 0);
        double maxErr = switch (ci) {
            case 0 -> 0.0;
            case 1 -> 0.15;
            case 2 -> 0.30;
            default -> 0.45;
        };

        // 기술(암호 통신): 첩보 오차 제거
        if (hasTech(ownerId, "CRYPTO")) {
            maxErr = 0.0;
        }

        // support/stability 관측(오차)
        int obsSupport = applyNoise01(g.support(), maxErr, 0, 100);
        int obsStability = applyNoise01(g.stability(), maxErr, 0, 100);

        // 유닛 관측(오차)
        Map<String, Integer> trueUnits = unitsAt(target);
        Map<String, Integer> obsUnits = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : trueUnits.entrySet()) {
            obsUnits.put(e.getKey(), applyNoise01(e.getValue(), maxErr, 0, 999999));
        }

        // 시설 관측(오차: 높은 방첩이면 일부 누락될 수 있음 - 간소)
        Set<BuildingType> trueFac = buildingStateRepository.getSetOrEmpty(target);
        Set<BuildingType> obsFac = new HashSet<>(trueFac);
        if (ci >= 2 && !obsFac.isEmpty() && ThreadLocalRandom.current().nextDouble() < 0.35) {
            // 하나를 누락
            BuildingType drop = obsFac.iterator().next();
            obsFac.remove(drop);
        }

        reconStateRepository.put(ownerId, target, new ReconSnapshot(day, obsSupport, obsStability, obsUnits, Set.copyOf(obsFac)));
        log.add(ownerId + " SPY " + shortName(target) + " (CI=" + ci + ")");
    }

    private int applyNoise01(int value, double maxErr, int min, int max) {
        if (maxErr <= 0.0) return clamp(value, min, max);
        double mul = 1.0 + ThreadLocalRandom.current().nextDouble(-maxErr, maxErr);
        int out = (int) Math.round(value * mul);
        return clamp(out, min, max);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // v3: 가시 지역은 항상 최신 recon 스냅샷으로 유지(USER 기준)
    private void refreshReconForVisibleRegions() {
        for (String key : regionMetaRepository.keys()) {
            if (!isVisibleTo(USER_OWNER, key)) continue;
            RegionStat stat = regionStatRepository.getByKey(key);
            GovernanceState g = governanceStateRepository.getOrCreate(key, stat.support(), stat.stability());
            reconStateRepository.put(USER_OWNER, key,
                    new ReconSnapshot(day, g.support(), g.stability(), unitsAt(key), buildingStateRepository.getSetOrEmpty(key)));
        }
    }

    private void ensureOwned(String key, String ownerId) {
        String cur = occupationStateRepository.getOwnerId(key);
        if (!ownerId.equals(cur)) throw new IllegalArgumentException("내 지역이 아닙니다: " + shortName(key));
    }

    /**
     * 점령 소유자를 조회하되, 미점령(null)이면 NEUTRAL로 간주한다.
     */
    private String occupationOwnerOfOrNeutral(String regionKey) {
        String owner = occupationStateRepository.getOwnerId(regionKey);
        return (owner == null) ? NEUTRAL_OWNER : owner;
    }

    private void ensureAdjacent(String from, String to) {
        if (!adjacencyRepository.hasAnyData()) return; // 데이터 없으면 검사 생략(호환)
        Set<String> ns = adjacencyRepository.neighborsOf(from);
        if (ns == null || !ns.contains(to)) throw new IllegalArgumentException("인접 지역이 아닙니다.");
    }

    private List<String> neighborsOf(String key) {
        if (!adjacencyRepository.hasAnyData()) return List.of();
        Set<String> ns = adjacencyRepository.neighborsOf(key);
        return (ns == null) ? List.of() : new ArrayList<>(ns);
    }

    private Unit pickUnitAt(String ownerId, String regionKey, String unitId) {
        if (StringUtils.hasText(unitId)) {
            Unit u = unitStateRepository.get(unitId);
            if (!ownerId.equals(u.ownerId())) throw new IllegalArgumentException("유닛 소유자가 아닙니다.");
            if (u.isInTransit() || !regionKey.equals(u.regionKey())) throw new IllegalArgumentException("유닛이 해당 지역에 없습니다.");
            return u;
        }
        return unitStateRepository.firstStationedAtOwned(regionKey, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("해당 지역에 유닛이 없습니다."));
    }

    private Unit pickUnitAtForAction(String ownerId, String regionKey, String unitId) {
        Unit u = pickUnitAt(ownerId, regionKey, unitId);
        if (actedUnitIdsThisTurn.contains(u.id())) {
            throw new IllegalArgumentException("이 유닛은 이번 턴에 이미 행동했습니다: " + u.id());
        }
        actedUnitIdsThisTurn.add(u.id());
        return u;
    }

    // ===== 금화/기술 유틸 =====
    private int goldOf(String ownerId) {
        EnumMap<ResourceType, Integer> m = resourcesByOwner.get(ownerId);
        return (m == null) ? 0 : m.getOrDefault(ResourceType.GOLD, 0);
    }

    private void ensureGold(String ownerId, int need) {
        if (goldOf(ownerId) < need) {
            throw new IllegalArgumentException("금화가 부족합니다. (필요: " + need + ", 보유: " + goldOf(ownerId) + ")");
        }
    }

    private void consumeGold(String ownerId, int cost) {
        EnumMap<ResourceType, Integer> m = resourcesByOwner.get(ownerId);
        if (m == null) return;
        int have = m.getOrDefault(ResourceType.GOLD, 0);
        if (have < cost) throw new IllegalStateException("consumeGold failed");
        m.put(ResourceType.GOLD, have - cost);
    }

    private int calcRecruitGain(long population) {
        double raw = (population * RECRUIT_POP_RATIO) / (double) RECRUIT_SCALE;
        int g = (int) Math.round(raw);
        return clamp(g, RECRUIT_MIN, RECRUIT_MAX);
    }

    private boolean hasTech(String ownerId, String code) {
        Set<String> set = unlockedTechByOwner.get(ownerId);
        return set != null && set.contains(code);
    }

    private void doUnlockTech(String ownerId, String techCodeRaw) {
        String techCode = String.valueOf(techCodeRaw).trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(techCode)) throw new IllegalArgumentException("techCode is required");

        TechDef def = TECH_DEFS.get(techCode);
        if (def == null) throw new IllegalArgumentException("Unknown tech: " + techCode);

        Set<String> unlocked = unlockedTechByOwner.computeIfAbsent(ownerId, k -> new LinkedHashSet<>());
        if (unlocked.contains(techCode)) {
            throw new IllegalArgumentException("이미 연구한 기술입니다: " + techCode);
        }

        int have = techPointsByOwner.getOrDefault(ownerId, 0);
        if (have < def.cost()) {
            throw new IllegalArgumentException("기술 점수가 부족합니다. (필요: " + def.cost() + ", 보유: " + have + ")");
        }
        techPointsByOwner.put(ownerId, have - def.cost());
        unlocked.add(techCode);

        // 효과 적용
        switch (techCode) {
            case "TAX" -> goldBonusByOwner.put(ownerId, goldBonusByOwner.getOrDefault(ownerId, 0.0) + 0.10);
            case "STEEL" -> attackBonusByOwner.put(ownerId, attackBonusByOwner.getOrDefault(ownerId, 0.0) + 0.10);
            case "CRYPTO" -> { /* doSpy에서 사용 */ }
            default -> { /* no-op */ }
        }

        log.add(ownerId + " TECH_UNLOCK " + techCode + " (tp -" + def.cost() + ")");
    }

    private void accrueTechPoints() {
        for (String owner : allOwners()) {
            if (!resourcesByOwner.containsKey(owner)) continue;

            List<String> ownedKeys = new ArrayList<>();
            for (Map.Entry<String, String> e : occupationStateRepository.snapshot().entrySet()) {
                if (owner.equals(e.getValue())) ownedKeys.add(e.getKey());
            }
            if (ownedKeys.isEmpty()) continue;

            int sum = 0;
            for (String key : ownedKeys) {
                RegionStat stat = regionStatRepository.getByKey(key);
                GovernanceState g = governanceStateRepository.getOrCreate(key, stat.support(), stat.stability());
                sum += g.support();
            }
            double avg = sum / (double) ownedKeys.size();
            if (avg >= TECH_SUPPORT_THRESHOLD) {
                techPointsByOwner.put(owner, techPointsByOwner.getOrDefault(owner, 0) + TECH_PER_TURN);
                log.add(owner + " TECH_POINT +" + TECH_PER_TURN + " (avgSupport " + fmt(avg) + ")");
            }
        }
    }

    private void buildFacilityNoChecks(String ownerId, String at, BuildingType type) {
        // AI도 유저와 동일한 룰을 최대한 따르게(편법 방지)
        if (builtThisTurnOwners.contains(ownerId)) return;

        // 항구는 해안에서만
        if (type == BuildingType.PORT && !geoRuleService.isCoastal(at)) return;

        // 군사시설은 경제시설이 선행
        if (type == BuildingType.MILITARY && !buildingStateRepository.has(at, BuildingType.ECONOMY)) return;

        if (buildingStateRepository.has(at, type)) return;
        int cost = BUILD_COST_GOLD.getOrDefault(type, 0);
        if (cost > 0) {
            if (goldOf(ownerId) < cost) return;
            consumeGold(ownerId, cost);
        }
        buildingStateRepository.add(at, type);

        RegionStat stat = regionStatRepository.getByKey(at);
        GovernanceState g = governanceStateRepository.getOrCreate(at, stat.support(), stat.stability());
        switch (type) {
            case ADMIN -> g.setSupport(g.support() + 10);
            case ECONOMY -> {
                g.setSupport(g.support() + 4);
                g.setStability(g.stability() + 2);
            }
            case MILITARY -> g.setStability(g.stability() + 3);
            default -> { /* no-op */ }
        }
        builtThisTurnOwners.add(ownerId);
        log.add(ownerId + " BUILD " + type + " @" + shortName(at) + " (gold -" + cost + ")");
    }

    private void checkWinner() {
        if (finished) return;

        // 승리조건(기획서): 한 세력이 전체 지역을 100% 점령하는 즉시 승리
        String candidate = null;
        for (String key : regionMetaRepository.keys()) {
            String owner = occupationStateRepository.getOwnerId(key);
            if (owner == null) owner = NEUTRAL_OWNER;
            if (NEUTRAL_OWNER.equals(owner)) {
                candidate = null;
                break;
            }
            if (candidate == null) candidate = owner;
            else if (!candidate.equals(owner)) {
                candidate = null;
                break;
            }
        }

        if (candidate == null) return;
        finished = true;
        winnerOwnerId = candidate;
        log.add("FINISH: winner=" + winnerOwnerId + " (100% domination)");
    }

    private int neutralCount() {
        // occupationStateRepository는 "점령된 지역만" 저장하므로,
        // 전체 지역 목록(regionMetaRepository.keys())을 기준으로 중립을 계산해야 한다.
        int total = regionMetaRepository.keys().size();
        int occupiedNonNeutral = 0;
        for (String owner : occupationStateRepository.snapshot().values()) {
            if (owner == null) continue;
            if (NEUTRAL_OWNER.equals(owner)) continue;
            occupiedNonNeutral++;
        }
        int neutral = total - occupiedNonNeutral;
        return Math.max(0, neutral);
    }

    private Map<String, Integer> territoriesByOwner() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String o : allOwners()) m.put(o, 0);

        // 점령된 지역만 카운트(중립/미점령은 제외)
        for (String owner : occupationStateRepository.snapshot().values()) {
            if (owner == null) continue;
            if (!m.containsKey(owner)) continue;
            m.put(owner, m.get(owner) + 1);
        }
        return m;
    }

    /**
     * UI/DTO용: 전체 지역에 대해 ownerId를 채운 스냅샷.
     * - 점령되지 않은 지역은 NEUTRAL로 채운다.
     */
    private Map<String, String> occupationFullSnapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : regionMetaRepository.keys()) {
            String owner = occupationStateRepository.getOwnerId(key);
            out.put(key, owner == null ? NEUTRAL_OWNER : owner);
        }
        return out;
    }

    private List<String> allOwners() {
        List<String> out = new ArrayList<>();
        out.add(USER_OWNER);
        out.addAll(aiOwnerIds);
        return out;
    }

    private GameStateDto buildStateDto() {
        Map<String, Integer> terr = territoriesByOwner();
        int total = regionMetaRepository.keys().size();
        int neutral = neutralCount();
        Map<String, String> occFull = occupationFullSnapshot();

        List<UnitDto> unitDtos = new ArrayList<>();
        for (Unit u : unitStateRepository.all()) {
            unitDtos.add(new UnitDto(
                    u.id(),
                    u.ownerId(),
                    u.isInTransit() ? null : u.regionKey(),
                    u.transitFromKey(),
                    u.transitToKey(),
                    u.remainingDays(),
                    u.soldiers()
            ));
        }

        // resources DTO (enum key -> string)
        Map<String, Map<String, Integer>> resDto = new LinkedHashMap<>();
        for (Map.Entry<String, EnumMap<ResourceType, Integer>> e : resourcesByOwner.entrySet()) {
            Map<String, Integer> one = new LinkedHashMap<>();
            for (ResourceType t : ResourceType.values()) one.put(t.name(), e.getValue().getOrDefault(t, 0));
            resDto.put(e.getKey(), one);
        }

        // 편의 DTO
        Map<String, Integer> goldDto = new LinkedHashMap<>();
        for (String o : allOwners()) goldDto.put(o, goldOf(o));

        Map<String, Integer> tpDto = new LinkedHashMap<>(techPointsByOwner);
        Map<String, Set<String>> unlockedDto = new LinkedHashMap<>();
        for (String o : allOwners()) {
            unlockedDto.put(o, unlockedTechByOwner.getOrDefault(o, Set.of()));
        }

        return new GameStateDto(
                started,
                finished,
                winnerOwnerId,
                turn,
                "USER",
                day,
                TURN_DAYS,
                USER_OWNER,
                List.copyOf(aiOwnerIds),
                userHomeKey,
                Map.copyOf(aiHomeByOwnerId),
                adjacencyRepository.hasAnyData(),
                total,
                neutral,
                terr,
                new ArrayList<>(buildingStateRepository.regionsHaving(BuildingType.PORT)),
                occFull,
                new ArrayList<>(log),
                buildingStateRepository.snapshot(),
                unitDtos,
                resDto,
                goldDto,
                tpDto,
                unlockedDto
        );
    }

    private void resetInternal() {
        started = false;
        finished = false;
        winnerOwnerId = null;
        turn = 0;
        day = 0;
        aiOwnerIds.clear();
        userHomeKey = null;
        aiHomeByOwnerId.clear();
        log.clear();
        builtThisTurnOwners.clear();

        resourcesByOwner.clear();
        occupationStateRepository.reset();
        buildingStateRepository.clear();
        unitStateRepository.clear();
        governanceStateRepository.clear();
        reconStateRepository.clear();
        counterIntelByOwnerId.clear();
    }

    private String requireKey(String v, String field) {
        if (!StringUtils.hasText(v)) throw new IllegalArgumentException(field + " is required");
        return v;
    }

    private String shortName(String key) {
        try {
            var r = regionMetaRepository.getByKey(key);
            return r.iso2() + "·" + (r.nameKo() == null ? r.nameEn() : r.nameKo());
        } catch (Exception e) {
            return key;
        }
    }

    private String newUnitId(String ownerId) {
        return ownerId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private void trimLog() {
        int max = 200;
        if (log.size() <= max) return;
        int start = log.size() - max;
        log.subList(0, start).clear();
    }

    private double supportMultiplier(int support) {
        if (support >= 70) return 1.00;
        if (support >= 40) return 0.85;
        if (support >= 20) return 0.65;
        return 0.50;
    }
}
