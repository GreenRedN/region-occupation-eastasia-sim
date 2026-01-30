package com.green.regionoccupation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.regionoccupation.domain.Owner;
import com.green.regionoccupation.dto.OccupyRequest;
import com.green.regionoccupation.dto.OccupyResponse;
import com.green.regionoccupation.dto.OwnerDto;
import com.green.regionoccupation.exception.BadRequestException;
import com.green.regionoccupation.repository.OccupationStateRepository;
import com.green.regionoccupation.repository.RegionMetaRepository;
import com.green.regionoccupation.util.KeyUtil;
import com.green.regionoccupation.util.ResourceUtil;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 점령 상태 관리 서비스.
 * - 실습/데모 목적: 현재는 인메모리 저장(서버 재시작 시 초기화)
 */
@Service
public class OccupationService {

    private final RegionMetaRepository regionMetaRepository;
    private final OccupationStateRepository occupationStateRepository;
    private final TurnGameService turnGameService;
    private final List<OwnerDto> owners;
    private final Set<String> ownerIds;

    public OccupationService(
            RegionMetaRepository regionMetaRepository,
            OccupationStateRepository occupationStateRepository,
            TurnGameService turnGameService,
            ObjectMapper objectMapper
    ) {
        this.regionMetaRepository = regionMetaRepository;
        this.occupationStateRepository = occupationStateRepository;

        this.turnGameService = turnGameService;

        // owners.json 로딩
        String json = ResourceUtil.readClasspath("static/data/owners.json");
        try {
            List<Owner> list = objectMapper.readValue(json, new TypeReference<List<Owner>>() {});
            this.owners = list.stream().map(o -> new OwnerDto(o.id(), o.name(), o.color())).toList();
            this.ownerIds = new HashSet<>();
            for (OwnerDto o : owners) ownerIds.add(o.id());
        } catch (Exception e) {
            throw new IllegalStateException("owners.json 로딩 실패: " + e.getMessage(), e);
        }
    }

    public List<OwnerDto> getOwners() {
        return owners;
    }

    public Map<String, String> getOccupationSnapshot() {
        return occupationStateRepository.snapshot();
    }

    public OccupyResponse occupy(OccupyRequest req) {
        if (turnGameService != null && turnGameService.isRunning()) {
            throw new BadRequestException("턴제 게임 진행 중에는 자유 점령을 사용할 수 없습니다. 게임 리셋 후 사용하세요.");
        }
        String regionKey = KeyUtil.requireValidRegionKey(req.regionKey());
        String ownerId = Objects.requireNonNull(req.ownerId(), "ownerId");

        if (!ownerIds.contains(ownerId)) {
            throw new BadRequestException("존재하지 않는 ownerId: " + ownerId);
        }
        // regionKey 존재 여부
        regionMetaRepository.getByKey(regionKey);

        // 중립이면 저장 제거(깔끔하게)
        if ("NEUTRAL".equalsIgnoreCase(ownerId)) {
            occupationStateRepository.remove(regionKey);
            return new OccupyResponse(regionKey, "NEUTRAL");
        }

        occupationStateRepository.setOwnerId(regionKey, ownerId);
        return new OccupyResponse(regionKey, ownerId);
    }

    public void reset() {
        if (turnGameService != null && turnGameService.isRunning()) {
            throw new BadRequestException("턴제 게임 진행 중에는 초기화할 수 없습니다. 게임 리셋을 사용하세요.");
        }
        occupationStateRepository.reset();
    }
}

