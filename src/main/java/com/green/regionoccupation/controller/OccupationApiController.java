package com.green.regionoccupation.controller;

import com.green.regionoccupation.dto.OccupationStatusDto;
import com.green.regionoccupation.dto.OccupyRequest;
import com.green.regionoccupation.dto.OccupyResponse;
import com.green.regionoccupation.dto.OwnerDto;
import com.green.regionoccupation.dto.RegionDto;
import com.green.regionoccupation.service.OccupationService;
import com.green.regionoccupation.service.RegionQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 지역 점령 API.
 * - 현재는 "동아시아(ADMIN1)" 데이터(리소스 JSON) 기준으로 동작.
 */
@RestController
@RequestMapping("/api")
public class OccupationApiController {

    private final OccupationService occupationService;
    private final RegionQueryService regionQueryService;

    public OccupationApiController(OccupationService occupationService, RegionQueryService regionQueryService) {
        this.occupationService = occupationService;
        this.regionQueryService = regionQueryService;
    }

    @GetMapping("/owners")
    public List<OwnerDto> owners() {
        return occupationService.getOwners();
    }

    @GetMapping("/regions")
    public List<RegionDto> regions() {
        return regionQueryService.getRegions();
    }

    @GetMapping("/occupation")
    public OccupationStatusDto occupation() {
        Map<String, String> occ = occupationService.getOccupationSnapshot();
        int total = regionQueryService.countRegions();
        int occupied = occ.size();
        return new OccupationStatusDto(occ, total, occupied);
    }

    @PostMapping("/occupation")
    public OccupyResponse occupy(@Valid @RequestBody OccupyRequest request) {
        return occupationService.occupy(request);
    }

    @PostMapping("/occupation/reset")
    public Map<String, Object> reset() {
        occupationService.reset();
        return Map.of("ok", true);
    }
}

