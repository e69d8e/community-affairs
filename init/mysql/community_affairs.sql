-- ==============================================================================
-- 社区综合事务管理系统 (community-affairs) 数据库初始化 DDL & 种子数据脚本
-- 适配 MySQL 8.4 LTS
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS `community_affairs` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `community_affairs`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------------
-- 1. 系统用户表: sys_user
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '密码哈希(BCrypt)',
  `real_name` VARCHAR(64) NOT NULL COMMENT '真实姓名',
  `phone` VARCHAR(20) NOT NULL COMMENT '联系手机号',
  `avatar` VARCHAR(255) DEFAULT '' COMMENT '头像URL',
  `gender` TINYINT DEFAULT 2 COMMENT '性别: 0-女, 1-男, 2-保密',
  `role` VARCHAR(32) NOT NULL DEFAULT 'ROLE_RESIDENT' COMMENT '主角色编码',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态: 1-正常, 0-停用',
  `credit_score` INT NOT NULL DEFAULT 100 COMMENT '社区互助信用积分',
  `community_id` BIGINT DEFAULT NULL COMMENT '所属小区ID(NULL=平台级账号,如系统管理员)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删, 1-已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_role` (`role`),
  KEY `idx_user_community` (`community_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ------------------------------------------------------------------------------
-- 2. 角色表: sys_role
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` BIGINT NOT NULL COMMENT '角色ID',
  `role_code` VARCHAR(32) NOT NULL COMMENT '角色编码',
  `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(255) DEFAULT '' COMMENT '角色职能描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- ------------------------------------------------------------------------------
-- 3. 用户角色关联表: sys_user_role
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ------------------------------------------------------------------------------
-- 4. 小区信息表: biz_community
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_community`;
CREATE TABLE `biz_community` (
  `id` BIGINT NOT NULL COMMENT '小区ID',
  `name` VARCHAR(100) NOT NULL COMMENT '小区名称',
  `code` VARCHAR(50) NOT NULL COMMENT '小区唯一编号',
  `address` VARCHAR(255) NOT NULL COMMENT '详细地址',
  `province` VARCHAR(50) DEFAULT '浙江省' COMMENT '省份',
  `city` VARCHAR(50) DEFAULT '杭州市' COMMENT '城市',
  `district` VARCHAR(50) DEFAULT '西湖区' COMMENT '区县',
  `contact_person` VARCHAR(50) DEFAULT '' COMMENT '物业负责人',
  `contact_phone` VARCHAR(20) DEFAULT '' COMMENT '物业服务热线',
  `longitude` DECIMAL(10, 7) DEFAULT 120.1550700 COMMENT '经度',
  `latitude` DECIMAL(10, 7) DEFAULT 30.2740850 COMMENT '纬度',
  `description` TEXT COMMENT '小区简介',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '运营状态: 1-正常, 0-停用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comm_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小区信息表';

-- ------------------------------------------------------------------------------
-- 5. 楼栋表: biz_building
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_building`;
CREATE TABLE `biz_building` (
  `id` BIGINT NOT NULL COMMENT '楼栋ID',
  `community_id` BIGINT NOT NULL COMMENT '所属小区ID',
  `building_name` VARCHAR(64) NOT NULL COMMENT '楼栋名称(如: 1号楼)',
  `building_no` VARCHAR(20) NOT NULL COMMENT '楼栋编号',
  `total_units` INT NOT NULL DEFAULT 1 COMMENT '总单元数',
  `total_floors` INT NOT NULL DEFAULT 18 COMMENT '总楼层数',
  `grid_officer_id` BIGINT DEFAULT NULL COMMENT '绑定的楼栋长/网格员ID(sys_user.id)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comm_bld` (`community_id`),
  KEY `idx_grid_officer` (`grid_officer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='楼栋网格表';

-- ------------------------------------------------------------------------------
-- 6. 单元表: biz_unit
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_unit`;
CREATE TABLE `biz_unit` (
  `id` BIGINT NOT NULL,
  `building_id` BIGINT NOT NULL COMMENT '所属楼栋ID',
  `unit_name` VARCHAR(32) NOT NULL COMMENT '单元名称(如: 1单元)',
  `unit_no` VARCHAR(20) NOT NULL COMMENT '单元编号',
  `has_elevator` TINYINT NOT NULL DEFAULT 1 COMMENT '是否有电梯: 1-有, 0-无',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bld_unit` (`building_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单元表';

-- ------------------------------------------------------------------------------
-- 7. 房屋表: biz_house
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_house`;
CREATE TABLE `biz_house` (
  `id` BIGINT NOT NULL COMMENT '房屋ID',
  `community_id` BIGINT NOT NULL COMMENT '小区ID',
  `building_id` BIGINT NOT NULL COMMENT '楼栋ID',
  `unit_id` BIGINT NOT NULL COMMENT '单元ID',
  `house_no` VARCHAR(32) NOT NULL COMMENT '门牌号(如: 301室)',
  `floor_num` INT NOT NULL DEFAULT 3 COMMENT '所在楼层',
  `area` DECIMAL(8, 2) DEFAULT 98.50 COMMENT '建筑面积(㎡)',
  `house_type` VARCHAR(32) DEFAULT '三室两厅' COMMENT '户型描述',
  `live_status` TINYINT NOT NULL DEFAULT 1 COMMENT '居住状态: 1-业主自住, 2-房屋出租, 3-空置',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_unit_house` (`unit_id`),
  KEY `idx_comm_house` (`community_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='房屋信息表';

-- ------------------------------------------------------------------------------
-- 8. 住户房产关联认证表: biz_resident_house
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_resident_house`;
CREATE TABLE `biz_resident_house` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL COMMENT '居民用户ID',
  `house_id` BIGINT NOT NULL COMMENT '房屋ID',
  `identity_type` TINYINT NOT NULL DEFAULT 1 COMMENT '住户身份: 1-业主, 2-家属亲友, 3-租客',
  `cert_status` TINYINT NOT NULL DEFAULT 0 COMMENT '认证状态: 0-待审核, 1-已认证, 2-已驳回',
  `cert_material` VARCHAR(500) DEFAULT '' COMMENT '房产证/购房合同/租赁合同证明照片',
  `reject_reason` VARCHAR(255) DEFAULT '' COMMENT '审核驳回原因',
  `review_user_id` BIGINT DEFAULT NULL COMMENT '审核人ID',
  `review_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_house` (`user_id`, `house_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='住户房产认证表';

-- ------------------------------------------------------------------------------
-- 9. 报修与投诉工单主表: biz_work_order
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_work_order`;
CREATE TABLE `biz_work_order` (
  `id` BIGINT NOT NULL COMMENT '工单ID',
  `order_no` VARCHAR(64) NOT NULL COMMENT '工单号(如: WO202610220001)',
  `order_type` TINYINT NOT NULL COMMENT '工单类型: 1-居民报修, 2-投诉建议',
  `category` VARCHAR(64) NOT NULL COMMENT '业务分类(如: 水电管道/公共照明/电梯/环境噪音/违规停放)',
  `community_id` BIGINT NOT NULL COMMENT '所属小区ID',
  `building_id` BIGINT DEFAULT NULL COMMENT '关联楼栋ID',
  `house_id` BIGINT DEFAULT NULL COMMENT '关联房屋ID',
  `reporter_id` BIGINT NOT NULL COMMENT '申报居民ID',
  `reporter_name` VARCHAR(64) NOT NULL COMMENT '申报人姓名',
  `reporter_phone` VARCHAR(20) NOT NULL COMMENT '申报人联系电话',
  `title` VARCHAR(128) NOT NULL COMMENT '工单简述',
  `content` TEXT NOT NULL COMMENT '详细情况描述',
  `images` JSON DEFAULT NULL COMMENT '现场照片URL数组',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-待审核, 2-已驳回, 3-已派单待处理, 4-处理中, 5-已完工待评价, 6-已评价结单',
  `is_urgent` TINYINT NOT NULL DEFAULT 0 COMMENT '紧急程度: 0-普通, 1-紧急/突发',
  `reviewer_id` BIGINT DEFAULT NULL COMMENT '初审人ID(网格员/物业)',
  `review_time` DATETIME DEFAULT NULL COMMENT '初审时间',
  `review_remark` VARCHAR(255) DEFAULT '' COMMENT '初审意见',
  `handler_id` BIGINT DEFAULT NULL COMMENT '指派维修/处理人员ID',
  `handler_name` VARCHAR(64) DEFAULT '' COMMENT '处理人姓名',
  `handler_phone` VARCHAR(20) DEFAULT '' COMMENT '处理人电话',
  `handle_time` DATETIME DEFAULT NULL COMMENT '接单开工时间',
  `handle_result` TEXT COMMENT '完工处理说明',
  `handle_images` JSON DEFAULT NULL COMMENT '处理后现场凭证图片',
  `finish_time` DATETIME DEFAULT NULL COMMENT '完工时间',
  `rating_star` TINYINT DEFAULT NULL COMMENT '居民满意度评分(1-5星)',
  `rating_content` VARCHAR(500) DEFAULT '' COMMENT '评价留言',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_reporter` (`reporter_id`),
  KEY `idx_status` (`status`),
  KEY `idx_comm_order` (`community_id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报修与投诉工单表';

-- ------------------------------------------------------------------------------
-- 10. 工单流转轨迹记录表: biz_order_log
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_order_log`;
CREATE TABLE `biz_order_log` (
  `id` BIGINT NOT NULL,
  `order_id` BIGINT NOT NULL COMMENT '工单ID',
  `action_type` VARCHAR(32) NOT NULL COMMENT '动作编码: SUBMIT, REVIEW_PASS, REVIEW_REJECT, DISPATCH, START_HANDLE, COMPLETE, EVALUATE',
  `from_status` TINYINT NOT NULL COMMENT '变更前状态',
  `to_status` TINYINT NOT NULL COMMENT '变更后状态',
  `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  `operator_role` VARCHAR(32) NOT NULL COMMENT '操作人角色',
  `remark` VARCHAR(500) DEFAULT '' COMMENT '流转意见或说明',
  `evidence_images` JSON DEFAULT NULL COMMENT '流转关联附件/凭证照片',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_trace` (`order_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单流转日志表';

-- ------------------------------------------------------------------------------
-- 11. 社区活动表: biz_activity
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_activity`;
CREATE TABLE `biz_activity` (
  `id` BIGINT NOT NULL COMMENT '活动ID',
  `title` VARCHAR(128) NOT NULL COMMENT '活动主题',
  `cover_image` VARCHAR(255) DEFAULT '' COMMENT '海报主图URL',
  `category` VARCHAR(64) NOT NULL DEFAULT '邻里文体' COMMENT '分类: 邻里文体, 公益讲座, 亲子教育, 志愿服务, 节日庆典',
  `community_id` BIGINT NOT NULL COMMENT '所属小区ID',
  `location` VARCHAR(255) NOT NULL COMMENT '活动开展具体场地',
  `start_time` DATETIME NOT NULL COMMENT '活动开始时间',
  `end_time` DATETIME NOT NULL COMMENT '活动结束时间',
  `signup_start_time` DATETIME NOT NULL COMMENT '报名开启时间',
  `signup_end_time` DATETIME NOT NULL COMMENT '报名截止时间',
  `max_participants` INT NOT NULL DEFAULT 50 COMMENT '报名人数上限',
  `current_participants` INT NOT NULL DEFAULT 0 COMMENT '当前已报名人数',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-草稿, 1-报名中, 2-报名截止待开展, 3-进行中, 4-已圆满结束, 5-已取消',
  `organizer` VARCHAR(64) NOT NULL DEFAULT '社区居委会' COMMENT '主办单位/承办方',
  `contact_phone` VARCHAR(20) DEFAULT '' COMMENT '活动咨询电话',
  `description` TEXT NOT NULL COMMENT '活动详情图文介绍',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_act_comm` (`community_id`),
  KEY `idx_act_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区活动表';

-- ------------------------------------------------------------------------------
-- 12. 活动报名与签到核销表: biz_activity_signup
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_activity_signup`;
CREATE TABLE `biz_activity_signup` (
  `id` BIGINT NOT NULL,
  `activity_id` BIGINT NOT NULL COMMENT '活动ID',
  `user_id` BIGINT NOT NULL COMMENT '报名用户ID',
  `resident_name` VARCHAR(64) NOT NULL COMMENT '报名人姓名',
  `resident_phone` VARCHAR(20) NOT NULL COMMENT '报名人电话',
  `verify_code` VARCHAR(16) NOT NULL COMMENT '专属6位核销码',
  `qr_code_content` VARCHAR(128) NOT NULL COMMENT '动态签到二维码串',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-已成功报名, 2-已核销签到, 3-已取消报名',
  `sign_in_time` DATETIME DEFAULT NULL COMMENT '现场签到核销时间',
  `verifier_id` BIGINT DEFAULT NULL COMMENT '核销员ID(网格员/管理员)',
  `verifier_name` VARCHAR(64) DEFAULT '' COMMENT '核销员姓名',
  `feedback_star` TINYINT DEFAULT NULL COMMENT '活动参与反馈(1-5星)',
  `feedback_content` VARCHAR(500) DEFAULT '' COMMENT '活动建议留言',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_act_user` (`activity_id`, `user_id`),
  UNIQUE KEY `uk_verify_code` (`verify_code`),
  KEY `idx_user_signup` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动报名签到表';

-- ------------------------------------------------------------------------------
-- 13. 二手闲置与邻里互助表: biz_market_post
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_market_post`;
CREATE TABLE `biz_market_post` (
  `id` BIGINT NOT NULL COMMENT '帖子/物品ID',
  `post_type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型: 1-二手闲置, 2-邻里求助, 3-物品借用, 4-邻里拼车, 5-技能交换',
  `community_id` BIGINT NOT NULL COMMENT '所属小区ID',
  `user_id` BIGINT NOT NULL COMMENT '发布者ID',
  `title` VARCHAR(128) NOT NULL COMMENT '标题',
  `description` TEXT NOT NULL COMMENT '详细介绍',
  `price` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '价格(0.00表示免费赠送/互助借用/面议)',
  `trade_type` TINYINT NOT NULL DEFAULT 1 COMMENT '交易属性: 1-一口价, 2-可面议, 3-免费赠送, 4-互助交换',
  `delivery_type` TINYINT NOT NULL DEFAULT 1 COMMENT '交付约定: 1-单元楼下自提, 2-上门送达, 3-社区物业服务台面交',
  `trade_address` VARCHAR(128) DEFAULT '' COMMENT '期望交接地约定',
  `contact_type` VARCHAR(32) DEFAULT '站内私信' COMMENT '首选联系方式(站内私信/手机/微信)',
  `contact_value` VARCHAR(64) DEFAULT '' COMMENT '联系账号/电话',
  `images` JSON DEFAULT NULL COMMENT '物品或求助图片URL数组',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-展示中, 2-流转锁定中, 3-已达成结题, 4-违规封禁下架',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT '获赞数',
  `comment_count` INT NOT NULL DEFAULT 0 COMMENT '互动评论数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_type` (`post_type`),
  KEY `idx_post_user` (`user_id`),
  KEY `idx_post_comm` (`community_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二手闲置与邻里互助表';

-- ------------------------------------------------------------------------------
-- 14. 帖子互动评论表: biz_post_comment
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_post_comment`;
CREATE TABLE `biz_post_comment` (
  `id` BIGINT NOT NULL,
  `post_id` BIGINT NOT NULL COMMENT '所属帖子ID',
  `user_id` BIGINT NOT NULL COMMENT '评论人ID',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID(楼中楼回复)',
  `reply_to_user_id` BIGINT DEFAULT NULL COMMENT '回复目标用户ID',
  `content` VARCHAR(500) NOT NULL COMMENT '评论文本内容',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常公开, 2-涉敏待核验, 3-已删除',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_cmt` (`post_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子互动评论表';

-- ------------------------------------------------------------------------------
-- 15. 帖子收藏表: biz_post_favorite
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_post_favorite`;
CREATE TABLE `biz_post_favorite` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `post_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fav` (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

-- ------------------------------------------------------------------------------
-- 16. 敏感词表: biz_sensitive_word
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_sensitive_word`;
CREATE TABLE `biz_sensitive_word` (
  `id` BIGINT NOT NULL,
  `word` VARCHAR(64) NOT NULL COMMENT '敏感词内容',
  `category` VARCHAR(32) NOT NULL DEFAULT 'VULGAR' COMMENT '分类: POLITICS, VIOLENCE, VULGAR, FRAUD, AD',
  `action` VARCHAR(16) NOT NULL DEFAULT 'REJECT' COMMENT '动作: REJECT(拦截), REPLACE(打码*)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-停用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统敏感词库表';

-- ------------------------------------------------------------------------------
-- 17. 违规与纠纷举报表: biz_report
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_report`;
CREATE TABLE `biz_report` (
  `id` BIGINT NOT NULL,
  `target_type` VARCHAR(32) NOT NULL COMMENT '目标类型: POST(帖子), COMMENT(评论), USER(用户)',
  `target_id` BIGINT NOT NULL COMMENT '被举报对象ID',
  `reporter_id` BIGINT NOT NULL COMMENT '举报居民ID',
  `community_id` BIGINT DEFAULT NULL COMMENT '举报人所属小区ID(冗余,用于社区隔离)',
  `reason_category` VARCHAR(64) NOT NULL COMMENT '举报原因: 色情低俗, 虚假广告, 恶意辱骂, 价格欺诈, 违禁物品',
  `description` VARCHAR(500) DEFAULT '' COMMENT '详细举报说明',
  `evidence_images` JSON DEFAULT NULL COMMENT '举证截图',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态: 0-待网格员核查, 1-确认违规已下架, 2-驳回举报',
  `handler_id` BIGINT DEFAULT NULL COMMENT '核查处理人ID',
  `handle_remark` VARCHAR(255) DEFAULT '' COMMENT '处理说明',
  `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_target` (`target_type`, `target_id`),
  KEY `idx_report_status` (`status`),
  KEY `idx_report_community` (`community_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='违规举报处理表';

-- ------------------------------------------------------------------------------
-- 18. 社区公告与通知中心: sys_notice
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
  `id` BIGINT NOT NULL COMMENT '通知公告ID',
  `title` VARCHAR(128) NOT NULL COMMENT '公告标题',
  `notice_type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型: 1-政策宣传, 2-便民提示, 3-物业通知, 4-突发应急广播',
  `community_id` BIGINT NOT NULL COMMENT '所属小区ID',
  `target_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '送达范围: 1-全区广播, 2-指定楼栋定向',
  `target_building_ids` JSON DEFAULT NULL COMMENT '指定楼栋ID数组(如: [1, 2])',
  `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶: 1-置顶, 0-常规',
  `content` MEDIUMTEXT NOT NULL COMMENT '公告富文本内容',
  `publish_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布生效时间',
  `publisher_id` BIGINT NOT NULL COMMENT '发布人用户ID',
  `publisher_name` VARCHAR(64) NOT NULL COMMENT '发布人姓名',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '阅读浏览量',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-草稿待发, 1-正式发布, 2-已撤回归档',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notice_comm` (`community_id`, `status`),
  KEY `idx_notice_top` (`is_top`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区公告与通知表';

-- ------------------------------------------------------------------------------
-- 19. 个人站内信送达表: sys_user_notice
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_notice`;
CREATE TABLE `sys_user_notice` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
  `notice_id` BIGINT DEFAULT NULL COMMENT '关联公告ID(系统业务提醒时可为空)',
  `notice_type` VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '通知业务类型: SYSTEM, WORK_ORDER, ACTIVITY, MODERATION, EMERGENCY',
  `title` VARCHAR(128) NOT NULL COMMENT '消息简述',
  `summary` VARCHAR(255) DEFAULT '' COMMENT '消息摘要内容',
  `biz_id` BIGINT DEFAULT NULL COMMENT '业务跳转ID(工单ID/活动ID/帖子ID)',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '已读状态: 0-未读, 1-已读',
  `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站内信表';

-- ------------------------------------------------------------------------------
-- 20. 操作审计日志表: sys_operation_log
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id` BIGINT NOT NULL,
  `title` VARCHAR(64) NOT NULL COMMENT '操作模块名称',
  `business_type` VARCHAR(32) NOT NULL COMMENT '业务类型: INSERT, UPDATE, DELETE, DISPATCH, AUDIT, EXPORT',
  `method` VARCHAR(128) NOT NULL COMMENT '调用的控制器方法',
  `request_method` VARCHAR(16) NOT NULL COMMENT 'HTTP请求方式: GET, POST, PUT, DELETE',
  `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  `operator_role` VARCHAR(32) DEFAULT '' COMMENT '操作人主角色',
  `oper_url` VARCHAR(255) DEFAULT '' COMMENT '请求URL',
  `oper_ip` VARCHAR(64) DEFAULT '' COMMENT '操作者IP地址',
  `oper_param` TEXT COMMENT '请求参数快照',
  `json_result` TEXT COMMENT '返回结果快照',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '操作状态: 1-正常成功, 0-发生异常',
  `error_msg` TEXT COMMENT '错误日志信息',
  `execution_time` BIGINT DEFAULT 0 COMMENT '执行耗时(毫秒)',
  `oper_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_oper_time` (`oper_time`),
  KEY `idx_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作审计日志表';

-- ==============================================================================
-- 种子数据初始化 (Seed Data)
-- 预置密码均为 123456 (BCrypt 哈希)
-- ==============================================================================

-- 预置角色
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`) VALUES
(1, 'ROLE_SYSTEM_ADMIN', '系统管理员', '负责全局用户赋权、空间架构树、字典配置与安全审计'),
(2, 'ROLE_COMMUNITY_ADMIN', '社区管理员', '负责社区政策公告、突发应急广播、文体活动组织、综合大屏监管'),
(3, 'ROLE_PROPERTY_ADMIN', '物业管理员', '负责报修工单指派与验收、物业通知下发、公用设施巡检'),
(4, 'ROLE_GRID_OFFICER', '网格员 / 楼栋长', '负责本楼栋住户走访、工单初审、违规举报初查、活动现场扫码签到'),
(5, 'ROLE_RESIDENT', '普通居民', '享受社区便民服务、发起在线报修投诉、参与二手互助集市与活动报名');

-- 预置初始用户 (密码统一为 123456; community_id: NULL=平台级账号)
-- BCrypt: $2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `phone`, `avatar`, `gender`, `role`, `status`, `credit_score`, `community_id`) VALUES
(1001, 'admin', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '系统超级管理员', '13800000001', 'https://api.dicebear.com/7.x/bottts/svg?seed=admin', 1, 'ROLE_SYSTEM_ADMIN', 1, 150, NULL),
(1002, 'community_admin', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '林主任(社区委)', '13800000002', 'https://api.dicebear.com/7.x/avataaars/svg?seed=lin', 0, 'ROLE_COMMUNITY_ADMIN', 1, 130, 101),
(1003, 'property_admin', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '陈主管(物业处)', '13800000003', 'https://api.dicebear.com/7.x/avataaars/svg?seed=chen', 1, 'ROLE_PROPERTY_ADMIN', 1, 120, 101),
(1004, 'grid_officer', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '王网格(1号楼长)', '13800000004', 'https://api.dicebear.com/7.x/avataaars/svg?seed=wang_grid', 1, 'ROLE_GRID_OFFICER', 1, 115, 101),
(1005, 'resident', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '李晓华(居民)', '13800000005', 'https://api.dicebear.com/7.x/avataaars/svg?seed=resident_li', 1, 'ROLE_RESIDENT', 1, 105, 101),
(1006, 'resident_zhang', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '张阿姨(热心业主)', '13800000006', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhang_ay', 0, 'ROLE_RESIDENT', 1, 125, 101),
(1007, 'resident_wang', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '王小川(年轻租客)', '13800000007', 'https://api.dicebear.com/7.x/avataaars/svg?seed=wang_xc', 1, 'ROLE_RESIDENT', 1, 100, 101),
(1008, 'community_admin2', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '周书记(枫林府社区委)', '13800000008', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhou_shuji', 0, 'ROLE_COMMUNITY_ADMIN', 1, 130, 102),
(1009, 'grid_officer2', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '赵网格(枫林府1号楼长)', '13800000009', 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhao_grid', 1, 'ROLE_GRID_OFFICER', 1, 115, 102),
(1010, 'resident_lin', '$2a$10$1mv0PTjRrMgxm3HAwG88tex1O3yLllEZ8XcKboTGR6CpEKOUAsU62', '林悦(枫林府居民)', '13800000010', 'https://api.dicebear.com/7.x/avataaars/svg?seed=lin_yue', 0, 'ROLE_RESIDENT', 1, 105, 102);

-- 用户角色绑定
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`) VALUES
(1, 1001, 1),
(2, 1002, 2),
(3, 1003, 3),
(4, 1004, 4),
(5, 1005, 5),
(6, 1006, 5),
(7, 1007, 5),
(8, 1008, 2),
(9, 1009, 4),
(10, 1010, 5);

-- 预置小区 (两个小区共用一套系统, 验证多社区数据隔离)
INSERT INTO `biz_community` (`id`, `name`, `code`, `address`, `province`, `city`, `district`, `contact_person`, `contact_phone`, `longitude`, `latitude`, `description`) VALUES
(101, '阳光翠竹苑智慧社区', 'YG-CZY-01', '文三西路888号', '浙江省', '杭州市', '西湖区', '陈主管', '0571-88889999', 120.1287600, 30.2831500, '国家级绿色智慧示范示范社区，建有现代化邻里中心、阳光便民服务站、智能化安防监控及分类环保回收驿站。'),
(102, '云栖枫林府智慧社区', 'YX-FLF-02', '转塘街道美院南路99号', '浙江省', '杭州市', '西湖区', '周书记', '0571-86666777', 120.0876500, 30.1903200, '毗邻云栖小镇的高品质人才安居社区，配建共享书房、屋顶农场、全龄段健身环道与24小时无人智慧服务站。');

-- 预置楼栋 (101 网格长为 1004, 102 网格长为 1009)
INSERT INTO `biz_building` (`id`, `community_id`, `building_name`, `building_no`, `total_units`, `total_floors`, `grid_officer_id`) VALUES
(201, 101, '1号楼 (晨曦苑)', 'B01', 2, 18, 1004),
(202, 101, '2号楼 (竹韵苑)', 'B02', 2, 18, 1004),
(203, 101, '3号楼 (雅居苑)', 'B03', 1, 11, 1004),
(204, 102, '1号楼 (云澜苑)', 'B01', 1, 26, 1009),
(205, 102, '2号楼 (枫雅苑)', 'B02', 1, 18, 1009);

-- 预置单元
INSERT INTO `biz_unit` (`id`, `building_id`, `unit_name`, `unit_no`, `has_elevator`) VALUES
(301, 201, '1单元', 'U01', 1),
(302, 201, '2单元', 'U02', 1),
(303, 202, '1单元', 'U01', 1),
(304, 203, '1单元', 'U01', 1),
(305, 204, '1单元', 'U01', 1),
(306, 205, '1单元', 'U01', 1);

-- 预置房屋
INSERT INTO `biz_house` (`id`, `community_id`, `building_id`, `unit_id`, `house_no`, `floor_num`, `area`, `house_type`, `live_status`) VALUES
(401, 101, 201, 301, '101室', 1, 89.50, '两室一厅', 1),
(402, 101, 201, 301, '201室', 2, 112.00, '三室两厅', 1),
(403, 101, 201, 301, '301室', 3, 125.60, '四室两厅', 1),
(404, 101, 201, 301, '302室', 3, 89.50, '两室一厅', 2),
(405, 101, 202, 303, '501室', 5, 105.00, '三室一厅', 1),
(406, 101, 203, 304, '601室', 6, 140.00, '四室两厅', 3),
(407, 102, 204, 305, '1201室', 12, 96.00, '三室一厅', 1),
(408, 102, 204, 305, '1301室', 13, 96.00, '三室一厅', 2),
(409, 102, 205, 306, '801室', 8, 128.00, '四室两厅', 1);

-- 住户与房屋认证绑定
INSERT INTO `biz_resident_house` (`id`, `user_id`, `house_id`, `identity_type`, `cert_status`, `cert_material`) VALUES
(501, 1005, 403, 1, 1, 'https://images.unsplash.com/photo-1560518883-ce09059eeffa?w=800'),
(502, 1006, 402, 1, 1, 'https://images.unsplash.com/photo-1560518883-ce09059eeffa?w=800'),
(503, 1007, 404, 3, 1, 'https://images.unsplash.com/photo-1560518883-ce09059eeffa?w=800'),
(504, 1010, 407, 1, 1, 'https://images.unsplash.com/photo-1560518883-ce09059eeffa?w=800');

-- 敏感词库预置
INSERT INTO `biz_sensitive_word` (`id`, `word`, `category`, `action`, `status`) VALUES
(1, '办证刻章', 'FRAUD', 'REJECT', 1),
(2, '代开增值税发票', 'FRAUD', 'REJECT', 1),
(3, '网络博彩开户', 'FRAUD', 'REJECT', 1),
(4, '高利借贷', 'FRAUD', 'REJECT', 1),
(5, '刷单返利', 'FRAUD', 'REJECT', 1),
(6, '违禁管制刀具', 'VIOLENCE', 'REJECT', 1),
(7, '迷魂药', 'VIOLENCE', 'REJECT', 1),
(8, '特价高仿手表', 'AD', 'REPLACE', 1),
(9, '低俗色情网址', 'VULGAR', 'REJECT', 1),
(10, '恶意政治谣言', 'POLITICS', 'REJECT', 1);

-- 预置通知公告
INSERT INTO `sys_notice` (`id`, `title`, `notice_type`, `community_id`, `target_scope`, `target_building_ids`, `is_top`, `content`, `publisher_id`, `publisher_name`, `view_count`, `status`) VALUES
(601, '【防台防汛应急预案】强台风黄色预警防御指引与社区避难所开放通告', 4, 101, 1, NULL, 1, '<p>广大翠竹苑邻里居民：</p><p>接市气象台紧急台风黄色预警，预计今夜至明日将伴随大风强降雨天气。请各位业主紧闭门窗，移走阳台室外花盆杂物。社区党群服务中心一楼已设立24小时应急避险服务点，配备热水、应急药品与饮用水。如遇地下车库返水或树木倒伏，请第一时间拨打社区24小时应急电话：0571-88889999。</p>', 1002, '林主任(社区委)', 389, 1),
(602, '关于本周六小区主管道二次加压供水清洗消杀及临时停水通知', 3, 101, 2, '[201, 202]', 0, '<p>尊敬的1号楼、2号楼业主：</p><p>为保障二次供水卫生安全，物业工程部计划于本周六上午 08:30 - 11:30 对楼顶生活水箱进行全面清洗消毒，期间高层区将暂停自来水供应，请受影响居民提前做好储水准备。恢复供水后如有短暂混浊请放水30秒即可正常使用，感谢您的理解与配合！</p>', 1003, '陈主管(物业处)', 156, 1),
(603, '翠竹苑社区2026年第二季度居民自治与综合事务满意度问卷征集', 1, 101, 1, NULL, 0, '<p>社区是我家，治理靠大家。为了不断提高社区环境卫生、文体活动组织与报修处理效率，现面向全区常住居民开展线上匿名满意度评议，您的每一条真诚建议都是我们前进的动力！</p>', 1002, '林主任(社区委)', 210, 1),
(604, '枫林府共享书房开放时间调整及屋顶农场认领招募公告', 2, 102, 1, NULL, 1, '<p>各位枫林府邻里：因设备升级维护，共享书房本周日起开放时间调整为每日 10:00-21:00。同时屋顶农场二期种植箱现开放认领，每户限认领一格，可在物业前台或线上登记，先到先得！</p>', 1008, '周书记(枫林府社区委)', 76, 1);

-- 预置社区活动
INSERT INTO `biz_activity` (`id`, `title`, `cover_image`, `category`, `community_id`, `location`, `start_time`, `end_time`, `signup_start_time`, `signup_end_time`, `max_participants`, `current_participants`, `status`, `organizer`, `contact_phone`, `description`) VALUES
(701, '翠竹苑邻里百家宴暨中秋金秋游园会', 'https://images.unsplash.com/photo-1511795409834-ef04bbd61622?w=800', '邻里文体', 101, '社区中心阳光大草坪', '2026-10-25 17:30:00', '2026-10-25 21:00:00', '2026-10-20 00:00:00', '2026-10-24 23:59:59', 50, 12, 1, '社区居委会 & 阳光义工团', '0571-88889999', '共聚邻里温情，每一户参与居民可带上一道拿手好菜（甜点/冷盘/热炒皆可）。现场设有投壶、灯谜、非遗糖画等趣味民俗摊位，报名成功后即可生成签到入场核销凭证！'),
(702, '居民日常应急救护技能普及与 AED/心肺复苏实操实战公益沙龙', 'https://images.unsplash.com/photo-1576091160550-2173dba999ef?w=800', '公益讲座', 101, '党群服务中心2楼多功能演播厅', '2026-10-28 14:00:00', '2026-10-28 16:30:00', '2026-10-20 00:00:00', '2026-10-27 18:00:00', 30, 8, 1, '红十字会急救普及志愿队', '13800000002', '特邀三甲急诊专家亲自示范成人/儿童心肺复苏 (CPR) 规范指引、异物海姆立克急救法以及社区公用 AED 自动体外除颤仪的标准开机操作，全程手把手实操练习，合格学员颁发社区初级急救志愿者荣誉证书。'),
(703, '枫林府屋顶农场秋日亲子播种节 & 稻草人创意大赛', 'https://images.unsplash.com/photo-1464226184884-fa280b87c399?w=800', '亲子农趣', 102, '2号楼屋顶农场', '2026-11-02 09:30:00', '2026-11-02 11:30:00', '2026-10-22 00:00:00', '2026-11-01 18:00:00', 20, 5, 1, '枫林府居委会 & 云栖志愿者联盟', '0571-86666777', '秋天是播种希望的季节！诚邀各家庭认领种植箱，亲手栽下萝卜、菠菜与豌豆苗，孩子们还可以用旧衣物合作扎制专属稻草人参赛，优秀作品将驻守农场一整个秋天。');

-- 预置活动报名记录 (核销码 6位数字)
INSERT INTO `biz_activity_signup` (`id`, `activity_id`, `user_id`, `resident_name`, `resident_phone`, `verify_code`, `qr_code_content`, `status`) VALUES
(801, 701, 1005, '李晓华', '13800000005', '882601', 'COMMUNITY:ACT:701:882601', 1),
(802, 701, 1006, '张阿姨', '13800000006', '882602', 'COMMUNITY:ACT:701:882602', 1),
(803, 702, 1005, '李晓华', '13800000005', '991201', 'COMMUNITY:ACT:702:991201', 1);

-- 预置二手闲置与邻里互助
INSERT INTO `biz_market_post` (`id`, `post_type`, `community_id`, `user_id`, `title`, `description`, `price`, `trade_type`, `delivery_type`, `trade_address`, `contact_type`, `contact_value`, `images`, `status`, `view_count`, `like_count`, `comment_count`) VALUES
(901, 1, 101, 1005, '九成新婴幼儿可折叠便携溜娃推车 (带遮阳蓬避震)', '宝宝已长大换大童三轮车，这款轻便小推车转让给有需要的邻居。带一键折叠功能，车架结实铝合金，布套全部拆洗消毒干净，送防雨防蚊罩。', 85.00, 1, 1, '1号楼1单元大堂自提', '手机/微信', '13800000005', '["https://images.unsplash.com/photo-1591088398332-8a7791972843?w=800"]', 1, 45, 6, 2),
(902, 2, 101, 1006, '【急求借】家中阳台安装晾衣架，急求借用电锤/冲击钻2小时，备自制曲奇答谢！', '邻居们好，今天下午家里装铝合金晾衣架需在混凝土墙上打4个孔，求借一把冲击钻用一两个小时，用完立即完好归还，附赠阿姨上午刚出炉的手工蔓越莓曲奇一盒~', 0.00, 4, 1, '1号楼201室上门取', '站内私信', '张阿姨', '["https://images.unsplash.com/photo-1504148455328-c376907d081c?w=800"]', 1, 62, 12, 3),
(903, 3, 101, 1007, '搬家整理出一批多肉植物与绿萝盆栽，全部免费结缘本小区邻友', '因为换租到外地工作，阳台精心养了三年的多肉植物带不走，全部带盆免费赠送给爱花爱草的邻居，先到先选，绿色环保低碳传递！', 0.00, 3, 2, '2号楼架空层连廊自取', '站内私信', '王小川', '["https://images.unsplash.com/photo-1485955900006-10f4d324d411?w=800"]', 1, 88, 18, 5),
(904, 1, 102, 1010, '九成新儿童滑步车/平衡车转让 (云栖枫林府自提)', '娃升级骑自行车了，这辆平衡车成色很好，轮胎几乎无磨损，高度可三档调节，适合2-5岁宝宝，枫林府邻居可上门试骑。', 120.00, 1, 1, '1号楼1单元大堂自提', '手机/微信', '13800000010', '["https://images.unsplash.com/photo-1610824352934-c10d87b700cc?w=800"]', 1, 21, 3, 1);

-- 预置工单 (报修与投诉)
INSERT INTO `biz_work_order` (`id`, `order_no`, `order_type`, `category`, `community_id`, `building_id`, `house_id`, `reporter_id`, `reporter_name`, `reporter_phone`, `title`, `content`, `images`, `status`, `is_urgent`, `reviewer_id`, `review_time`, `review_remark`, `handler_id`, `handler_name`, `handler_phone`, `handle_time`) VALUES
(10001, 'WO202610220001', 1, '电梯设施', 101, 201, 403, 1005, '李晓华', '13800000005', '1号楼客梯运行至8楼附近有轻微刮擦异响与停顿', '早上上班乘坐1号楼1单元客梯下行，在8层至7层之间听到明显的金属摩擦异响，轿厢伴有轻微震颤，存在安全隐患，请物业尽快安排维保单位排查轨道！', '["https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800"]', 3, 1, 1004, '2026-10-22 09:10:00', '情况属实，已加急转派迅捷电梯定点维保师傅', 1003, '陈主管(物业处)', '0571-88889999', '2026-10-22 09:20:00'),
(10002, 'WO202610220002', 2, '违规飞线充电', 101, 202, 405, 1006, '张阿姨', '13800000006', '2号楼架空层消防通道处有业主私拉飞线为电瓶车过夜充电', '昨晚散步发现2号楼南侧单元门旁有人从二楼窗户悬挂插线板飞线给电动自行车充电，旁边就是配电箱和杂物堆，消防隐患极大！', '["https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=800"]', 1, 1, NULL, NULL, '', NULL, '', '', NULL),
(10003, 'WO202610230001', 1, '电梯设施', 102, 204, 407, 1010, '林悦', '13800000010', '1号楼高层客梯轿厢照明闪烁并偶发开门延迟', '最近一周乘坐1号楼客梯，轿厢顶灯频繁闪烁，且到站开门偶有2-3秒延迟，担心感应器老化，请物业安排检修，谢谢！', '["https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800"]', 1, 0, NULL, NULL, '', NULL, '', '', NULL);

-- 预置工单流转日志
INSERT INTO `biz_order_log` (`id`, `order_id`, `action_type`, `from_status`, `to_status`, `operator_id`, `operator_name`, `operator_role`, `remark`) VALUES
(11001, 10001, 'SUBMIT', 0, 1, 1005, '李晓华', 'ROLE_RESIDENT', '居民在线发起紧急报修工单申报'),
(11002, 10001, 'REVIEW_PASS', 1, 3, 1004, '王网格(1号楼长)', 'ROLE_GRID_OFFICER', '网格员现场核实确认存在异响，审核通过并指派物业工程维保');

-- 预置站内信
INSERT INTO `sys_user_notice` (`id`, `user_id`, `notice_id`, `notice_type`, `title`, `summary`, `biz_id`, `is_read`) VALUES
(12001, 1005, 601, 'EMERGENCY', '突发台风黄色预警应急通告', '请紧闭门窗注意防汛，避难服务点已设立于党群服务中心一楼。', 601, 0),
(12002, 1005, NULL, 'WORK_ORDER', '工单派单进度提醒', '您提交的“1号楼客梯运行异响”工单已被网格员初审通过并派单至物业工程部。', 10001, 0),
(12003, 1005, NULL, 'ACTIVITY', '活动报名成功核销凭证', '恭喜您成功报名“翠竹苑百家宴暨中秋游园会”，核销码【882601】。', 701, 1);

SET FOREIGN_KEY_CHECKS = 1;
