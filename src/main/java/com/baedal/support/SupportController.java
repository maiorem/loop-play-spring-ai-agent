package com.baedal.support;

import com.baedal.support.tool.OrderTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

/**
 * Structured Output + Tool Calling + Chat Memory 통합 엔드포인트.
 *
 * <p>3주차 변경점 (숙제에서 직접 구현): Memory Advisor 추가 + X-Session-Id 헤더 처리.
 * Triage 용도에도 Memory를 연결해 두면 "같은 세션에서 반복 분류할 때 맥락이 유지"된다.
 *
 * <p>구현 방법은 {@link AssistantController}와 동일하다. 거기서 배운 패턴을 여기에도 적용하라.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient.Builder builder;
    private final PerformanceLoggingAdvisor performanceAdvisor;
    private final MessageChatMemoryAdvisor memoryAdvisor; // 3주차에서 추가
    private final OrderTools orderTools;

    // TODO [1단계-I] AssistantController와 동일한 패턴으로 X-Session-Id 헤더를 처리하라.
    //
    // 요구사항 (AssistantController와 동일):
    //   1) @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId
    //   2) .defaultAdvisors(memoryAdvisor, performanceAdvisor)
    //   3) .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
    //
    // 같은 세션 ID로 AssistantController와 SupportController 양쪽을 호출하면
    // 두 엔드포인트가 같은 대화 이력을 공유한다는 사실을 README에 검증 기록으로 남겨라.
    @PostMapping
    public SupportResponse triage(@RequestBody ChatRequest req) {
        // TODO: AssistantController처럼 X-Session-Id 헤더와 Memory Advisor를 연결하라.
        return builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(performanceAdvisor) // TODO: memoryAdvisor를 첫 번째로 추가
                .defaultTools(orderTools)
                .build()
                .prompt()
                .user(req.message())
                // TODO: .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .entity(SupportResponse.class);
    }
}
