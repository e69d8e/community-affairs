package com.li.community.service;

import com.li.community.common.result.PageResult;
import com.li.community.model.dto.*;
import com.li.community.model.entity.BizWorkOrder;
import com.li.community.model.vo.WorkOrderVo;
import jakarta.servlet.http.HttpServletResponse;

public interface WorkOrderService {

    WorkOrderVo submitOrder(WorkOrderSubmitDto dto);

    PageResult<WorkOrderVo> getMyOrders(Integer orderType, Integer status, Long page, Long size);

    WorkOrderVo getOrderDetail(Long orderId);

    void evaluateOrder(WorkOrderEvaluateDto dto);

    PageResult<WorkOrderVo> listAdminOrders(Integer orderType, Integer status, Long buildingId, Integer isUrgent, Long page, Long size);

    void auditOrder(WorkOrderAuditDto dto);

    void dispatchOrder(Long orderId, Long handlerId, String handlerName, String handlerPhone);

    void startHandle(Long orderId);

    void completeOrder(WorkOrderHandleDto dto);

    void exportOrdersExcel(HttpServletResponse response);
}
