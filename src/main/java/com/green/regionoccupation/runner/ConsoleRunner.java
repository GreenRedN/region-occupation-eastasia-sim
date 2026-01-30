package com.green.regionoccupation.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.regionoccupation.domain.BuildingType;
import com.green.regionoccupation.domain.Region;
import com.green.regionoccupation.domain.Unit;
import com.green.regionoccupation.dto.GameActionRequest;
import com.green.regionoccupation.dto.GameStartRequest;
import com.green.regionoccupation.dto.GameStateDto;
import com.green.regionoccupation.repository.*;
import com.green.regionoccupation.service.GeoRuleService;
import com.green.regionoccupation.service.TurnGameService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 스프링 없이(=서버 기동 없이) 규칙을 빠르게 확인하기 위한 콘솔 데모 러너.
 *
 * 실행:
 *  - ./mvnw -q test
 *  - ./mvnw -q exec:java
 */
public class ConsoleRunner {

    // TurnGameService.USER_OWNER와 동일 (콘솔 러너는 스프링 DI 없이 돌기 때문에 상수로 둔다)
    private static final String USER = "B";

    public static void main(String[] args) {
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();

        // core components (no Spring)
        RegionMetaRepository regionMeta = new RegionMetaRepository(om);
        RegionStatRepository regionStat = new RegionStatRepository(om);
        AdjacencyRepository adjacency = new AdjacencyRepository(om);
        OccupationStateRepository occ = new OccupationStateRepository();
        BuildingStateRepository buildings = new BuildingStateRepository();
        UnitStateRepository units = new UnitStateRepository();
        RegionCoordRepository coords = new RegionCoordRepository(om);
        GeoRuleService geo = new GeoRuleService(regionMeta, coords);
        GovernanceStateRepository gov = new GovernanceStateRepository();
        ReconStateRepository recon = new ReconStateRepository();
        TurnGameService game = new TurnGameService(occ, buildings, units, regionMeta, regionStat, adjacency, geo, gov, recon);

        System.out.println("=== Region Occupation Simulator | Console Demo ===");
        System.out.println("- 1 turn = 10 days");
        System.out.println("- Neutral: immediate occupation (no militia / no probability)");
        System.out.println("- Combat: capture if Attack/EffectiveDefense >= 2.0, WALL gives +40% defense");
        System.out.println();

        String home = pickKeyWithNeighbors(regionMeta, adjacency);
        String neigh = pickFirstNeighbor(adjacency, home);
        System.out.println("[Selected] HOME=" + label(regionMeta, home));
        System.out.println("[Selected] NEIGHBOR=" + label(regionMeta, neigh));
        System.out.println();

        // S1: Neutral immediate capture
        System.out.println("--- S1. Neutral immediate occupation ---");
        game.start(new GameStartRequest(home, 0, null));
        GameStateDto s1 = game.userAction(new GameActionRequest("MOVE", home, neigh, null, null, null));
        printlnLastLogs(s1, 8);
        System.out.println("Occupation(" + shortLabel(regionMeta, neigh) + ")=" + s1.occupation().get(neigh));
        System.out.println();

        // S2: 1:1 defend holds
        System.out.println("--- S2. 1:1 defend holds (B:60 vs garrison:60) ---");
        game.start(new GameStartRequest(home, 0, null));
        forceOwner(game, neigh, "A");
        setUserSoldiers(game, 60);
        GameStateDto s2 = game.userAction(new GameActionRequest("ATTACK", home, neigh, null, null, null));
        printlnLastLogs(s2, 10);
        System.out.println("Occupation(" + shortLabel(regionMeta, neigh) + ")=" + s2.occupation().get(neigh));
        System.out.println();

        // S3: 2:1 capture
        System.out.println("--- S3. 2:1 capture (B:120 vs garrison:60) ---");
        game.start(new GameStartRequest(home, 0, null));
        forceOwner(game, neigh, "A");
        setUserSoldiers(game, 120);
        GameStateDto s3 = game.userAction(new GameActionRequest("ATTACK", home, neigh, null, null, null));
        printlnLastLogs(s3, 10);
        System.out.println("Occupation(" + shortLabel(regionMeta, neigh) + ")=" + s3.occupation().get(neigh));
        System.out.println();

        // S4: WALL prevents capture
        System.out.println("--- S4. WALL +40% prevents capture (B:120 vs garrison:60*1.4) ---");
        game.start(new GameStartRequest(home, 0, null));
        forceOwner(game, neigh, "A");
        forceBuilding(game, neigh, BuildingType.WALL);
        setUserSoldiers(game, 120);
        GameStateDto s4 = game.userAction(new GameActionRequest("ATTACK", home, neigh, null, null, null));
        printlnLastLogs(s4, 10);
        System.out.println("Occupation(" + shortLabel(regionMeta, neigh) + ")=" + s4.occupation().get(neigh));
        System.out.println();

        // S5: Naval travel takes > 1 turn
        System.out.println("--- S5. Naval travel uses distance-based days (approx) ---");
        NavalPair pair = pickNavalPair(regionMeta, geo);
        if (pair == null) {
            System.out.println("(No suitable naval pair found in current data set)");
        } else {
            System.out.println("[Selected] FROM=" + label(regionMeta, pair.from) + ", TO=" + label(regionMeta, pair.to) + ", travelDays=" + pair.days);
            game.start(new GameStartRequest(pair.from, 0, null));
            // build PORT (takes one turn)
            GameStateDto b1 = game.userAction(new GameActionRequest("BUILD_FACILITY", pair.from, null, "PORT", null, null));
            printlnLastLogs(b1, 6);
            // start naval move
            GameStateDto m1 = game.userAction(new GameActionRequest("NAVAL_MOVE", pair.from, pair.to, null, null, null));
            printlnLastLogs(m1, 8);
            // pass turns until arrival completes
            int safety = 10;
            while (safety-- > 0) {
                boolean anyTransit = m1.units().stream().anyMatch(u -> USER.equals(u.ownerId()) && u.remainingDays() > 0);
                if (!anyTransit) break;
                m1 = game.userAction(new GameActionRequest("PASS", null, null, null, null, null));
            }
            System.out.println("After travel, A-unit at: " + findUserUnitRegion(m1));
            System.out.println("Occupation(" + shortLabel(regionMeta, pair.to) + ")=" + m1.occupation().get(pair.to));
        }

        System.out.println();
        System.out.println("=== Done ===");
        System.out.println("Docs: ./docs/00_EXEC_SUMMARY.md (how to run) / 01_RULEBOOK.md (rules)");
    }

