package com.aicust.interceptor;

import com.aicust.service.MultiLevelRateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final MultiLevelRateLimitService multiLevelRateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(MultiLevelRateLimitService multiLevelRateLimitService, ObjectMapper objectMapper) {
        this.multiLevelRateLimitService = multiLevelRateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow async dispatch (e.g. second phase of SSE) without duplicate limiting.
        if (DispatcherType.ASYNC.equals(request.getDispatcherType())) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            sendJsonError(response, 401, "Unauthorized");
            return false;
        }

        String userId = auth.getName();
        String api = request.getRequestURI();
        String key = "user:" + userId + ":api:" + api;

        // Flow:
        // Client -> Gateway(Token Bucket) -> Controller -> MultiLevelRateLimitService
        //        -> Local(Guava) -> Sentinel -> Redis(Sliding Window) -> response
        boolean allowed = multiLevelRateLimitService.allow(key, api);
        if (!allowed) {
            sendJsonError(response, 429, "Too Many Requests");
            return false;
        }

        return true;
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> data = new HashMap<>();
        data.put("code", status);
        data.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(data));
    }
}
