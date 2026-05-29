package com.baedal.support;

import com.baedal.support.tool.OrderTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient.Builder builder;
    private final PerformanceLoggingAdvisor performanceAdvisor;
    private final MessageChatMemoryAdvisor memoryAdvisor; // 3주차에서 추가
    private final OrderTools orderTools;

    @PostMapping
    public SupportResponse triage(@RequestBody ChatRequest req,
                                  @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId) {
        return builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(memoryAdvisor, performanceAdvisor)
                .defaultTools(orderTools)
                .build()
                .prompt()
                .user(req.message())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .entity(SupportResponse.class);
    }
}
