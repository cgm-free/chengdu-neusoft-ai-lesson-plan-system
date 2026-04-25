package cn.edu.nsu.maic.dto;

import java.util.ArrayList;
import java.util.List;

public class TeachingCalendarDto {

    private String fileName;
    private String fileType;
    private Integer rowCount;
    private String excerpt;
    private String uploadedAt;
    private List<TeachingCalendarEntryDto> entries = new ArrayList<>();

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(String uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public List<TeachingCalendarEntryDto> getEntries() {
        return entries;
    }

    public void setEntries(List<TeachingCalendarEntryDto> entries) {
        this.entries = entries == null ? new ArrayList<>() : entries;
    }
}
