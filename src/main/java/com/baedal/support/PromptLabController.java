package com.baedal.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/prompt-lab")
public class PromptLabController {

    private final ChatClient.Builder builder;

    private static final ObjectMapper LENIENT_MAPPER = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

    private static final OllamaOptions JSON_OPTIONS = OllamaOptions.builder()
            .format("json")
            .temperature(0.3)
            .build();

    @PostMapping
    public PromptLabResult experiment(@RequestBody PromptLabRequest req) {
        var converter = new BeanOutputConverter<>(SupportResponse.class, LENIENT_MAPPER);
        String systemPrompt = req.useDefaultPrompt() ? BaedalPrompt.SYSTEM_PROMPT : req.systemPrompt();
        var client = builder.defaultSystem(systemPrompt).build();

        List<SupportResponse> results = new ArrayList<>();
        for (int i = 0; i < req.repeat(); i++) {
            results.add(client.prompt()
                    .user(req.message())
                    .options(JSON_OPTIONS)
                    .call()
                    .entity(converter));
        }
        return PromptLabResult.from(results);
    }

    public record PromptLabRequest(
            String systemPrompt,
            boolean useDefaultPrompt,
            String message,
            int repeat
    ) {}

    public record PromptLabResult(
            int totalRuns,
            Map<String, Long> categoryCounts,
            Map<String, Long> urgencyCounts,
            double categoryConsistency
    ) {
        public static PromptLabResult from(List<SupportResponse> results) {
            var catCounts = results.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.category().name(), Collectors.counting()));
            var urgCounts = results.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.urgency().name(), Collectors.counting()));
            long maxCat = catCounts.values().stream()
                    .mapToLong(Long::longValue).max().orElse(0);

            return new PromptLabResult(
                    results.size(), catCounts, urgCounts,
                    results.isEmpty() ? 0 : (double) maxCat / results.size()
            );
        }
    }
}
