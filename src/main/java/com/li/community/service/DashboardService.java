package com.li.community.service;

import com.li.community.model.vo.DashboardMetricsVo;
import com.li.community.model.vo.ScreenDataVo;

public interface DashboardService {

    DashboardMetricsVo getMetrics();

    ScreenDataVo getScreenData();
}
