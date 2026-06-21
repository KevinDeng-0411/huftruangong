package com.aicust.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 检索服务 —— 通过 HTTP 调用 RAG 项目的混合搜索接口，
 * 将检索到的文档片段返回给对话层注入 LLM prompt。
 *
 * <p>调用链：AiChatService → RagSearchService → RAG /rag/search API
 *
 * <p>错误处理：RAG 服务不可达时返回空列表，不阻塞对话流程。
 */
@Service
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);

    private final RestTemplate restTemplate;

    @Value("${rag.base-url}")
    private String ragBaseUrl;

    @Value("${rag.username}")
    private String ragUsername;

    @Value("${rag.password}")
    private String ragPassword;

    /** 每个 hit 返回的文本最大长度（字符），避免单篇过长撑爆 LLM context */
    private static final int MAX_TEXT_LENGTH = 1000;

    public RagSearchService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 获取 Basic Auth 请求头（懒初始化，@Value 在构造后注入）。
     */
    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ragUsername != null && !ragUsername.isBlank()) {
            String auth = ragUsername + ":" + ragPassword;
            String encoded = Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encoded);
        }
        return headers;
    }

    // ==================== 公开方法 ====================

    /**
     * 调用 RAG 搜索，返回命中文档的文本片段列表。
     *
     * @param query 用户问题
     * @param topK  期望返回条数
     * @return 文档文本片段列表，RAG 不可用时返回空列表
     */
    public List<SearchHit> search(String query, int topK) {
        return search(query, topK, null);
    }

    /**
     * 带分类过滤的 RAG 搜索。
     *
     * @param query    用户问题
     * @param topK     期望返回条数
     * @param category 文档分类过滤（"历史文化"/"自然风光"/"美食特产"等），null 不过滤
     * @return 文档文本片段列表
     */
    public List<SearchHit> search(String query, int topK, String category) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Instant start = Instant.now();
        Map<String, Object> requestBody = buildRequestBody(query, topK, category);

        try {
            HttpHeaders headers = authHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    ragBaseUrl + "/rag/search", entity, Map.class);

            long elapsed = Duration.between(start, Instant.now()).toMillis();
            List<SearchHit> hits = parseHits(response.getBody());
            log.info("[RAG] query='{}' topK={} category={} → {} hits in {}ms",
                    truncate(query, 50), topK, category, hits.size(), elapsed);
            return hits;

        } catch (RestClientException e) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.warn("[RAG] service unreachable after {}ms: {} (degraded to empty result)",
                    elapsed, e.getMessage());
            return List.of();
        } catch (Exception e) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.error("[RAG] unexpected error after {}ms: {}", elapsed, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 只返回文本字符串（便捷方法，用于直接拼 prompt）。
     */
    public List<String> searchTexts(String query, int topK, String category) {
        return search(query, topK, category).stream()
                .map(SearchHit::text)
                .collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /**
     * 构建 RAG /rag/search 请求体。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(String query, int topK, String category) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("topK", Math.min(topK, 20)); // 兜底，RAG 侧也有限制

        // 分类过滤
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            Map<String, Object> filter = new LinkedHashMap<>();
            List<Map<String, Object>> conditions = new ArrayList<>();

            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("field", "category");
            cond.put("op", "eq");
            cond.put("value", category);
            conditions.add(cond);

            filter.put("conditions", conditions);
            filter.put("logic", "AND");
            body.put("filter", filter);
        }

        return body;
    }

    /**
     * 解析 RAG 返回的 JSON 响应中的 hits 数组。
     *
     * <p>RAG 返回格式：
     * <pre>
     * {
     *   "success": true,
     *   "hits": [{
     *     "chunkId": "...",
     *     "text": "...",
     *     "vectorScore": 0.85,
     *     "keywordScore": 0.67,
     *     "fusedScore": 0.80,
     *     "category": "...",
     *     "title": "..."
     *   }],
     *   "hitCount": 3
     * }
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private List<SearchHit> parseHits(Map<String, Object> responseBody) {
        if (responseBody == null) {
            return List.of();
        }

        Object success = responseBody.get("success");
        if (!Boolean.TRUE.equals(success)) {
            log.warn("[RAG] search returned success=false: {}", responseBody.get("error"));
            return List.of();
        }

        List<Map<String, Object>> hits = (List<Map<String, Object>>) responseBody.get("hits");
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        return hits.stream()
                .map(this::mapToHit)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SearchHit mapToHit(Map<String, Object> raw) {
        try {
            String text = (String) raw.getOrDefault("text", "");
            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH) + "...";
            }

            double fusedScore = raw.get("fusedScore") instanceof Number n
                    ? n.doubleValue() : 0.0;
            String title = (String) raw.getOrDefault("title", "");
            String category = (String) raw.getOrDefault("category", "");
            String chunkId = (String) raw.getOrDefault("chunkId", "");

            return new SearchHit(chunkId, text, title, category, fusedScore);
        } catch (Exception e) {
            log.warn("[RAG] failed to parse hit: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ==================== 内部类 ====================

    /**
     * RAG 搜索结果中的一条命中记录。
     */
    public record SearchHit(
            String chunkId,
            String text,
            String title,
            String category,
            double fusedScore
    ) {}
}
