package com.baedal.support.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/session")
public class SessionController {

    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;

    @GetMapping("/{sessionId}/messages")
    public List<MessageView> messages(@PathVariable String sessionId) {
        return chatMemory.get(sessionId).stream().map(MessageView::from).toList();
    }

    @DeleteMapping("/{sessionId}")
    public void clear(@PathVariable String sessionId) {
        chatMemory.clear(sessionId);
        log.info("[Session] clear sessionId={}", sessionId);
    }

    @GetMapping("/ids")
    public List<String> sessions() {
        return chatMemoryRepository.findConversationIds();
    }

    public record MessageView(String type, String content) {
        static MessageView from(Message m) {
            return new MessageView(m.getMessageType().name(), m.getText());
        }
    }
}
