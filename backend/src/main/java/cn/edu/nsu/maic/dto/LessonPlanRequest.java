package cn.edu.nsu.maic.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class LessonPlanRequest {

    private Long userId = 1L;

    @NotBlank(message = "课程名称不能为空")
    private String courseName;

    private String major;
    private String grade;
    private String targetStudents;

    @NotBlank(message = "章节主题不能为空")
    private String topic;

    @NotBlank(message = "课程类型不能为空")
    private String lessonType;

    private String teachingMode;

    @Min(value = 1, message = "课时数至少为1")
    private Integer periodCount = 2;

    @Min(value = 1, message = "每节课分钟数至少为1")
    private Integer minutesPerPeriod = 40;

    private String studentAnalysis;
    private String prerequisiteKnowledge;
    private String commonMisconceptions;
    private String classLevelProfile;
    private String lessonFocus;
    private String expectedOutputs;
    private String textbook;
    private String experimentEnv;
    private Boolean includeIdeology = true;
    private Boolean includeObe = true;
    private String extraRequirements;
    private List<ReferenceMaterialDto> referenceMaterials;
    private TeachingCalendarDto teachingCalendar;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getStudentAnalysis() {
        return studentAnalysis;
    }

    public void setStudentAnalysis(String studentAnalysis) {
        this.studentAnalysis = studentAnalysis;
    }

    public String getPrerequisiteKnowledge() {
        return prerequisiteKnowledge;
    }

    public void setPrerequisiteKnowledge(String prerequisiteKnowledge) {
        this.prerequisiteKnowledge = prerequisiteKnowledge;
    }

    public String getCommonMisconceptions() {
        return commonMisconceptions;
    }

    public void setCommonMisconceptions(String commonMisconceptions) {
        this.commonMisconceptions = commonMisconceptions;
    }

    public String getClassLevelProfile() {
        return classLevelProfile;
    }

    public void setClassLevelProfile(String classLevelProfile) {
        this.classLevelProfile = classLevelProfile;
    }

    public String getLessonFocus() {
        return lessonFocus;
    }

    public void setLessonFocus(String lessonFocus) {
        this.lessonFocus = lessonFocus;
    }

    public String getExpectedOutputs() {
        return expectedOutputs;
    }

    public void setExpectedOutputs(String expectedOutputs) {
        this.expectedOutputs = expectedOutputs;
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

    public List<ReferenceMaterialDto> getReferenceMaterials() {
        return referenceMaterials;
    }

    public void setReferenceMaterials(List<ReferenceMaterialDto> referenceMaterials) {
        this.referenceMaterials = referenceMaterials;
    }

    public TeachingCalendarDto getTeachingCalendar() {
        return teachingCalendar;
    }

    public void setTeachingCalendar(TeachingCalendarDto teachingCalendar) {
        this.teachingCalendar = teachingCalendar;
    }
}
