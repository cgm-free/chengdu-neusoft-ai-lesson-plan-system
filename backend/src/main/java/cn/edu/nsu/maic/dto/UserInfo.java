package cn.edu.nsu.maic.dto;

public class UserInfo {

    private Long id;
    private String username;
    private String realName;
    private String role;
    private String department;

    public UserInfo() {
    }

    public UserInfo(Long id, String username, String realName, String role, String department) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.role = role;
        this.department = department;
    }

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

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }
}

