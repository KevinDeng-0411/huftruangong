package com.aicust.service;

import com.aicust.model.ChatMessage;
import com.aicust.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ChatMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatMessageRepository messageRepository;

    private static final int MEMORY_WINDOW = 10;
    private static final int RELATED_HISTORY_LIMIT = 6;
    private static final int MIN_RELATED_TOKEN_OVERLAP = 2;
    private static final int MIN_CHINESE_CHAR_OVERLAP = 3;
    private static final Set<String> FOLLOW_UP_WORDS = Set.of(
            "\u8FD9\u4E2A", "\u8FD9\u4E2A\u95EE\u9898", "\u4E0A\u8FF0", "\u4E0A\u9762", "\u521A\u624D", "\u4E4B\u524D",
            "\u7EE7\u7EED", "\u7136\u540E", "\u518D", "\u5B83", "\u4ED6", "\u5979", "that", "it", "continue"
    );
    private static final Set<String> STOP_WORDS = Set.of(
            "\u7684", "\u4E86", "\u548C", "\u662F", "\u5728", "\u5417", "\u5462", "\u554A", "\u5440",
            "\u6211", "\u4F60", "\u4ED6", "\u5979", "\u5B83", "\u6211\u4EEC", "\u4F60\u4EEC", "\u4ED6\u4EEC",
            "a", "an", "the", "is", "are", "to", "of", "for", "and", "or", "in", "on", "with", "what", "how", "why"
    );

    public ChatMemoryService(StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             ChatMessageRepository messageRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messageRepository = messageRepository;
    }

    private String key(Long userId) {
        return "ai:memory:" + userId;
    }

    static class MemMessage {
        public String role;
        public String content;

        public MemMessage() {
        }

        public MemMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public List<Message> getHistory(Long userId) {
        String redisKey = key(userId);
        List<String> rawList = redisTemplate.opsForList().range(redisKey, 0, -1);

        List<Message> history = new ArrayList<>();
        if (rawList == null) {
            return history;
        }

        for (String json : rawList) {
            try {
                MemMessage mem = objectMapper.readValue(json, MemMessage.class);
                if ("user".equals(mem.role)) {
                    history.add(new UserMessage(mem.content));
                } else {
                    history.add(new AssistantMessage(mem.content));
                }
            } catch (Exception e) {
                System.err.println("memory parse failed: " + e.getMessage());
            }
        }
        return history;
    }

    public List<Message> getRelatedHistory(Long userId, String currentPrompt) {
        List<Message> history = getHistory(userId);
        if (history.isEmpty()) {
            return history;
        }

        String prompt = safe(currentPrompt);
        if (prompt.isBlank()) {
            return tail(history, RELATED_HISTORY_LIMIT);
        }

        if (isFollowUpPrompt(prompt)) {
            return tail(history, RELATED_HISTORY_LIMIT);
        }

        Set<String> promptTokens = tokenize(prompt);
        Set<Character> promptChineseChars = chineseChars(prompt);
        List<Message> related = new ArrayList<>();

        for (Message message : history) {
            String content = safe(message.getContent());
            if (content.isBlank()) {
                continue;
            }

            Set<String> msgTokens = tokenize(content);
            Set<Character> msgChineseChars = chineseChars(content);
            if (hasEnoughOverlap(promptTokens, msgTokens, promptChineseChars, msgChineseChars)) {
                related.add(message);
            }
        }

        if (related.isEmpty()) {
            return tail(history, 2);
        }

        return tail(related, RELATED_HISTORY_LIMIT);
    }

    @Async("taskExecutor")
    public void addMessage(Long userId, Message message) {
        String redisKey = key(userId);
        try {
            String role = (message instanceof UserMessage) ? "user" : "assistant";
            MemMessage mem = new MemMessage(role, message.getContent());
            String json = objectMapper.writeValueAsString(mem);

            redisTemplate.opsForList().rightPush(redisKey, json);
            Long size = redisTemplate.opsForList().size(redisKey);
            if (size != null && size > MEMORY_WINDOW) {
                redisTemplate.opsForList().trim(redisKey, -MEMORY_WINDOW, -1);
            }
            redisTemplate.expire(redisKey, 1, TimeUnit.HOURS);

            ChatMessage entity = new ChatMessage();
            entity.setUserId(userId);
            entity.setRole(role);
            entity.setContent(message.getContent());
            messageRepository.save(entity);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("message persist failed: " + e.getMessage());
        }
    }

    public void clear(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private List<Message> tail(List<Message> messages, int size) {
        if (messages.size() <= size) {
            return messages;
        }
        return new ArrayList<>(messages.subList(messages.size() - size, messages.size()));
    }

    private boolean hasEnoughOverlap(Set<String> promptTokens,
                                     Set<String> msgTokens,
                                     Set<Character> promptChineseChars,
                                     Set<Character> msgChineseChars) {
        long tokenOverlap = promptTokens.stream().filter(msgTokens::contains).count();
        if (tokenOverlap >= MIN_RELATED_TOKEN_OVERLAP) {
            return true;
        }

        long charOverlap = promptChineseChars.stream().filter(msgChineseChars::contains).count();
        return charOverlap >= MIN_CHINESE_CHAR_OVERLAP;
    }

    private boolean isFollowUpPrompt(String prompt) {
        String normalized = prompt.toLowerCase(Locale.ROOT);
        for (String word : FOLLOW_UP_WORDS) {
            if (normalized.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> tokenize(String text) {
        String normalized = safe(text)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\u4E00-\\u9FA5]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return Set.of();
        }

        String[] parts = normalized.split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            if (part.length() < 2 || STOP_WORDS.contains(part)) {
                continue;
            }
            tokens.add(part);
        }
        return tokens;
    }

    private Set<Character> chineseChars(String text) {
        return safe(text).chars()
                .mapToObj(c -> (char) c)
                .filter(c -> c >= 0x4E00 && c <= 0x9FA5)
                .collect(Collectors.toSet());
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}