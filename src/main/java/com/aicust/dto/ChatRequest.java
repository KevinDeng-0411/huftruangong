package com.aicust.dto;

public class ChatRequest {
    private Long userId;
    private String prompt;
    /**
     * 游客兴趣模式，用于 RAG 分类过滤。
     * "history"→历史文化, "nature"→自然风光, "food"→美食特产, null/""/"all"→不过滤
     */
    private String mode;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    /** 将前端传的 mode 转为 RAG 的 category 值，返回 null 表示不过滤 */
    public String toCategory() {
        if (mode == null || mode.isBlank() || "all".equalsIgnoreCase(mode)) {
            return null;
        }
        return switch (mode.toLowerCase()) {
            case "history" -> "历史文化";
            case "nature" -> "自然风光";
            case "food" -> "美食特产";
            default -> null; // 不认识的 mode 当 all 处理
        };
    }
}

