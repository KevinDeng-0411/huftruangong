package com.aicust.model;

public class AiPlan {

    private final String name;
    private final int dailyTokenLimit;

    public AiPlan(String name, int dailyTokenLimit) {
            this.name = name;
            this.dailyTokenLimit = dailyTokenLimit;
        }

        public String getName() {
        return name;
        }
        public int getDailyTokenLimit() {
        return dailyTokenLimit;
        }
}
