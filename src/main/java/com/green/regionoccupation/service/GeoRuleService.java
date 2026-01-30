package com.green.regionoccupation.service;

import com.green.regionoccupation.domain.Region;
import com.green.regionoccupation.domain.RegionCoord;
import com.green.regionoccupation.repository.RegionCoordRepository;
import com.green.regionoccupation.repository.RegionMetaRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 해상 이동용 "근사" 지리 규칙.
 *
 * ✅ 핵심
 * - 항구(PORT)가 있는 해안 지역에서 출항 가능
 * - 해상 이동 소요일은 "지도 좌표 거리" 기반으로 근사 계산
 *   turns = ceil(distance / 500), min 1
 *   days  = turns * daysPerTurn
 *
 * ⚠️ 주의
 * - distance는 실제 km가 아니라, 프론트 캔버스 라벨 포인트 좌표계(픽셀 유사 값) 기반.
 */
@Service
public class GeoRuleService {

    private final RegionMetaRepository regionMetaRepository;
    private final RegionCoordRepository regionCoordRepository;

    // ======= coastal 판정용 리스트(최소한만 정교) =======
    private static final Set<String> JP_LANDLOCKED = Set.of(
            "Gunma", "Tochigi", "Saitama", "Nagano", "Yamanashi", "Gifu", "Shiga", "Nara"
    );

    private static final Set<String> KR_COASTAL = Set.of(
            "Busan", "Chungcheongnam-do", "Gangwon-do", "Gyeonggi-do", "Gyeongsangbuk-do",
            "Gyeongsangnam-do", "Incheon", "Jeju-do", "Jeollanam-do", "Jeonbuk-do", "Ulsan"
    );

    private static final Set<String> KP_COASTAL = Set.of(
            "Kangwon", "North Hamgyong", "South Hamgyong", "Rason",
            "North Pyongan", "South Pyongan", "South Hwanghae", "Nampo"
    );

    private static final Set<String> CN_COASTAL = Set.of(
            "Fujian", "Guangdong", "Guangxi", "Hainan", "Hebei", "Hong Kong",
            "Jiangsu", "Liaoning", "Macau", "Shandong", "Shanghai", "Tianjin", "Zhejiang"
    );

    private static final Set<String> TW_LANDLOCKED = Set.of("Nantou");

    public GeoRuleService(RegionMetaRepository regionMetaRepository,
                          RegionCoordRepository regionCoordRepository) {
        this.regionMetaRepository = regionMetaRepository;
        this.regionCoordRepository = regionCoordRepository;
    }

    public boolean isCoastal(String regionKey) {
        Region r = regionMetaRepository.getByKey(regionKey);
        String iso2 = r.iso2();
        String name = r.nameEn();

        if ("MN".equalsIgnoreCase(iso2)) return false;
        if ("KR".equalsIgnoreCase(iso2)) return KR_COASTAL.contains(name);
        if ("KP".equalsIgnoreCase(iso2)) return KP_COASTAL.contains(name);
        if ("CN".equalsIgnoreCase(iso2)) return CN_COASTAL.contains(name);
        if ("JP".equalsIgnoreCase(iso2)) return !JP_LANDLOCKED.contains(name);
        if ("TW".equalsIgnoreCase(iso2)) return !TW_LANDLOCKED.contains(name);

        // 데이터에 새로운 국가가 추가될 경우: 기본은 "해안 아님"으로 둔다.
        return false;
    }

    /**
     * 거리 기반 해상 이동 소요일(일) 근사.
     */
    public OptionalInt travelDaysBetweenCoastalRegions(String fromCoastalKey, String toCoastalKey, int daysPerTurn) {
        if (!isCoastal(fromCoastalKey) || !isCoastal(toCoastalKey)) return OptionalInt.empty();

        Optional<RegionCoord> aOpt = regionCoordRepository.find(fromCoastalKey);
        Optional<RegionCoord> bOpt = regionCoordRepository.find(toCoastalKey);
        if (aOpt.isEmpty() || bOpt.isEmpty()) {
            // 좌표 데이터가 일부 누락된 경우라도 게임이 멈추지 않도록 기본값(2턴)으로 처리
            int dpt = Math.max(1, daysPerTurn);
            return OptionalInt.of(2 * dpt);
        }

        RegionCoord a = aOpt.get();
        RegionCoord b = bOpt.get();

        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dist = Math.hypot(dx, dy);

        int turns = (int) Math.ceil(dist / 500.0);
        if (turns < 1) turns = 1;

        int dpt = Math.max(1, daysPerTurn);
        int days = turns * dpt;
        return OptionalInt.of(days);
    }
}
