package com.li.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.community.model.entity.BizChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BizChatMessageMapper extends BaseMapper<BizChatMessage> {

    /**
     * 每个会话最新一条消息（避免把用户全部历史消息拉回内存分组）
     */
    @Select("""
            SELECT m.* FROM (
                SELECT session_id, MAX(create_time) AS max_time
                FROM biz_chat_message
                WHERE sender_id = #{userId} OR receiver_id = #{userId}
                GROUP BY session_id
            ) t JOIN biz_chat_message m
                ON m.session_id = t.session_id AND m.create_time = t.max_time
            ORDER BY m.create_time DESC
            """)
    List<BizChatMessage> selectLatestOfEachSession(@Param("userId") Long userId);

    /**
     * 当前用户各会话未读数
     */
    @Select("""
            SELECT session_id, COUNT(*) AS unread
            FROM biz_chat_message
            WHERE receiver_id = #{userId} AND is_read = 0
            GROUP BY session_id
            """)
    List<Map<String, Object>> selectUnreadCountBySession(@Param("userId") Long userId);
}
