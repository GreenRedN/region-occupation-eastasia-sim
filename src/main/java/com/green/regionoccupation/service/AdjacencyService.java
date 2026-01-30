package com.green.regionoccupation.service;

import com.green.regionoccupation.repository.AdjacencyRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * 인접 조회/판정.
 * - 실습 버전에서는 adjacency.json이 비어 있을 수 있으며, 그 경우 neighbors는 빈 Set.
 */
@Service
public class AdjacencyService {

    private final AdjacencyRepository adjacencyRepository;

    public AdjacencyService(AdjacencyRepository adjacencyRepository) {
        this.adjacencyRepository = adjacencyRepository;
    }

    public List<String> neighborsOf(String regionKey) {
        return new ArrayList<>(adjacencyRepository.neighborsOf(regionKey));
    }

    public boolean hasAdjacencyData() {
        return adjacencyRepository.hasAnyData();
    }

    // TurnGameService 호환용 별칭
    public boolean hasData() {
        return hasAdjacencyData();
    }
}


