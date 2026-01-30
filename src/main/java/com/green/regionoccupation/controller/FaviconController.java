package com.green.regionoccupation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 브라우저가 자동으로 요청하는 /favicon.ico 처리.
 * 아이콘 파일이 없어도 204로 정상 응답하여 콘솔 500을 막는다.
 */
@RestController
public class FaviconController {

    @RequestMapping("/favicon.ico")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favicon() {
        // no-op
    }
}
