package com.green.regionoccupation.service;

import com.green.regionoccupation.domain.Region;
import com.green.regionoccupation.dto.RegionDto;
import com.green.regionoccupation.repository.RegionMetaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 지역 조회 서비스.
 */
@Service
public class RegionQueryService {

    private final RegionMetaRepository regionMetaRepository;

    public RegionQueryService(RegionMetaRepository regionMetaRepository) {
        this.regionMetaRepository = regionMetaRepository;
    }

    public List<RegionDto> getRegions() {
        return regionMetaRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public int countRegions() {
        return regionMetaRepository.count();
    }

    private RegionDto toDto(Region r) {
        return new RegionDto(
                r.key(),
                r.iso2(),
                r.type(),
                r.nameKo(),
                r.nameEn(),
                r.displayKo(),
                r.displayEn()
        );
    }
}

