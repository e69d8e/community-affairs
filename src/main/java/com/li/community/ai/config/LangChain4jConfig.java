package com.li.community.ai.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class LangChain4jConfig {

    private final AiProperties aiProperties;

    @Bean
    public OpenAiChatModel openAiChatModel() {
        if (!aiProperties.hasValidApiKey()) {
            log.info("未检测到有效 AI_API_KEY，LangChain4j 同步模型启用离线 Mock 降级模式");
            return null;
        }
        try {
            log.info("正在初始化 LangChain4j ChatModel -> baseUrl: {}, modelName: {}", 
                    aiProperties.getBaseUrl(), aiProperties.getModelName());
            return OpenAiChatModel.builder()
                    .apiKey(aiProperties.getApiKey())
                    .baseUrl(aiProperties.getBaseUrl())
                    .modelName(aiProperties.getModelName())
                    .temperature(aiProperties.getTemperature())
                    .timeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                    .logRequests(false)
                    .logResponses(false)
                    .build();
        } catch (Exception e) {
            log.warn("初始化 OpenAiChatModel 失败，降级为离线模式: {}", e.getMessage());
            return null;
        }
    }

    @Bean
    public OpenAiStreamingChatModel openAiStreamingChatModel() {
        if (!aiProperties.hasValidApiKey()) {
            log.info("未检测到有效 AI_API_KEY，LangChain4j 流式模型启用离线 Mock 降级模式");
            return null;
        }
        try {
            log.info("正在初始化 LangChain4j StreamingChatModel -> baseUrl: {}, modelName: {}", 
                    aiProperties.getBaseUrl(), aiProperties.getModelName());
            return OpenAiStreamingChatModel.builder()
                    .apiKey(aiProperties.getApiKey())
                    .baseUrl(aiProperties.getBaseUrl())
                    .modelName(aiProperties.getModelName())
                    .temperature(aiProperties.getTemperature())
                    .timeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                    .logRequests(false)
                    .logResponses(false)
                    .build();
        } catch (Exception e) {
            log.warn("初始化 OpenAiStreamingChatModel 失败，降级为离线模式: {}", e.getMessage());
            return null;
        }
    }
}
