package cn.edu.nsu.maic.dto;

public class TeachingCalendarEntryDto {

    private String week;
    private String session;
    private Integer periodCount;
    private String lessonType;
    private String topic;
    private String rawText;
    private Integer allocatedHours;

    public String getWeek() {
        return week;
    }

    public void setWeek(String week) {
        this.week = week;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public Integer getPeriodCount() {
        return periodCount;
    }

    public void setPeriodCount(Integer periodCount) {
        this.periodCount = periodCount;
    }

    public String getLessonType() {
        return lessonType;
    }

    public void setLessonType(String lessonType) {
        this.lessonType = lessonType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public Integer getAllocatedHours() {
        return allocatedHours;
    }

    public void setAllocatedHours(Integer allocatedHours) {
        this.allocatedHours = allocatedHours;
    }
}
