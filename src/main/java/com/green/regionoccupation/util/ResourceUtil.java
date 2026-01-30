package com.green.regionoccupation.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * classpath 리소스 읽기 유틸.
 */
public class ResourceUtil {

    private ResourceUtil() {}

    public static String readClasspath(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            return StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("리소스 읽기 실패: " + path + " (" + e.getMessage() + ")", e);
        }
    }
}

