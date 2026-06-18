package cn.edu.nsu.maic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class AdminUserDtos {

    public static class Summary {
        private Long id;
        private String username;
        private String realName;
        private String role;
        private String department;
        private Boolean enabled;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public LocalDateTime getLastLoginAt() {
            return lastLoginAt;
        }

        public void setLastLoginAt(LocalDateTime lastLoginAt) {
            this.lastLoginAt = lastLoginAt;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class CreateRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名不能超过64个字符")
        private String username;

        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为8到72个字符")
        private String password;

        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名不能超过64个字符")
        private String realName;

        @NotBlank(message = "角色不能为空")
        @Pattern(regexp = "admin|teacher", message = "角色无效")
        private String role;

        @Size(max = 128, message = "院系不能超过128个字符")
        private String department;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }
    }

    public static class UpdateRequest {
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名不能超过64个字符")
        private String realName;

        @NotBlank(message = "角色不能为空")
        @Pattern(regexp = "admin|teacher", message = "角色无效")
        private String role;

        @Size(max = 128, message = "院系不能超过128个字符")
        private String department;

        @NotNull(message = "账号状态不能为空")
        private Boolean enabled;

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ResetPasswordRequest {
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为8到72个字符")
        private String password;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class EnabledRequest {
        @NotNull(message = "账号状态不能为空")
        private Boolean enabled;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
