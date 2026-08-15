package dev.anand.claudeskills.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression guard for requestId correlation: RequestIdFilter owns the requestId for the
 * whole request, so RequestLoggingAspect must NOT clear it — otherwise the id is gone by
 * the time GlobalExceptionHandler logs the failure, breaking error.log correlation.
 */
class RequestLoggingAspectMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void requestId_survivesAfterControllerThrows() throws Throwable {
        MDC.put("requestId", "abcd1234");

        Signature signature = mock(Signature.class);
        when(signature.getDeclaringType()).thenReturn(RequestLoggingAspectMdcTest.class);
        when(signature.getName()).thenReturn("boom");

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> new RequestLoggingAspect().logRequest(joinPoint))
                .isInstanceOf(IllegalStateException.class);

        // The aspect must leave the requestId in place for the exception handler.
        assertThat(MDC.get("requestId")).isEqualTo("abcd1234");
    }
}
