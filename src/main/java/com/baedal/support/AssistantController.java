package com.baedal.support;

import com.baedal.support.tool.OrderTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

/**
 * Tool Calling이 적용된 자연어 응답 엔드포인트.
 * <p>
 * {@code /api/v1/support}가 Structured Output(JSON)을 반환하는 데 반해,
 * 이 엔드포인트는 <b>Tool 호출의 흐름을 평문으로 관찰</b>하기 위한 용도다.
 * DEBUG 로그와 함께 보면 Tool이 언제 어떻게 호출되는지 직관적으로 이해할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final ChatClient.Builder builder;
    private final PerformanceLoggingAdvisor performanceAdvisor;
    private final OrderTools orderTools;

    @PostMapping
    public String ask(@RequestBody ChatRequest req) {
        return builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(performanceAdvisor)
                .defaultTools(orderTools)
                .build()
                .prompt()
                .user(req.message())
                .call()
                .content();
    }
}
