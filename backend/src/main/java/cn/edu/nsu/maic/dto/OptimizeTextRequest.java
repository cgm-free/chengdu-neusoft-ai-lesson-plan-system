package cn.edu.nsu.maic.dto;

import jakarta.validation.constraints.NotBlank;

public class OptimizeTextRequest {

    @NotBlank(message = "待优化文本不能为空")
    private String text;

    private String fieldName;
    private String courseName;
    private String topic;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}

