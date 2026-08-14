package dev.anand.claudeskills.logging;

import dev.anand.claudeskills.dto.LoginRequest;
import dev.anand.claudeskills.dto.RegisterRequest;
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
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    @Pointcut("execution(* dev.anand.claudeskills.service.TaskServiceImpl.createTask(..))"
            + " || execution(* dev.anand.claudeskills.service.TaskServiceImpl.updateTask(..))"
            + " || execution(* dev.anand.claudeskills.service.TaskServiceImpl.deleteTask(..))")
    public void taskMutations() {
    }

    @Pointcut("execution(* dev.anand.claudeskills.service.AuthServiceImpl.*(..))")
    public void authOperations() {
    }

    @Pointcut("execution(* dev.anand.claudeskills.service.ProfileServiceImpl.updateProfile(..))"
            + " || execution(* dev.anand.claudeskills.service.ProfileServiceImpl.changePassword(..))"
            + " || execution(* dev.anand.claudeskills.service.ProfileServiceImpl.updatePicture(..))")
    public void profileMutations() {
    }

    @Around("taskMutations() || authOperations() || profileMutations()")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String action = joinPoint.getSignature().getName();
        String actor = resolveActor(joinPoint.getArgs());
        String details = LoggingSupport.describeArgs(joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            log.info("action={} | actor={} | outcome=SUCCESS | details={}", action, actor, details);
            return result;
        } catch (Throwable ex) {
            log.warn("action={} | actor={} | outcome=FAILURE | reason={} | details={}",
                    action, actor, ex.getMessage(), details);
            throw ex;
        }
    }

    // Auth endpoints run unauthenticated, so fall back to the username inside the request payload
    private String resolveActor(Object[] args) {
        String actor = LoggingSupport.currentUser();
        if (!"anonymous".equals(actor)) {
            return actor;
        }
        for (Object arg : args) {
            if (arg instanceof LoginRequest login) {
                return login.username();
            }
            if (arg instanceof RegisterRequest register) {
                return register.username();
            }
            if (arg instanceof String username) {
                return username;
            }
        }
        return actor;
    }
}
