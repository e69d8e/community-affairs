package com.li.community.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.community.ai.config.AiProperties;
import com.li.community.ai.service.AiAssistantService;
import com.li.community.ai.service.fallback.AiMockEngine;
import com.li.community.common.result.PageResult;
import com.li.community.mapper.BizAiChatRecordMapper;
import com.li.community.mapper.SysNoticeMapper;
import com.li.community.model.dto.AiOrderParseDto;
import com.li.community.model.dto.AiOrderTriageDto;
import com.li.community.model.entity.BizAiChatRecord;
import com.li.community.model.entity.SysNotice;
import com.li.community.model.vo.AiChatRecordVo;
import com.li.community.model.vo.AiOrderParseVo;
import com.li.community.model.vo.AiOrderTriageVo;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    private final AiProperties aiProperties;
    private final AiMockEngine aiMockEngine;
    private final SysNoticeMapper sysNoticeMapper;
    private final BizAiChatRecordMapper aiChatRecordMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<OpenAiChatModel> chatModelProvider;
    private final ObjectProvider<OpenAiStreamingChatModel> streamingChatModelProvider;

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public SseEmitter chatStream(String prompt, List<Map<String, String>> history, Long communityId, Long userId) {
        // 创建 120 秒超时的 SseEmitter
        SseEmitter emitter = new SseEmitter(120_000L);

        // 如果用户已登录，立即持久化用户提问记录
        if (userId != null) {
            saveChatRecord(userId, "user", prompt, null, communityId);
        }

        executorService.submit(() -> {
            try {
                // 1. 构建 RAG 知识背景 (系统最新通知公告 + 社区办事指引)
                String ragContext = buildRagContext(communityId);

                OpenAiStreamingChatModel streamingModel = streamingChatModelProvider.getIfAvailable();

                if (streamingModel != null && aiProperties.hasValidApiKey()) {
                    log.info("启用 LangChain4j 流式调用 -> prompt: {}", prompt);
                    executeLangChainStreaming(streamingModel, prompt, history, ragContext, emitter, communityId, userId);
                } else {
                    log.info("未启用公网大模型或无有效 Key，使用内置离线 Mock 引擎流式输出 -> prompt: {}", prompt);
                    executeMockStreaming(prompt, emitter, communityId, userId);
                }
            } catch (Exception e) {
                log.error("流式问答执行异常: {}", e.getMessage(), e);
                try {
                    executeMockStreaming(prompt, emitter, communityId, userId);
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        emitter.onCompletion(() -> log.debug("SseEmitter completed"));
        emitter.onTimeout(() -> {
            log.warn("SseEmitter timeout");
            emitter.complete();
        });
        emitter.onError(e -> log.error("SseEmitter error: {}", e.getMessage()));

        return emitter;
    }

    @Override
    public PageResult<AiChatRecordVo> getChatHistory(Long userId, Long page, Long size) {
        if (userId == null) {
            return PageResult.of(Collections.emptyList(), 0L, size, page);
        }
        Page<BizAiChatRecord> mpPage = new Page<>(page, size);
        LambdaQueryWrapper<BizAiChatRecord> wrapper = new LambdaQueryWrapper<BizAiChatRecord>()
                .eq(BizAiChatRecord::getUserId, userId)
                .orderByDesc(BizAiChatRecord::getId);
        Page<BizAiChatRecord> resultPage = aiChatRecordMapper.selectPage(mpPage, wrapper);

        List<BizAiChatRecord> records = resultPage.getRecords();
        if (records == null || records.isEmpty()) {
            return PageResult.of(Collections.emptyList(), resultPage.getTotal(), size, page);
        }

        // 倒序拉取的是最新历史，转换为正序以供前端从上到下按时间呈现
        List<BizAiChatRecord> sortedRecords = new ArrayList<>(records);
        Collections.reverse(sortedRecords);

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        List<AiChatRecordVo> voList = sortedRecords.stream().map(r -> {
            Map<String, Object> card = null;
            if (StrUtil.isNotBlank(r.getCardJson())) {
                try {
                    card = objectMapper.readValue(r.getCardJson(), new TypeReference<Map<String, Object>>() {});
                } catch (Exception ignored) {}
            }
            return AiChatRecordVo.builder()
                    .id(r.getId())
                    .role(r.getRole())
                    .content(r.getContent())
                    .card(card)
                    .time(r.getCreateTime() != null ? r.getCreateTime().format(timeFmt) : "")
                    .createTime(r.getCreateTime())
                    .build();
        }).collect(Collectors.toList());

        return PageResult.of(voList, resultPage.getTotal(), size, page);
    }

    @Override
    public void clearChatHistory(Long userId) {
        if (userId == null) return;
        aiChatRecordMapper.delete(new LambdaQueryWrapper<BizAiChatRecord>()
                .eq(BizAiChatRecord::getUserId, userId));
    }

    private void saveChatRecord(Long userId, String role, String content, String cardJson, Long communityId) {
        if (userId == null) return;
        try {
            BizAiChatRecord record = BizAiChatRecord.builder()
                    .userId(userId)
                    .role(role)
                    .content(content)
                    .cardJson(cardJson)
                    .communityId(communityId)
                    .deleted(0)
                    .createTime(LocalDateTime.now())
                    .build();
            aiChatRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("保存 AI 聊天记录失败: {}", e.getMessage());
        }
    }

    @Override
    public AiOrderParseVo parseOrder(AiOrderParseDto dto) {
        String rawText = dto.getRawText();
        OpenAiChatModel chatModel = chatModelProvider.getIfAvailable();

        if (chatModel != null && aiProperties.hasValidApiKey()) {
            try {
                String systemPrompt = """
                        你是一位智慧社区物业报修工单分析专家。请根据居民提交的口语化报修长文本，提炼为标准规范的工单数据。
                        请直接输出纯 JSON 对象，严禁输出任何 Markdown 标记或多余文字。
                        JSON 格式规范：
                        {
                          "title": "简短规范的工单标题，不超过18字",
                          "category": "必须从以下选一个：给排水系统、强电弱电、电梯公用、门禁安防、公用设施、环境卫生",
                          "isUrgent": 0或1 (0-普通, 1-紧急。管道大面积爆裂喷水、大面积停电短路、电梯困人等具有人身或次生危害定为1),
                          "urgentReason": "定级为普通或紧急的专业判断原因，简要精炼",
                          "standardContent": "基于居民原意整理后的标准化、专业化工单报修描述"
                        }
                        """;

                String userPrompt = "居民口语化报修描述：" + rawText;

                ChatMessage sysMsg = SystemMessage.from(systemPrompt);
                ChatMessage usrMsg = UserMessage.from(userPrompt);

                String response = chatModel.chat(List.of(sysMsg, usrMsg)).aiMessage().text();
                log.info("LangChain4j 工单解析响应: {}", response);

                return parseOrderJson(response, rawText);
            } catch (Exception e) {
                log.warn("LangChain4j 解析工单异常，降级至内置规则引擎: {}", e.getMessage());
            }
        }
        return aiMockEngine.parseOrderMock(rawText);
    }

    @Override
    public AiOrderTriageVo triageOrder(AiOrderTriageDto dto) {
        OpenAiChatModel chatModel = chatModelProvider.getIfAvailable();

        if (chatModel != null && aiProperties.hasValidApiKey()) {
            try {
                String systemPrompt = """
                        你是一位智慧社区工程物业检修总监。请针对居民申报的待初审工单，提供专业的初审研判与维修建议。
                        请直接输出纯 JSON 对象，严禁输出任何 Markdown 标记或多余文字。
                        JSON 格式规范：
                        {
                          "faultDiagnosis": "针对故障成因与机理的简明专业诊断（不超过40字）",
                          "maintenanceTips": "维修师傅到场排查工具携带与检修注意事项，分条清晰列出（不超过120字）",
                          "recommendedRole": "推荐指派的维修工种（如：水暖维修工、特种电工、电梯维保技师、通用综合维修工）",
                          "estimatedHours": 预计修复耗时（小时，支持小数如 1.0, 1.5, 2.0）,
                          "suggestedAuditRemark": "建议网格员或物业审核时填写的规范审核意见批注（不超过50字）"
                        }
                        """;

                String userPrompt = String.format("""
                        工单信息如下：
                        - 工单编号：%s
                        - 工单标题：%s
                        - 报修分类：%s
                        - 紧急加急状态：%s
                        - 居民报修详述：%s
                        - 现场照片张数：%d 张
                        """,
                        dto.getOrderId(),
                        dto.getTitle() != null ? dto.getTitle() : "未命名",
                        dto.getCategory() != null ? dto.getCategory() : "综合杂项",
                        dto.getIsUrgent() != null && dto.getIsUrgent() == 1 ? "加急紧急" : "普通处理",
                        dto.getContent() != null ? dto.getContent() : "无详细描述",
                        dto.getImages() != null ? dto.getImages().size() : 0);

                ChatMessage sysMsg = SystemMessage.from(systemPrompt);
                ChatMessage usrMsg = UserMessage.from(userPrompt);

                String response = chatModel.chat(List.of(sysMsg, usrMsg)).aiMessage().text();
                log.info("LangChain4j 工单研判响应: {}", response);

                return parseTriageJson(response, dto);
            } catch (Exception e) {
                log.warn("LangChain4j 工单研判异常，降级至内置规则研判: {}", e.getMessage());
            }
        }
        return aiMockEngine.triageOrderMock(dto);
    }

    /**
     * 动态构建 RAG 检索上下文
     */
    private String buildRagContext(Long communityId) {
        StringBuilder sb = new StringBuilder();
        try {
            LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                    .eq(SysNotice::getStatus, 1)
                    .orderByDesc(SysNotice::getIsTop)
                    .orderByDesc(SysNotice::getCreateTime)
                    .last("LIMIT 4");

            if (communityId != null) {
                wrapper.eq(SysNotice::getCommunityId, communityId);
            }

            List<SysNotice> notices = sysNoticeMapper.selectList(wrapper);
            if (notices != null && !notices.isEmpty()) {
                sb.append("【社区最新有效通知公告 (优先据此提醒防范)】:\n");
                for (SysNotice n : notices) {
                    String cleanContent = StrUtil.cleanBlank(n.getContent() != null ? n.getContent().replaceAll("<[^>]+>", " ") : "");
                    if (cleanContent.length() > 100) {
                        cleanContent = cleanContent.substring(0, 100) + "...";
                    }
                    sb.append(String.format("- 公告标题《%s》: %s\n", n.getTitle(), cleanContent));
                }
            }
        } catch (Exception e) {
            log.warn("构建 RAG 公告上下文失败，忽略: {}", e.getMessage());
        }
        return sb.toString();
    }

    /**
     * 执行 LangChain4j 流式调用并实时推流
     */
    private void executeLangChainStreaming(OpenAiStreamingChatModel model,
                                          String prompt,
                                          List<Map<String, String>> history,
                                          String ragContext,
                                          SseEmitter emitter,
                                          Long communityId,
                                          Long userId) {
        List<ChatMessage> messages = new ArrayList<>();

        String systemPrompt = "你是'智慧社区综合事务管理系统'专属的 AI 社区百事通管家。" +
                "请用亲切、条理清晰、专业的语气解答居民关于社区生活、办事政策、物业报修、邻里活动等提问。" +
                (ragContext.isBlank() ? "" : "\n请参考以下系统实时知识库与通知公告回答：\n" + ragContext);

        messages.add(SystemMessage.from(systemPrompt));

        // 载入最近历史上下文 (最多取最近 6 条 / 3 轮)
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                Map<String, String> item = history.get(i);
                String role = item.get("role");
                String content = item.get("content");
                if (content != null && !content.isBlank()) {
                    if ("user".equalsIgnoreCase(role)) {
                        messages.add(UserMessage.from(content));
                    } else if ("assistant".equalsIgnoreCase(role)) {
                        messages.add(AiMessage.from(content));
                    }
                }
            }
        }

        messages.add(UserMessage.from(prompt));

        StringBuilder fullReply = new StringBuilder();
        AtomicBoolean savedAssistant = new AtomicBoolean(false);

        model.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                fullReply.append(partialResponse);
                sendSseChunk(emitter, partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                // 检查意图识别并发送动作卡片
                Map<String, Object> card = aiMockEngine.detectActionCard(prompt, fullReply.toString());
                if (card != null) {
                    sendSseCard(emitter, card);
                }

                // 持久化助手回复消息
                if (userId != null && savedAssistant.compareAndSet(false, true)) {
                    String cardJson = null;
                    if (card != null) {
                        try {
                            cardJson = objectMapper.writeValueAsString(card);
                        } catch (Exception ignored) {}
                    }
                    saveChatRecord(userId, "assistant", fullReply.toString(), cardJson, communityId);
                }

                sendSseDone(emitter);
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                log.warn("LangChain4j 流式传输中断，启动 Mock 兜底补充: {}", error.getMessage());
                try {
                    executeMockStreaming(prompt, emitter, communityId, userId);
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });
    }

    /**
     * 离线 Mock 逐字流式打字模拟
     */
    private void executeMockStreaming(String prompt, SseEmitter emitter, Long communityId, Long userId) {
        try {
            String answer = aiMockEngine.matchFaq(prompt);
            if (answer == null) {
                answer = String.format("您好！我是社区智能百事通管家。您咨询的“%s”已收到。\n\n" +
                        "针对该项事务，建议您查看平台「社区公告」或进入「个人中心」办理对应业务。" +
                        "如遇突发水电设备故障或环境卫生问题，可点击下方卡片随时发起在线报修，责任网格员将竭诚为您服务！", prompt);
            }

            // 模拟打字机流式逐块输出 (按段落或小句子切分)
            String[] segments = answer.split("(?<=[，。！？\n])");
            for (String seg : segments) {
                sendSseChunk(emitter, seg);
                Thread.sleep(40); // 40ms 打字间隔
            }

            // 发送意图识别业务直达卡片
            Map<String, Object> card = aiMockEngine.detectActionCard(prompt, answer);
            if (card != null) {
                sendSseCard(emitter, card);
            }

            // 持久化 Mock 助手回复消息
            if (userId != null) {
                String cardJson = null;
                if (card != null) {
                    try {
                        cardJson = objectMapper.writeValueAsString(card);
                    } catch (Exception ignored) {}
                }
                saveChatRecord(userId, "assistant", answer, cardJson, communityId);
            }

            sendSseDone(emitter);
            emitter.complete();
        } catch (Exception e) {
            log.error("Mock 流式输出异常", e);
            emitter.complete();
        }
    }

    private void sendSseChunk(SseEmitter emitter, String chunk) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", "chunk");
            data.put("content", chunk);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.debug("SSE 客户端断开连接: {}", e.getMessage());
        }
    }

    private void sendSseCard(SseEmitter emitter, Map<String, Object> card) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", "card");
            data.put("card", card);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.debug("SSE 发送卡片失败: {}", e.getMessage());
        }
    }

    private void sendSseDone(SseEmitter emitter) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", "done");
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.debug("SSE 发送结束符失败: {}", e.getMessage());
        }
    }

    private AiOrderParseVo parseOrderJson(String json, String rawText) {
        try {
            String cleaned = cleanJsonString(json);
            JsonNode root = objectMapper.readTree(cleaned);
            return AiOrderParseVo.builder()
                    .title(root.path("title").asText("居民在线报修工单"))
                    .category(root.path("category").asText("给排水系统"))
                    .isUrgent(root.path("isUrgent").asInt(0))
                    .urgentReason(root.path("urgentReason").asText("日常报修"))
                    .standardContent(root.path("standardContent").asText(rawText))
                    .build();
        } catch (Exception e) {
            log.warn("解析工单 JSON 异常，回退至规则提取: {}", e.getMessage());
            return aiMockEngine.parseOrderMock(rawText);
        }
    }

    private AiOrderTriageVo parseTriageJson(String json, AiOrderTriageDto dto) {
        try {
            String cleaned = cleanJsonString(json);
            JsonNode root = objectMapper.readTree(cleaned);
            return AiOrderTriageVo.builder()
                    .faultDiagnosis(root.path("faultDiagnosis").asText("待现场排查定损"))
                    .maintenanceTips(root.path("maintenanceTips").asText("准备标准检修工具，佩戴防护用具"))
                    .recommendedRole(root.path("recommendedRole").asText("综合维修工"))
                    .estimatedHours(root.path("estimatedHours").asDouble(1.5))
                    .suggestedAuditRemark(root.path("suggestedAuditRemark").asText("初审通过，请及时跟进。"))
                    .build();
        } catch (Exception e) {
            log.warn("解析研判 JSON 异常，回退至规则研判: {}", e.getMessage());
            return aiMockEngine.triageOrderMock(dto);
        }
    }

    private String cleanJsonString(String str) {
        if (str == null) return "{}";
        String cleaned = str.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
