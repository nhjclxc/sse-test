package com.nhjclxc.ssetest.controller;

import com.nhjclxc.ssetest.client.SseClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/sse")
public class IndexController {

    @Autowired
    private SseClient sseClient;

    @CrossOrigin
    @GetMapping("/createSse")
    public SseEmitter createSse(String uuid, HttpServletResponse response) {
        response.setHeader("Content-Type", "text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        return sseClient.createSse(uuid);
    }

    @CrossOrigin
    @GetMapping("/sendMsg")
    public SseEmitter sendMsg(String uuid, String text, HttpServletResponse response) {
        response.setHeader("Content-Type", "text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
//        sseClient.sendMessage(uuid, text);
//        return "ok";
        return sseClient.sendMessageByDeepSeek(uuid, text);
    }

    /**
     * 关闭连接
     */
    @CrossOrigin
    @GetMapping("/closeSse")
    public void closeConnect(String uuid) {
        sseClient.closeSse(uuid);
    }
}
