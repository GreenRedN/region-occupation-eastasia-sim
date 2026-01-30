package com.green.regionoccupation.controller;

import com.green.regionoccupation.service.AdjacencyService;
import com.green.regionoccupation.service.RegionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 인접 데이터 조회 API.
 */
@RestController
@RequestMapping("/api")
public class AdjacencyApiController {

    private final RegionQueryService regionQueryService;
    private final AdjacencyService adjacencyService;

    public AdjacencyApiController(RegionQueryService regionQueryService, AdjacencyService adjacencyService) {
        this.regionQueryService = regionQueryService;
        this.adjacencyService = adjacencyService;
    }

    @GetMapping("/adjacency")
    public Map<String, Object> adjacency() {
        Map<String, List<String>> neighbors = new LinkedHashMap<>();

        regionQueryService.getRegions().forEach(r -> {
            List<String> ns = adjacencyService.neighborsOf(r.key()); // List<String>
            if (ns == null) ns = List.of();
            neighbors.put(r.key(), ns.stream().sorted().toList());
        });

        return Map.of(
                "hasData", adjacencyService.hasAdjacencyData(),
                "neighbors", neighbors
        );
    }

}
