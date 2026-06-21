package com.aicust.tool;

import java.util.function.Function;

// 简单的工具定义
public record AgentTool(
        String name,            // 工具名称 (如 "getCurrentTime")
        String description,     // 工具描述 (给 AI 看的，如 "获取当前系统时间")
        String paramSchema,     // 参数格式 (JSON Schema，这里简化为 String)
        Function<String, String> function // 实际执行的逻辑
) {}
