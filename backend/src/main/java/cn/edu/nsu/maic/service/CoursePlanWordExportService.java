package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTParaRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CoursePlanWordExportService {

    public byte[] export(CoursePlanDtos.Detail detail, byte[] templateBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            requireAutomaticPageField(document);
            TemplateSegments segments = captureTemplateSegments(document);
            clearBody(document);

            SegmentRange courseRange = appendSegment(document, segments.courseCover());
            List<UnitSegment> unitSegments = new ArrayList<>();

            for (CoursePlanDtos.GeneratedUnit unit : detail.content().units()) {
                appendPageBreakIfNeeded(document);
                SegmentRange unitRange = appendSegment(document, segments.unitCover());
                List<DesignSegment> designSegments = new ArrayList<>();
                for (CoursePlanDtos.TeachingDesign design : unit.teachingDesigns()) {
                    appendPageBreakIfNeeded(document);
                    SegmentRange designRange = appendSegment(document, segments.teachingDesign());
                    designSegments.add(new DesignSegment(design, designRange));
                }
                unitSegments.add(new UnitSegment(unit, unitRange, designSegments));
            }
            restoreSectionProperties(document, segments.sectionProperties());

            byte[] skeletonBytes;
            try (ByteArrayOutputStream skeletonOutputStream = new ByteArrayOutputStream()) {
                document.write(skeletonOutputStream);
                skeletonBytes = skeletonOutputStream.toByteArray();
            }

            try (XWPFDocument filledDocument = new XWPFDocument(new ByteArrayInputStream(skeletonBytes))) {
                fillCourseCover(filledDocument, courseRange, detail);
                for (UnitSegment unitSegment : unitSegments) {
                    fillUnitCover(filledDocument, unitSegment.range(), unitSegment.unit(), detail);
                    for (DesignSegment designSegment : unitSegment.designs()) {
                        fillTeachingDesign(filledDocument, designSegment.range(), unitSegment.unit(), designSegment.design());
                    }
                }
                removeEmptyPageArtifacts(filledDocument);
                sanitizeDocumentBodyText(filledDocument);
                restoreSectionProperties(filledDocument, segments.sectionProperties());
                filledDocument.write(outputStream);
            }

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("导出课程教案 Word 失败", e);
        }
    }

    private TemplateSegments captureTemplateSegments(XWPFDocument document) {
        List<IBodyElement> bodyElements = document.getBodyElements();
        int firstBreakIndex = -1;
        int secondBreakIndex = -1;
        for (int i = 0; i < bodyElements.size(); i++) {
            IBodyElement element = bodyElements.get(i);
            if (element.getElementType() != BodyElementType.PARAGRAPH) {
                continue;
            }
            XWPFParagraph paragraph = (XWPFParagraph) element;
            if (paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSectPr()) {
                if (firstBreakIndex < 0) {
                    firstBreakIndex = i;
                } else {
                    secondBreakIndex = i;
                    break;
                }
            }
        }
        if (firstBreakIndex < 0 || secondBreakIndex < 0) {
            throw new IllegalStateException("模板缺少课程首页/单元首页的节分隔结构，无法导出课程教案。");
        }
        CTSectPr sectionProperties = (CTSectPr) ((XWPFParagraph) bodyElements.get(firstBreakIndex))
                .getCTP()
                .getPPr()
                .getSectPr()
                .copy();
        requireHeaderFooterReferences(sectionProperties);
        List<BodyTemplate> courseCover = cloneElements(bodyElements, 0, firstBreakIndex);
        moveCourseCodeBeforeFirstTable(courseCover);
        return new TemplateSegments(
                courseCover,
                cloneElements(bodyElements, firstBreakIndex + 1, secondBreakIndex),
                cloneElements(bodyElements, secondBreakIndex + 1, bodyElements.size() - 1),
                sectionProperties
        );
    }

    private void requireHeaderFooterReferences(CTSectPr sectionProperties) {
        String xml = sectionProperties == null ? "" : sectionProperties.xmlText();
        if (!xml.contains("headerReference") || !xml.contains("footerReference")) {
            throw new IllegalStateException("模板节属性缺少页眉或页脚引用，无法保留页眉页码。");
        }
    }

    private void restoreSectionProperties(XWPFDocument document, CTSectPr sectionProperties) {
        if (sectionProperties == null) {
            throw new IllegalStateException("模板节属性为空，无法保留页眉页码。");
        }
        document.getDocument().getBody().setSectPr((CTSectPr) sectionProperties.copy());
    }

    private void requireAutomaticPageField(XWPFDocument document) {
        for (XWPFFooter footer : document.getFooterList()) {
            String footerXml = footer._getHdrFtr().xmlText();
            if (footerXml.contains("PAGE")) {
                return;
            }
        }
        throw new IllegalStateException("模板页脚缺少自动页码 PAGE 字段，无法导出课程教案。");
    }

    private void moveCourseCodeBeforeFirstTable(List<BodyTemplate> courseCover) {
        int codeIndex = -1;
        int firstTableIndex = -1;
        for (int i = 0; i < courseCover.size(); i++) {
            BodyTemplate template = courseCover.get(i);
            if (firstTableIndex < 0 && template.type() == BodyElementType.TABLE) {
                firstTableIndex = i;
            }
            if (template.type() == BodyElementType.PARAGRAPH
                    && template.paragraph() != null
                    && normalize(readParagraphText(template.paragraph())).contains("课程代码")) {
                codeIndex = i;
            }
        }
        if (codeIndex < 0) {
            throw new IllegalStateException("模板课程首页缺少课程代码段落。");
        }
        if (firstTableIndex < 0) {
            throw new IllegalStateException("模板课程首页缺少表格结构。");
        }
        if (codeIndex > firstTableIndex) {
            BodyTemplate codeTemplate = courseCover.remove(codeIndex);
            courseCover.add(firstTableIndex, codeTemplate);
        }
        compactCourseCodeSpacing(courseCover);
    }

    private void compactCourseCodeSpacing(List<BodyTemplate> courseCover) {
        int codeIndex = -1;
        for (int i = 0; i < courseCover.size(); i++) {
            BodyTemplate template = courseCover.get(i);
            if (template.type() == BodyElementType.PARAGRAPH
                    && template.paragraph() != null
                    && normalize(readParagraphText(template.paragraph())).contains("课程代码")) {
                codeIndex = i;
                compactParagraphSpacing(template.paragraph());
                break;
            }
        }
        if (codeIndex < 0) {
            throw new IllegalStateException("模板课程首页缺少课程代码段落。");
        }
        for (int i = codeIndex + 1; i < courseCover.size(); ) {
            BodyTemplate template = courseCover.get(i);
            if (template.type() == BodyElementType.TABLE) {
                return;
            }
            if (template.type() == BodyElementType.PARAGRAPH
                    && template.paragraph() != null
                    && normalize(readParagraphText(template.paragraph())).isBlank()) {
                courseCover.remove(i);
                continue;
            }
            throw new IllegalStateException("模板课程代码段落与首页表格之间存在非空内容，无法调整位置。");
        }
        throw new IllegalStateException("模板课程代码段落后缺少首页表格。");
    }

    private List<BodyTemplate> cloneElements(List<IBodyElement> bodyElements, int startInclusive, int endInclusive) {
        List<BodyTemplate> templates = new ArrayList<>();
        for (int i = startInclusive; i <= endInclusive; i++) {
            IBodyElement element = bodyElements.get(i);
            if (element.getElementType() == BodyElementType.PARAGRAPH) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                if (isSegmentSeparatorParagraph(paragraph)) {
                    continue;
                }
                templates.add(new BodyTemplate(BodyElementType.PARAGRAPH, (CTP) paragraph.getCTP().copy(), null));
            } else if (element.getElementType() == BodyElementType.TABLE) {
                templates.add(new BodyTemplate(BodyElementType.TABLE, null, (CTTbl) ((XWPFTable) element).getCTTbl().copy()));
            }
        }
        return templates;
    }

    private void clearBody(XWPFDocument document) {
        for (int i = document.getBodyElements().size() - 1; i >= 0; i--) {
            document.removeBodyElement(i);
        }
    }

    private SegmentRange appendSegment(XWPFDocument document, List<BodyTemplate> templates) {
        int startIndex = document.getBodyElements().size();
        for (BodyTemplate template : templates) {
            if (template.type() == BodyElementType.PARAGRAPH && template.paragraph() != null) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.getCTP().set(template.paragraph());
            } else if (template.type() == BodyElementType.TABLE && template.table() != null) {
                XWPFTable table = document.createTable();
                table.getCTTbl().set(template.table());
            }
        }
        int endIndex = document.getBodyElements().size() - 1;
        return new SegmentRange(startIndex, endIndex);
    }

    private void appendPageBreak(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().addBreak(BreakType.PAGE);
    }

    private void appendPageBreakIfNeeded(XWPFDocument document) {
        List<IBodyElement> bodyElements = document.getBodyElements();
        if (bodyElements.isEmpty()) {
            return;
        }
        IBodyElement lastElement = bodyElements.get(bodyElements.size() - 1);
        if (lastElement.getElementType() == BodyElementType.PARAGRAPH
                && isEmptyPageBreakParagraph((XWPFParagraph) lastElement)) {
            return;
        }
        appendPageBreak(document);
    }

    private boolean isSegmentSeparatorParagraph(XWPFParagraph paragraph) {
        return hasSectionBreak(paragraph) || isEmptyPageBreakParagraph(paragraph);
    }

    private boolean hasSectionBreak(XWPFParagraph paragraph) {
        return paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSectPr();
    }

    private boolean isEmptyPageBreakParagraph(XWPFParagraph paragraph) {
        return normalize(paragraph.getText()).isBlank() && paragraph.getCTP().xmlText().contains("type=\"page\"");
    }

    private void removeEmptyPageArtifacts(XWPFDocument document) {
        for (int i = document.getBodyElements().size() - 1; i >= 0; i--) {
            IBodyElement element = document.getBodyElements().get(i);
            if (element.getElementType() != BodyElementType.PARAGRAPH) {
                continue;
            }
            XWPFParagraph paragraph = (XWPFParagraph) element;
            if (hasSectionBreak(paragraph) && normalize(paragraph.getText()).isBlank()) {
                document.removeBodyElement(i);
            }
        }
        for (int i = document.getBodyElements().size() - 1; i > 0; i--) {
            IBodyElement current = document.getBodyElements().get(i);
            IBodyElement previous = document.getBodyElements().get(i - 1);
            if (current.getElementType() == BodyElementType.PARAGRAPH
                    && previous.getElementType() == BodyElementType.PARAGRAPH
                    && isEmptyPageBreakParagraph((XWPFParagraph) current)
                    && isEmptyPageBreakParagraph((XWPFParagraph) previous)) {
                document.removeBodyElement(i);
            }
        }
    }

    private void fillCourseCover(XWPFDocument document, SegmentRange range, CoursePlanDtos.Detail detail) {
        CoursePlanDtos.BasicInfo info = detail.content().basicInfo();
        List<XWPFParagraph> paragraphs = visibleParagraphsInRange(document, range);
        if (paragraphs.size() < 3) {
            throw new IllegalStateException("模板课程首页缺少学校、院系或课程名称段落。");
        }
        rewriteParagraph(paragraphs.get(0), value(info.school()));
        rewriteParagraph(paragraphs.get(1), collegeLine(info.department()));
        replaceRunTextOrFail(paragraphs.get(2), "课程名称", value(info.courseName()), "模板课程首页缺少课程名称占位符。");
        paragraphs.get(0).setAlignment(ParagraphAlignment.CENTER);
        paragraphs.get(1).setAlignment(ParagraphAlignment.CENTER);
        paragraphs.get(2).setAlignment(ParagraphAlignment.CENTER);

        for (XWPFParagraph paragraph : paragraphs) {
            if (normalize(paragraph.getText()).contains("课程代码")) {
                rewriteCourseCodeParagraph(paragraph, value(info.courseCode()));
                break;
            }
        }

        XWPFTable table = requireFirstTableInRange(document, range, "课程首页");
        fillAdjacentCell(table, "系/部", departmentName(info.department()));
        fillCourseNatureCell(table, info.courseNature());
        fillAdjacentCell(table, "年级专业", value(info.targetStudents()));
        fillStudentLevelCell(table, info.targetStudents());
        fillAdjacentCell(table, "课程负责人", value(info.responsibleTeacher()));
        fillAdjacentCell(table, "任课教师", value(info.teacherName()));
        fillAdjacentCell(table, "学时学分", buildHourSummary(info));
        fillCellContaining(table, "总学分", buildCreditSummary(info));
        fillAdjacentCell(table, "课外学习安排", buildCourseArrangement(detail));
        fillAdjacentCell(table, "授课时间", value(info.semester()));
        fillAdjacentCell(table, "先修课程/项目", valueOrNone(info.prerequisites()));
        fillAdjacentCell(table, "后续课程/项目", valueOrNone(info.followUpCourses()));
        fillTeachingResourceCells(table, buildTeachingResourceSummary(detail));
        fillAdjacentCell(table, "教学环境", buildGlobalEnvironment(detail.content()));
    }

    private void fillUnitCover(
            XWPFDocument document,
            SegmentRange range,
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.Detail detail
    ) {
        XWPFTable table = requireFirstTableInRange(document, range, "单元首页");
        fillAdjacentCell(table, "单元", unit.code());
        fillAdjacentCell(table, "学时", value(unit.hours()));
        fillAdjacentCell(table, "周次", value(detail.content().basicInfo().semester()));
        fillAdjacentCell(table, "教学环境设计", value(unit.environmentDesign()));
        fillAdjacentCell(table, "单元名称", value(unit.name()));
        fillAdjacentCell(table, "项目名称（级别）", valueOrNone(unit.projectName()));
        fillAdjacentCell(table, "理论知识", numberedLines(unit.theoryObjectives()));
        fillAdjacentCell(table, "专业技能", numberedLines(unit.skillObjectives()));
        fillAdjacentCell(table, "个人素质", numberedLines(unit.qualityObjectives()));
        fillAdjacentCell(table, "教学重点难点", "教学重点：\n" + numberedLines(unit.keyPoints()) + "\n\n教学难点：\n" + numberedLines(unit.difficultPoints()));
        fillAdjacentCell(table, "教学方法手段媒介", value(unit.teachingMethods()));
        fillAdjacentCell(table, "教学组织方式", value(unit.teachingOrganization()));
        fillAdjacentCell(table, "项目简介要求", value(unit.projectIntroduction()));
    }

    private void fillTeachingDesign(
            XWPFDocument document,
            SegmentRange range,
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign design
    ) {
        XWPFTable table = requireFirstTableInRange(document, range, "教学设计页");
        XWPFTableCell bodyCell = requireCellContaining(table, "【教学进程安排】", "教学设计正文栏");
        XWPFTableCell remarkCell = requireCellContaining(table, "注释及备注", "教学设计备注栏");
        TeachingDesignStyles styles = extractTeachingDesignStyles(bodyCell, remarkCell);
        writeTeachingDesignBodyCell(bodyCell, unit, design, styles);
        writeTeachingDesignRemarkCell(remarkCell, unit, design, styles);
    }

    private TeachingDesignStyles extractTeachingDesignStyles(XWPFTableCell bodyCell, XWPFTableCell remarkCell) {
        ParagraphStyle progressTitle = findParagraphStyle(
                bodyCell,
                "【教学进程安排】",
                "模板教学设计正文栏缺少“【教学进程安排】”样式段落。"
        );
        ParagraphStyle sectionHeading = findParagraphStyle(
                bodyCell,
                this::isTeachingSectionHeading,
                "模板教学设计正文栏缺少一级环节标题样式段落。"
        );
        ParagraphStyle bodyText = findBodyTextStyle(bodyCell);
        ParagraphStyle remarkHeading = findParagraphStyle(
                remarkCell,
                "注释及备注",
                "模板教学设计备注栏缺少“注释及备注”样式段落。"
        );
        return new TeachingDesignStyles(progressTitle, sectionHeading, bodyText, remarkHeading);
    }

    private ParagraphStyle findParagraphStyle(XWPFTableCell cell, String marker, String errorMessage) {
        String normalizedMarker = normalize(marker);
        return findParagraphStyle(
                cell,
                text -> normalize(text).contains(normalizedMarker),
                errorMessage
        );
    }

    private ParagraphStyle findParagraphStyle(XWPFTableCell cell, TextMatcher matcher, String errorMessage) {
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            String text = value(paragraph.getText());
            if (!text.isBlank() && matcher.matches(text)) {
                return paragraphStyle(paragraph);
            }
        }
        throw new IllegalStateException(errorMessage);
    }

    private ParagraphStyle findBodyTextStyle(XWPFTableCell cell) {
        boolean sectionSeen = false;
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            String text = value(paragraph.getText());
            if (text.isBlank() || normalize(text).contains("【教学进程安排】")) {
                continue;
            }
            if (isTeachingSectionHeading(text)) {
                sectionSeen = true;
                continue;
            }
            if (sectionSeen) {
                return paragraphStyle(paragraph);
            }
        }
        throw new IllegalStateException("模板教学设计正文栏缺少普通正文样式段落。");
    }

    private void writeTeachingDesignBodyCell(
            XWPFTableCell cell,
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign design,
            TeachingDesignStyles styles
    ) {
        clearCell(cell);
        appendStyledParagraph(cell, "【教学进程安排】", styles.progressTitle());
        appendStyledParagraph(cell, design.title(), styles.bodyText());
        appendStyledParagraph(cell, "一、课外学习讲评（约" + sectionMinutes(design.afterClassReviewMinutes(), 10) + "分钟）", styles.sectionHeading());
        appendStyledParagraph(cell, design.afterClassReview(), styles.bodyText());
        appendStyledParagraph(cell, "二、内容导入（约" + sectionMinutes(design.introductionMinutes(), 10) + "分钟）", styles.sectionHeading());
        appendStyledParagraph(cell, design.introduction(), styles.bodyText());
        appendStyledParagraph(cell, "三、主要内容设计（约" + mainContentMinutes(design) + "分钟）", styles.sectionHeading());
        int index = 1;
        for (CoursePlanDtos.MainContentBlock block : design.mainContentBlocks()) {
            appendStyledParagraph(cell, index++ + ". " + block.title() + "（约" + value(block.minutes()) + "分钟）", styles.bodyText());
            for (String point : block.points()) {
                appendStyledParagraph(cell, buildTeachingContentLine(block.title(), point), styles.bodyText());
            }
        }
        appendStyledParagraph(cell, "四、归纳总结（约" + sectionMinutes(design.summaryMinutes(), 10) + "分钟）", styles.sectionHeading());
        appendStyledParagraph(cell, design.summary(), styles.bodyText());
        appendStyledParagraph(cell, "五、课外学习要求（约" + sectionMinutes(design.assignmentMinutes(), 5) + "分钟）", styles.sectionHeading());
        for (int assignmentIndex = 0; assignmentIndex < design.assignments().size(); assignmentIndex++) {
            appendStyledParagraph(cell, (assignmentIndex + 1) + ". " + design.assignments().get(assignmentIndex), styles.bodyText());
        }
    }

    private void writeTeachingDesignRemarkCell(
            XWPFTableCell cell,
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign design,
            TeachingDesignStyles styles
    ) {
        clearCell(cell);
        appendStyledParagraph(cell, "注释及备注", styles.remarkHeading());
        for (String line : buildTeachingDesignRemarkLines(unit, design)) {
            appendStyledParagraph(cell, line, styles.bodyText());
        }
    }

    private List<String> buildTeachingDesignRemarkLines(CoursePlanDtos.GeneratedUnit unit, CoursePlanDtos.TeachingDesign design) {
        if (design.remarks() != null && !design.remarks().isEmpty()) {
            return design.remarks().stream()
                    .map(this::value)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        List<String> lines = new ArrayList<>();
        lines.add("单元：" + value(unit.name()));
        lines.add("教学焦点：" + abbreviate(design.focus(), 42));
        List<String> matchedSlides = design.matchedSlides().stream()
                .map(this::value)
                .filter(item -> !item.isBlank())
                .limit(2)
                .map(item -> abbreviate(item, 24))
                .toList();
        if (!matchedSlides.isEmpty()) {
            lines.add("课件提示：" + String.join("；", matchedSlides));
        }
        lines.add("课堂观察：关注核心概念、案例步骤与练习反馈。");
        return lines;
    }

    private String buildTeachingContentLine(String blockTitle, String point) {
        String content = value(point);
        if (content.isBlank()) {
            return "";
        }
        return content;
    }

    private int sectionMinutes(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int mainContentMinutes(CoursePlanDtos.TeachingDesign design) {
        if (design == null || design.mainContentBlocks() == null || design.mainContentBlocks().isEmpty()) {
            return 0;
        }
        int total = 0;
        for (CoursePlanDtos.MainContentBlock block : design.mainContentBlocks()) {
            if (block.minutes() != null && block.minutes() > 0) {
                total += block.minutes();
            }
        }
        return total;
    }

    private List<XWPFParagraph> paragraphsInRange(XWPFDocument document, SegmentRange range) {
        List<XWPFParagraph> result = new ArrayList<>();
        for (int i = range.start(); i <= range.end(); i++) {
            IBodyElement element = document.getBodyElements().get(i);
            if (element.getElementType() == BodyElementType.PARAGRAPH) {
                result.add((XWPFParagraph) element);
            }
        }
        return result;
    }

    private List<XWPFParagraph> visibleParagraphsInRange(XWPFDocument document, SegmentRange range) {
        return paragraphsInRange(document, range).stream()
                .filter(paragraph -> !value(paragraph.getText()).isBlank())
                .toList();
    }

    private XWPFTable requireFirstTableInRange(XWPFDocument document, SegmentRange range, String segmentName) {
        for (int i = range.start(); i <= range.end(); i++) {
            IBodyElement element = document.getBodyElements().get(i);
            if (element.getElementType() == BodyElementType.TABLE) {
                return (XWPFTable) element;
            }
        }
        throw new IllegalStateException("模板" + segmentName + "缺少表格结构。");
    }

    private XWPFTableCell requireCellContaining(XWPFTable table, String marker, String segmentName) {
        String normalizedMarker = normalize(marker);
        for (var row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                if (normalize(cell.getText()).contains(normalizedMarker)) {
                    return cell;
                }
            }
        }
        throw new IllegalStateException("模板" + segmentName + "缺少必填标识：“" + marker + "”。");
    }

    private void rewriteParagraph(XWPFParagraph paragraph, String text) {
        CTRPr runProperties = firstRunProperties(paragraph);
        int runCount = paragraph.getRuns().size();
        for (int i = runCount - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
        sanitizeGeneratedParagraph(paragraph);
        XWPFRun run = paragraph.createRun();
        applyGeneratedRunProperties(run, runProperties);
        run.setText(value(text));
    }

    private void rewriteCourseCodeParagraph(XWPFParagraph paragraph, String courseCode) {
        boolean replaced = false;
        for (XWPFRun run : paragraph.getRuns()) {
            String text = runText(run);
            if (text.isBlank()) {
                sanitizeGeneratedRun(run);
                continue;
            }
            if (!replaced && text.contains("10")) {
                setRunText(run, text.replace("10", value(courseCode)));
                sanitizeGeneratedRun(run);
                replaced = true;
                continue;
            }
            if (replaced && isCourseCodePlaceholderTail(text)) {
                setRunText(run, "");
                sanitizeGeneratedRun(run);
            }
        }
        if (!replaced) {
            throw new IllegalStateException("模板课程代码段落缺少可替换的课程代码占位符。");
        }
        compactParagraphSpacing(paragraph.getCTP());
        sanitizeGeneratedParagraph(paragraph);
    }

    private boolean isCourseCodePlaceholderTail(String text) {
        String normalized = normalize(text);
        return normalized.isBlank()
                || normalized.contains("位课程代码")
                || normalized.contains("课程标准")
                || normalized.contains("一致");
    }

    private void replaceRunTextOrFail(XWPFParagraph paragraph, String marker, String replacement, String errorMessage) {
        boolean replaced = false;
        for (XWPFRun run : paragraph.getRuns()) {
            String text = runText(run);
            if (text.contains(marker)) {
                setRunText(run, text.replace(marker, value(replacement)));
                sanitizeGeneratedRun(run);
                replaced = true;
            }
        }
        if (!replaced) {
            throw new IllegalStateException(errorMessage);
        }
        sanitizeGeneratedParagraph(paragraph);
    }

    private void fillCourseNatureCell(XWPFTable table, String courseNature) {
        XWPFTableCell cell = requireAdjacentCell(table, "课程性质");
        requireCheckboxOptions(cell, "课程性质", "通识课", "专业课", "必修课", "选修课");
        String normalizedNature = normalize(courseNature);
        boolean general = containsAny(normalizedNature, "通识");
        boolean professional = containsAny(normalizedNature, "专业");
        boolean required = containsAny(normalizedNature, "必修");
        boolean elective = containsAny(normalizedNature, "选修");
        if (!general && !professional && !required && !elective) {
            throw new IllegalStateException("课程性质无法映射到模板复选项：“" + value(courseNature) + "”。");
        }
        writeCell(
                cell,
                "通  识  课" + checkbox(general) + "        专  业  课" + checkbox(professional) + "\n"
                        + "必  修  课" + checkbox(required) + "         选  修  课" + checkbox(elective)
        );
    }

    private void fillStudentLevelCell(XWPFTable table, String targetStudents) {
        XWPFTableCell cell = requireAdjacentCell(table, "学生层次");
        requireCheckboxOptions(cell, "学生层次", "本科", "专科");
        String normalizedTarget = normalize(targetStudents);
        boolean undergraduate = containsAny(normalizedTarget, "本科");
        boolean juniorCollege = containsAny(normalizedTarget, "专科");
        if (!undergraduate && !juniorCollege) {
            throw new IllegalStateException("授课对象无法映射到学生层次复选项：“" + value(targetStudents) + "”。");
        }
        writeCell(cell, "本科" + checkbox(undergraduate) + "        专科" + checkbox(juniorCollege));
    }

    private void requireCheckboxOptions(XWPFTableCell cell, String label, String... options) {
        String text = normalize(cell.getText());
        for (String option : options) {
            if (!text.contains(normalize(option))) {
                throw new IllegalStateException("模板“" + label + "”单元格缺少复选项：“" + option + "”。");
            }
        }
    }

    private String checkbox(boolean selected) {
        return selected ? "☑" : "□";
    }

    private void fillAdjacentCell(XWPFTable table, String label, String text) {
        writeCell(requireAdjacentCell(table, label), text);
    }

    private XWPFTableCell requireAdjacentCell(XWPFTable table, String label) {
        String normalizedLabel = normalize(label);
        for (var row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                if (normalize(cells.get(i).getText()).equals(normalizedLabel)) {
                    if (i + 1 >= cells.size()) {
                        throw new IllegalStateException("模板标签“" + label + "”右侧缺少可写入单元格。");
                    }
                    return cells.get(i + 1);
                }
            }
        }
        throw new IllegalStateException("模板中未找到必填标签：“" + label + "”。");
    }

    private void fillCellContaining(XWPFTable table, String label, String text) {
        String normalizedLabel = normalize(label);
        for (var row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                if (normalize(cell.getText()).contains(normalizedLabel)) {
                    writeCell(cell, text);
                    return;
                }
            }
        }
        throw new IllegalStateException("模板中未找到必填文本：“" + label + "”。");
    }

    private void fillBlockCell(XWPFTable table, String label, String text) {
        String normalizedLabel = normalize(label);
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            List<XWPFTableCell> cells = table.getRow(rowIndex).getTableCells();
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                if (!normalize(cells.get(cellIndex).getText()).equals(normalizedLabel)) {
                    continue;
                }
                if (cellIndex + 1 >= cells.size()) {
                    throw new IllegalStateException("模板标签“" + label + "”右侧缺少可写入单元格。");
                }
                writeCell(cells.get(cellIndex + 1), text);
                clearContinuationRows(table, rowIndex + 1, cellIndex);
                return;
            }
        }
        throw new IllegalStateException("模板中未找到必填标签：“" + label + "”。");
    }

    private void fillTeachingResourceCells(XWPFTable table, TeachingResourceSummary summary) {
        String normalizedLabel = normalize("教学资源");
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            List<XWPFTableCell> cells = table.getRow(rowIndex).getTableCells();
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                if (!normalize(cells.get(cellIndex).getText()).equals(normalizedLabel)) {
                    continue;
                }
                if (cellIndex + 1 >= cells.size()) {
                    throw new IllegalStateException("模板“教学资源”右侧缺少教材及参考资料单元格。");
                }
                XWPFTableCell referenceCell = cells.get(cellIndex + 1);
                XWPFTableCell otherResourceCell = findContinuationCell(table, rowIndex + 1, cellIndex);
                writeLabeledCell(referenceCell, "教材及参考资料：", summary.references());
                writeLabeledCell(otherResourceCell, "其他教学资源：", summary.otherResources());
                return;
            }
        }
        throw new IllegalStateException("模板中未找到必填标签：“教学资源”。");
    }

    private XWPFTableCell findContinuationCell(XWPFTable table, int startRowIndex, int labelCellIndex) {
        for (int rowIndex = startRowIndex; rowIndex < table.getRows().size(); rowIndex++) {
            List<XWPFTableCell> cells = table.getRow(rowIndex).getTableCells();
            if (labelCellIndex < cells.size() && !normalize(cells.get(labelCellIndex).getText()).isBlank()) {
                break;
            }
            if (labelCellIndex + 1 < cells.size()) {
                return cells.get(labelCellIndex + 1);
            }
        }
        throw new IllegalStateException("模板“教学资源”缺少其他教学资源右侧单元格。");
    }

    private void writeLabeledCell(XWPFTableCell cell, String label, String text) {
        CTPPr paragraphProperties = firstParagraphProperties(cell);
        CTRPr runProperties = firstRunProperties(cell);
        clearCell(cell);
        String[] lines = value(text).split("\n", -1);
        XWPFParagraph paragraph = cell.addParagraph();
        if (paragraphProperties != null) {
            paragraph.getCTP().setPPr((CTPPr) paragraphProperties.copy());
        }
        sanitizeGeneratedParagraph(paragraph);
        clearParagraphBold(paragraph);
        XWPFRun labelRun = paragraph.createRun();
        applyGeneratedRunProperties(labelRun, runProperties);
        clearRunBold(labelRun);
        labelRun.setBold(true);
        labelRun.setText(label);

        XWPFRun contentRun = paragraph.createRun();
        applyGeneratedRunProperties(contentRun, runProperties);
        clearRunBold(contentRun);
        if (lines.length > 0 && !value(lines[0]).isBlank()) {
            contentRun.setText(" " + value(lines[0]));
        }
        for (int i = 1; i < lines.length; i++) {
            if (value(lines[i]).isBlank()) {
                continue;
            }
            XWPFParagraph extraParagraph = cell.addParagraph();
            if (paragraphProperties != null) {
                extraParagraph.getCTP().setPPr((CTPPr) paragraphProperties.copy());
            }
            sanitizeGeneratedParagraph(extraParagraph);
            clearParagraphBold(extraParagraph);
            XWPFRun extraRun = extraParagraph.createRun();
            applyGeneratedRunProperties(extraRun, runProperties);
            clearRunBold(extraRun);
            extraRun.setText(value(lines[i]));
        }
    }

    private void clearContinuationRows(XWPFTable table, int startRowIndex, int labelCellIndex) {
        for (int rowIndex = startRowIndex; rowIndex < table.getRows().size(); rowIndex++) {
            List<XWPFTableCell> cells = table.getRow(rowIndex).getTableCells();
            if (labelCellIndex >= cells.size() || !normalize(cells.get(labelCellIndex).getText()).isBlank()) {
                return;
            }
            if (labelCellIndex + 1 < cells.size()) {
                writeCell(cells.get(labelCellIndex + 1), "");
            }
        }
    }

    private void writeCell(XWPFTableCell cell, String text) {
        CTPPr paragraphProperties = firstParagraphProperties(cell);
        CTRPr runProperties = firstRunProperties(cell);
        clearCell(cell);
        String[] lines = value(text).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFParagraph target = cell.addParagraph();
            if (paragraphProperties != null) {
                target.getCTP().setPPr((CTPPr) paragraphProperties.copy());
            }
            sanitizeGeneratedParagraph(target);
            XWPFRun run = target.createRun();
            applyGeneratedRunProperties(run, runProperties);
            run.setText(lines[i]);
        }
    }

    private void clearCell(XWPFTableCell cell) {
        while (!cell.getParagraphs().isEmpty()) {
            cell.removeParagraph(0);
        }
    }

    private void sanitizeDocumentBodyText(XWPFDocument document) {
        for (IBodyElement element : document.getBodyElements()) {
            if (element.getElementType() == BodyElementType.PARAGRAPH) {
                sanitizeParagraphText((XWPFParagraph) element);
                continue;
            }
            if (element.getElementType() == BodyElementType.TABLE) {
                sanitizeTableText((XWPFTable) element);
            }
        }
    }

    private void sanitizeTableText(XWPFTable table) {
        for (var row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    sanitizeParagraphText(paragraph);
                }
            }
        }
    }

    private void sanitizeParagraphText(XWPFParagraph paragraph) {
        sanitizeGeneratedParagraph(paragraph);
        for (XWPFRun run : paragraph.getRuns()) {
            sanitizeGeneratedRun(run);
        }
    }

    private void appendStyledParagraph(XWPFTableCell cell, String text, ParagraphStyle style) {
        String[] lines = value(text).split("\n", -1);
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            XWPFParagraph paragraph = cell.addParagraph();
            if (style.paragraphProperties() != null) {
                paragraph.getCTP().setPPr((CTPPr) style.paragraphProperties().copy());
            }
            sanitizeGeneratedParagraph(paragraph);
            XWPFRun run = paragraph.createRun();
            applyGeneratedRunProperties(run, style.runProperties());
            run.setText(line);
        }
    }

    private ParagraphStyle paragraphStyle(XWPFParagraph paragraph) {
        CTPPr paragraphProperties = paragraph.getCTP().isSetPPr()
                ? (CTPPr) paragraph.getCTP().getPPr().copy()
                : null;
        return new ParagraphStyle(paragraphProperties, firstRunProperties(paragraph));
    }

    private boolean isTeachingSectionHeading(String text) {
        String normalized = normalize(text);
        return normalized.startsWith("一、")
                || normalized.startsWith("二、")
                || normalized.startsWith("三、")
                || normalized.startsWith("四、")
                || normalized.startsWith("五、")
                || normalized.startsWith("六、");
    }

    private void compactParagraphSpacing(CTP paragraph) {
        CTPPr paragraphProperties = paragraph.isSetPPr() ? paragraph.getPPr() : paragraph.addNewPPr();
        CTSpacing spacing = paragraphProperties.isSetSpacing()
                ? paragraphProperties.getSpacing()
                : paragraphProperties.addNewSpacing();
        spacing.setBefore(BigInteger.ZERO);
        spacing.setAfter(BigInteger.ZERO);
        spacing.setBeforeLines(BigInteger.ZERO);
        spacing.setAfterLines(BigInteger.ZERO);
        if (spacing.isSetLine()) {
            spacing.unsetLine();
        }
        if (spacing.isSetLineRule()) {
            spacing.unsetLineRule();
        }
        if (spacing.isSetBeforeAutospacing()) {
            spacing.unsetBeforeAutospacing();
        }
        if (spacing.isSetAfterAutospacing()) {
            spacing.unsetAfterAutospacing();
        }
    }

    private void applyGeneratedRunProperties(XWPFRun run, CTRPr runProperties) {
        if (runProperties != null) {
            run.getCTR().setRPr((CTRPr) runProperties.copy());
        }
        sanitizeGeneratedRun(run);
    }

    private void sanitizeGeneratedParagraph(XWPFParagraph paragraph) {
        if (!paragraph.getCTP().isSetPPr() || !paragraph.getCTP().getPPr().isSetRPr()) {
            return;
        }
        sanitizeRunProperties(paragraph.getCTP().getPPr().getRPr());
    }

    private void sanitizeGeneratedRun(XWPFRun run) {
        if (!run.getCTR().isSetRPr()) {
            return;
        }
        sanitizeRunProperties(run.getCTR().getRPr());
    }

    private void clearRunBold(XWPFRun run) {
        if (!run.getCTR().isSetRPr()) {
            return;
        }
        CTRPr runProperties = run.getCTR().getRPr();
        while (runProperties.sizeOfBArray() > 0) {
            runProperties.removeB(0);
        }
        while (runProperties.sizeOfBCsArray() > 0) {
            runProperties.removeBCs(0);
        }
    }

    private void clearParagraphBold(XWPFParagraph paragraph) {
        if (!paragraph.getCTP().isSetPPr() || !paragraph.getCTP().getPPr().isSetRPr()) {
            return;
        }
        CTParaRPr runProperties = paragraph.getCTP().getPPr().getRPr();
        while (runProperties.sizeOfBArray() > 0) {
            runProperties.removeB(0);
        }
        while (runProperties.sizeOfBCsArray() > 0) {
            runProperties.removeBCs(0);
        }
    }

    private void sanitizeRunProperties(CTRPr runProperties) {
        while (runProperties.sizeOfColorArray() > 0) {
            runProperties.removeColor(0);
        }
        while (runProperties.sizeOfUArray() > 0) {
            runProperties.removeU(0);
        }
    }

    private void sanitizeRunProperties(CTParaRPr runProperties) {
        while (runProperties.sizeOfColorArray() > 0) {
            runProperties.removeColor(0);
        }
        while (runProperties.sizeOfUArray() > 0) {
            runProperties.removeU(0);
        }
    }

    private CTPPr firstParagraphProperties(XWPFTableCell cell) {
        if (cell.getParagraphs().isEmpty()) {
            return null;
        }
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        if (!paragraph.getCTP().isSetPPr()) {
            return null;
        }
        return (CTPPr) paragraph.getCTP().getPPr().copy();
    }

    private CTRPr firstRunProperties(XWPFTableCell cell) {
        if (cell.getParagraphs().isEmpty()) {
            return null;
        }
        return firstRunProperties(cell.getParagraphs().get(0));
    }

    private CTRPr firstRunProperties(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.getCTR().isSetRPr()) {
                return (CTRPr) run.getCTR().getRPr().copy();
            }
        }
        return null;
    }

    private String runText(XWPFRun run) {
        StringBuilder builder = new StringBuilder();
        for (var text : run.getCTR().getTList()) {
            builder.append(text.getStringValue());
        }
        return builder.toString();
    }

    private void setRunText(XWPFRun run, String text) {
        while (run.getCTR().sizeOfTArray() > 0) {
            run.getCTR().removeT(0);
        }
        if (!value(text).isEmpty()) {
            run.setText(value(text));
        }
    }

    private String readParagraphText(CTP paragraph) {
        StringBuilder builder = new StringBuilder();
        for (var text : paragraph.getRList().stream().flatMap(run -> run.getTList().stream()).toList()) {
            builder.append(text.getStringValue());
        }
        return builder.toString();
    }

    private String buildHourSummary(CoursePlanDtos.BasicInfo info) {
        return "总学时：" + value(info.totalHours()) + "    理论学时：" + value(info.theoryHours()) + "    实践学时：" + value(info.practiceHours());
    }

    private String buildCreditSummary(CoursePlanDtos.BasicInfo info) {
        return "总学分：" + value(info.credits());
    }

    private String buildCourseArrangement(CoursePlanDtos.Detail detail) {
        if (!detail.content().teacherRequirements().isBlank()) {
            return abbreviate(detail.content().teacherRequirements(), 90);
        }
        return "围绕课程标准、课堂练习、项目/实验要求组织预习、复习与成果提交。";
    }

    private TeachingResourceSummary buildTeachingResourceSummary(CoursePlanDtos.Detail detail) {
        CoursePlanDtos.DocumentContent content = detail.content();
        if (content == null) {
            throw new IllegalStateException("课程教案缺少生成内容，无法填写教学资源。");
        }
        String references = value(content.textbooksAndReferences());
        String otherResources = value(content.otherTeachingResources());
        if (references.isBlank() || otherResources.isBlank()) {
            throw new IllegalStateException("课程教案缺少大模型加工后的教学资源文本，请重新生成课程教案后再导出。");
        }
        return new TeachingResourceSummary(references, otherResources);
    }

    private String buildGlobalEnvironment(CoursePlanDtos.DocumentContent content) {
        if (!value(content.courseEnvironment()).isBlank()) {
            return value(content.courseEnvironment());
        }
        List<String> environments = new ArrayList<>();
        List<String> resources = new ArrayList<>();
        List<String> courseware = new ArrayList<>();
        List<String> projects = new ArrayList<>();
        for (CoursePlanDtos.GeneratedUnit unit : content.units()) {
            if (!value(unit.environmentDesign()).isBlank()) {
                environments.addAll(splitItems(unit.environmentDesign()));
            }
            resources.addAll(unit.resources());
            courseware.addAll(unit.matchedPpts());
            if (!value(unit.projectName()).isBlank() && !"无".equals(value(unit.projectName()))) {
                projects.add(unit.projectName());
            }
        }
        List<String> venues = collectEnvironmentLabels(environments, resources,
                new String[][]{{"机房", "机房"}, {"实验室", "实验室"}, {"实训室", "实训室"}, {"多媒体", "多媒体教室"}, {"教室", "普通教室"}});
        List<String> devices = collectEnvironmentLabels(environments, resources,
                new String[][]{{"超星", "超星平台"}, {"Python", "Python开发环境"}, {"Jupyter", "Jupyter"}, {"PyCharm", "PyCharm"}, {"网络", "网络接入"}});
        courseware.addAll(filterByKeywords(resources, "PPT", "课件", "讲义", "视频", "指导书"));
        projects.addAll(filterByKeywords(resources, "实验", "项目", "任务", "案例"));
        return "教学场地：" + joinShortItems(venues) + "\n"
                + "设备软件：" + joinShortItems(devices) + "\n"
                + "课件材料：" + joinShortItems(courseware) + "\n"
                + "实验/项目条件：" + joinShortItems(projects);
    }

    private List<String> filterResourceItems(List<String> resources, boolean reference) {
        List<String> result = new ArrayList<>();
        for (String resource : deduplicate(resources)) {
            String cleaned = value(resource);
            if (cleaned.isBlank()
                    || cleaned.length() > 60
                    || "课程资源".equals(cleaned)
                    || "学习支持".equals(cleaned)
                    || "学习支持：".equals(cleaned)
                    || cleaned.contains("教学法")
                    || cleaned.contains("课程思政")
                    || cleaned.contains("培养目标")
                    || cleaned.contains("考核")) {
                continue;
            }
            boolean referenceResource = containsAny(resource, "教材", "参考", "图书", "课程标准", "阅读", "文献");
            if (referenceResource == reference) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private List<String> splitItems(String text) {
        List<String> result = new ArrayList<>();
        for (String item : value(text).split("[；;\\n]+")) {
            String cleaned = value(item);
            if (!cleaned.isBlank()) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private List<String> filterByKeywords(List<String> source, String... keywords) {
        List<String> result = new ArrayList<>();
        for (String item : deduplicate(source)) {
            String cleaned = value(item);
            if (cleaned.length() <= 42 && containsAny(cleaned, keywords)) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private List<String> collectEnvironmentLabels(List<String> environments, List<String> resources, String[][] mappings) {
        List<String> source = new ArrayList<>();
        source.addAll(deduplicate(environments));
        source.addAll(deduplicate(resources));
        List<String> result = new ArrayList<>();
        for (String[] mapping : mappings) {
            if (mapping.length < 2) {
                continue;
            }
            String keyword = mapping[0];
            String label = mapping[1];
            if (source.stream().anyMatch(item -> containsAny(value(item), keyword))) {
                result.add(label);
            }
        }
        return deduplicate(result);
    }

    private String joinShortItems(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String item : deduplicate(values)) {
            result.add(abbreviate(item, 42));
            if (result.size() >= 4) {
                break;
            }
        }
        return String.join("；", result);
    }

    private String joinLines(List<String> values) {
        return String.join("\n", deduplicate(values));
    }

    private String numberedLines(List<String> values) {
        List<String> items = deduplicate(values);
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            lines.add((index + 1) + ". " + stripLeadingNumber(items.get(index)));
        }
        return String.join("\n", lines);
    }

    private String stripLeadingNumber(String value) {
        return value(value).replaceFirst("^\\s*(?:\\d+[\\.、]|[（(]?\\d+[）)]|[一二三四五六七八九十]+[、.])\\s*", "");
    }

    private List<String> deduplicate(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String cleaned = value(value);
            if (!cleaned.isBlank() && !result.contains(cleaned)) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private String collegeLine(String department) {
        String value = value(department);
        int collegeIndex = value.lastIndexOf("学院");
        if (collegeIndex < 0) {
            throw new IllegalStateException("课程标准开课单位缺少学院信息，无法填写课程首页院系行。");
        }
        return value.substring(0, collegeIndex + 2);
    }

    private String departmentName(String department) {
        String value = value(department);
        int collegeIndex = value.lastIndexOf("学院");
        if (collegeIndex >= 0 && collegeIndex + 2 < value.length() && value.endsWith("系")) {
            return value.substring(collegeIndex + 2).trim();
        }
        if (value.endsWith("系")) {
            return value;
        }
        throw new IllegalStateException("课程标准缺少明确的系/部信息，无法填写课程首页系/部。");
    }

    private String inferStudentLevel(String targetStudents) {
        String value = value(targetStudents).toLowerCase(Locale.ROOT);
        if (value.contains("专科")) {
            return "专科";
        }
        if (value.contains("本科")) {
            return "本科";
        }
        return "";
    }

    private String abbreviate(String text, int maxLength) {
        String value = value(text);
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }

    private String value(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String valueOrNone(String text) {
        String value = value(text);
        return value.isBlank() ? "无" : value;
    }

    private String firstNonBlank(String... values) {
        for (String candidate : values) {
            String cleaned = value(candidate);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
    }

    private String normalize(String value) {
        return value(value).replace("　", "").replace(" ", "");
    }

    private boolean containsAny(String value, String... keywords) {
        String normalizedValue = normalize(value).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalizedValue.contains(normalize(keyword).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record BodyTemplate(
            BodyElementType type,
            CTP paragraph,
            CTTbl table
    ) {
    }

    private record ParagraphStyle(
            CTPPr paragraphProperties,
            CTRPr runProperties
    ) {
    }

    private record TeachingDesignStyles(
            ParagraphStyle progressTitle,
            ParagraphStyle sectionHeading,
            ParagraphStyle bodyText,
            ParagraphStyle remarkHeading
    ) {
    }

    private record TeachingResourceSummary(
            String references,
            String otherResources
    ) {
    }

    private record TemplateSegments(
            List<BodyTemplate> courseCover,
            List<BodyTemplate> unitCover,
            List<BodyTemplate> teachingDesign,
            CTSectPr sectionProperties
    ) {
    }

    private record SegmentRange(
            int start,
            int end
    ) {
    }

    private record UnitSegment(
            CoursePlanDtos.GeneratedUnit unit,
            SegmentRange range,
            List<DesignSegment> designs
    ) {
    }

    private record DesignSegment(
            CoursePlanDtos.TeachingDesign design,
            SegmentRange range
    ) {
    }

    @FunctionalInterface
    private interface TextMatcher {
        boolean matches(String text);
    }
}
