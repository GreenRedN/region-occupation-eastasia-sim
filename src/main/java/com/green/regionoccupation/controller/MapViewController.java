package com.green.regionoccupation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 지도 UI 진입점.
 * ("/"와 "/map"은 WebConfig에서 view-controller로 연결됨)
 */
@Controller
public class MapViewController {

    @GetMapping("/ui")
    public String ui() {
        return "map";
    }
}

