package org.example.aiboard.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.common.Role;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.example.aiboard.config.QwenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QwenThinkingStreamService {

    private static final Logger log = LoggerFactory.getLogger(QwenThinkingStreamService.class);

    private final QwenProperties properties;
    private final Generation generation;

    public QwenThinkingStreamService(QwenProperties properties) {
        this.properties = properties;
        this.generation = new Generation();
    }

    public record StreamCompletion(String content, String thinking) {}

    public void streamPrompt(
            String prompt,
            Consumer<String> onThinking,
            Consumer<StreamCompletion> onComplete,
            Consumer<Throwable> onError) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            onError.accept(new IllegalStateException("Qwen API key is not configured"));
            return;
        }

        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        try {
            generation.streamCall(buildParam(prompt), new ResultCallback<>() {
                @Override
                public void onEvent(GenerationResult result) {
                    Message message = extractMessage(result);
                    if (message == null) {
                        return;
                    }
                    String reasoning = message.getReasoningContent();
                    if (reasoning != null && !reasoning.isEmpty()) {
                        thinkingBuilder.append(reasoning);
                        onThinking.accept(reasoning);
                    }
                    String content = message.getContent();
                    if (content != null && !content.isEmpty()) {
                        contentBuilder.append(content);
                    }
                }

                @Override
                public void onComplete() {
                    onComplete.accept(new StreamCompletion(contentBuilder.toString(), thinkingBuilder.toString()));
                    latch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    errorRef.set(e);
                    latch.countDown();
                }
            });
        } catch (Exception e) {
            onError.accept(e);
            return;
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onError.accept(e);
            return;
        }

        Throwable error = errorRef.get();
        if (error != null) {
            log.warn("qwen streaming failed: {}", error.getMessage());
            onError.accept(error);
        }
    }

    private GenerationParam buildParam(String prompt) {
        Message userMessage = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();

        var builder = GenerationParam.builder()
                .apiKey(properties.getApiKey())
                .model(properties.getModelName())
                .messages(List.of(userMessage))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true);

        if (properties.getTemperature() != null) {
            builder.temperature(properties.getTemperature());
        }
        if (properties.getMaxTokens() != null) {
            builder.maxTokens(properties.getMaxTokens());
        }
        if (Boolean.TRUE.equals(properties.getEnableThinking())) {
            builder.enableThinking(true);
        }
        return builder.build();
    }

    private static Message extractMessage(GenerationResult result) {
        if (result == null
                || result.getOutput() == null
                || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            return null;
        }
        return result.getOutput().getChoices().get(0).getMessage();
    }
}
