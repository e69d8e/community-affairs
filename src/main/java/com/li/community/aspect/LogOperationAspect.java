package com.li.community.aspect;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.li.community.common.annotation.LogOperation;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.SysOperationLogMapper;
import com.li.community.model.entity.SysOperationLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogOperationAspect {

    private final SysOperationLogMapper operationLogMapper;

    // 独立单线程异步写库：不阻塞请求线程（原 @Async 自调用不生效，实为同步写库）
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "op-log-writer");
        t.setDaemon(true);
        return t;
    });

    // 简易手机号脱敏，避免 PII 明文入库
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");

    @Around("@annotation(logOperation)")
    public Object doAround(ProceedingJoinPoint joinPoint, LogOperation logOperation) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            // 在请求线程内完成上下文快照（request/SecurityContext 仅在当前线程可用）
            SysOperationLog operLog = buildLog(joinPoint, logOperation, result, exception, executionTime);
            if (operLog != null) {
                LOG_EXECUTOR.execute(() -> {
                    try {
                        operationLogMapper.insert(operLog);
                    } catch (Exception e) {
                        log.warn("异步记录审计日志失败: {}", e.getMessage());
                    }
                });
            }
        }
    }

    private SysOperationLog buildLog(ProceedingJoinPoint joinPoint, LogOperation logOperation,
                                     Object result, Exception exception, long executionTime) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

            SysOperationLog operLog = new SysOperationLog();
            operLog.setId(IdUtil.getSnowflakeNextId());
            operLog.setTitle(logOperation.title());
            operLog.setBusinessType(logOperation.businessType());
            operLog.setExecutionTime(executionTime);
            operLog.setOperTime(LocalDateTime.now());

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            operLog.setMethod(joinPoint.getTarget().getClass().getName() + "." + method.getName() + "()");

            if (request != null) {
                operLog.setRequestMethod(request.getMethod());
                operLog.setOperUrl(request.getRequestURI());
                operLog.setOperIp(request.getRemoteAddr());
            }

            try {
                Long userId = SecurityUtils.getUserId();
                String username = SecurityUtils.getUsername();
                String role = SecurityUtils.getRole();
                operLog.setOperatorId(userId != null ? userId : 0L);
                operLog.setOperatorName(username != null ? username : "匿名/公开");
                operLog.setOperatorRole(role != null ? role : "");
            } catch (Exception ignored) {
                operLog.setOperatorId(0L);
                operLog.setOperatorName("匿名/公开");
                operLog.setOperatorRole("");
            }

            if (logOperation.saveParam()) {
                operLog.setOperParam(maskPhones(JSONUtil.toJsonStr(joinPoint.getArgs())));
            }

            if (exception != null) {
                operLog.setStatus(0);
                operLog.setErrorMsg(exception.getMessage());
            } else {
                operLog.setStatus(1);
                if (logOperation.saveResult() && result != null) {
                    operLog.setJsonResult(maskPhones(JSONUtil.toJsonStr(result)));
                }
            }

            return operLog;
        } catch (Exception e) {
            log.warn("构建审计日志快照失败: {}", e.getMessage());
            return null;
        }
    }

    private String maskPhones(String json) {
        if (json == null) {
            return null;
        }
        return PHONE_PATTERN.matcher(json).replaceAll("$1****$2");
    }
}
