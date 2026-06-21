package com.aicust.service;

import org.springframework.stereotype.Service;

@Service
public class TokenEstimator {

    private final TokenCalibrationService calibrationService;

    public TokenEstimator(TokenCalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    /**
     * 基础字符统计逻辑
     */
    private int estimateByChar(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cn = 0;
        int en = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) {
                cn++;
            } else if (Character.isLetter(c)) {
                en++;
            }
        }
        // 粗略估算公式
        return (int) (cn * 1.8 + en * 1.2);
    }

    /**
     * 核心估算方法
     * 对应 AiChatService.java 第 31 行
     */
    public int estimate(String model, String text) {
        // 1. 基础估算
        int base = estimateByChar(text);

        // 2. 获取动态校准因子 (如果 CalibrationService 还没写好，这里可以暂时返回 1.0)
        double factor = calibrationService.getFactor(model);

        // 3. 计算最终结果 (+100 是预留的 Prompt Template 开销)
        return (int) Math.ceil(base * factor + 100);
    }
}