package com.green.regionoccupation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.regionoccupation.domain.BuildingType;
import com.green.regionoccupation.domain.Unit;
import com.green.regionoccupation.dto.GameActionRequest;
import com.green.regionoccupation.dto.GameStartRequest;
import com.green.regionoccupation.dto.GameStateDto;
import com.green.regionoccupation.repository.*;
import com.green.regionoccupation.service.GeoRuleService;
import com.green.regionoccupation.service.TurnGameService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 규칙 검증 테스트(서버/스프링 컨텍스트 없이 순수 자바로 실행).
 */
public class RuleEngineTests {

    private record Engine(
            TurnGameService game,
            RegionMetaRepository meta,
            AdjacencyRepository adjacency,
            OccupationStateRepository occ,
            BuildingStateRepository buildings,
            UnitStateRepository units,
            GeoRuleService geo
    ) {}

    private Engine engine() {
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();
        RegionMetaRepository meta = new RegionMetaRepository(om);
        RegionStatRepository stat = new RegionStatRepository(om);
        AdjacencyRepository adjacency = new AdjacencyRepository(om);
        OccupationStateRepository occ = new OccupationStateRepository();
        BuildingStateRepository buildings = new BuildingStateRepository();
        UnitStateRepository units = new UnitStateRepository();
        GovernanceStateRepository governance = new GovernanceStateRepository();
        ReconStateRepository recon = new ReconStateRepository();
        RegionCoordRepository coords = new RegionCoordRepository(om);
        GeoRuleService geo = new GeoRuleService(meta, coords);
        TurnGameService game = new TurnGameService(occ, buildings, units, meta, stat, adjacency, geo, governance, recon);
        return new Engine(game, meta, adjacency, occ, buildings, units, geo);
    }

    private static String pickKeyWithNeighbors(RegionMetaRepository meta, AdjacencyRepository adj) {
        List<String> keys = new ArrayList<>(meta.keys());
        Collections.sort(keys);
        for (String k : keys) {
            if (!adj.neighborsOf(k).isEmpty()) return k;
        }
        throw new IllegalStateException("No region with neighbors found");
    }

    private static String pickFirstNeighbor(AdjacencyRepository adj, String from) {
        List<String> ns = new ArrayList<>(adj.neighborsOf(from));
        assertFalse(ns.isEmpty(), "Expected at least 1 neighbor");
        Collections.sort(ns);
        return ns.getFirst();
    }

    private static Unit userUnit(UnitStateRepository units) {
        // TurnGameService의 유저 세력은 B로 고정(요청 반영)
        List<Unit> us = units.byOwner("B");
        assertFalse(us.isEmpty(), "User unit missing");
        return us.getFirst();
    }

    private static void setSoldiers(Unit u, int soldiers) {
        int cur = u.soldiers();
        u.addSoldiers(soldiers - cur);
        assertEquals(soldiers, u.soldiers());
    }

    @Test
    void neutral_is_immediate_occupation_and_unit_moves() {
        Engine e = engine();

        String home = pickKeyWithNeighbors(e.meta, e.adjacency);
        String to = pickFirstNeighbor(e.adjacency, home);

        e.game.start(new GameStartRequest(home, 0, null));

        // 시작 병력은 0이므로, 이동/점령 테스트를 위해 병력을 임의로 세팅한다.
        setSoldiers(userUnit(e.units), 10);

        // 기획서 규칙: 중립은 이동(MOVE)으로 '진입 즉시 점령'
        GameStateDto s = e.game.userAction(new GameActionRequest("MOVE", home, to, null, null, null));

        assertEquals("B", s.occupation().get(to), "Neutral target should become B");
        assertTrue(s.units().stream().anyMatch(u -> "B".equals(u.ownerId()) && to.equals(u.regionKey())),
                "User unit should move to captured region");
    }

    @Test
    void combat_ratio_1_to_1_defender_holds() {
        Engine e = engine();

        String home = pickKeyWithNeighbors(e.meta, e.adjacency);
        String to = pickFirstNeighbor(e.adjacency, home);

        e.game.start(new GameStartRequest(home, 0, null));

        // 준비기간(내정 턴) 2턴은 전투가 금지됨
        e.game.userAction(new GameActionRequest("PASS", null, null, null, null, null));
        e.game.userAction(new GameActionRequest("PASS", null, null, null, null, null));

        // force enemy owner (garrison=60)
        e.occ.occupy(to, "A");
        setSoldiers(userUnit(e.units), 60);

        GameStateDto s = e.game.userAction(new GameActionRequest("ATTACK", home, to, null, null, null));
        assertEquals("A", s.occupation().get(to), "At ratio 1.0, defense should hold");
    }

    @Test
    void combat_ratio_2_to_1_attacker_captures() {
        Engine e = engine();

        String home = pickKeyWithNeighbors(e.meta, e.adjacency);
        String to = pickFirstNeighbor(e.adjacency, home);

        e.game.start(new GameStartRequest(home, 0, null));

        // 준비기간(내정 턴) 2턴은 전투가 금지됨
        e.game.userAction(new GameActionRequest("PASS", null, null, null, null, null));
        e.game.userAction(new GameActionRequest("PASS", null, null, null, null, null));

        e.occ.occupy(to, "A");
        setSoldiers(userUnit(e.units), 120); // 120 / 60 = 2.0

        GameStateDto s = e.game.userAction(new GameActionRequest("ATTACK", home, to, null, null, null));
        assertEquals("B", s.occupation().get(to), "At ratio 2.0, attacker should capture");
    }

    @Test
    void wall_bonus_blocks_capture_under_threshold() {
        Engine e = engine();

        String home = pickKeyWithNeighbors(e.meta, e.adjacency);
        String to = pickFirstNeighbor(e.adjacency, home);

        e.game.start(new GameStartRequest(home, 0, null));

        // 준비기간(내정 턴) 2턴은 전투가 금지됨
        e.game.userAction(new GameActionRequest("PASS", null, null, null, null, null));
        e.game.userAction(new GameActionRequest("PASS", null, null, null, null, null));

        e.occ.occupy(to, "A");
        e.buildings.add(to, BuildingType.WALL);
        setSoldiers(userUnit(e.units), 120); // 120 / (60*1.4=84) = 1.428...

        GameStateDto s = e.game.userAction(new GameActionRequest("ATTACK", home, to, null, null, null));
        assertEquals("A", s.occupation().get(to), "WALL should raise defense and block capture");
    }
}
