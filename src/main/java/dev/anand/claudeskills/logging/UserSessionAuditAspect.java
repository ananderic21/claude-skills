package dev.anand.claudeskills.logging;

import dev.anand.claudeskills.dto.LoginRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
public class UserSessionAuditAspect {

    private static final Logger log = LoggerFactory.getLogger("USER_AUDIT");

    @Pointcut("execution(* dev.anand.claudeskills.service.AuthServiceImpl.login(..))"
            + " || execution(* dev.anand.claudeskills.service.AuthServiceImpl.logout(..))")
    public void sessionOperations() {
    }

    @Around("sessionOperations()")
    public Object logSessionEvent(ProceedingJoinPoint joinPoint) throws Throwable {
        String event = joinPoint.getSignature().getName().toUpperCase();
        String user = resolveUser(joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            log.info("event={} | user={} | outcome=SUCCESS", event, user);
            return result;
        } catch (Throwable ex) {
            log.warn("event={} | user={} | outcome=FAILURE | reason={}", event, user, ex.getMessage());
            throw ex;
        }
    }

    // login carries a LoginRequest payload, logout carries the authenticated username
    private String resolveUser(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof LoginRequest login) {
                return login.username();
            }
            if (arg instanceof String username) {
                return username;
            }
        }
        return LoggingSupport.currentUser();
    }
}
