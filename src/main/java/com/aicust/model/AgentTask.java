package com.aicust.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_agent_task")
@Data
public class AgentTask {
    @Id
    private String id; // 使用 UUID 作为主键

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String prompt;

    private String status; // THINKING, EXECUTING, COMPLETED, FAILED

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}