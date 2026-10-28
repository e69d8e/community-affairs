package com.li.community.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * 是否启用 AI 智能化模块
     */
    private boolean enabled = true;

    /**
     * 大模型 API Key (若留空，系统自动无缝启用离线 Mock 降级器)
     */
    private String apiKey = "";

    /**
     * OpenAI 兼容接口 Base URL (例如 DeepSeek: https://api.deepseek.com/v1)
     */
    private String baseUrl = "https://api.deepseek.com/v1";

    /**
     * 模型名称 (默认 deepseek-chat)
     */
    private String modelName = "deepseek-chat";

    /**
     * 生成采样温度
     */
    private Double temperature = 0.3;

    /**
     * 请求超时时间 (秒)
     */
    private Integer timeoutSeconds = 30;

    /**
     * 判断当前是否配置了有效的公网/本地大模型 API Key
     */
    public boolean hasValidApiKey() {
        return apiKey != null 
                && !apiKey.trim().isEmpty() 
                && !apiKey.contains("your_") 
                && !apiKey.contains("your-");
    }
}
