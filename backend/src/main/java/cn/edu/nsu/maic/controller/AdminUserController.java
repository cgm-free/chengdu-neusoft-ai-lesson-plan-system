package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.AdminUserDtos;
import cn.edu.nsu.maic.dto.ApiResponse;
import cn.edu.nsu.maic.dto.UserInfo;
import cn.edu.nsu.maic.service.AdminUserService;
import cn.edu.nsu.maic.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AuthService authService;
    private final AdminUserService adminUserService;

    public AdminUserController(AuthService authService, AdminUserService adminUserService) {
        this.authService = authService;
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<List<AdminUserDtos.Summary>> list(HttpServletRequest request) {
        authService.requireAdmin(request);
        return ApiResponse.ok(adminUserService.listUsers());
    }

    @PostMapping
    public ApiResponse<AdminUserDtos.Summary> create(@Valid @RequestBody AdminUserDtos.CreateRequest payload,
                                                     HttpServletRequest request) {
        authService.requireAdmin(request);
        return ApiResponse.ok(adminUserService.createUser(payload));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminUserDtos.Summary> update(@PathVariable Long id,
                                                     @Valid @RequestBody AdminUserDtos.UpdateRequest payload,
                                                     HttpServletRequest request) {
        UserInfo operator = authService.requireAdmin(request);
        return ApiResponse.ok(adminUserService.updateUser(id, payload, operator));
    }

    @PatchMapping("/{id}/password")
    public ApiResponse<AdminUserDtos.Summary> resetPassword(@PathVariable Long id,
                                                            @Valid @RequestBody AdminUserDtos.ResetPasswordRequest payload,
                                                            HttpServletRequest request) {
        authService.requireAdmin(request);
        return ApiResponse.ok(adminUserService.resetPassword(id, payload));
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<AdminUserDtos.Summary> updateEnabled(@PathVariable Long id,
                                                            @Valid @RequestBody AdminUserDtos.EnabledRequest payload,
                                                            HttpServletRequest request) {
        UserInfo operator = authService.requireAdmin(request);
        return ApiResponse.ok(adminUserService.updateEnabled(id, Boolean.TRUE.equals(payload.getEnabled()), operator));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        UserInfo operator = authService.requireAdmin(request);
        adminUserService.disableUser(id, operator);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Void> deletePermanent(@PathVariable Long id, HttpServletRequest request) {
        UserInfo operator = authService.requireAdmin(request);
        adminUserService.deleteUserPermanently(id, operator);
        return ApiResponse.ok(null);
    }
}
