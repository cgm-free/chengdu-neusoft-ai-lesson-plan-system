package cn.edu.nsu.maic.dto;

import jakarta.validation.constraints.NotBlank;

public class LessonPlanSaveRequest {

    @NotBlank(message = "教案标题不能为空")
    private String title;

    private String courseName;

    private String major;

    private String grade;

    private String targetStudents;

    private String topic;

    private String lessonType;

    private String teachingMode;

    private Integer periodCount;

    private Integer minutesPerPeriod;

    @NotBlank(message = "教案内容不能为空")
    private String contentJson;

    private String status = "draft";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

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

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getLessonType() {
        return lessonType;
    }

    public void setLessonType(String lessonType) {
        this.lessonType = lessonType;
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

    public String getContentJson() {
        return contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
