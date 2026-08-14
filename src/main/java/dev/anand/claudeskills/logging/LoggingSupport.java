package dev.anand.claudeskills.logging;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.Arrays;
import java.util.stream.Collectors;

final class LoggingSupport {

    private LoggingSupport() {
    }

    static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return authentication.getName();
    }

    // Sensitive DTOs mask their own secrets via toString(); principals are reduced to a name
    static String describeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.stream(args)
                .map(LoggingSupport::describeArg)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String describeArg(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof Principal principal) {
            return "principal=" + principal.getName();
        }
        return arg.toString();
    }
}
