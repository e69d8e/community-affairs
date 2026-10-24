package com.li.community.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.constant.RoleConstant;
import com.li.community.common.result.PageResult;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.*;
import com.li.community.model.dto.*;
import com.li.community.model.entity.*;
import com.li.community.model.vo.WorkOrderVo;
import com.li.community.service.WorkOrderService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private final BizWorkOrderMapper workOrderMapper;
    private final BizOrderLogMapper orderLogMapper;
    private final SysUserMapper userMapper;
    private final BizCommunityMapper communityMapper;
    private final BizBuildingMapper buildingMapper;
    private final BizHouseMapper houseMapper;
    private final SysUserNoticeMapper userNoticeMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // 可查看任意工单详情的工作人员角色；网格员另受"仅本人负责楼栋"的数据范围约束
    private static final Set<String> ADMIN_ROLES = Set.of(
            RoleConstant.ROLE_SYSTEM_ADMIN, RoleConstant.ROLE_COMMUNITY_ADMIN,
            RoleConstant.ROLE_PROPERTY_ADMIN, RoleConstant.ROLE_GRID_OFFICER);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderVo submitOrder(WorkOrderSubmitDto dto) {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userMapper.selectById(userId);

        String orderNo = "WO" + DateUtil.format(new Date(), "yyyyMMdd") + RandomUtil.randomNumbers(6);

        long orderId = IdUtil.getSnowflakeNextId();
        BizWorkOrder order = BizWorkOrder.builder()
                .id(orderId)
                .orderNo(orderNo)
                .orderType(dto.getOrderType())
                .category(dto.getCategory())
                .communityId(dto.getCommunityId() != null ? dto.getCommunityId() : 101L)
                .buildingId(dto.getBuildingId())
                .houseId(dto.getHouseId())
                .reporterId(userId)
                .reporterName(user != null ? user.getRealName() : "居民")
                .reporterPhone(user != null ? user.getPhone() : "")
                .title(dto.getTitle())
                .content(dto.getContent())
                .images(cleanJson(dto.getImages()))
                .status(1) // 1-待审核
                .isUrgent(dto.getIsUrgent() != null ? dto.getIsUrgent() : 0)
                .build();

        workOrderMapper.insert(order);

        // 插入初始化流转日志
        BizOrderLog orderLog = BizOrderLog.builder()
                .id(IdUtil.getSnowflakeNextId())
                .orderId(orderId)
                .actionType("SUBMIT")
                .fromStatus(0)
                .toStatus(1)
                .operatorId(userId)
                .operatorName(user != null ? user.getRealName() : "居民")
                .operatorRole(SecurityUtils.getRole())
                .remark("居民在线提交工单申请")
                .evidenceImages(cleanJson(dto.getImages()))
                .build();

        orderLogMapper.insert(orderLog);

        return convertToVo(order);
    }

    @Override
    public PageResult<WorkOrderVo> getMyOrders(Integer orderType, Integer status, Long page, Long size) {
        Long userId = SecurityUtils.getUserId();
        Page<BizWorkOrder> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);

        Page<BizWorkOrder> result = workOrderMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getReporterId, userId)
                        .eq(orderType != null, BizWorkOrder::getOrderType, orderType)
                        .eq(status != null, BizWorkOrder::getStatus, status)
                        .orderByDesc(BizWorkOrder::getCreateTime)
        );

        List<WorkOrderVo> voList = convertToVoList(result.getRecords());

        return PageResult.of(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public WorkOrderVo getOrderDetail(Long orderId) {
        Long userId = SecurityUtils.getUserId();
        BizWorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.WORK_ORDER_NOT_FOUND);
        }
        // 工单含报修人手机号、住址等隐私，仅报修人、处理人及管理角色可查看；
        // 网格员作为管理角色还需在数据范围内(仅本人负责楼栋)
        boolean involved = userId.equals(order.getReporterId())
                || (order.getHandlerId() != null && userId.equals(order.getHandlerId()));
        if (!involved && !isAdminUser()) {
            throw new BizException(ErrorCode.WORK_ORDER_PERMISSION_DENIED);
        }
        if (!involved && !isInOfficerBuildingScope(order.getBuildingId())) {
            throw new BizException(ErrorCode.WORK_ORDER_PERMISSION_DENIED);
        }
        WorkOrderVo vo = convertToVo(order);

        // 查出流转轨迹
        List<BizOrderLog> logs = orderLogMapper.selectList(
                new LambdaQueryWrapper<BizOrderLog>()
                        .eq(BizOrderLog::getOrderId, orderId)
                        .orderByAsc(BizOrderLog::getCreateTime)
        );
        vo.setLogs(logs);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void evaluateOrder(WorkOrderEvaluateDto dto) {
        Long userId = SecurityUtils.getUserId();
        BizWorkOrder order = workOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.WORK_ORDER_NOT_FOUND);
        }
        if (!order.getReporterId().equals(userId)) {
            throw new BizException(ErrorCode.WORK_ORDER_PERMISSION_DENIED);
        }
        if (order.getStatus() != 5) {
            throw new BizException(ErrorCode.WORK_ORDER_STATUS_ERROR, "仅已完工工单支持居民评价");
        }

        order.setStatus(6); // 已评价结单
        order.setRatingStar(dto.getRatingStar());
        order.setRatingContent(dto.getRatingContent() != null ? dto.getRatingContent() : "");
        workOrderMapper.updateById(order);

        // 记录流转日志
        orderLogMapper.insert(BizOrderLog.builder()
                .id(IdUtil.getSnowflakeNextId())
                .orderId(order.getId())
                .actionType("EVALUATE")
                .fromStatus(5)
                .toStatus(6)
                .operatorId(userId)
                .operatorName(SecurityUtils.getUsername())
                .operatorRole(SecurityUtils.getRole())
                .remark("居民给出 " + dto.getRatingStar() + " 星评价: " + dto.getRatingContent())
                .build());

        // 奖励居民 5 点社区互助积分（SQL 原子自增，避免并发覆盖）
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .setSql("credit_score = credit_score + 5")
                .eq(SysUser::getId, userId));
    }

    @Override
    public PageResult<WorkOrderVo> listAdminOrders(Integer orderType, Integer status, Long buildingId, Integer isUrgent, Long page, Long size) {
        Page<BizWorkOrder> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);

        // 网格员数据范围：仅可见本人负责楼栋的工单
        List<Long> officerScope = null;
        if (SecurityUtils.hasRole(RoleConstant.ROLE_GRID_OFFICER)) {
            officerScope = currentOfficerBuildingIds();
            if (officerScope.isEmpty() || (buildingId != null && !officerScope.contains(buildingId))) {
                return PageResult.of(new ArrayList<>(), 0L, pageParam.getSize(), pageParam.getCurrent());
            }
        }

        Page<BizWorkOrder> result = workOrderMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(orderType != null, BizWorkOrder::getOrderType, orderType)
                        .eq(status != null, BizWorkOrder::getStatus, status)
                        .eq(buildingId != null, BizWorkOrder::getBuildingId, buildingId)
                        .in(officerScope != null, BizWorkOrder::getBuildingId, officerScope)
                        .eq(isUrgent != null, BizWorkOrder::getIsUrgent, isUrgent)
                        .orderByDesc(BizWorkOrder::getIsUrgent)
                        .orderByDesc(BizWorkOrder::getCreateTime)
        );

        List<WorkOrderVo> voList = convertToVoList(result.getRecords());

        return PageResult.of(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditOrder(WorkOrderAuditDto dto) {
        Long operatorId = SecurityUtils.getUserId();
        SysUser operator = userMapper.selectById(operatorId);
        BizWorkOrder order = workOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.WORK_ORDER_NOT_FOUND);
        }
        if (!isInOfficerBuildingScope(order.getBuildingId())) {
            throw new BizException(ErrorCode.WORK_ORDER_PERMISSION_DENIED);
        }
        if (order.getStatus() != 1) {
            throw new BizException(ErrorCode.WORK_ORDER_STATUS_ERROR, "仅待审核状态工单可初审");
        }

        int targetStatus = Boolean.TRUE.equals(dto.getPass()) ? 3 : 2;
        order.setStatus(targetStatus);
        order.setReviewerId(operatorId);
        order.setReviewTime(LocalDateTime.now());
        order.setReviewRemark(dto.getRemark() != null ? dto.getRemark() : "");

        if (Boolean.TRUE.equals(dto.getPass())) {
            order.setHandlerId(dto.getHandlerId());
            order.setHandlerName(dto.getHandlerName() != null ? dto.getHandlerName() : "物业维修部");
            order.setHandlerPhone(dto.getHandlerPhone() != null ? dto.getHandlerPhone() : "");
        }

        workOrderMapper.updateById(order);

        // 记录流转日志
        orderLogMapper.insert(BizOrderLog.builder()
                .id(IdUtil.getSnowflakeNextId())
                .orderId(order.getId())
                .actionType(Boolean.TRUE.equals(dto.getPass()) ? "REVIEW_PASS" : "REVIEW_REJECT")
                .fromStatus(1)
                .toStatus(targetStatus)
                .operatorId(operatorId)
                .operatorName(operator != null ? operator.getRealName() : "审核员")
                .operatorRole(SecurityUtils.getRole())
                .remark(Boolean.TRUE.equals(dto.getPass()) ? "审核通过已派单: " + dto.getRemark() : "初审驳回: " + dto.getRemark())
                .build());

        // 发送站内信与 STOMP 实时通知居民
        sendOrderNotification(order, Boolean.TRUE.equals(dto.getPass()) ? "工单已通过审核并派单" : "工单已被审核驳回");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatchOrder(Long orderId, Long handlerId, String handlerName, String handlerPhone) {
        BizWorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) throw new BizException(ErrorCode.WORK_ORDER_NOT_FOUND);
        if (order.getStatus() != 1 && order.getStatus() != 3) {
            throw new BizException(ErrorCode.WORK_ORDER_STATUS_ERROR, "仅待审核或已派单状态的工单可(重新)派单");
        }
        int fromStatus = order.getStatus();

        order.setStatus(3);
        order.setHandlerId(handlerId);
        order.setHandlerName(handlerName);
        order.setHandlerPhone(handlerPhone);
        workOrderMapper.updateById(order);

        orderLogMapper.insert(BizOrderLog.builder()
                .id(IdUtil.getSnowflakeNextId())
                .orderId(order.getId())
                .actionType("DISPATCH")
                .fromStatus(fromStatus)
                .toStatus(3)
                .operatorId(SecurityUtils.getUserId())
                .operatorName(SecurityUtils.getUsername())
                .operatorRole(SecurityUtils.getRole())
                .remark("指派维修处理人员: " + handlerName + "(" + handlerPhone + ")")
                .build());

        sendOrderNotification(order, "工单已指派专人处理: " + handlerName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startHandle(Long orderId) {
        BizWorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) throw new BizException(ErrorCode.WORK_ORDER_NOT_FOUND);
        if (order.getStatus() != 3) {
            throw new BizException(ErrorCode.WORK_ORDER_STATUS_ERROR, "仅已派单工单可开工处理");
        }

        order.setStatus(4); // 处理中
        order.setHandleTime(LocalDateTime.now());
        workOrderMapper.updateById(order);

        orderLogMapper.insert(BizOrderLog.builder()
                .id(IdUtil.getSnowflakeNextId())
                .orderId(order.getId())
                .actionType("START_HANDLE")
                .fromStatus(3)
                .toStatus(4)
                .operatorId(SecurityUtils.getUserId())
                .operatorName(SecurityUtils.getUsername())
                .operatorRole(SecurityUtils.getRole())
                .remark("维修师傅已接单开工处理")
                .build());

        sendOrderNotification(order, "您的工单已开工处理中");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(WorkOrderHandleDto dto) {
        BizWorkOrder order = workOrderMapper.selectById(dto.getOrderId());
        if (order == null) throw new BizException(ErrorCode.WORK_ORDER_NOT_FOUND);
        if (order.getStatus() != 4) {
            throw new BizException(ErrorCode.WORK_ORDER_STATUS_ERROR, "仅处理中工单可标记完工");
        }
        int fromStatus = order.getStatus();

        order.setStatus(5); // 已完工待评价
        order.setHandleResult(dto.getHandleResult());
        order.setHandleImages(cleanJson(dto.getHandleImages()));
        order.setFinishTime(LocalDateTime.now());
        workOrderMapper.updateById(order);

        orderLogMapper.insert(BizOrderLog.builder()
                .id(IdUtil.getSnowflakeNextId())
                .orderId(order.getId())
                .actionType("COMPLETE")
                .fromStatus(fromStatus)
                .toStatus(5)
                .operatorId(SecurityUtils.getUserId())
                .operatorName(SecurityUtils.getUsername())
                .operatorRole(SecurityUtils.getRole())
                .remark("施工维修已完成: " + dto.getHandleResult())
                .evidenceImages(cleanJson(dto.getHandleImages()))
                .build());

        sendOrderNotification(order, "您的工单已完工，请前去验收并给出星级评价");
    }

    @Override
    public void exportOrdersExcel(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("社区工单报修记录表_" + DateUtil.format(new Date(), "yyyyMMddHHmmss"), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            List<BizWorkOrder> list = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>().orderByDesc(BizWorkOrder::getCreateTime)
            );

            List<OrderExcelModel> excelList = list.stream().map(o -> OrderExcelModel.builder()
                    .orderNo(o.getOrderNo())
                    .orderType(o.getOrderType() == 1 ? "报修工单" : "投诉建议")
                    .category(o.getCategory())
                    .title(o.getTitle())
                    .reporterName(o.getReporterName())
                    .reporterPhone(o.getReporterPhone())
                    .statusName(getStatusName(o.getStatus()))
                    .isUrgent(o.getIsUrgent() == 1 ? "加急" : "常规")
                    .handlerName(o.getHandlerName())
                    .ratingStar(o.getRatingStar() != null ? o.getRatingStar() + "星" : "未评")
                    .createTime(o.getCreateTime() != null ? o.getCreateTime().toString() : "")
                    .build()
            ).collect(Collectors.toList());

            EasyExcel.write(response.getOutputStream(), OrderExcelModel.class)
                    .sheet("社区事务工单数据")
                    .doWrite(excelList);
        } catch (IOException e) {
            log.error("导出工单 Excel 异常: ", e);
            throw new BizException("导出 Excel 失败: " + e.getMessage());
        }
    }

    private void sendOrderNotification(BizWorkOrder order, String summary) {
        try {
            // 写入站内信（随事务提交/回滚）
            SysUserNotice notice = SysUserNotice.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .userId(order.getReporterId())
                    .noticeType("WORK_ORDER")
                    .title("工单进度动态: " + order.getOrderNo())
                    .summary(summary)
                    .bizId(order.getId())
                    .isRead(0)
                    .build();
            userNoticeMapper.insert(notice);

            // WebSocket 推送延迟到事务提交后执行，避免回滚后居民已收到通知
            Runnable push = () -> {
                try {
                    messagingTemplate.convertAndSendToUser(
                            order.getReporterId().toString(),
                            "/queue/notify",
                            Map.of(
                                    "type", "WORK_ORDER",
                                    "title", "工单进度动态提醒",
                                    "summary", summary,
                                    "orderId", order.getId(),
                                    "status", order.getStatus()
                            )
                    );
                } catch (Exception e) {
                    log.warn("发送工单消息推送异常: {}", e.getMessage());
                }
            };
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        push.run();
                    }
                });
            } else {
                push.run();
            }
        } catch (Exception e) {
            log.warn("写入工单站内信异常: {}", e.getMessage());
        }
    }

    private boolean isAdminUser() {
        String role = SecurityUtils.getRole();
        return role != null && ADMIN_ROLES.contains(role);
    }

    /**
     * 非网格员角色不受楼栋数据范围限制；网格员仅可触达 biz_building.grid_officer_id
     * 绑定给本人的楼栋下的数据。
     */
    private boolean isInOfficerBuildingScope(Long buildingId) {
        if (!SecurityUtils.hasRole(RoleConstant.ROLE_GRID_OFFICER)) {
            return true;
        }
        return buildingId != null && currentOfficerBuildingIds().contains(buildingId);
    }

    private List<Long> currentOfficerBuildingIds() {
        return buildingMapper.selectList(
                        new LambdaQueryWrapper<BizBuilding>()
                                .select(BizBuilding::getId)
                                .eq(BizBuilding::getGridOfficerId, SecurityUtils.getUserId()))
                .stream().map(BizBuilding::getId).collect(Collectors.toList());
    }

    private WorkOrderVo convertToVo(BizWorkOrder order) {
        return convertToVoList(List.of(order)).get(0);
    }

    /**
     * 批量转换 VO：楼栋/房屋/社区一次 IN 查询加载，避免每单 3 次回表（N+1）
     */
    private List<WorkOrderVo> convertToVoList(List<BizWorkOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> buildingIds = orders.stream()
                .map(BizWorkOrder::getBuildingId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> houseIds = orders.stream()
                .map(BizWorkOrder::getHouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> communityIds = orders.stream()
                .map(BizWorkOrder::getCommunityId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, BizBuilding> buildingMap = buildingIds.isEmpty() ? Map.of() :
                buildingMapper.selectBatchIds(buildingIds).stream()
                        .collect(Collectors.toMap(BizBuilding::getId, b -> b, (a, b) -> a));
        Map<Long, BizHouse> houseMap = houseIds.isEmpty() ? Map.of() :
                houseMapper.selectBatchIds(houseIds).stream()
                        .collect(Collectors.toMap(BizHouse::getId, h -> h, (a, b) -> a));
        Map<Long, BizCommunity> communityMap = communityIds.isEmpty() ? Map.of() :
                communityMapper.selectBatchIds(communityIds).stream()
                        .collect(Collectors.toMap(BizCommunity::getId, c -> c, (a, b) -> a));

        return orders.stream().map(order -> {
            String bldName = "";
            String houseAddress = "";
            String commName = "";

            BizBuilding bld = order.getBuildingId() != null ? buildingMap.get(order.getBuildingId()) : null;
            if (bld != null) bldName = bld.getBuildingName();
            BizHouse house = order.getHouseId() != null ? houseMap.get(order.getHouseId()) : null;
            if (house != null) houseAddress = bldName + " " + house.getHouseNo();
            BizCommunity comm = order.getCommunityId() != null ? communityMap.get(order.getCommunityId()) : null;
            if (comm != null) commName = comm.getName();

            return WorkOrderVo.builder()
                    .id(order.getId())
                    .orderNo(order.getOrderNo())
                    .orderType(order.getOrderType())
                    .category(order.getCategory())
                    .communityId(order.getCommunityId())
                    .communityName(commName)
                    .buildingId(order.getBuildingId())
                    .buildingName(bldName)
                    .houseId(order.getHouseId())
                    .houseAddress(houseAddress)
                    .reporterId(order.getReporterId())
                    .reporterName(order.getReporterName())
                    .reporterPhone(order.getReporterPhone())
                    .title(order.getTitle())
                    .content(order.getContent())
                    .images(order.getImages())
                    .status(order.getStatus())
                    .isUrgent(order.getIsUrgent())
                    .reviewerId(order.getReviewerId())
                    .reviewTime(order.getReviewTime())
                    .reviewRemark(order.getReviewRemark())
                    .handlerId(order.getHandlerId())
                    .handlerName(order.getHandlerName())
                    .handlerPhone(order.getHandlerPhone())
                    .handleTime(order.getHandleTime())
                    .handleResult(order.getHandleResult())
                    .handleImages(order.getHandleImages())
                    .finishTime(order.getFinishTime())
                    .ratingStar(order.getRatingStar())
                    .ratingContent(order.getRatingContent())
                    .createTime(order.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    private String cleanJson(String str) {
        if (str == null || str.trim().isEmpty() || "null".equalsIgnoreCase(str.trim()) || "\"\"".equals(str.trim())) {
            return null;
        }
        return str.trim();
    }

    private String getStatusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 1 -> "待审核";
            case 2 -> "已驳回";
            case 3 -> "已派单";
            case 4 -> "处理中";
            case 5 -> "已完工待评价";
            case 6 -> "已评价结单";
            default -> "未知状态";
        };
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderExcelModel {
        @com.alibaba.excel.annotation.ExcelProperty("工单单号")
        private String orderNo;
        @com.alibaba.excel.annotation.ExcelProperty("业务类型")
        private String orderType;
        @com.alibaba.excel.annotation.ExcelProperty("故障分类")
        private String category;
        @com.alibaba.excel.annotation.ExcelProperty("工单主题")
        private String title;
        @com.alibaba.excel.annotation.ExcelProperty("报修住户")
        private String reporterName;
        @com.alibaba.excel.annotation.ExcelProperty("联系电话")
        private String reporterPhone;
        @com.alibaba.excel.annotation.ExcelProperty("流转状态")
        private String statusName;
        @com.alibaba.excel.annotation.ExcelProperty("紧急程度")
        private String isUrgent;
        @com.alibaba.excel.annotation.ExcelProperty("处理人员")
        private String handlerName;
        @com.alibaba.excel.annotation.ExcelProperty("居民满意度")
        private String ratingStar;
        @com.alibaba.excel.annotation.ExcelProperty("发起时间")
        private String createTime;
    }
}
