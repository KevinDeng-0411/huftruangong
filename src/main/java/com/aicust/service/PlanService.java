package com.aicust.service;

import com.aicust.model.AiPlan;
import org.springframework.stereotype.Service;

@Service
public class PlanService {
    public AiPlan getPlan(Long userId) {
        if (userId == 2L) {
            return new AiPlan("VIP", 100_000);
        }
        return new AiPlan("FREE", 10_000);
    }

    }
