package cn.edu.nsu.maic.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RequestRateLimitService {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final int loginLimit;
    private final long loginWindowMillis;
    private final int registrationLimit;
    private final long registrationWindowMillis;

    public RequestRateLimitService(
            @Value("${maic.rate-limit.login.max-requests:10}") int loginLimit,
            @Value("${maic.rate-limit.login.window-minutes:10}") long loginWindowMinutes,
            @Value("${maic.rate-limit.registration.max-requests:5}") int registrationLimit,
            @Value("${maic.rate-limit.registration.window-minutes:60}") long registrationWindowMinutes
    ) {
        this.loginLimit = Math.max(1, loginLimit);
        this.loginWindowMillis = Duration.ofMinutes(Math.max(1, loginWindowMinutes)).toMillis();
        this.registrationLimit = Math.max(1, registrationLimit);
        this.registrationWindowMillis = Duration.ofMinutes(Math.max(1, registrationWindowMinutes)).toMillis();
    }

    public LimitResult checkLogin(HttpServletRequest request, String username) {
        String clientIp = clientIp(request);
        LimitResult accountResult = check(
                "login-account",
                clientIp + ":" + normalize(username),
                loginLimit,
                loginWindowMillis
        );
        LimitResult ipResult = check("login-ip", clientIp, Math.max(100, loginLimit * 10), loginWindowMillis);
        return !accountResult.allowed() ? accountResult : ipResult;
    }

    public LimitResult checkRegistration(HttpServletRequest request) {
        return check("registration", clientIp(request), registrationLimit, registrationWindowMillis);
    }

    private LimitResult check(String scope, String clientIp, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        String key = scope + ":" + clientIp;
        WindowCounter counter = counters.compute(key, (ignored, current) -> {
            if (current == null || now - current.windowStartedAt >= windowMillis) {
                return new WindowCounter(now, 1);
            }
            return new WindowCounter(current.windowStartedAt, current.count + 1);
        });
        if (counter.count <= limit) {
            return new LimitResult(true, 0);
        }
        long retryAfterMillis = Math.max(1000, windowMillis - (now - counter.windowStartedAt));
        return new LimitResult(false, Math.max(1, (retryAfterMillis + 999) / 1000));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record WindowCounter(long windowStartedAt, int count) {
    }

    public record LimitResult(boolean allowed, long retryAfterSeconds) {
    }
}
