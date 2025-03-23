package com.nhjclxc.ssetest.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * <a href="https://blog.csdn.net/u011291844/article/details/136387503">...</a>
 */
@Slf4j
@Component
public class SseClient {
    // key为uuid，value为连接的客户端
    private static final Map<String, SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();

    // key为uuid，value为所有聊天记录
    private static final Map<String, JSONArray> messageList = new ConcurrentHashMap<>();

    private static final String BOS = "<BOS>";
    private static final String EOS = "<EOS>";
    // 模拟GPT生成的返回内容
    private static final List<String> resultContentList = Arrays.asList(
            "成功属于不断努力的人。",
            "只要路是对的，就不怕路远。",
            "生活没有捷径，只有脚踏实地。"
    );

    @Value("${deepseek.api}")
    private String deepseekAPI;

    private static final String url = "https://api.deepseek.com/chat/completions";
    private static final String DONE = "data: [DONE]";
    private static final String STOP = "stop";

    /**
     * 创建连接
     */
    public SseEmitter createSse(String uuid) {

        //默认30秒超时,设置为0L则永不超时
        SseEmitter sseEmitter = new SseEmitter(0L);
        //完成后回调
        sseEmitter.onCompletion(() -> {
            log.info("[{}]结束连接...................", uuid);
            sseEmitterMap.remove(uuid);
        });
        //超时回调
        sseEmitter.onTimeout(() -> {
            log.info("[{}]连接超时...................", uuid);
        });
        //异常回调
        sseEmitter.onError(
                throwable -> {
                    try {
                        log.info("[{}]连接异常,{}", uuid, throwable.toString());
                        sseEmitter.send(SseEmitter.event()
                                .id(uuid)
                                .name("发生异常！")
                                .data("发生异常请重试！")
                                .reconnectTime(3000));
                        sseEmitterMap.put(uuid, sseEmitter);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        );
        try {
            sseEmitter.send(SseEmitter.event().reconnectTime(5000));
        } catch (IOException e) {
            e.printStackTrace();
        }
        sseEmitterMap.put(uuid, sseEmitter);
        log.info("[{}]创建sse连接成功！", uuid);
        return sseEmitter;
    }

    /**
     * 给指定用户发送消息
     */
    public void sendMessage(String uuid, String content) {
        if (null == content || "".equals(content)) {
            log.info("{} 参数异常，msg为null", uuid);
            return;
        }
        SseEmitter sseEmitter = sseEmitterMap.get(uuid);
        if (sseEmitter == null) {
            log.info("消息推送失败uuid:[{}],没有创建连接，请重试。", uuid);
            return;
        }
        String restMsgUuid = UUID.randomUUID().toString();
        try {
            // 通过一个<BOS> 和 <EOS> 来模拟本次对话的开始与结束，客户端读取到这两个特殊字符的时候做一些处理
            // 1、开始传输消息
            sseEmitter.send(SseEmitter.event().id(restMsgUuid).reconnectTime(60 * 1000L).data(BOS));

            // 2、这里模拟调用gpt实现消息回复
            // 生成一个 0 到 19 之间的随机整数
            int index = new Random().nextInt(20);
            String restMsg = "对于 '" + content + "' GPT的回复内容是：" + resultContentList.get(index);

            for (int i = 0; i < restMsg.length(); i++) {
                char c = restMsg.charAt(i);
                sseEmitter.send(SseEmitter.event().id(restMsgUuid).reconnectTime(60 * 1000L).data(c));
                Thread.sleep(100);
            }

            // 2、消息传输结束
            sseEmitter.send(SseEmitter.event().id(restMsgUuid).reconnectTime(60 * 1000L).data(EOS));

            log.info("用户{},消息id:{},推送成功:{}", uuid, restMsgUuid, restMsg);
        } catch (Exception e) {
            sseEmitterMap.remove(uuid);
            log.info("用户{},消息id:{},推送异常:{}", uuid, restMsgUuid, e.getMessage());
            sseEmitter.complete();
        }
    }

    public SseEmitter sendMessageByDeepSeek(String uuid, String content) {
        if (null == content || "".equals(content)) {
            log.info("{} 参数异常，msg为null", uuid);
            return null;
        }
        SseEmitter sseEmitter = sseEmitterMap.get(uuid);
        boolean newSseEmitterFlag = false;
        if (sseEmitter == null) {
            log.info("消息推送失败uuid:[{}],没有创建连接，请重试。", uuid);
            // 连接客户端
            sseEmitter = createSse(uuid);
            sseEmitterMap.put(uuid, sseEmitter);
            newSseEmitterFlag = true;
        }
        String restMsgUuid = UUID.randomUUID().toString();
        try {
            // 通过一个<BOS> 和 <EOS> 来模拟本次对话的开始与结束，客户端读取到这两个特殊字符的时候做一些处理
            // 1、开始传输消息
            sseEmitter.send(SseEmitter.event().id(restMsgUuid).reconnectTime(60 * 1000L).data(BOS));

            // 2、这里调用gpt实现消息回复

            // 判断是否有聊天记录
            JSONArray messages = messageList.getOrDefault(uuid, new JSONArray());
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", content); // "What are you model???"
            messages.add(message);
            messageList.putIfAbsent(uuid, messages);


            // 发送请求
            BufferedReader bufferedReader = request2DeepSeek(messages);

            // 以下是以sse的流式消息返回前端处理

            // 前面的个数据片格式：：data: {"id":"bc36e2d4-53bc-42e5-b62d-e14e5afaa23a","object":"chat.completion.chunk","created":1742647075,"model":"deepseek-chat","system_fingerprint":"fp_3a5770e1b4_prod0225","choices":[{"index":0,"delta":{"content":" today"},"logprobs":null,"finish_reason":null}]}
            // 倒数第二个数据片格式：data: {"id":"bc36e2d4-53bc-42e5-b62d-e14e5afaa23a","object":"chat.completion.chunk","created":1742647075,"model":"deepseek-chat","system_fingerprint":"fp_3a5770e1b4_prod0225","choices":[{"index":0,"delta":{"content":""},"logprobs":null,"finish_reason":"stop"}],"usage":{"prompt_tokens":8,"completion_tokens":54,"total_tokens":62,"prompt_tokens_details":{"cached_tokens":0},"prompt_cache_hit_tokens":0,"prompt_cache_miss_tokens":8}}
            // 最后第一个数据片格式：data: [DONE]
            StringBuilder responseContent = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
//                responseBody.append(line).append("\n");
//                System.out.println(responseBody);
                // 在这里使用sse将每次读取到的数据发送给前端

                if (null == line || "".equals(line)) {
                    continue;
                }

                // 1、判断是否最后一个数据片，是的话结束数据发送
                if (DONE.equals(line)) {
                    break;
                }

                // 2、判断是否倒数第二个数据片，是的话结束数据发送，并且保存本次聊天记录，因为下次聊天还要把本次聊天记录发过去，这样才能记忆聊天内容
                JSONObject response = JSONObject.parse(line.substring(5));
//                System.out.println(response);
                String stop = response.getJSONArray("choices").getJSONObject(0).getString("finish_reason");
                if (STOP.equals(stop)){

                    JSONObject system = new JSONObject();
                    system.put("role", "assistant");
                    system.put("content", responseContent.toString());
                    messages.add(system);
//                    messageList.put(uuid, messages);

                    break;
                }

                // 3、前面的 n - 2 个数据片，直接通过 sse 发送前端，并且临时保存每一个消息片，以便在最后一篇的时候用于保存本次聊天的消息记录
                JSONObject delta = response.getJSONArray("choices").getJSONObject(0).getJSONObject("delta");
                // 发送这一个消息片给前端
                // json传参保证空格不丢失 或者使用特殊字符进行占位，但是目前还不知道其他字符会不会丢失，因此，使用json从而五险在关心字符丢失问题

                sseEmitter.send(SseEmitter.event().id(restMsgUuid).reconnectTime(60 * 1000L).data(delta.toJSONString(), org.springframework.http.MediaType.APPLICATION_JSON));
                // 用于保存本次聊天记录
                responseContent.append(delta.getString("content"));
//                System.out.println(c);

            }
            bufferedReader.close();

            // 2、消息传输结束
            sseEmitter.send(SseEmitter.event().id(restMsgUuid).reconnectTime(60 * 1000L).data(EOS));

            System.out.println(messages.toJSONString());
            log.info("用户{},消息id:{},推送成功:{}", uuid, restMsgUuid, responseContent);
        } catch (Exception e) {
            sseEmitterMap.remove(uuid);
            log.info("用户{},消息id:{},推送异常:{}", uuid, restMsgUuid, e.getMessage());
            sseEmitter.complete();
        }

        // 判断是否返回新创建的sse
        return newSseEmitterFlag ? sseEmitter : null;
    }

    /**
     * 向 DeepSeek 发送聊天请求
     *
     * <a href="https://api-docs.deepseek.com/zh-cn/api/create-chat-completion"> 对话补全 </a>
     */
    @NotNull
    private BufferedReader request2DeepSeek(JSONArray messages) throws IOException {
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("messages", messages);
        jsonBody.put("model", "deepseek-chat");
        jsonBody.put("frequency_penalty", 0);
        jsonBody.put("max_tokens", 2048);
        jsonBody.put("stream", true); // 如果设置为 True，将会以 SSE（server-sent events）的形式以流式发送消息增量。消息流以 data: [DONE] 结尾。
        jsonBody.put("temperature", 1.0);
// {"messages":[{"role":"user","content":"What are you model???"}],"model":"deepseek-chat","frequency_penalty":0,"max_tokens":2048,"stream":true,"temperature":1.0}

        log.info("request2DeepSeek：{}", jsonBody.toJSONString());

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonBody.toJSONString());
        Request request = new Request.Builder()
                .url(url)
                .method("POST", requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + deepseekAPI)
                .build();
        Response response = client.newCall(request).execute();
        Reader reader = response.body().charStream();
        BufferedReader bufferedReader = new BufferedReader(reader);
        return bufferedReader;
    }


    /**
     * 断开
     */
    public void closeSse(String uuid) {
        if (sseEmitterMap.containsKey(uuid)) {
            SseEmitter sseEmitter = sseEmitterMap.get(uuid);
            sseEmitter.complete();
            sseEmitterMap.remove(uuid);
        } else {
            log.info("用户{} 连接已关闭", uuid);
        }

    }

}
