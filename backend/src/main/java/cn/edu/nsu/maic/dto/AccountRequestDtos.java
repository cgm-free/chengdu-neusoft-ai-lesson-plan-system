package cn.edu.nsu.maic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class AccountRequestDtos {

    public static class CreateRequest {
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_\\-]{3,64}$", message = "用户名需为3到64位字母、数字、下划线或短横线")
        private String username;

        @NotBlank(message = "教师姓名不能为空")
        @Size(max = 64, message = "教师姓名不能超过64个字符")
        private String realName;

        @NotBlank(message = "学院不能为空")
        @Size(max = 128, message = "学院不能超过128个字符")
        private String college;

        @NotBlank(message = "系部不能为空")
        @Size(max = 128, message = "系部不能超过128个字符")
        private String department;

        @Size(max = 128, message = "专业不能超过128个字符")
        private String major;

        @Size(max = 255, message = "课程名称不能超过255个字符")
        private String courseName;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 72, message = "密码长度必须为6到72个字符")
        private String password;

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

        public String getCollege() {
            return college;
        }

        public void setCollege(String college) {
            this.college = college;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getMajor() {
            return major;
        }

        public void setMajor(String major) {
            this.major = major;
        }

        public String getCourseName() {
            return courseName;
        }

        public void setCourseName(String courseName) {
            this.courseName = courseName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class ReviewRequest {
        @Size(max = 255, message = "审核备注不能超过255个字符")
        private String reviewNote;

        public String getReviewNote() {
            return reviewNote;
        }

        public void setReviewNote(String reviewNote) {
            this.reviewNote = reviewNote;
        }
    }

    public static class ApprovalResult {
        private AdminUserDtos.Summary user;
        private Summary request;
        private String initialPassword;

        public AdminUserDtos.Summary getUser() {
            return user;
        }

        public void setUser(AdminUserDtos.Summary user) {
            this.user = user;
        }

        public Summary getRequest() {
            return request;
        }

        public void setRequest(Summary request) {
            this.request = request;
        }

        public String getInitialPassword() {
            return initialPassword;
        }

        public void setInitialPassword(String initialPassword) {
            this.initialPassword = initialPassword;
        }
    }

    public static class Summary {
        private Long id;
        private String username;
        private String realName;
        private String college;
        private String department;
        private String major;
        private String courseName;
        private String status;
        private String reviewNote;
        private Long reviewedBy;
        private LocalDateTime reviewedAt;
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

        public String getCollege() {
            return college;
        }

        public void setCollege(String college) {
            this.college = college;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getMajor() {
            return major;
        }

        public void setMajor(String major) {
            this.major = major;
        }

        public String getCourseName() {
            return courseName;
        }

        public void setCourseName(String courseName) {
            this.courseName = courseName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReviewNote() {
            return reviewNote;
        }

        public void setReviewNote(String reviewNote) {
            this.reviewNote = reviewNote;
        }

        public Long getReviewedBy() {
            return reviewedBy;
        }

        public void setReviewedBy(Long reviewedBy) {
            this.reviewedBy = reviewedBy;
        }

        public LocalDateTime getReviewedAt() {
            return reviewedAt;
        }

        public void setReviewedAt(LocalDateTime reviewedAt) {
            this.reviewedAt = reviewedAt;
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
}
