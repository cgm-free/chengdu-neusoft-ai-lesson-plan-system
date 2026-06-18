package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.ApiResponse;
import cn.edu.nsu.maic.dto.LoginRequest;
import cn.edu.nsu.maic.dto.LoginResponse;
import cn.edu.nsu.maic.dto.UserInfo;
import cn.edu.nsu.maic.service.AuthService;
import cn.edu.nsu.maic.service.RequestRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RequestRateLimitService requestRateLimitService;

    public AuthController(AuthService authService, RequestRateLimitService requestRateLimitService) {
        this.authService = authService;
        this.requestRateLimitService = requestRateLimitService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        RequestRateLimitService.LimitResult limit = requestRateLimitService.checkLogin(servletRequest, request.getUsername());
        if (!limit.allowed()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
        }
        return ApiResponse.ok(authService.login(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(authService.extractToken(request));
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserInfo> me(HttpServletRequest request) {
        return ApiResponse.ok(authService.requireUser(request));
    }
}
