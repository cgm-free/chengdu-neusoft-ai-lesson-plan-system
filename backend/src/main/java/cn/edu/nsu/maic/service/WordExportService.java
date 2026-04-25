package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.LessonPlanDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;

@Service
public class WordExportService {

    private static final String FONT = "Microsoft YaHei";

    private final ObjectMapper objectMapper;
    private final LessonPlanContentNormalizer contentNormalizer;

    public WordExportService(ObjectMapper objectMapper, LessonPlanContentNormalizer contentNormalizer) {
        this.objectMapper = objectMapper;
        this.contentNormalizer = contentNormalizer;
    }

    public byte[] export(LessonPlanDetail detail) {
        try (XWPFDocument document = createDocumentFromTemplate(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            setupPage(document);
            String normalizedContentJson = contentNormalizer.normalizeForDetail(detail.getContentJson(), detail);
            JsonNode content = parseContent(normalizedContentJson);

            title(document, "成都东软学院教案");
            subtitle(document, detail.getTitle());
            basicInfoTable(document, detail);

            heading1(document, "一、学情分析");
            bodyParagraph(document, text(content.path("studentAnalysis")));
            if (hasArray(content.path("studentProblems"))) {
                heading2(document, "学情诊断与教学对策");
                diagnosticTable(document, content.path("studentProblems"));
            }

            heading1(document, "二、教学目标");
            heading2(document, "1. 知识目标");
            list(document, content.path("objectives").path("knowledge"));
            heading2(document, "2. 能力目标");
            list(document, content.path("objectives").path("ability"));
            heading2(document, "3. 素质目标");
            list(document, content.path("objectives").path("quality"));
            heading2(document, "4. OBE 支撑关系");
            list(document, content.path("objectives").path("obeSupport"));

            heading1(document, "三、教学重点与难点");
            heading2(document, "1. 教学重点");
            list(document, content.path("keyPoints"));
            heading2(document, "2. 教学难点");
            list(document, content.path("difficultPoints"));

            heading1(document, "四、教学方法与资源");
            heading2(document, "1. 教学方法与模式组合");
            list(document, content.path("teachingMethods"));
            heading2(document, "2. 教学资源");
            list(document, content.path("resources"));
            if (hasArray(content.path("referenceMaterials"))) {
                heading2(document, "3. 参考资料");
                referenceMaterials(document, content.path("referenceMaterials"));
            }
            if (hasArray(content.path("teachingCalendar").path("entries"))) {
                heading2(document, "4. 教学日历参考");
                teachingCalendar(document, content.path("teachingCalendar").path("entries"));
            }

            heading1(document, "五、课程思政融入设计");
            list(document, content.path("ideologyDesign"));

            heading1(document, "六、教学过程设计");
            processTable(document, content.path("teachingProcess"));

            heading1(document, "七、实践任务设计");
            JsonNode task = content.path("practiceTask");
            heading2(document, "1. 任务名称");
            bodyParagraph(document, text(task.path("taskName")));
            if (hasText(task.path("scenario"))) {
                heading2(document, "2. 任务情境");
                bodyParagraph(document, text(task.path("scenario")));
            }
            int taskSectionIndex = 3;
            heading2(document, taskSectionIndex++ + ". 基础任务");
            list(document, task.path("basicTasks"));
            heading2(document, taskSectionIndex++ + ". 提高任务");
            list(document, task.path("advancedTasks"));
            heading2(document, taskSectionIndex++ + ". 挑战任务");
            list(document, task.path("challengeTasks"));
            heading2(document, taskSectionIndex++ + ". 实施步骤");
            numberedList(document, task.path("steps"));
            heading2(document, taskSectionIndex++ + ". 验收标准");
            list(document, task.path("acceptanceCriteria"));
            if (hasArray(task.path("commonErrors"))) {
                heading2(document, taskSectionIndex++ + ". 常见错误与指导策略");
                list(document, task.path("commonErrors"));
            }
            JsonNode codeExamples = content.path("codeExamples");
            if (hasArray(codeExamples)) {
                heading2(document, taskSectionIndex + ". 代码示例");
                codeExamples(document, codeExamples);
            }

            heading1(document, "八、作业与课后任务");
            list(document, content.path("homework"));

            heading1(document, "九、OBE 支撑与评价体系");
            heading2(document, "1. OBE 支撑关系");
            list(document, content.path("objectives").path("obeSupport"));
            heading2(document, "2. 评价设计");
            list(document, content.path("evaluationDesign"));
            JsonNode rubric = content.path("rubric");
            if (hasArray(rubric)) {
                heading2(document, "3. Rubric 评分表");
                rubricTable(document, rubric);
            }

            heading1(document, "十、课后反思");
            bodyParagraph(document, text(content.path("reflection")));

            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("导出 Word 失败", e);
        }
    }

    private XWPFDocument createDocumentFromTemplate() throws IOException {
        File template = new File("E:\\nsu-edu-maic\\页眉页脚.docx");
        XWPFDocument document = template.exists()
                ? new XWPFDocument(new FileInputStream(template))
                : new XWPFDocument();
        clearBody(document);
        return document;
    }

    private void clearBody(XWPFDocument document) {
        for (int i = document.getBodyElements().size() - 1; i >= 0; i--) {
            document.removeBodyElement(i);
        }
    }

    private void setupPage(XWPFDocument document) {
        CTSectPr sectPr = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageMar margins = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1440));
        margins.setBottom(BigInteger.valueOf(1440));
        margins.setLeft(BigInteger.valueOf(1440));
        margins.setRight(BigInteger.valueOf(1440));
    }

    private void basicInfoTable(XWPFDocument document, LessonPlanDetail detail) {
        XWPFTable table = document.createTable(4, 4);
        table.setTableAlignment(TableRowAlign.CENTER);
        tableWidth(table, "100%");
        fillRow(table.getRow(0), true, "课程名称", detail.getCourseName(), "章节主题", detail.getTopic());
        fillRow(table.getRow(1), true, "授课专业", value(detail.getMajor()), "授课对象", value(detail.getTargetStudents()));
        fillRow(table.getRow(2), true, "课程类型", value(detail.getLessonType()), "教学模式/方法组合", value(detail.getTeachingMode()));
        fillRow(table.getRow(3), true, "课时安排", detail.getPeriodCount() + " 节，共 " + detail.getTotalMinutes() + " 分钟", "单课时长", detail.getMinutesPerPeriod() + " 分钟");
        spacing(document, 8);
    }

    private JsonNode parseContent(String contentJson) throws IOException {
        if (contentJson == null || contentJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(contentJson);
    }

    private void title(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(120);
        XWPFRun run = run(paragraph, 20, true);
        run.setText(text);
    }

    private void subtitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(260);
        XWPFRun run = run(paragraph, 15, true);
        run.setText(value(text));
    }

    private void heading1(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(220);
        paragraph.setSpacingAfter(120);
        XWPFRun run = run(paragraph, 15, true);
        run.setText(text);
    }

    private void heading2(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationFirstLine(0);
        paragraph.setSpacingBefore(120);
        paragraph.setSpacingAfter(60);
        XWPFRun run = run(paragraph, 12, true);
        run.setText(text);
    }

    private void bodyParagraph(XWPFDocument document, String text) {
        if (text == null || text.isBlank()) {
            text = "待补充。";
        }
        for (String part : text.split("\\n+")) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.BOTH);
            paragraph.setIndentationFirstLine(480);
            paragraph.setSpacingBetween(1.35);
            paragraph.setSpacingAfter(80);
            XWPFRun run = run(paragraph, 11, false);
            run.setText(part.trim());
        }
    }

    private void list(XWPFDocument document, JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            bodyParagraph(document, "待补充。");
            return;
        }
        for (JsonNode item : array) {
            bullet(document, listItemText(item));
        }
    }

    private void numberedList(XWPFDocument document, JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            bodyParagraph(document, "待补充。");
            return;
        }
        int index = 1;
        for (JsonNode item : array) {
            bullet(document, index++ + ". " + listItemText(item));
        }
    }

    private void bullet(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(420);
        paragraph.setIndentationHanging(220);
        paragraph.setSpacingBetween(1.25);
        paragraph.setSpacingAfter(70);
        XWPFRun run = run(paragraph, 11, false);
        run.setText("• " + value(text));
    }

    private void processTable(XWPFDocument document, JsonNode rows) {
        XWPFTable table = document.createTable(Math.max(2, rows.isArray() ? rows.size() + 1 : 2), 9);
        table.setTableAlignment(TableRowAlign.CENTER);
        tableWidth(table, "100%");
        fillRow(table.getRow(0), true, "教学环节", "时间", "教师活动", "学生活动", "课堂产出", "检查点", "设计意图", "教学资源", "评价方式");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            fillRow(table.getRow(1), false, "待补充", "", "", "", "", "", "", "", "");
            return;
        }
        int index = 1;
        for (JsonNode row : rows) {
            fillRow(
                    table.getRow(index++),
                    false,
                    text(row.path("stage")),
                    text(row.path("duration")) + " 分钟",
                    text(row.path("teacherActivity")),
                    text(row.path("studentActivity")),
                    text(row.path("output")),
                    text(row.path("checkpoint")),
                    text(row.path("designPurpose")),
                    text(row.path("resources")),
                    text(row.path("evaluation"))
            );
        }
    }

    private void diagnosticTable(XWPFDocument document, JsonNode rows) {
        XWPFTable table = document.createTable(Math.max(2, rows.isArray() ? rows.size() + 1 : 2), 4);
        table.setTableAlignment(TableRowAlign.CENTER);
        tableWidth(table, "100%");
        fillRow(table.getRow(0), true, "学情问题", "课堂表现", "教学对策", "评价证据");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            fillRow(table.getRow(1), false, "待补充", "", "", "");
            return;
        }
        int index = 1;
        for (JsonNode row : rows) {
            fillRow(
                    table.getRow(index++),
                    false,
                    text(row.path("problem")),
                    text(row.path("evidence")),
                    text(row.path("strategy")),
                    text(row.path("assessment"))
            );
        }
    }

    private void rubricTable(XWPFDocument document, JsonNode rows) {
        XWPFTable table = document.createTable(Math.max(2, rows.isArray() ? rows.size() + 1 : 2), 5);
        table.setTableAlignment(TableRowAlign.CENTER);
        tableWidth(table, "100%");
        fillRow(table.getRow(0), true, "评价维度", "权重", "优秀标准", "达标标准", "评价证据");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            fillRow(table.getRow(1), false, "待补充", "", "", "", "");
            return;
        }
        int index = 1;
        for (JsonNode row : rows) {
            fillRow(
                    table.getRow(index++),
                    false,
                    text(row.path("criterion")),
                    text(row.path("weight")),
                    text(row.path("excellent")),
                    text(row.path("qualified")),
                    text(row.path("evidence"))
            );
        }
    }

    private void codeExamples(XWPFDocument document, JsonNode rows) {
        for (JsonNode row : rows) {
            String title = text(row.path("title"));
            if (!title.isBlank()) {
                heading2(document, title);
            }
            String purpose = text(row.path("purpose"));
            if (!purpose.isBlank()) {
                bodyParagraph(document, purpose);
            }
            codeBlock(document, text(row.path("code")));
        }
    }

    private void referenceMaterials(XWPFDocument document, JsonNode rows) {
        for (JsonNode row : rows) {
            String fileName = text(row.path("fileName"));
            if (fileName.isBlank()) {
                continue;
            }
            String suffix = "primary".equalsIgnoreCase(text(row.path("role"))) ? "（主）" : "";
            bullet(document, fileName + suffix);
        }
    }

    private void teachingCalendar(XWPFDocument document, JsonNode rows) {
        int rowCount = Math.min(rows.size(), 12);
        XWPFTable table = document.createTable(Math.max(2, rowCount + 1), 5);
        table.setTableAlignment(TableRowAlign.CENTER);
        tableWidth(table, "100%");
        fillRow(table.getRow(0), true, "周次", "课次", "学时", "课型", "授课内容");
        if (rowCount == 0) {
            fillRow(table.getRow(1), false, "待补充", "", "", "", "");
            return;
        }
        for (int i = 0; i < rowCount; i++) {
            JsonNode row = rows.get(i);
            fillRow(
                    table.getRow(i + 1),
                    false,
                    text(row.path("week")),
                    text(row.path("session")),
                    hasText(row.path("periodCount")) ? text(row.path("periodCount")) : "",
                    text(row.path("lessonType")),
                    text(row.path("topic"))
            );
        }
    }

    private void codeBlock(XWPFDocument document, String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(360);
        paragraph.setSpacingBefore(80);
        paragraph.setSpacingAfter(120);
        paragraph.setSpacingBetween(1.1);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Consolas");
        run.setFontSize(9);
        String[] lines = code.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lines[i].replace(" ", "\u00A0"));
        }
    }

    private void fillRow(XWPFTableRow row, boolean header, String... values) {
        for (int i = 0; i < values.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            if (header && i % 2 == 0) {
                cell.setColor("EAF4F2");
            } else if (header) {
                cell.setColor("F7FAFA");
            }
            cell.removeParagraph(0);
            XWPFParagraph paragraph = cell.addParagraph();
            paragraph.setSpacingBetween(1.15);
            paragraph.setSpacingAfter(40);
            XWPFRun run = run(paragraph, 9, header && i % 2 == 0);
            run.setText(value(values[i]));
        }
    }

    private void tableWidth(XWPFTable table, String width) {
        CTTblWidth tblWidth = table.getCTTbl().getTblPr().isSetTblW()
                ? table.getCTTbl().getTblPr().getTblW()
                : table.getCTTbl().getTblPr().addNewTblW();
        tblWidth.setType(STTblWidth.PCT);
        tblWidth.setW(BigInteger.valueOf(5000));
    }

    private void spacing(XWPFDocument document, int points) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(points * 20);
    }

    private XWPFRun run(XWPFParagraph paragraph, int fontSize, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(FONT);
        run.setFontSize(fontSize);
        run.setBold(bold);
        return run;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private String listItemText(JsonNode item) {
        if (item == null || item.isNull() || item.isMissingNode()) {
            return "";
        }
        if (!item.isObject()) {
            return item.asText("");
        }
        StringBuilder builder = new StringBuilder();
        appendObjectText(builder, item, "point", "要点");
        appendObjectText(builder, item, "reason", "说明");
        appendObjectText(builder, item, "strategy", "突破策略");
        appendObjectText(builder, item, "stage", "融入环节");
        appendObjectText(builder, item, "carrier", "融入载体");
        appendObjectText(builder, item, "integration", "融入设计");
        appendObjectText(builder, item, "item", "评价项");
        appendObjectText(builder, item, "weight", "权重");
        appendObjectText(builder, item, "evidence", "评价证据");
        appendObjectText(builder, item, "standard", "达标标准");
        appendObjectText(builder, item, "method", "方法");
        appendObjectText(builder, item, "applicablePhase", "适用环节");
        appendObjectText(builder, item, "teacherOperation", "教师操作");
        appendObjectText(builder, item, "resource", "资源");
        appendObjectText(builder, item, "description", "说明");
        appendObjectText(builder, item, "design", "融入设计");
        appendObjectText(builder, item, "binding", "融入环节");
        appendObjectText(builder, item, "type", "类型");
        appendObjectText(builder, item, "submission", "提交方式");
        appendObjectText(builder, item, "feedback", "反馈方式");
        appendObjectText(builder, item, "OBE_evidence", "OBE证据");
        appendObjectText(builder, item, "错误表现", "错误表现");
        appendObjectText(builder, item, "教师干预办法", "教师干预办法");
        if (builder.isEmpty()) {
            item.fields().forEachRemaining(entry -> appendText(builder, entry.getKey(), entry.getValue().asText("")));
        }
        return builder.toString();
    }

    private void appendObjectText(StringBuilder builder, JsonNode item, String key, String label) {
        JsonNode value = item.path(key);
        if (!value.isMissingNode() && !value.isNull() && !value.asText("").isBlank()) {
            appendText(builder, label, value.asText(""));
        }
    }

    private void appendText(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("；");
        }
        builder.append(label).append("：").append(value.trim());
    }

    private String value(String text) {
        return text == null || text.isBlank() ? "待补充" : text;
    }

    private boolean hasArray(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty();
    }

    private boolean hasText(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() && !node.asText("").isBlank();
    }
}
