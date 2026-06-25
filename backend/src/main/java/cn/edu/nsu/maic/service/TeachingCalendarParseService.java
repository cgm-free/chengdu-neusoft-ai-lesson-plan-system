package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.TeachingCalendarDto;
import cn.edu.nsu.maic.dto.TeachingCalendarEntryDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TeachingCalendarParseService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int EXCERPT_LENGTH = 1500;
    private static final int MAX_ROWS = 120;

    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public TeachingCalendarDto parse(MultipartFile file) throws IOException {
        validate(file);
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = selectTeachingCalendarSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("教学日历文件没有可读取的工作表");
            }
            int headerRowIndex = detectHeaderRow(sheet);
            ColumnMapping mapping = detectColumns(sheet, headerRowIndex);
            List<TeachingCalendarEntryDto> entries = collapseEntries(readEntries(sheet, headerRowIndex + 1, mapping));
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("未从教学日历中识别到授课安排，请确认表格包含周次、课次、课型和授课内容");
            }

            TeachingCalendarDto dto = new TeachingCalendarDto();
            dto.setFileName(fileName);
            dto.setFileType(detectFileType(fileName));
            dto.setEntries(entries);
            dto.setRowCount(entries.size());
            dto.setExcerpt(buildExcerpt(entries));
            dto.setUploadedAt(OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
            return dto;
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的教学日历");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("教学日历不能超过 10MB");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            throw new IllegalArgumentException("教学日历暂只支持 .xls / .xlsx 文件");
        }
    }

    private Sheet selectTeachingCalendarSheet(Workbook workbook) {
        Sheet fallback = null;
        Sheet bestSheet = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet != null && sheet.getPhysicalNumberOfRows() > 0) {
                if (fallback == null) {
                    fallback = sheet;
                }
                int score = scoreSheet(sheet);
                if (score > bestScore) {
                    bestScore = score;
                    bestSheet = sheet;
                }
            }
        }
        return bestSheet != null ? bestSheet : fallback;
    }

    private int scoreSheet(Sheet sheet) {
        int max = Math.min(sheet.getLastRowNum(), Math.max(sheet.getFirstRowNum(), 0) + 40);
        int best = -1;
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= max; rowIndex++) {
            best = Math.max(best, schoolCalendarHeaderScore(sheet, rowIndex));
        }
        if (containsAny(normalizeHeader(sheet.getSheetName()), "教学日历")) {
            best += 10;
        }
        return best;
    }

    private int detectHeaderRow(Sheet sheet) {
        int max = Math.min(sheet.getLastRowNum(), Math.max(sheet.getFirstRowNum(), 0) + 40);
        int bestRow = Math.max(sheet.getFirstRowNum(), 0);
        int bestScore = -1;
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= max; rowIndex++) {
            int score = schoolCalendarHeaderScore(sheet, rowIndex);
            if (score > bestScore) {
                bestScore = score;
                bestRow = rowIndex;
            }
        }
        if (bestScore >= 35) {
            return bestRow;
        }

        int genericBestRow = Math.max(sheet.getFirstRowNum(), 0);
        int genericBestScore = -1;
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= max; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String rawText = rowText(sheet, rowIndex);
            if (looksLikeMetadataRow(rawText)) {
                continue;
            }
            int score = 0;
            for (int col = 0; col < Math.max(row.getLastCellNum(), 0); col++) {
                String text = normalizeHeader(cellText(sheet, rowIndex, col));
                if (containsAny(text, "周次", "周")) score++;
                if (containsAny(text, "课次", "节次", "次数")) score++;
                if (containsAny(text, "学时", "课时", "节数")) score++;
                if (containsAny(text, "课型", "课程类型")) score++;
                if (containsAny(text, "基本教学内容", "教学内容", "授课内容", "章节主题")) score++;
            }
            if (score > genericBestScore) {
                genericBestScore = score;
                genericBestRow = rowIndex;
            }
        }
        return genericBestScore >= 3 ? genericBestRow : Math.max(sheet.getFirstRowNum(), 0);
    }

    private ColumnMapping detectColumns(Sheet sheet, int headerRowIndex) {
        Row header = sheet.getRow(headerRowIndex);
        int lastCell = header == null ? 0 : Math.max(header.getLastCellNum(), 0);
        ColumnMapping mapping = new ColumnMapping();
        for (int col = 0; col < lastCell; col++) {
            String text = normalizeHeader(cellText(sheet, headerRowIndex, col));
            if (mapping.week < 0 && containsAny(text, "周次")) mapping.week = col;
            if (mapping.session < 0 && containsAny(text, "课次", "节次")) mapping.session = col;
            if (mapping.periodCount < 0 && containsAny(text, "学时", "课时", "节数")) mapping.periodCount = col;
            if (mapping.lessonType < 0 && containsAny(text, "课型", "课程类型")) mapping.lessonType = col;
            if (mapping.topic < 0 && containsAny(text, "基本教学内容", "教学内容", "授课内容", "章节主题", "章节", "主题")) mapping.topic = col;
        }
        if (mapping.week < 0) mapping.week = 0;
        if (mapping.session < 0) mapping.session = Math.min(1, Math.max(lastCell - 1, 0));
        if (mapping.periodCount < 0) mapping.periodCount = Math.min(2, Math.max(lastCell - 1, 0));
        if (mapping.lessonType < 0) mapping.lessonType = Math.min(3, Math.max(lastCell - 1, 0));
        if (mapping.topic < 0) mapping.topic = lastCell > 4 ? 4 : Math.max(lastCell - 1, 0);
        return mapping;
    }

    private List<TeachingCalendarEntryDto> readEntries(Sheet sheet, int startRowIndex, ColumnMapping mapping) {
        List<TeachingCalendarEntryDto> entries = new ArrayList<>();
        String lastWeek = "";
        String lastSession = "";
        int lastRow = Math.min(sheet.getLastRowNum(), startRowIndex + MAX_ROWS);
        for (int rowIndex = startRowIndex; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String rawText = rowText(sheet, rowIndex);
            if (rawText.isBlank() || looksLikeMetadataRow(rawText)) {
                continue;
            }
            String week = cellText(sheet, rowIndex, mapping.week);
            String session = cellText(sheet, rowIndex, mapping.session);
            String lessonType = cellText(sheet, rowIndex, mapping.lessonType);
            String topic = cellText(sheet, rowIndex, mapping.topic);
            String period = cellText(sheet, rowIndex, mapping.periodCount);

            if (!week.isBlank()) lastWeek = week;
            if (!session.isBlank()) lastSession = session;
            if (topic.isBlank()) topic = inferTopic(sheet, rowIndex, mapping);
            if (topic.isBlank() || looksLikeHeader(rawText) || looksLikeMetadataRow(topic)) {
                continue;
            }

            TeachingCalendarEntryDto entry = new TeachingCalendarEntryDto();
            entry.setWeek(lastWeek);
            entry.setSession(session.isBlank() ? lastSession : session);
            entry.setPeriodCount(parseInt(period));
            entry.setLessonType(lessonType);
            entry.setTopic(topic);
            entry.setRawText(rawText);
            entry.setAllocatedHours(parseInt(period));
            entries.add(entry);
        }
        return entries;
    }

    private List<TeachingCalendarEntryDto> collapseEntries(List<TeachingCalendarEntryDto> entries) {
        List<TeachingCalendarEntryDto> collapsed = new ArrayList<>();
        for (TeachingCalendarEntryDto entry : entries) {
            if (entry == null) {
                continue;
            }
            if (!collapsed.isEmpty() && sameSession(collapsed.get(collapsed.size() - 1), entry)) {
                collapsed.set(collapsed.size() - 1, mergeEntries(collapsed.get(collapsed.size() - 1), entry));
                continue;
            }
            collapsed.add(copyEntry(entry));
        }
        return collapsed;
    }

    private boolean sameSession(TeachingCalendarEntryDto left, TeachingCalendarEntryDto right) {
        return clean(left.getWeek()).equals(clean(right.getWeek()))
                && clean(left.getSession()).equals(clean(right.getSession()));
    }

    private TeachingCalendarEntryDto mergeEntries(TeachingCalendarEntryDto base, TeachingCalendarEntryDto current) {
        TeachingCalendarEntryDto merged = copyEntry(base);
        merged.setLessonType(joinDistinct(base.getLessonType(), current.getLessonType(), " / "));
        merged.setTopic(joinDistinct(base.getTopic(), current.getTopic(), "；"));
        merged.setRawText(joinDistinct(base.getRawText(), current.getRawText(), "\n"));
        merged.setAllocatedHours(firstPositive(base.getAllocatedHours(), current.getAllocatedHours(), base.getPeriodCount(), current.getPeriodCount()));
        merged.setPeriodCount(firstPositive(base.getPeriodCount(), current.getPeriodCount(), base.getAllocatedHours(), current.getAllocatedHours()));
        return merged;
    }

    private TeachingCalendarEntryDto copyEntry(TeachingCalendarEntryDto source) {
        TeachingCalendarEntryDto copy = new TeachingCalendarEntryDto();
        copy.setWeek(source.getWeek());
        copy.setSession(source.getSession());
        copy.setPeriodCount(source.getPeriodCount());
        copy.setLessonType(source.getLessonType());
        copy.setTopic(source.getTopic());
        copy.setRawText(source.getRawText());
        copy.setAllocatedHours(source.getAllocatedHours());
        return copy;
    }

    private Integer firstPositive(Integer... values) {
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private String joinDistinct(String left, String right, String separator) {
        List<String> parts = new ArrayList<>();
        String leftValue = clean(left);
        String rightValue = clean(right);
        if (!leftValue.isBlank()) {
            parts.add(leftValue);
        }
        if (!rightValue.isBlank() && parts.stream().noneMatch(item -> item.equals(rightValue))) {
            parts.add(rightValue);
        }
        return String.join(separator, parts);
    }

    private String inferTopic(Sheet sheet, int rowIndex, ColumnMapping mapping) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return "";
        }
        String best = "";
        for (int col = 0; col < Math.max(row.getLastCellNum(), 0); col++) {
            if (col == mapping.week || col == mapping.session || col == mapping.periodCount || col == mapping.lessonType) {
                continue;
            }
            String text = cellText(sheet, rowIndex, col);
            if (text.length() > best.length()) {
                best = text;
            }
        }
        return best;
    }

    private String rowText(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (int col = 0; col < Math.max(row.getLastCellNum(), 0); col++) {
            String text = cellText(sheet, rowIndex, col);
            if (!text.isBlank()) {
                parts.add(text);
            }
        }
        return String.join(" | ", parts).trim();
    }

    private String cellText(Sheet sheet, int rowIndex, int colIndex) {
        if (colIndex < 0) {
            return "";
        }
        Row row = sheet.getRow(rowIndex);
        Cell cell = row == null ? null : row.getCell(colIndex);
        String value = formatter.formatCellValue(cell).trim();
        if (!value.isBlank()) {
            return clean(value);
        }
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIndex, colIndex)) {
                Row firstRow = sheet.getRow(region.getFirstRow());
                Cell firstCell = firstRow == null ? null : firstRow.getCell(region.getFirstColumn());
                return clean(formatter.formatCellValue(firstCell).trim());
            }
        }
        return "";
    }

    private String buildExcerpt(List<TeachingCalendarEntryDto> entries) {
        StringBuilder builder = new StringBuilder();
        for (TeachingCalendarEntryDto entry : entries) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(clean(String.join(" ",
                    nullToEmpty(entry.getWeek()),
                    nullToEmpty(entry.getSession()),
                    nullToEmpty(entry.getLessonType()),
                    nullToEmpty(entry.getPeriodCount() == null ? "" : entry.getPeriodCount() + "学时"),
                    nullToEmpty(entry.getTopic())
            )));
            if (builder.length() >= EXCERPT_LENGTH) {
                return builder.substring(0, EXCERPT_LENGTH);
            }
        }
        return builder.toString().trim();
    }

    private Integer parseInt(String value) {
        String number = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (number.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean looksLikeHeader(String text) {
        String value = normalizeHeader(text);
        return containsAny(value, "周次")
                && containsAny(value, "课次")
                && containsAny(value, "基本教学内容", "教学内容", "授课内容", "课型");
    }

    private boolean looksLikeMetadataRow(String text) {
        String value = normalizeHeader(text);
        return containsAny(value, "任课教师", "任课班级", "行政班", "姓名", "日期")
                || containsAny(value, "注：", "注:", "签字", "填表日期", "下拉列表")
                || (containsAny(value, "课程名称", "开课单位", "课程性质") && !containsAny(value, "周次"));
    }

    private int schoolCalendarHeaderScore(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return -1;
        }
        int score = 0;
        int hits = 0;
        for (int col = 0; col < Math.max(row.getLastCellNum(), 0); col++) {
            String text = normalizeHeader(cellText(sheet, rowIndex, col));
            if (text.isBlank()) {
                continue;
            }
            if (containsAny(text, "周次")) {
                score += 12;
                hits++;
            }
            if (containsAny(text, "课次", "节次")) {
                score += 12;
                hits++;
            }
            if (containsAny(text, "课时", "学时", "节数")) {
                score += 12;
                hits++;
            }
            if (containsAny(text, "课型", "课程类型")) {
                score += 12;
                hits++;
            }
            if (containsAny(text, "基本教学内容", "教学内容", "授课内容")) {
                score += 16;
                hits++;
            }
            if (containsAny(text, "任课教师", "任课班级", "行政班", "姓名", "日期")) {
                score -= 10;
            }
        }
        return hits >= 3 ? score : -1;
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeHeader(String value) {
        return clean(value).replace(" ", "");
    }

    private String detectFileType(String fileName) {
        String value = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return value.endsWith(".xls") && !value.endsWith(".xlsx") ? "xls" : "xlsx";
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static class ColumnMapping {
        private int week = -1;
        private int session = -1;
        private int periodCount = -1;
        private int lessonType = -1;
        private int topic = -1;
    }
}
