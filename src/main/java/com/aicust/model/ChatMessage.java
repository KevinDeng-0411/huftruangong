package com.aicust.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String role; // user / assistant
    @Column(columnDefinition = "TEXT")
    private String content;
    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatMessage() {}
    public ChatMessage(Long userId, String role, String content) {
        this.userId = userId;
        this.role = role;
        this.content = content;
    }
}