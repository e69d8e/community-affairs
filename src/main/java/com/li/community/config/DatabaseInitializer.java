package com.li.community.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 1. 创建闲置互助点赞记录表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `biz_post_like` (
                    `id` BIGINT NOT NULL PRIMARY KEY,
                    `user_id` BIGINT NOT NULL,
                    `post_id` BIGINT NOT NULL,
                    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`),
                    KEY `idx_post_id` (`post_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闲置互助点赞记录表';
            """);

            // 2. 创建邻里闲置互助私信表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `biz_chat_message` (
                    `id` BIGINT NOT NULL PRIMARY KEY,
                    `session_id` VARCHAR(64) NOT NULL COMMENT '会话唯一标识',
                    `post_id` BIGINT NOT NULL COMMENT '关联闲置/互助帖子ID',
                    `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
                    `sender_name` VARCHAR(50) NOT NULL COMMENT '发送者姓名',
                    `sender_avatar` VARCHAR(500) DEFAULT '' COMMENT '发送者头像',
                    `receiver_id` BIGINT NOT NULL COMMENT '接收者ID',
                    `receiver_name` VARCHAR(50) NOT NULL COMMENT '接收者姓名',
                    `receiver_avatar` VARCHAR(500) DEFAULT '' COMMENT '接收者头像',
                    `content` TEXT NOT NULL COMMENT '私信内容',
                    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读 (0-未读, 1-已读)',
                    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
                    KEY `idx_session` (`session_id`),
                    KEY `idx_receiver_unread` (`receiver_id`, `is_read`),
                    KEY `idx_sender_receiver` (`sender_id`, `receiver_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邻里闲置互助私信表';
            """);

            log.info("数据库 biz_post_like 与 biz_chat_message 架构检查与自动初始化成功");

            // 3. 补充唯一索引兜底（表已存在时 CREATE TABLE 不会生效，用 ALTER 追加；重复执行/索引已存在时忽略）
            safeExecute("ALTER TABLE `biz_activity_signup` ADD UNIQUE KEY `uk_verify_code` (`verify_code`)");
            safeExecute("ALTER TABLE `biz_activity_signup` ADD UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`)");
            safeExecute("ALTER TABLE `biz_post_favorite` ADD UNIQUE KEY `uk_user_post` (`user_id`, `post_id`)");
        } catch (Exception e) {
            log.error("自动初始化数据库表结构异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行 DDL，索引已存在或历史数据存在重复值导致失败时仅记录日志，不阻断启动
     */
    private void safeExecute(String ddl) {
        try {
            jdbcTemplate.execute(ddl);
            log.info("数据库索引初始化成功: {}", ddl);
        } catch (Exception e) {
            log.info("数据库索引初始化跳过（可能已存在或数据重复）: {} -> {}", ddl, e.getMessage());
        }
    }
}
