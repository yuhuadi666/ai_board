package org.example.aiboard.config;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 配置类
 * 配置 Qwen 对话模型和带记忆的 AI 服务
 */
@Configuration
@EnableConfigurationProperties(QwenProperties.class)
public class LangChain4jConfig {

    @Bean
    public ChatModel chatLanguageModel(QwenProperties properties) {
        return QwenChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .maxTokens(properties.getMaxTokens())
                .temperature(properties.getTemperature())
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel(QwenProperties properties) {
        return QwenStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .maxTokens(properties.getMaxTokens())
                .temperature(properties.getTemperature())
                .build();
    }

    /**
     * 共享的 ChatMemoryStore，按 sessionId 存储不同会话的记忆
     */
    @Bean
    public ChatMemoryStore chatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }

    /**
     * 为每个 sessionId 提供独立的 ChatMemory，最多保留 10 轮对话
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    /**
     * 带记忆的 AI 助手接口
     */
    public interface Assistant {
        String chat(@MemoryId String sessionId, @UserMessage String userMessage);
    }

    @Bean
    public Assistant assistant(ChatModel chatLanguageModel,
                               ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(Assistant.class)
                .chatModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }
}
