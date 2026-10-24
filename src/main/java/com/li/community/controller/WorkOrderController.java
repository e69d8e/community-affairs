package com.li.community.controller;

import com.li.community.common.annotation.LogOperation;
import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.dto.*;
import com.li.community.model.vo.WorkOrderVo;
import com.li.community.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "03. 报修与投诉工单状态机模块")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @Operation(summary = "居民在线提交报修/投诉工单")
    @PostMapping("/submit")
    @LogOperation(title = "工单申报", businessType = "INSERT", saveParam = true)
    public Result<WorkOrderVo> submitOrder(@Valid @RequestBody WorkOrderSubmitDto dto) {
        return Result.success("工单申报成功", workOrderService.submitOrder(dto));
    }

    @Operation(summary = "居民获取本人历史工单列表")
    @GetMapping("/my-list")
    public Result<PageResult<WorkOrderVo>> getMyOrders(
            @RequestParam(required = false) Integer orderType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(workOrderService.getMyOrders(orderType, status, page, size));
    }

    @Operation(summary = "获取工单完整流转详情与轨迹时间轴 (支持数字主键或工单编号)")
    @GetMapping("/detail/{id}")
    public Result<WorkOrderVo> getOrderDetail(@PathVariable String id) {
        return Result.success(workOrderService.getOrderDetailByIdOrNo(id));
    }

    @Operation(summary = "居民对已完工工单进行星级评价结单")
    @PostMapping("/evaluate")
    @LogOperation(title = "工单满意度评价", businessType = "UPDATE")
    public Result<Void> evaluateOrder(@Valid @RequestBody WorkOrderEvaluateDto dto) {
        workOrderService.evaluateOrder(dto);
        return Result.success("评价成功，已为您增加5点社区互助信用分！", null);
    }

    @Operation(summary = "管理端分页查询工单列表 (支持多维度检索, 网格员仅可见本人负责楼栋; 非平台管理员强制本小区数据)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN', 'GRID_OFFICER')")
    @GetMapping("/admin/list")
    public Result<PageResult<WorkOrderVo>> listAdminOrders(
            @RequestParam(required = false) Integer orderType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Integer isUrgent,
            @RequestParam(required = false) Long communityId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(workOrderService.listAdminOrders(orderType, status, buildingId, isUrgent, communityId, page, size));
    }

    @Operation(summary = "网格员/物业管理员初审工单 (通过派单/驳回)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN', 'GRID_OFFICER')")
    @PutMapping("/admin/audit")
    @LogOperation(title = "工单初审", businessType = "AUDIT")
    public Result<Void> auditOrder(@Valid @RequestBody WorkOrderAuditDto dto) {
        workOrderService.auditOrder(dto);
        return Result.success("工单审核处理成功", null);
    }

    @Operation(summary = "物业管理员指派/改派维修人员")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'PROPERTY_ADMIN')")
    @PutMapping("/admin/dispatch")
    @LogOperation(title = "工单指派派单", businessType = "DISPATCH")
    public Result<Void> dispatchOrder(@RequestBody Map<String, Object> params) {
        Long orderId = Long.parseLong(params.get("orderId").toString());
        Long handlerId = params.get("handlerId") != null ? Long.parseLong(params.get("handlerId").toString()) : null;
        String handlerName = params.get("handlerName") != null ? params.get("handlerName").toString() : "";
        String handlerPhone = params.get("handlerPhone") != null ? params.get("handlerPhone").toString() : "";
        workOrderService.dispatchOrder(orderId, handlerId, handlerName, handlerPhone);
        return Result.success("派单成功", null);
    }

    @Operation(summary = "处理师傅接单开工")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'PROPERTY_ADMIN')")
    @PutMapping("/admin/start")
    public Result<Void> startHandle(@RequestParam Long orderId) {
        workOrderService.startHandle(orderId);
        return Result.success("接单成功，状态已流转为处理中", null);
    }

    @Operation(summary = "处理师傅完工反馈并上传现场凭证")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'PROPERTY_ADMIN')")
    @PutMapping("/admin/complete")
    @LogOperation(title = "工单完工登记", businessType = "UPDATE")
    public Result<Void> completeOrder(@Valid @RequestBody WorkOrderHandleDto dto) {
        workOrderService.completeOrder(dto);
        return Result.success("完工登记成功，已通知业主前去验收评价", null);
    }

    @Operation(summary = "导出工单报修记录 Excel 表格")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @GetMapping("/admin/export")
    @LogOperation(title = "导出工单Excel", businessType = "EXPORT")
    public void exportOrdersExcel(
            @RequestParam(required = false) Long communityId,
            HttpServletResponse response) {
        workOrderService.exportOrdersExcel(communityId, response);
    }
}
