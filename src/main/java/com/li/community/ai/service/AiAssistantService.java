package com.li.community.ai.service;

import com.li.community.common.result.PageResult;
import com.li.community.model.dto.AiOrderParseDto;
import com.li.community.model.dto.AiOrderTriageDto;
import com.li.community.model.vo.AiChatRecordVo;
import com.li.community.model.vo.AiOrderParseVo;
import com.li.community.model.vo.AiOrderTriageVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface AiAssistantService {

    /**
     * AI 社区百事通助手：流式 SSE 问答 (结合内置知识库与动态公告 RAG 检索及意图识别直达卡片)
     *
     * @param prompt 用户当前输入提问
     * @param history 历史消息上下文
     * @param communityId 所属小区ID (可选)
     * @param userId 当前用户ID (可选，登录用户自动持久化)
     * @return SseEmitter 流式响应发射器
     */
    SseEmitter chatStream(String prompt, List<Map<String, String>> history, Long communityId, Long userId);

    /**
     * 分页查询当前用户的 AI 历史会话记录 (按时间倒序切片，返回正序以供聊天流展示)
     *
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 历史消息分页结果
     */
    PageResult<AiChatRecordVo> getChatHistory(Long userId, Long page, Long size);

    /**
     * 清空当前用户的全部 AI 会话历史
     *
     * @param userId 用户ID
     */
    void clearChatHistory(Long userId);

    /**
     * 居民口语化报修一键 AI 智能结构化提炼
     *
     * @param dto 口语化报修原始描述
     * @return 规范化后的工单标题、分类、紧急程度判定与正文
     */
    AiOrderParseVo parseOrder(AiOrderParseDto dto);

    /**
     * 管理端工单初审 AI 智能分诊与派工建议研判
     *
     * @param dto 工单基础数据与报修信息
     * @return 故障成因诊断、维修排查要点、推荐派工角色与一键审核批注
     */
    AiOrderTriageVo triageOrder(AiOrderTriageDto dto);
}
