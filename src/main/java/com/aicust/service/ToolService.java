package com.aicust.service;

import com.aicust.model.AiPlan;
import com.aicust.tool.AgentTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ToolService {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);

    private final Map<String, AgentTool> tools = new HashMap<>();

    // 注入业务服务，让工具具备访问数据库/Redis的能力
    private final TokenQuotaService quotaService;
    private final PlanService planService;
    private final ObjectMapper objectMapper;

    public ToolService(TokenQuotaService quotaService, PlanService planService, ObjectMapper objectMapper) {
        this.quotaService = quotaService;
        this.planService = planService;
        this.objectMapper = objectMapper;

        // 初始化注册所有工具
        initTools();
    }

    private void initTools() {
        // 🛠️ 工具 1: 获取系统时间
        register(new AgentTool(
                "getCurrentTime",
                "获取当前服务器时间，无参数",
                "{}",
                (arg) -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ));

        // 🛠️ 工具 2: 查询用户真实余额 (Redis + Plan)
        register(new AgentTool(
                "getUserBalance",
                "查询用户当前剩余 Token 额度，参数: userId",
                "{\"userId\": \"用户ID\"}",
                (jsonArgs) -> {
                    try {
                        // 1. 解析参数
                        Long userId = parseUserId(jsonArgs);
                        if (userId == null) return "❌ 错误: 无法解析 userId";

                        // 2. 查询套餐限额 (PlanService)
                        AiPlan plan = planService.getPlan(userId);
                        int totalLimit = plan.getDailyTokenLimit();

                        // 3. 查询已用额度 (TokenQuotaService -> Redis)
                        int used = quotaService.getUsedToken(userId);

                        // 4. 计算剩余
                        int balance = totalLimit - used;

                        return String.format(
                                "用户套餐: %s\n今日限额: %d\n已使用: %d\n剩余可用: %d Tokens",
                                plan.getName(), totalLimit, used, balance
                        );
                    } catch (Exception e) {
                        log.error("工具执行失败", e);
                        return "❌ 查询失败: " + e.getMessage();
                    }
                }
        ));
    }

    /**
     * 注册工具
     */
    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    /**
     * 执行工具入口
     */
    public String execute(String toolName, String jsonArgs) {
        AgentTool tool = tools.get(toolName);
        if (tool == null) {
            return "❌ 错误: 未知工具 " + toolName;
        }

        log.info("执行工具: {} 参数: {}", toolName, jsonArgs);
        try {
            return tool.function().apply(jsonArgs);
        } catch (Exception e) {
            log.error("工具 {} 执行异常", toolName, e);
            return "❌ 工具内部错误: " + e.getMessage();
        }
    }

    /**
     * 辅助方法：从 JSON 参数中提取 userId
     * 兼容格式: "100" 或 {"userId": 100} 或 {"userId": "100"}
     */
    private Long parseUserId(String jsonArgs) {
        if (jsonArgs == null || jsonArgs.isBlank()) return null;
        try {
            // 尝试直接解析数字
            if (jsonArgs.matches("^\\d+$")) {
                return Long.parseLong(jsonArgs);
            }
            // 解析 JSON 对象
            JsonNode node = objectMapper.readTree(jsonArgs);
            if (node.has("userId")) {
                return node.get("userId").asLong();
            }
        } catch (Exception e) {
            log.warn("参数解析失败: {}", jsonArgs);
        }
        return null;
    }
}
