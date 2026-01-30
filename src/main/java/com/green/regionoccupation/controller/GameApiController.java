package com.green.regionoccupation.controller;

import com.green.regionoccupation.dto.GameActionRequest;
import com.green.regionoccupation.dto.GameStartRequest;
import com.green.regionoccupation.dto.GameStateDto;
import com.green.regionoccupation.dto.RegionInspectDto;
import com.green.regionoccupation.dto.TechUnlockRequest;
import com.green.regionoccupation.service.TurnGameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 턴제 게임 API.
 */
@RestController
@RequestMapping("/api/game")
public class GameApiController {

    private final TurnGameService turnGameService;

    public GameApiController(TurnGameService turnGameService) {
        this.turnGameService = turnGameService;
    }

    @GetMapping("/state")
    public GameStateDto state() {
        return turnGameService.state();
    }


    @GetMapping("/inspect")
    public RegionInspectDto inspect(@RequestParam("regionKey") String regionKey) {
        return turnGameService.inspect(regionKey);
    }

    @PostMapping("/start")
    public GameStateDto start(@Valid @RequestBody GameStartRequest request) {
        return turnGameService.start(request);
    }

    @PostMapping("/action")
    public GameStateDto action(@Valid @RequestBody GameActionRequest request) {
        return turnGameService.userAction(request);
    }

    @PostMapping("/tech/unlock")
    public GameStateDto unlockTech(@Valid @RequestBody TechUnlockRequest request) {
        return turnGameService.unlockTech(request.techCode());
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        turnGameService.reset();
        return Map.of("ok", true);
    }
}
