package com.green.regionoccupation.util;

import com.green.regionoccupation.exception.BadRequestException;

import java.util.Objects;

/**
 * regionKey 규칙 최소 검증.
 * 예: "KR:Province:Gyeonggi-do:884296e1"
 */
public class KeyUtil {

    private KeyUtil() {}

    public static String requireValidRegionKey(String key) {
        key = Objects.requireNonNull(key, "regionKey").trim();
        if (key.isEmpty()) throw new BadRequestException("regionKey is blank");

        // 최소 형식: 4파트 이상(ISO2:TYPE:NAME:HASH) - NAME 안에 ':'가 들어갈 수 있으므로 4파트 이상만 강제
        String[] parts = key.split(":");
        if (parts.length < 4) throw new BadRequestException("regionKey 형식 오류: " + key);

        String iso2 = parts[0];
        if (iso2.length() != 2) throw new BadRequestException("regionKey ISO2 형식 오류: " + key);

        return key;
    }
}