    private static String label(RegionMetaRepository meta, String key) {
        Region r = meta.getByKey(key);
        return r.iso2() + "·" + r.nameKo() + " (" + r.nameEn() + ")";
    }

    private static String shortLabel(RegionMetaRepository meta, String key) {
        Region r = meta.getByKey(key);
        return r.iso2() + "·" + r.nameKo();
    }

    private static String pickKeyWithNeighbors(RegionMetaRepository meta, AdjacencyRepository adj) {
        List<String> keys = new ArrayList<>(meta.keys());
        Collections.sort(keys);
        for (String k : keys) {
            if (!adj.neighborsOf(k).isEmpty()) return k;
        }
        throw new IllegalStateException("No region with neighbors found (adjacency data empty?)");
    }

    private static String pickFirstNeighbor(AdjacencyRepository adj, String from) {
        List<String> ns = new ArrayList<>(adj.neighborsOf(from));
        if (ns.isEmpty()) throw new IllegalStateException("No neighbor for: " + from);
        Collections.sort(ns);
        return ns.getFirst();
    }

    private static void printlnLastLogs(GameStateDto s, int n) {
        List<String> log = s.log();
        int from = Math.max(0, log.size() - n);
        for (int i = from; i < log.size(); i++) {
            System.out.println(log.get(i));
        }
    }

    private static void forceOwner(TurnGameService game, String regionKey, String ownerId) {
        // small hack for console demo: direct repository access via reflection is avoided
        // because TurnGameService uses shared repositories passed in ctor.
        try {
            var f = TurnGameService.class.getDeclaredField("occupationStateRepository");
            f.setAccessible(true);
            OccupationStateRepository occ = (OccupationStateRepository) f.get(game);
            occ.occupy(regionKey, ownerId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void forceBuilding(TurnGameService game, String regionKey, BuildingType type) {
        try {
            var f = TurnGameService.class.getDeclaredField("buildingStateRepository");
            f.setAccessible(true);
            BuildingStateRepository b = (BuildingStateRepository) f.get(game);
            b.add(regionKey, type);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setUserSoldiers(TurnGameService game, int soldiers) {
        try {
            var f = TurnGameService.class.getDeclaredField("unitStateRepository");
            f.setAccessible(true);
            UnitStateRepository repo = (UnitStateRepository) f.get(game);
            List<Unit> us = repo.byOwner(USER);
            if (us.isEmpty()) throw new IllegalStateException("User unit not found");
            Unit u = us.getFirst();
            int cur = u.soldiers();
            u.addSoldiers(soldiers - cur);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String findUserUnitRegion(GameStateDto state) {
        return state.units().stream()
                .filter(u -> USER.equals(u.ownerId()))
                .map(u -> u.regionKey() == null ? (u.transitToKey() + " (in transit, remaining=" + u.remainingDays() + ")") : u.regionKey())
                .findFirst().orElse("(none)");
    }

    private record NavalPair(String from, String to, int days) {}

    private static NavalPair pickNavalPair(RegionMetaRepository meta, GeoRuleService geo) {
        List<String> keys = new ArrayList<>(meta.keys());
        Collections.sort(keys);

        // candidate coastal keys only
        List<String> coastal = keys.stream().filter(geo::isCoastal).collect(Collectors.toList());
        for (String from : coastal) {
            for (String to : coastal) {
                if (from.equals(to)) continue;
                var daysOpt = geo.travelDaysBetweenCoastalRegions(from, to, 10);
                if (daysOpt.isEmpty()) continue;
                int days = daysOpt.getAsInt();
                if (days > 10) {
                    return new NavalPair(from, to, days);
                }
            }
        }
        return null;
    }
}
