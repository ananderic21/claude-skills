package dev.anand.claudeskills.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Aspect
@Component
@Order(0)
public class RequestLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger("REQUEST");

    @Around("within(dev.anand.claudeskills.controller..*)")
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
        HttpServletRequest request = currentRequest();
        String method = request != null ? request.getMethod() : "-";
        String uri = request != null ? request.getRequestURI() : "-";
        String handler = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        String client = request != null ? request.getRemoteAddr() : "-";

        log.info("--> {} {} | user={} | client={} | handler={} | args={}",
                method, uri, LoggingSupport.currentUser(), client, handler,
                LoggingSupport.describeArgs(joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            int status = result instanceof ResponseEntity<?> response ? response.getStatusCode().value() : 200;
            log.info("<-- {} {} | status={}", method, uri, status);
            return result;
        } catch (Throwable ex) {
            log.warn("<-- {} {} | exception={} | message={}",
                    method, uri, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        } finally {
            MDC.remove("requestId");
        }
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
