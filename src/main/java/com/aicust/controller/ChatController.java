package com.aicust.controller;

import com.aicust.dto.ChatRequest;
import com.aicust.service.AgentLoopService;
import com.aicust.service.AiChatService;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AiChatService aiChatService;
    private final AgentLoopService agentLoopService;

    public ChatController(AiChatService aiChatService, AgentLoopService agentLoopService) {
        this.aiChatService = aiChatService;
        this.agentLoopService = agentLoopService;
    }

    /**
     * 辅助方法：获取当前登录用户 ID
     */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        // 理论上 JwtFilter 保证了这里是 Long，防止转型异常兜底
        return Long.valueOf(principal.toString());
    }

    // 流式对话（支持 RAG 检索增强 + 兴趣模式分类过滤）
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest req) {
        // ✅ 修改：忽略 req.getUserId()，使用 Token 中的 ID
        Long userId = getCurrentUserId();

        return aiChatService.streamChat(userId, req.getPrompt(), req.getMode());
    }

    // ✅ 新接口：Agent 任务模式 (支持思考、工具调用)
    @PostMapping(value = "/agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> runAgent(@RequestBody ChatRequest req) {
        // ✅ 修改：忽略 req.getUserId()，使用 Token 中的 ID
        Long userId = getCurrentUserId();

        return agentLoopService.runAgent(userId, req.getPrompt());
    }
}

