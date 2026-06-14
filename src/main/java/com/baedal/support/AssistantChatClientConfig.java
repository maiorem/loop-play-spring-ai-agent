package com.baedal.support;

import com.baedal.support.guardrail.InputGuardrailAdvisor;
import com.baedal.support.guardrail.OutputGuardrailAdvisor;
import com.baedal.support.tool.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantChatClientConfig {

    @Bean
    public ChatClient assistantChatClient(ChatClient.Builder builder,
                                          MessageChatMemoryAdvisor memoryAdvisor,
                                          PerformanceLoggingAdvisor performanceAdvisor,
                                          QuestionAnswerAdvisor ragAdvisor,
                                          InputGuardrailAdvisor inputGuardrail,
                                          OutputGuardrailAdvisor outputGuardrail,
                                          OrderTools orderTools) {
        return builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(inputGuardrail, memoryAdvisor, ragAdvisor, outputGuardrail, performanceAdvisor)
                .defaultTools(orderTools)
                .build();
    }

    @Bean
    public ChatClient supportChatClient(ChatClient.Builder builder,
                                        MessageChatMemoryAdvisor memoryAdvisor,
                                        PerformanceLoggingAdvisor performanceAdvisor,
                                        QuestionAnswerAdvisor ragAdvisor,
                                        InputGuardrailAdvisor inputGuardrail,
                                        OutputGuardrailAdvisor outputGuardrail,
                                        OrderTools orderTools) {
        return builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(inputGuardrail, memoryAdvisor, ragAdvisor, outputGuardrail, performanceAdvisor)
                .defaultTools(orderTools)
                .build();
    }
}
