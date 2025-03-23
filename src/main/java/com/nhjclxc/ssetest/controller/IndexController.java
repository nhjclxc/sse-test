package com.nhjclxc.ssetest.controller;

import com.nhjclxc.ssetest.client.SseClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

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

    @CrossOrigin
    @GetMapping(value = "/sendMsgByFlux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Object> sendMsgByFlux(String uuid, String text, HttpServletResponse response) {
        response.setHeader("Content-Type", "text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        return sseClient.sendMsgByFlux(uuid, text);

//        List<String> words = Arrays.asList(text.split(" ")); // 模拟逐字生成
//        return Flux.fromIterable(words)
//                .delayElements(Duration.ofMillis(300)) // 每个单词延迟300ms
//                .map(word -> word + " ");

//        return Flux.create(sink -> {
//            try (BufferedReader reader = new BufferedReader(new FileReader(".gitignore"))) {
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    for (String word : line.split(" ")) { // 按单词拆分
//                        sink.next(word + " ");
//                        Thread.sleep(300); // 模拟逐字流式返回
//                    }
//                }
//                sink.complete();
//            } catch (IOException | InterruptedException e) {
//                sink.error(e);
//            }
//        }).delayElements(Duration.ofMillis(100)); // 控制流速
    }



}
