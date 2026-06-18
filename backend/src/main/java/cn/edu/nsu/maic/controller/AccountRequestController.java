package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.AccountRequestDtos;
import cn.edu.nsu.maic.dto.ApiResponse;
import cn.edu.nsu.maic.dto.UserInfo;
import cn.edu.nsu.maic.service.AccountRequestService;
import cn.edu.nsu.maic.service.AuthService;
import cn.edu.nsu.maic.service.RequestRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class AccountRequestController {

    private final AccountRequestService accountRequestService;
    private final AuthService authService;
    private final RequestRateLimitService requestRateLimitService;

    public AccountRequestController(
            AccountRequestService accountRequestService,
            AuthService authService,
            RequestRateLimitService requestRateLimitService
    ) {
        this.accountRequestService = accountRequestService;
        this.authService = authService;
        this.requestRateLimitService = requestRateLimitService;
    }

    @PostMapping("/api/account-requests")
    public ApiResponse<AccountRequestDtos.Summary> submit(
            @Valid @RequestBody AccountRequestDtos.CreateRequest request,
            HttpServletRequest servletRequest
    ) {
        RequestRateLimitService.LimitResult limit = requestRateLimitService.checkRegistration(servletRequest);
        if (!limit.allowed()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "注册尝试过于频繁，请稍后再试");
        }
        return ApiResponse.ok(accountRequestService.submit(request));
    }

    @GetMapping("/api/admin/account-requests")
    public ApiResponse<List<AccountRequestDtos.Summary>> list(@RequestParam(value = "status", required = false) String status,
                                                              HttpServletRequest request) {
        authService.requireAdmin(request);
        return ApiResponse.ok(accountRequestService.list(status));
    }

    @PostMapping("/api/admin/account-requests/{id}/approve")
    public ApiResponse<AccountRequestDtos.ApprovalResult> approve(@PathVariable Long id,
                                                                  @Valid @RequestBody(required = false) AccountRequestDtos.ReviewRequest payload,
                                                                  HttpServletRequest request) {
        UserInfo operator = authService.requireAdmin(request);
        return ApiResponse.ok(accountRequestService.approve(id, payload, operator));
    }

    @PostMapping("/api/admin/account-requests/{id}/reject")
    public ApiResponse<AccountRequestDtos.Summary> reject(@PathVariable Long id,
                                                          @Valid @RequestBody(required = false) AccountRequestDtos.ReviewRequest payload,
                                                          HttpServletRequest request) {
        UserInfo operator = authService.requireAdmin(request);
        return ApiResponse.ok(accountRequestService.reject(id, payload, operator));
    }
}
