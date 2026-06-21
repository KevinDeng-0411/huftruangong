package com.aicust.service;

import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class SensitiveWordService {

    // 简单黑名单 (生产环境建议接入第三方 API)
    private static final Set<String> BLACKLIST = Set.of(
            "暴力", "赌博", "涉黄", "炸弹", "笨蛋", "杀人"
    );

    /**
     * 检查是否包含敏感词
     */
    public boolean hasSensitiveWord(String text) {
        if (text == null || text.isBlank()) return false;
        for (String word : BLACKLIST) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}