package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.AccountRequestDtos;
import cn.edu.nsu.maic.dto.AdminUserDtos;
import cn.edu.nsu.maic.dto.ApiResponse;
import cn.edu.nsu.maic.dto.UserInfo;
import cn.edu.nsu.maic.service.AccountRequestService;
import cn.edu.nsu.maic.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AccountRequestController {

    private final AccountRequestService accountRequestService;
    private final AuthService authService;

    public AccountRequestController(AccountRequestService accountRequestService, AuthService authService) {
        this.accountRequestService = accountRequestService;
        this.authService = authService;
    }

    @PostMapping("/api/account-requests")
    public ApiResponse<AccountRequestDtos.Summary> submit(@Valid @RequestBody AccountRequestDtos.CreateRequest request) {
        return ApiResponse.ok(accountRequestService.submit(request));
    }

    @GetMapping("/api/admin/account-requests")
    public ApiResponse<List<AccountRequestDtos.Summary>> list(@RequestParam(value = "status", required = false) String status,
                                                              HttpServletRequest request) {
        authService.requireAdmin(request);
        return ApiResponse.ok(accountRequestService.list(status));
    }

    @PostMapping("/api/admin/account-requests/{id}/approve")
    public ApiResponse<AdminUserDtos.Summary> approve(@PathVariable Long id,
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
