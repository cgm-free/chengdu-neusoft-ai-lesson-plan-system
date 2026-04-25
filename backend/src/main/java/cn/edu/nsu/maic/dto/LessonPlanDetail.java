package cn.edu.nsu.maic.dto;

import java.time.LocalDateTime;

public class LessonPlanDetail extends LessonPlanSummary {

    private String major;
    private String grade;
    private String targetStudents;
    private Long userId;
    private String teachingMode;
    private Integer periodCount;
    private Integer minutesPerPeriod;
    private String studentAnalysis;
    private String textbook;
    private String experimentEnv;
    private Boolean includeIdeology;
    private Boolean includeObe;
    private String extraRequirements;
    private String contentJson;
    private LocalDateTime createdAt;

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getTargetStudents() {
        return targetStudents;
    }

    public void setTargetStudents(String targetStudents) {
        this.targetStudents = targetStudents;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTeachingMode() {
        return teachingMode;
    }

    public void setTeachingMode(String teachingMode) {
        this.teachingMode = teachingMode;
    }

    public Integer getPeriodCount() {
        return periodCount;
    }

    public void setPeriodCount(Integer periodCount) {
        this.periodCount = periodCount;
    }

    public Integer getMinutesPerPeriod() {
        return minutesPerPeriod;
    }

    public void setMinutesPerPeriod(Integer minutesPerPeriod) {
        this.minutesPerPeriod = minutesPerPeriod;
    }

    public String getStudentAnalysis() {
        return studentAnalysis;
    }

    public void setStudentAnalysis(String studentAnalysis) {
        this.studentAnalysis = studentAnalysis;
    }

    public String getTextbook() {
        return textbook;
    }

    public void setTextbook(String textbook) {
        this.textbook = textbook;
    }

    public String getExperimentEnv() {
        return experimentEnv;
    }

    public void setExperimentEnv(String experimentEnv) {
        this.experimentEnv = experimentEnv;
    }

    public Boolean getIncludeIdeology() {
        return includeIdeology;
    }

    public void setIncludeIdeology(Boolean includeIdeology) {
        this.includeIdeology = includeIdeology;
    }

    public Boolean getIncludeObe() {
        return includeObe;
    }

    public void setIncludeObe(Boolean includeObe) {
        this.includeObe = includeObe;
    }

    public String getExtraRequirements() {
        return extraRequirements;
    }

    public void setExtraRequirements(String extraRequirements) {
        this.extraRequirements = extraRequirements;
    }

    public String getContentJson() {
        return contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
