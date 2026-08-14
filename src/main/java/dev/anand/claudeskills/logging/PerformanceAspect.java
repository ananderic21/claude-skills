package dev.anand.claudeskills.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(2)
public class PerformanceAspect {

    private static final Logger log = LoggerFactory.getLogger("PERFORMANCE");

    private final long slowThresholdMs;

    public PerformanceAspect(@Value("${app.logging.slow-threshold-ms:500}") long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    @Around("within(dev.anand.claudeskills.controller..*) || within(dev.anand.claudeskills.service..*)")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            String method = joinPoint.getSignature().getDeclaringType().getSimpleName()
                    + "." + joinPoint.getSignature().getName();
            if (elapsedMs >= slowThresholdMs) {
                log.warn("SLOW | {} took {} ms (threshold {} ms)", method, elapsedMs, slowThresholdMs);
            } else {
                log.info("{} took {} ms", method, elapsedMs);
            }
        }
    }
}
