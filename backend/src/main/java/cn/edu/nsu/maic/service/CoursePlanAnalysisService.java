package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;
import cn.edu.nsu.maic.dto.TeachingCalendarDto;
import cn.edu.nsu.maic.dto.TeachingCalendarEntryDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CoursePlanAnalysisService {

    private static final Pattern UNIT_PATTERN = Pattern.compile(
            "(第[一二三四五六七八九十百零〇\\d]+单元)[：:\\s\\u3000]*([^（(\\n]+?)[\\s\\u3000]*[（(]\\s*(\\d+)\\s*(?:个)?\\s*学时[）)]"
    );
    private static final Pattern HOURS_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern SEMESTER_PATTERN = Pattern.compile("(20\\d{2}\\s*[-—－~至]\\s*20\\d{2}\\s*学年\\s*第\\s*[一二三四五六七八九十\\d]+\\s*学期)");
    private static final Pattern PROJECT_LEVEL_PATTERN = Pattern.compile("[一二三四五六七八九十百零〇\\d]+\\s*级\\s*项目");

    private final TeachingCalendarParseService teachingCalendarParseService;
    private final CoursePlanSchedulePlannerService coursePlanSchedulePlannerService;

    public CoursePlanAnalysisService(
            TeachingCalendarParseService teachingCalendarParseService,
            CoursePlanSchedulePlannerService coursePlanSchedulePlannerService
    ) {
        this.teachingCalendarParseService = teachingCalendarParseService;
        this.coursePlanSchedulePlannerService = coursePlanSchedulePlannerService;
    }

    public CoursePlanDtos.AnalysisResult analyze(
            MultipartFile templateFile,
            MultipartFile standardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles,
            String teacherRequirements
    ) throws IOException {
        validateRequiredFile(templateFile, "请上传教案模板");
        validateRequiredFile(standardFile, "请上传课程标准");
        if (pptFiles == null || pptFiles.isEmpty()) {
            throw new IllegalArgumentException("请至少上传一份 PPT/课件");
        }

        TemplateSnapshot templateSnapshot = parseTemplate(templateFile);
        StandardSnapshot standardSnapshot = parseCourseStandard(standardFile);
        List<CoursePlanDtos.PptMaterial> pptMaterials = parsePptMaterials(pptFiles);
        CoursePlanDtos.TeachingCalendar teachingCalendar = parseTeachingCalendar(referenceFiles);
        List<CoursePlanDtos.ReferenceMaterial> referenceMaterials = parseReferenceMaterials(referenceFiles);
        List<CoursePlanDtos.Issue> conflicts = new ArrayList<>();

        CoursePlanDtos.BasicInfo basicInfo = buildBasicInfo(standardSnapshot, pptMaterials, teachingCalendar);
        conflicts.addAll(validateBasicInfo(basicInfo));

        List<CoursePlanDtos.UnitAnalysis> units = parseUnits(standardSnapshot.text(), pptMaterials);
        var plannedSchedule = coursePlanSchedulePlannerService.plan(units, teachingCalendar, basicInfo.totalHours());
        units = plannedSchedule.units();
        if (units.isEmpty()) {
            conflicts.add(issue("units.missing", "error", "课程标准中未识别到任何“第X单元（X学时）”结构。"));
        }

        int summedHours = units.stream()
                .map(CoursePlanDtos.UnitAnalysis::hours)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (basicInfo.totalHours() != null && basicInfo.totalHours() > 0 && summedHours > 0 && summedHours != basicInfo.totalHours()) {
            conflicts.add(issue(
                    "hours.totalMismatch",
                    "error",
                    "课程标准中的单元学时总和为 " + summedHours + "，与课程总学时 " + basicInfo.totalHours() + " 不一致。"
            ));
        }
        conflicts.addAll(plannedSchedule.conflicts());

        conflicts.addAll(templateSnapshot.templateCheck().issues());
        conflicts.addAll(units.stream().map(CoursePlanDtos.UnitAnalysis::issues).flatMap(Collection::stream).toList());
        conflicts.addAll(validateTemplateCourseName(templateSnapshot, basicInfo));

        boolean valid = conflicts.stream().noneMatch(item -> "error".equalsIgnoreCase(item.level()))
                && templateSnapshot.templateCheck().valid()
                && !units.isEmpty();

        return new CoursePlanDtos.AnalysisResult(
                fileNameOf(templateFile),
                basicInfo,
                templateSnapshot.templateCheck(),
                units,
                deduplicateIssues(conflicts),
                valid,
                safeText(teacherRequirements),
                plannedSchedule.splitStrategy(),
                new CoursePlanDtos.SourceContext(
                        fileNameOf(standardFile),
                        standardSnapshot.text(),
                        pptMaterials,
                        referenceMaterials,
                        teachingCalendar,
                        standardSnapshot.textbooksAndReferences(),
                        standardSnapshot.otherTeachingResources()
                )
        );
    }

    private List<CoursePlanDtos.Issue> validateBasicInfo(CoursePlanDtos.BasicInfo basicInfo) {
        List<CoursePlanDtos.Issue> issues = new ArrayList<>();
        if (isBlank(basicInfo.courseName())) {
            issues.add(issue("basic.courseNameMissing", "error", "课程标准中未识别到课程名称。"));
        }
        if (basicInfo.totalHours() == null || basicInfo.totalHours() <= 0) {
            issues.add(issue("basic.totalHoursMissing", "error", "课程标准中未识别到总学时。"));
        }
        if (isBlank(basicInfo.school())) {
            issues.add(issue("basic.schoolMissing", "warning", "课程标准中未识别到学校名称。"));
        }
        if (isBlank(extractDepartmentName(basicInfo.department()))) {
            issues.add(issue("basic.departmentMissing", "error", "课程标准中未识别到明确的系/部信息。"));
        }
        if (!safeText(basicInfo.department()).contains("学院")) {
            issues.add(issue("basic.collegeMissing", "error", "课程标准中未识别到明确的学院信息。"));
        }
        return issues;
    }

    private List<CoursePlanDtos.Issue> validateTemplateCourseName(TemplateSnapshot templateSnapshot, CoursePlanDtos.BasicInfo basicInfo) {
        if (isBlank(templateSnapshot.courseTitleText()) || isBlank(basicInfo.courseName())) {
            return List.of();
        }
        String templateTitle = normalizeForCompare(templateSnapshot.courseTitleText());
        if (templateTitle.contains("课程名称")) {
            return List.of();
        }
        if (templateTitle.contains(normalizeForCompare(basicInfo.courseName()))) {
            return List.of();
        }
        return List.of(issue(
                "template.courseNameMismatch",
                "error",
                "模板中的课程名称与课程标准不一致，请确认模板是否匹配当前课程。"
        ));
    }

    private CoursePlanDtos.BasicInfo buildBasicInfo(
            StandardSnapshot standardSnapshot,
            List<CoursePlanDtos.PptMaterial> pptMaterials,
            CoursePlanDtos.TeachingCalendar teachingCalendar
    ) {
        String text = standardSnapshot.text();
        String school = firstNonBlank(
                firstLineMatching(text, ".*(学院|大学).*"),
                extractField(text, "学校名称"),
                extractField(text, "学校"),
                extractField(text, "学院")
        );
        String department = firstNonBlank(
                extractField(text, "开课单位"),
                extractField(text, "院系"),
                standardSnapshot.departmentFromTables(),
                extractField(text, "系/部"),
                extractField(text, "系部")
        );
        String courseName = firstNonBlank(
                extractBracketTitle(text, "课\\s*程\\s*标\\s*准"),
                extractField(text, "课程名称")
        );
        String courseCode = extractField(text, "课程代码");
        String targetStudents = extractField(text, "授课对象");
        String courseNature = firstNonBlank(extractField(text, "课程类别"), extractField(text, "课程性质"));
        String prerequisites = firstNonBlank(extractField(text, "先修课程"), extractField(text, "先修课程/项目"));
        String followUpCourses = firstNonBlank(extractField(text, "后续课程"), extractField(text, "后续课程/项目"));
        String credits = firstNonBlank(standardSnapshot.credits(), extractField(text, "学分"));
        Integer totalHours = firstPositive(standardSnapshot.totalHours(), extractIntegerField(text, "总学时"), extractIntegerField(text, "课内学时"));
        Integer theoryHours = firstPositive(standardSnapshot.theoryHours(), extractIntegerField(text, "理论学时"), extractIntegerField(text, "理论授课"));
        Integer practiceHours = firstPositive(standardSnapshot.practiceHours(), extractIntegerField(text, "实践学时"), extractIntegerField(text, "课内实践"));
        String teacherName = extractTeacherName(pptMaterials);
        String semester = firstNonBlank(
                extractSemester(fileNameOf(standardSnapshot.fileName())),
                extractSemester(text),
                extractSemester(teachingCalendar == null ? "" : teachingCalendar.fileName()),
                extractSemester(teachingCalendar == null ? "" : teachingCalendar.excerpt())
        );
        return new CoursePlanDtos.BasicInfo(
                school,
                department,
                courseName,
                courseCode,
                targetStudents,
                courseNature,
                credits,
                totalHours,
                theoryHours,
                practiceHours,
                prerequisites,
                followUpCourses,
                teacherName,
                teacherName,
                semester
        );
    }

    private List<CoursePlanDtos.UnitAnalysis> parseUnits(
            String standardText,
            List<CoursePlanDtos.PptMaterial> pptMaterials
    ) {
        List<UnitSection> sections = extractUnitSections(standardText);
        List<UnitDraft> drafts = new ArrayList<>();
        for (UnitSection section : sections) {
            List<CoursePlanDtos.Issue> issues = new ArrayList<>();
            if (section.hours() == null || section.hours() <= 0) {
                issues.add(issue("unit.hoursMissing", "error", "单元“" + section.name() + "”未识别到有效学时。"));
            }

            List<String> contentItems = extractContentItems(section.body());
            String requirementText = extractSectionBody(section.body(), "2．教学要求", "3．教学重点与难点");
            List<String> keyPoints = splitInlineItems(extractInlineField(section.body(), "教学重点"));
            List<String> difficultPoints = splitInlineItems(extractInlineField(section.body(), "教学难点"));
            List<String> implementationSuggestions = extractImplementationSuggestions(section.body());
            String projectText = extractExplicitProjectText(section.body());
            List<String> resources = extractResourceLines(section.body());
            List<String> assessments = extractAssessmentLines(section.body());
            drafts.add(new UnitDraft(
                    section,
                    distinctTexts(contentItems),
                    safeText(requirementText),
                    distinctTexts(keyPoints),
                    distinctTexts(difficultPoints),
                    distinctTexts(implementationSuggestions),
                    safeText(projectText),
                    distinctTexts(resources),
                    distinctTexts(assessments),
                    issues
            ));
        }

        List<CoursePlanDtos.UnitAnalysis> units = new ArrayList<>();
        var assignments = assignPptsToUnits(drafts, pptMaterials);
        for (UnitDraft draft : drafts) {
            List<CoursePlanDtos.Issue> issues = new ArrayList<>(draft.issues());
            List<CoursePlanDtos.PptMaterial> matchedPpts = assignments.getOrDefault(draft.section().index(), List.of());
            if (matchedPpts.isEmpty()) {
                issues.add(issue("unit.pptMissing", "error", "单元“" + draft.section().name() + "”未找到可匹配的 PPT/课件。"));
            }

            List<String> slideHeadings = matchedPpts.stream()
                    .flatMap(item -> item.headings().stream())
                    .map(this::safeText)
                    .filter(this::isSemanticFragment)
                    .distinct()
                    .toList();
            if (slideHeadings.isEmpty()) {
                issues.add(issue("unit.slideHeadingsMissing", "warning", "单元“" + draft.section().name() + "”对应课件中未提取到可用标题，将优先使用课程标准内容生成。"));
            }

            String status = issues.stream().anyMatch(item -> "error".equalsIgnoreCase(item.level())) ? "blocked" : "ready";
            units.add(new CoursePlanDtos.UnitAnalysis(
                    draft.section().index(),
                    "CU(" + draft.section().index() + ")",
                    draft.section().name(),
                    draft.section().hours(),
                    0,
                    List.of(),
                    draft.contentItems(),
                    draft.requirementText(),
                    draft.keyPoints(),
                    draft.difficultPoints(),
                    draft.implementationSuggestions(),
                    draft.projectText(),
                    draft.resources(),
                    draft.assessments(),
                    matchedPpts.stream().map(CoursePlanDtos.PptMaterial::fileName).toList(),
                    matchedPpts.stream().map(CoursePlanDtos.PptMaterial::title).toList(),
                    distinctTexts(slideHeadings),
                    List.of(),
                    "",
                    status,
                    issues
            ));
        }
        return resolveReviewUnitMappings(units, pptMaterials);
    }

    private String extractExplicitProjectText(String body) {
        for (String line : safeText(body).split("\n")) {
            String value = safeText(line);
            if (value.isBlank() || !hasExplicitProjectLabel(value) || !PROJECT_LEVEL_PATTERN.matcher(value).find()) {
                continue;
            }
            String fieldValue = extractProjectFieldValue(value);
            if (!fieldValue.isBlank() && PROJECT_LEVEL_PATTERN.matcher(fieldValue).find()) {
                return fieldValue;
            }
            return value;
        }
        return "";
    }

    private boolean hasExplicitProjectLabel(String value) {
        String normalized = normalizeForCompare(value);
        return normalized.contains("项目名称(级别)")
                || normalized.contains("项目名称")
                || normalized.contains("单元项目");
    }

    private String extractProjectFieldValue(String value) {
        Matcher matcher = Pattern.compile("(?:项目名称（级别）|项目名称\\(级别\\)|单元项目|项目名称)\\s*[:：]?\\s*(.+)").matcher(value);
        if (matcher.find()) {
            return safeText(matcher.group(1));
        }
        return "";
    }

    private java.util.Map<Integer, List<CoursePlanDtos.PptMaterial>> assignPptsToUnits(
            List<UnitDraft> drafts,
            List<CoursePlanDtos.PptMaterial> pptMaterials
    ) {
        java.util.Map<Integer, List<CoursePlanDtos.PptMaterial>> assignments = new java.util.LinkedHashMap<>();
        if (drafts.isEmpty() || pptMaterials == null || pptMaterials.isEmpty()) {
            return assignments;
        }
        List<CoursePlanDtos.PptMaterial> orderedPpts = orderPptsForAssignment(pptMaterials);
        List<UnitDraft> teachingUnits = drafts.stream()
                .filter(draft -> !isReviewUnit(draft.section().name()))
                .toList();
        if (teachingUnits.isEmpty()) {
            return assignments;
        }

        AssignmentPlan plan = computeAssignmentPlan(teachingUnits, orderedPpts, 1);
        if (plan == null) {
            plan = computeAssignmentPlan(teachingUnits, orderedPpts, 0);
        }
        if (plan == null) {
            return assignments;
        }

        for (int i = 0; i < teachingUnits.size(); i++) {
            int start = plan.boundaries()[i];
            int end = plan.boundaries()[i + 1];
            if (start < end) {
                assignments.put(
                        teachingUnits.get(i).section().index(),
                        new ArrayList<>(orderedPpts.subList(start, end))
                );
            }
        }
        return assignments;
    }

    private List<CoursePlanDtos.UnitAnalysis> resolveReviewUnitMappings(
            List<CoursePlanDtos.UnitAnalysis> units,
            List<CoursePlanDtos.PptMaterial> pptMaterials
    ) {
        List<CoursePlanDtos.UnitAnalysis> resolved = new ArrayList<>();
        List<String> previousPptFiles = new ArrayList<>();
        List<String> previousPptTitles = new ArrayList<>();
        List<String> previousHeadings = new ArrayList<>();
        for (CoursePlanDtos.UnitAnalysis unit : units) {
            boolean reviewUnit = normalizeForCompare(unit.name()).contains("复习") || normalizeForCompare(unit.name()).contains("总结");
            if (reviewUnit && unit.matchedPptFiles().isEmpty()) {
                List<CoursePlanDtos.Issue> issues = new ArrayList<>(unit.issues());
                if (previousPptFiles.isEmpty()) {
                    issues.add(issue("unit.reviewSourceMissing", "error", "复习/总结单元未找到可复用的前序课件。"));
                    resolved.add(new CoursePlanDtos.UnitAnalysis(
                            unit.index(),
                            unit.code(),
                            unit.name(),
                            unit.hours(),
                            unit.teachingDesignCount(),
                            unit.teachingDesignHours(),
                            unit.contentItems(),
                            unit.requirementText(),
                            unit.keyPoints(),
                            unit.difficultPoints(),
                            unit.implementationSuggestions(),
                            unit.projectText(),
                            unit.resources(),
                            unit.assessments(),
                            unit.matchedPptFiles(),
                            unit.matchedPptTitles(),
                            unit.slideHeadings(),
                            unit.teachingCalendarEntries(),
                            unit.weekRange(),
                            "blocked",
                            issues
                    ));
                    continue;
                }
                resolved.add(new CoursePlanDtos.UnitAnalysis(
                        unit.index(),
                        unit.code(),
                        unit.name(),
                        unit.hours(),
                        unit.teachingDesignCount(),
                        unit.teachingDesignHours(),
                        unit.contentItems(),
                        unit.requirementText(),
                        unit.keyPoints(),
                        unit.difficultPoints(),
                        unit.implementationSuggestions(),
                        unit.projectText(),
                        unit.resources(),
                        unit.assessments(),
                        distinctTexts(previousPptFiles),
                        distinctTexts(previousPptTitles),
                        distinctTexts(previousHeadings),
                        unit.teachingCalendarEntries(),
                        unit.weekRange(),
                        "ready",
                        issues.stream().filter(item -> !"unit.pptMissing".equals(item.code())).toList()
                ));
                continue;
            }
            previousPptFiles = new ArrayList<>(unit.matchedPptFiles());
            previousPptTitles = new ArrayList<>(unit.matchedPptTitles());
            previousHeadings = new ArrayList<>(unit.slideHeadings());
            resolved.add(unit);
        }
        return resolved;
    }

    private AssignmentPlan computeAssignmentPlan(
            List<UnitDraft> teachingUnits,
            List<CoursePlanDtos.PptMaterial> orderedPpts,
            int minSegmentSize
    ) {
        int unitCount = teachingUnits.size();
        int pptCount = orderedPpts.size();
        double impossible = -1_000_000_000d;
        int totalHours = teachingUnits.stream()
                .map(UnitDraft::section)
                .map(UnitSection::hours)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        double[] expectedStarts = new double[unitCount];
        double[] expectedEnds = new double[unitCount];
        int accumulatedHours = 0;
        for (int i = 0; i < unitCount; i++) {
            expectedStarts[i] = totalHours > 0
                    ? (accumulatedHours * 1.0 * pptCount) / totalHours
                    : (i * 1.0 * pptCount) / unitCount;
            accumulatedHours += Math.max(0, valueOrZero(teachingUnits.get(i).section().hours()));
            expectedEnds[i] = totalHours > 0
                    ? (accumulatedHours * 1.0 * pptCount) / totalHours
                    : ((i + 1) * 1.0 * pptCount) / unitCount;
        }
        double[][] dp = new double[unitCount + 1][pptCount + 1];
        int[][] prev = new int[unitCount + 1][pptCount + 1];
        for (double[] row : dp) {
            java.util.Arrays.fill(row, impossible);
        }
        for (int[] row : prev) {
            java.util.Arrays.fill(row, -1);
        }
        dp[0][0] = 0d;

        for (int unitIndex = 0; unitIndex < unitCount; unitIndex++) {
            for (int used = 0; used <= pptCount; used++) {
                if (dp[unitIndex][used] <= impossible / 2) {
                    continue;
                }
                if (unitIndex == unitCount - 1) {
                    if (pptCount - used < minSegmentSize) {
                        continue;
                    }
                    double candidate = dp[unitIndex][used]
                            + computeSegmentScore(
                            teachingUnits.get(unitIndex),
                            orderedPpts,
                            used,
                            pptCount,
                            expectedStarts[unitIndex],
                            expectedEnds[unitIndex]
                    );
                    if (candidate > dp[unitIndex + 1][pptCount]) {
                        dp[unitIndex + 1][pptCount] = candidate;
                        prev[unitIndex + 1][pptCount] = used;
                    }
                    continue;
                }

                int remainingUnits = unitCount - unitIndex - 1;
                int minRemaining = remainingUnits * minSegmentSize;
                int segmentStart = used + minSegmentSize;
                int segmentEnd = pptCount - minRemaining;
                if (segmentStart > segmentEnd) {
                    continue;
                }
                for (int end = segmentStart; end <= segmentEnd; end++) {
                    double candidate = dp[unitIndex][used]
                            + computeSegmentScore(
                            teachingUnits.get(unitIndex),
                            orderedPpts,
                            used,
                            end,
                            expectedStarts[unitIndex],
                            expectedEnds[unitIndex]
                    );
                    if (candidate > dp[unitIndex + 1][end]) {
                        dp[unitIndex + 1][end] = candidate;
                        prev[unitIndex + 1][end] = used;
                    }
                }
            }
        }

        if (dp[unitCount][pptCount] <= impossible / 2) {
            return null;
        }
        int[] boundaries = new int[unitCount + 1];
        boundaries[unitCount] = pptCount;
        int end = pptCount;
        for (int unitIndex = unitCount; unitIndex > 0; unitIndex--) {
            int start = prev[unitIndex][end];
            if (start < 0) {
                return null;
            }
            boundaries[unitIndex - 1] = start;
            end = start;
        }
        return new AssignmentPlan(boundaries);
    }

    private double computeSegmentScore(
            UnitDraft unit,
            List<CoursePlanDtos.PptMaterial> orderedPpts,
            int start,
            int end,
            double expectedStart,
            double expectedEnd
    ) {
        if (start == end) {
            return -60d;
        }
        double score = 0d;
        for (int i = start; i < end; i++) {
            score += computeUnitPptScore(unit, orderedPpts.get(i));
        }
        double actualSize = end - start;
        double expectedSize = Math.max(1d, expectedEnd - expectedStart);
        score -= Math.abs(start - expectedStart) * 9d;
        score -= Math.abs(end - expectedEnd) * 9d;
        score -= Math.abs(actualSize - expectedSize) * 12d;
        double averageScore = score / (end - start);
        if (averageScore < 10d) {
            score -= 30d;
        }
        return score;
    }

    private int computeUnitPptScore(UnitDraft unit, CoursePlanDtos.PptMaterial ppt) {
        String titleSource = normalizeForCompare(ppt.title() + "\n" + String.join("\n", ppt.headings()));
        String excerptSource = normalizeForCompare(ppt.excerpt());
        String unitName = normalizeForCompare(unit.section().name());
        int score = 0;
        if (!unitName.isBlank()) {
            if (titleSource.contains(unitName)) {
                score += 120;
            } else if (excerptSource.contains(unitName)) {
                score += 40;
            }
        }

        for (String term : buildUnitTerms(unit)) {
            if (term.length() < 2) {
                continue;
            }
            if (titleSource.contains(term)) {
                score += 45;
            } else if (excerptSource.contains(term)) {
                score += 12;
            }
        }

        if (score == 0 && ppt.chapterNumber() != null && unit.section().index() != null
                && Math.abs(ppt.chapterNumber() - unit.section().index()) <= 1) {
            score += 6;
        }
        return score;
    }

    private List<String> buildUnitTerms(UnitDraft unit) {
        List<String> terms = new ArrayList<>();
        addTerms(terms, unit.contentItems());
        addTerms(terms, unit.keyPoints());
        addTerms(terms, unit.difficultPoints());
        addTerms(terms, splitInlineItems(unit.projectText()));
        return distinctTexts(terms);
    }

    private void addTerms(List<String> target, List<String> source) {
        for (String item : source) {
            String normalized = normalizeForCompare(item);
            if (normalized.length() >= 2 && normalized.length() <= 32) {
                target.add(normalized);
            }
        }
    }

    private List<CoursePlanDtos.PptMaterial> orderPptsForAssignment(List<CoursePlanDtos.PptMaterial> pptMaterials) {
        List<CoursePlanDtos.PptMaterial> ordered = new ArrayList<>(pptMaterials);
        ordered.sort(Comparator
                .comparing((CoursePlanDtos.PptMaterial item) -> item.chapterNumber() == null ? Integer.MAX_VALUE : item.chapterNumber())
                .thenComparing(item -> safeText(item.title())));
        return ordered;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isReviewUnit(String unitName) {
        String normalized = normalizeForCompare(unitName);
        return normalized.contains("复习") || normalized.contains("总结");
    }

    private TemplateSnapshot parseTemplate(MultipartFile templateFile) throws IOException {
        if (!fileNameOf(templateFile).toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new IllegalArgumentException("教案模板仅支持 docx 文件");
        }
        DocxSnapshot snapshot = readDocx(templateFile);
        String normalized = normalizeForCompare(snapshot.text());
        boolean courseCoverDetected = normalized.contains("课程教案");
        boolean unitCoverDetected = normalized.contains("单元教案首页");
        boolean teachingDesignDetected = normalized.contains("教学设计");
        List<CoursePlanDtos.Issue> issues = new ArrayList<>();
        if (!courseCoverDetected) {
            issues.add(issue("template.courseCoverMissing", "error", "模板中未识别到“课程教案首页”。"));
        }
        if (!unitCoverDetected) {
            issues.add(issue("template.unitCoverMissing", "error", "模板中未识别到“单元教案首页”。"));
        }
        if (!teachingDesignDetected) {
            issues.add(issue("template.teachingDesignMissing", "error", "模板中未识别到“教学设计”页面。"));
        }
        return new TemplateSnapshot(
                new CoursePlanDtos.TemplateCheck(
                        fileNameOf(templateFile),
                        courseCoverDetected,
                        unitCoverDetected,
                        teachingDesignDetected,
                        issues.isEmpty(),
                        issues
                ),
                snapshot.firstCourseTitle()
        );
    }

    private StandardSnapshot parseCourseStandard(MultipartFile standardFile) throws IOException {
        String fileName = fileNameOf(standardFile);
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            DocxSnapshot snapshot = readDocx(standardFile);
            HoursFromTables hours = parseHoursFromTables(snapshot.tableRows());
            TeachingResources teachingResources = parseTeachingResources(snapshot.text());
            return new StandardSnapshot(
                    fileName,
                    snapshot.text(),
                    snapshot.tableRows(),
                    hours.credits(),
                    hours.totalHours(),
                    hours.theoryHours(),
                    hours.practiceHours(),
                    hours.department(),
                    teachingResources.textbooksAndReferences(),
                    teachingResources.otherTeachingResources()
            );
        }
        if (lower.endsWith(".pdf")) {
            String text = readPdfText(standardFile);
            TeachingResources teachingResources = parseTeachingResources(text);
            return new StandardSnapshot(
                    fileName,
                    text,
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    teachingResources.textbooksAndReferences(),
                    teachingResources.otherTeachingResources()
            );
        }
        throw new IllegalArgumentException("课程标准仅支持 docx 或可解析文本的 pdf 文件");
    }

    private TeachingResources parseTeachingResources(String standardText) {
        String block = extractMajorSectionBlock(standardText, "教材及课程资源");
        if (block.isBlank()) {
            List<String> textbooks = extractSubsectionItems(standardText, "教材及参考书");
            List<String> resources = extractSubsectionItems(standardText, "课程资源的开发与利用");
            return new TeachingResources(textbooks, resources);
        }
        return new TeachingResources(
                extractSubsectionItems(block, "教材及参考书"),
                extractSubsectionItems(block, "课程资源的开发与利用")
        );
    }

    private String extractMajorSectionBlock(String text, String keyword) {
        List<String> lines = List.of(cleanText(text).split("\n"));
        List<String> collected = new ArrayList<>();
        boolean started = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String value = safeText(line);
            if (value.isBlank()) {
                continue;
            }
            if (!started) {
                if (value.contains(keyword)) {
                    started = true;
                    collected.add(value);
                } else if (isTopLevelHeading(value) && nextNonBlankLine(lines, index).contains(keyword)) {
                    started = true;
                    collected.add(value);
                }
                continue;
            }
            if (isTopLevelHeading(value) && !value.contains(keyword)) {
                break;
            }
            collected.add(value);
        }
        return String.join("\n", collected);
    }

    private List<String> extractSubsectionItems(String block, String subsectionLabel) {
        List<String> lines = List.of(cleanText(block).split("\n"));
        List<String> subsectionLines = new ArrayList<>();
        boolean started = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String value = safeText(line);
            if (value.isBlank()) {
                continue;
            }
            if (!started) {
                if (normalizeForCompare(value).contains(normalizeForCompare(subsectionLabel))) {
                    started = true;
                } else if (isSubsectionHeading(value)
                        && normalizeForCompare(nextNonBlankLine(lines, index)).contains(normalizeForCompare(subsectionLabel))) {
                    started = true;
                }
                continue;
            }
            if (isSubsectionHeading(value) || isTopLevelHeading(value) || isTeachingResourceBoundary(value)) {
                break;
            }
            if (normalizeForCompare(value).contains(normalizeForCompare(subsectionLabel))) {
                continue;
            }
            subsectionLines.add(value);
        }
        return mergeEnumeratedItems(subsectionLines).stream()
                .map(this::trimTeachingResourceNoise)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<String> mergeEnumeratedItems(List<String> lines) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String value = safeText(line);
            if (value.isBlank()) {
                continue;
            }
            if (startsEnumeratedItem(value)) {
                if (!current.isEmpty()) {
                    items.add(current.toString());
                }
                current = new StringBuilder(value);
                continue;
            }
            if (current.isEmpty()) {
                current = new StringBuilder(value);
                continue;
            }
            current.append(' ').append(value);
        }
        if (!current.isEmpty()) {
            items.add(current.toString());
        }
        return distinctTexts(items);
    }

    private boolean isTopLevelHeading(String line) {
        return safeText(line).matches("^[一二三四五六七八九十百零〇]+[、.．].*");
    }

    private boolean isSubsectionHeading(String line) {
        String normalized = safeText(line);
        return normalized.matches("^[0-9１２]+[、.．].*");
    }

    private boolean startsEnumeratedItem(String line) {
        String normalized = safeText(line);
        return normalized.matches("^\\(?[0-9]+\\)?[、.．].*")
                || normalized.matches("^（[0-9]+）.*")
                || normalized.matches("^\\([0-9]+\\).*")
                || normalized.matches("^（[一二三四五六七八九十]+）.*")
                || normalized.matches("^\\([一二三四五六七八九十]+\\).*");
    }

    private boolean isTeachingResourceBoundary(String value) {
        String normalized = normalizeForCompare(value);
        return normalized.contains("学习策略与技巧")
                || normalized.contains("课程教学基本条件")
                || normalized.contains("学习方法")
                || normalized.contains("学生勤思考")
                || normalized.contains("学生自学")
                || normalized.contains("学生边学边总结")
                || normalized.contains("考核")
                || normalized.contains("评价");
    }

    private String trimTeachingResourceNoise(String value) {
        String cleaned = safeText(value);
        for (String marker : List.of("学习策略与技巧", "课程教学基本条件", "学生勤思考", "学生自学", "学生边学边总结", "做笔记是个好习惯")) {
            int index = cleaned.indexOf(marker);
            if (index >= 0) {
                cleaned = cleaned.substring(0, index);
            }
        }
        return safeText(cleaned);
    }

    private String nextNonBlankLine(List<String> lines, int currentIndex) {
        for (int index = currentIndex + 1; index < lines.size(); index++) {
            String value = safeText(lines.get(index));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private List<CoursePlanDtos.PptMaterial> parsePptMaterials(List<MultipartFile> pptFiles) throws IOException {
        List<CoursePlanDtos.PptMaterial> materials = new ArrayList<>();
        for (MultipartFile file : pptFiles) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fileName = fileNameOf(file);
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pptx")) {
                materials.add(readPptx(file));
                continue;
            }
            if (lower.endsWith(".ppt")) {
                materials.add(readPpt(file));
                continue;
            }
            throw new IllegalArgumentException("PPT/课件仅支持 ppt 或 pptx 文件");
        }
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("请至少上传一份有效的 PPT/课件");
        }
        return materials;
    }

    private List<CoursePlanDtos.ReferenceMaterial> parseReferenceMaterials(List<MultipartFile> referenceFiles) throws IOException {
        if (referenceFiles == null || referenceFiles.isEmpty()) {
            return List.of();
        }
        List<CoursePlanDtos.ReferenceMaterial> materials = new ArrayList<>();
        for (MultipartFile file : referenceFiles) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fileName = fileNameOf(file);
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (isSpreadsheetFile(lower)) {
                continue;
            }
            String text;
            String fileType;
            if (lower.endsWith(".docx")) {
                text = readDocx(file).text();
                fileType = "docx";
            } else if (lower.endsWith(".pdf")) {
                text = readPdfText(file);
                fileType = "pdf";
            } else if (lower.endsWith(".pptx")) {
                text = readPptx(file).extractedText();
                fileType = "pptx";
            } else if (lower.endsWith(".ppt")) {
                text = readPpt(file).extractedText();
                fileType = "ppt";
            } else if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
                fileType = lower.endsWith(".md") ? "md" : "txt";
            } else {
                throw new IllegalArgumentException("其他参考资料仅支持 txt、md、docx、pdf、ppt、pptx、xls、xlsx");
            }
            String cleaned = cleanText(text);
            if (!cleaned.isBlank()) {
                materials.add(new CoursePlanDtos.ReferenceMaterial(fileName, fileType, abbreviate(cleaned, 1200), abbreviate(cleaned, 20000)));
            }
        }
        return materials;
    }

    private CoursePlanDtos.TeachingCalendar parseTeachingCalendar(List<MultipartFile> referenceFiles) throws IOException {
        if (referenceFiles == null || referenceFiles.isEmpty()) {
            return null;
        }
        List<MultipartFile> calendarFiles = referenceFiles.stream()
                .filter(file -> file != null && !file.isEmpty())
                .filter(file -> isSpreadsheetFile(fileNameOf(file).toLowerCase(Locale.ROOT)))
                .toList();
        if (calendarFiles.isEmpty()) {
            return null;
        }
        if (calendarFiles.size() > 1) {
            throw new IllegalArgumentException("课程教案流程仅支持上传一份教学日历 xls/xlsx。");
        }
        try {
            return toCoursePlanTeachingCalendar(teachingCalendarParseService.parse(calendarFiles.get(0)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持该 xlsx 结构：" + e.getMessage(), e);
        }
    }

    private boolean isSpreadsheetFile(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private CoursePlanDtos.TeachingCalendar toCoursePlanTeachingCalendar(TeachingCalendarDto dto) {
        if (dto == null) {
            return null;
        }
        List<CoursePlanDtos.TeachingCalendarEntry> entries = dto.getEntries() == null
                ? List.of()
                : dto.getEntries().stream().map(this::toCoursePlanTeachingCalendarEntry).toList();
        return new CoursePlanDtos.TeachingCalendar(
                safeText(dto.getFileName()),
                safeText(dto.getFileType()),
                dto.getRowCount(),
                safeText(dto.getExcerpt()),
                safeText(dto.getUploadedAt()),
                entries
        );
    }

    private CoursePlanDtos.TeachingCalendarEntry toCoursePlanTeachingCalendarEntry(TeachingCalendarEntryDto entry) {
        return new CoursePlanDtos.TeachingCalendarEntry(
                safeText(entry.getWeek()),
                safeText(entry.getSession()),
                entry.getPeriodCount(),
                safeText(entry.getLessonType()),
                safeText(entry.getTopic()),
                safeText(entry.getRawText()),
                entry.getAllocatedHours()
        );
    }

    private DocxSnapshot readDocx(MultipartFile file) throws IOException {
        StringBuilder text = new StringBuilder();
        List<List<String>> tableRows = new ArrayList<>();
        String firstCourseTitle = "";
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String value = safeText(paragraph.getText());
                if (value.isBlank()) {
                    continue;
                }
                if (firstCourseTitle.isBlank() && normalizeForCompare(value).contains("课程教案")) {
                    firstCourseTitle = value;
                }
                appendLine(text, value);
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String value = safeText(cell.getText());
                        cells.add(value);
                    }
                    if (!cells.stream().allMatch(String::isBlank)) {
                        tableRows.add(cells);
                        appendLine(text, String.join(" | ", cells));
                    }
                }
            }
        }
        return new DocxSnapshot(cleanText(text.toString()), tableRows, firstCourseTitle);
    }

    private String readPdfText(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = cleanText(stripper.getText(document));
            if (text.isBlank()) {
                throw new IllegalArgumentException("当前版本暂不支持扫描图片版 PDF，请改传 docx 或可提取文本的 PDF。");
            }
            return text;
        }
    }

    private CoursePlanDtos.PptMaterial readPptx(MultipartFile file) throws IOException {
        List<String> headings = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        try (XMLSlideShow slideShow = new XMLSlideShow(file.getInputStream())) {
            int page = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                List<String> slideTexts = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String value = cleanText(textShape.getText());
                        if (!value.isBlank() && !isPptNoise(value)) {
                            slideTexts.add(value);
                        }
                    }
                }
                String heading = inferSlideHeading(slideTexts);
                if (!heading.isBlank()) {
                    headings.add(heading);
                }
                appendLine(text, "第" + page++ + "页 " + String.join(" | ", slideTexts));
            }
            headings = filterRepeatedPptHeadings(headings, slideShow.getSlides().size());
            String extracted = abbreviate(cleanText(text.toString()), 40000);
            return new CoursePlanDtos.PptMaterial(
                    fileNameOf(file),
                    inferPptTitle(fileNameOf(file), headings),
                    slideShow.getSlides().size(),
                    inferChapterNumber(fileNameOf(file)),
                    distinctSemanticTexts(headings),
                    abbreviate(extracted, 1500),
                    extracted
            );
        }
    }

    private CoursePlanDtos.PptMaterial readPpt(MultipartFile file) throws IOException {
        List<String> headings = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        try (HSLFSlideShow slideShow = new HSLFSlideShow(file.getInputStream())) {
            int page = 1;
            for (HSLFSlide slide : slideShow.getSlides()) {
                List<String> slideTexts = new ArrayList<>();
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        String value = cleanText(textShape.getText());
                        if (!value.isBlank() && !isPptNoise(value)) {
                            slideTexts.add(value);
                        }
                    }
                }
                String heading = inferSlideHeading(slideTexts);
                if (!heading.isBlank()) {
                    headings.add(heading);
                }
                appendLine(text, "第" + page++ + "页 " + String.join(" | ", slideTexts));
            }
            headings = filterRepeatedPptHeadings(headings, slideShow.getSlides().size());
            String extracted = abbreviate(cleanText(text.toString()), 40000);
            return new CoursePlanDtos.PptMaterial(
                    fileNameOf(file),
                    inferPptTitle(fileNameOf(file), headings),
                    slideShow.getSlides().size(),
                    inferChapterNumber(fileNameOf(file)),
                    distinctSemanticTexts(headings),
                    abbreviate(extracted, 1500),
                    extracted
            );
        }
    }

    private HoursFromTables parseHoursFromTables(List<List<String>> tableRows) {
        String department = firstNonBlank(
                extractTableValue(tableRows, "开课单位"),
                extractTableValue(tableRows, "院系"),
                extractTableValue(tableRows, "系/部", "系部")
        );
        for (int i = 0; i < tableRows.size() - 1; i++) {
            List<String> header = tableRows.get(i);
            List<String> values = tableRows.get(i + 1);
            if (header.size() >= 4
                    && containsAny(header.get(0), "学分")
                    && containsAny(header.get(1), "学时", "课内学时")) {
                return new HoursFromTables(
                        safeText(valueAt(values, 0)),
                        parsePositiveInt(valueAt(values, 1)),
                        parsePositiveInt(valueAt(values, 2)),
                        parsePositiveInt(valueAt(values, 3)),
                        department
                );
            }
        }
        return new HoursFromTables(null, null, null, null, department);
    }

    private String extractTableValue(List<List<String>> tableRows, String... labels) {
        for (int rowIndex = 0; rowIndex < tableRows.size(); rowIndex++) {
            List<String> row = tableRows.get(rowIndex);
            for (int cellIndex = 0; cellIndex < row.size(); cellIndex++) {
                if (!matchesAnyLabel(normalizeForCompare(row.get(cellIndex)), labels)) {
                    continue;
                }
                if (cellIndex + 1 < row.size()) {
                    String sameRowValue = safeText(row.get(cellIndex + 1));
                    if (!sameRowValue.isBlank()) {
                        return sameRowValue;
                    }
                }
                if (rowIndex + 1 < tableRows.size() && cellIndex < tableRows.get(rowIndex + 1).size()) {
                    String nextRowValue = safeText(tableRows.get(rowIndex + 1).get(cellIndex));
                    if (!nextRowValue.isBlank()) {
                        return nextRowValue;
                    }
                }
            }
        }
        return "";
    }

    private boolean matchesAnyLabel(String cell, String... labels) {
        for (String label : labels) {
            String normalizedLabel = normalizeForCompare(label);
            if (cell.equals(normalizedLabel) || cell.contains(normalizedLabel)) {
                return true;
            }
        }
        return false;
    }

    private List<UnitSection> extractUnitSections(String standardText) {
        List<UnitSection> sections = new ArrayList<>();
        Matcher matcher = UNIT_PATTERN.matcher(standardText);
        List<MatchPoint> matches = new ArrayList<>();
        while (matcher.find()) {
            String name = safeText(matcher.group(2));
            Integer hours = parsePositiveInt(matcher.group(3));
            matches.add(new MatchPoint(matches.size() + 1, matcher.start(), matcher.end(), name, hours));
        }
        for (int i = 0; i < matches.size(); i++) {
            MatchPoint current = matches.get(i);
            int end = i + 1 < matches.size() ? matches.get(i + 1).start() : standardText.length();
            String body = standardText.substring(current.end(), end);
            sections.add(new UnitSection(current.index(), current.name(), current.hours(), body));
        }
        return sections;
    }

    private List<String> extractContentItems(String body) {
        String contentBlock = extractSectionBody(body, "1．教学内容", "2．教学要求");
        List<String> items = new ArrayList<>();
        for (String line : contentBlock.split("\n")) {
            String normalized = line.trim().replaceFirst("^\\d+(?:\\.\\d+)*[.．、]?", "").trim();
            if (!normalized.isBlank() && normalized.length() <= 60 && !normalized.contains("教学内容")) {
                items.add(normalized);
            }
        }
        return distinctTexts(items);
    }

    private List<String> extractImplementationSuggestions(String body) {
        String block = extractSectionBody(body, "4．教学实施建议", "");
        if (block.isBlank()) {
            block = extractSectionBody(body, "教学活动", "");
        }
        List<String> items = new ArrayList<>();
        for (String line : block.split("\n")) {
            String value = line.trim();
            if (!value.isBlank() && value.length() <= 120) {
                items.add(value);
            }
        }
        return distinctTexts(items);
    }

    private List<String> extractResourceLines(String body) {
        List<String> items = new ArrayList<>();
        addIfPresent(items, extractFirstLineContaining(body, "课程资源"));
        addIfPresent(items, extractFirstLineContaining(body, "学习支持"));
        for (String line : body.split("\n")) {
            String value = line.trim();
            if ((value.contains("课件") || value.contains("讲义") || value.contains("视频") || value.contains("习题库")
                    || value.contains("实验指导书") || value.contains("多媒体")
                    || value.contains("教材") || value.contains("参考资料"))
                    && value.length() <= 80
                    && !value.contains("教学法")
                    && !value.contains("课程思政")
                    && !value.contains("培养目标")
                    && !value.contains("考核")) {
                items.add(value);
            }
        }
        return distinctTexts(items);
    }

    private List<String> extractAssessmentLines(String body) {
        List<String> items = new ArrayList<>();
        for (String line : body.split("\n")) {
            String value = line.trim();
            if (value.contains("考核") || value.contains("课堂表现") || value.contains("作业") || value.contains("实验报告")) {
                items.add(value);
            }
        }
        return distinctTexts(items);
    }

    private String extractSectionBody(String body, String startLabel, String endLabel) {
        int start = locate(body, startLabel);
        if (start < 0) {
            return "";
        }
        int contentStart = body.indexOf('\n', start);
        if (contentStart < 0) {
            contentStart = start + startLabel.length();
        }
        int end = isBlank(endLabel) ? body.length() : locate(body, endLabel);
        if (end < 0) {
            end = body.length();
        }
        return cleanText(body.substring(contentStart, end));
    }

    private int locate(String text, String label) {
        if (isBlank(label)) {
            return -1;
        }
        List<String> candidates = List.of(
                label,
                label.replace("．", "."),
                label.replace("。", "."),
                label.replace(" ", "")
        );
        for (String candidate : candidates) {
            int index = text.indexOf(candidate);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }

    private String extractInlineField(String body, String label) {
        Pattern pattern = Pattern.compile(label + "[:：]\\s*([^\\n]+)");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return safeText(matcher.group(1));
        }
        return "";
    }

    private List<String> splitInlineItems(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String item : value.split("[、；;，,]")) {
            String cleaned = item.trim();
            if (!cleaned.isBlank()) {
                items.add(cleaned);
            }
        }
        return distinctTexts(items);
    }

    private String extractField(String text, String label) {
        Pattern pattern = Pattern.compile(label + "\\s*[：:]\\s*([^\\n]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return safeText(matcher.group(1));
        }
        return "";
    }

    private Integer extractIntegerField(String text, String label) {
        return parsePositiveInt(extractField(text, label));
    }

    private String extractBracketTitle(String text, String suffixRegex) {
        Pattern pattern = Pattern.compile("《([^》]+)》\\s*" + suffixRegex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return safeText(matcher.group(1));
        }
        return "";
    }

    private String extractTeacherName(List<CoursePlanDtos.PptMaterial> pptMaterials) {
        for (CoursePlanDtos.PptMaterial ppt : pptMaterials) {
            String value = extractField(ppt.extractedText(), "授课教师");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String extractSemester(String text) {
        if (isBlank(text)) {
            return "";
        }
        Matcher matcher = SEMESTER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1)
                    .replaceAll("\\s+", "")
                    .replace("—", "-")
                    .replace("－", "-")
                    .replace("~", "-")
                    .replace("至", "-");
        }
        return "";
    }

    private String extractFirstLineContaining(String text, String keyword) {
        for (String line : text.split("\n")) {
            String value = line.trim();
            if (!value.isBlank() && value.contains(keyword)) {
                return value;
            }
        }
        return "";
    }

    private String firstLineMatching(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        for (String line : text.split("\n")) {
            String value = line.trim();
            if (!value.isBlank() && pattern.matcher(value).matches()) {
                return value;
            }
        }
        return "";
    }

    private Integer inferChapterNumber(String fileName) {
        Matcher matcher = Pattern.compile("第\\s*(\\d+)\\s*章").matcher(fileName);
        if (matcher.find()) {
            return parsePositiveInt(matcher.group(1));
        }
        return null;
    }

    private String inferPptTitle(String fileName, List<String> headings) {
        String title = fileName.replaceFirst("\\.[^.]+$", "").trim();
        if (Pattern.compile("第\\s*[一二三四五六七八九十百零〇\\d]+\\s*章").matcher(title).find()) {
            return title;
        }
        for (String heading : headings) {
            if (isSemanticFragment(heading)) {
                return heading;
            }
        }
        return title;
    }

    private String inferSlideHeading(List<String> slideTexts) {
        for (String text : slideTexts) {
            String value = text.replace('\n', ' ').trim();
            if (value.isBlank()) {
                continue;
            }
            String candidate = value.split("\\|")[0].trim();
            if (candidate.length() > 1 && candidate.length() <= 48 && isLikelySlideHeading(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private boolean isLikelySlideHeading(String value) {
        String cleaned = safeText(value);
        if (!isSemanticFragment(cleaned)) {
            return false;
        }
        if (cleaned.length() > 36) {
            return false;
        }
        String normalized = normalizeForCompare(cleaned);
        if (normalized.startsWith("例") && cleaned.length() > 16) {
            return false;
        }
        if (normalized.contains("算法如下")
                || normalized.contains("过程如下")
                || normalized.contains("定义如下")
                || normalized.contains("例如")
                || normalized.contains("如下图所示")
                || normalized.contains("时间复杂度")
                || normalized.contains("空间复杂度")) {
            return false;
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.contains("def ")
                || lower.contains("print(")
                || lower.contains("return ")
                || lower.contains("while ")
                || lower.contains("for ")) {
            return false;
        }
        return !cleaned.contains("。")
                && !cleaned.contains("；")
                && !cleaned.contains("=")
                && !cleaned.contains("->")
                && !cleaned.contains("=>");
    }

    private List<String> filterRepeatedPptHeadings(List<String> headings, int slideCount) {
        if (headings == null || headings.isEmpty()) {
            return List.of();
        }
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String heading : headings) {
            String key = normalizeForCompare(heading);
            if (!key.isBlank()) {
                counts.put(key, counts.getOrDefault(key, 0) + 1);
            }
        }
        int repeatThreshold = Math.max(2, (int) Math.ceil(Math.max(1, slideCount) * 0.25d));
        List<String> filtered = new ArrayList<>();
        for (String heading : headings) {
            String key = normalizeForCompare(heading);
            if (!isSemanticFragment(heading)) {
                continue;
            }
            if (counts.getOrDefault(key, 0) > repeatThreshold && !looksLikeCourseHeading(heading)) {
                continue;
            }
            filtered.add(heading);
        }
        return distinctSemanticTexts(filtered);
    }

    private boolean looksLikeCourseHeading(String value) {
        String normalized = normalizeForCompare(value);
        return normalized.contains("第") && (normalized.contains("章") || normalized.contains("节") || normalized.contains("单元"));
    }

    private List<CoursePlanDtos.Issue> deduplicateIssues(List<CoursePlanDtos.Issue> issues) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<CoursePlanDtos.Issue> result = new ArrayList<>();
        for (CoursePlanDtos.Issue issue : issues) {
            String key = issue.code() + "|" + issue.message();
            if (seen.add(key)) {
                result.add(issue);
            }
        }
        return result;
    }

    private void validateRequiredFile(MultipartFile file, String message) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String valueAt(List<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return "";
        }
        return safeText(values.get(index));
    }

    private boolean containsAny(String value, String... words) {
        if (value == null) {
            return false;
        }
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private Integer firstPositive(Integer... values) {
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String extractDepartmentName(String value) {
        String department = safeText(value);
        int collegeIndex = department.lastIndexOf("学院");
        if (collegeIndex >= 0 && department.endsWith("系") && collegeIndex + 2 < department.length()) {
            return department.substring(collegeIndex + 2).trim();
        }
        return department.endsWith("系") ? department : "";
    }

    private void addIfPresent(List<String> target, String value) {
        if (!isBlank(value)) {
            target.add(value.trim());
        }
    }

    private void appendLine(StringBuilder builder, String value) {
        String text = safeText(value);
        if (text.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(text);
    }

    private List<String> distinctTexts(List<String> values) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = safeText(value);
            if (!cleaned.isBlank()) {
                distinct.add(cleaned);
            }
        }
        return new ArrayList<>(distinct);
    }

    private List<String> distinctSemanticTexts(List<String> values) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = safeText(value);
            if (isSemanticFragment(cleaned)) {
                distinct.add(cleaned);
            }
        }
        return new ArrayList<>(distinct);
    }

    private boolean isSemanticFragment(String value) {
        String cleaned = safeText(value);
        if (cleaned.length() <= 1) {
            return false;
        }
        if (cleaned.matches("^[\\d\\s.．、:：\\-—_]+$")) {
            return false;
        }
        if (isPptNoise(cleaned)) {
            return false;
        }
        return cleaned.matches(".*[\\p{IsHan}A-Za-z].*");
    }

    private boolean isPptNoise(String value) {
        String cleaned = safeText(value);
        if (cleaned.isBlank()) {
            return true;
        }
        String normalized = normalizeForCompare(cleaned);
        if (normalized.contains("neusoft")
                || normalized.contains("chengduneusoft")
                || normalized.contains("instituteofinformation")
                || normalized.contains("成都东软学院")
                || normalized.contains("东软学院")
                || normalized.contains("thankyou")
                || normalized.contains("thanks")
                || normalized.contains("谢谢")
                || normalized.contains("感谢")
                || normalized.contains("视频链接")
                || normalized.contains("youtube")
                || normalized.contains("bilibili")
                || normalized.contains("copyright")
                || normalized.contains("版权所有")
                || normalized.contains("logo")) {
            return true;
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if ((lower.contains("http://") || lower.contains("https://") || lower.contains("www."))
                && !cleaned.matches(".*[\\p{IsHan}].*")) {
            return true;
        }
        if (lower.matches(".*[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}.*")) {
            return true;
        }
        return normalized.matches("^(page)?\\d+$") || normalized.matches("^第?\\d+页$");
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String cleaned = safeText(text);
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength);
    }

    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\n]]", " ")
                .replaceAll("[ ]{2,}", " ");
        List<String> lines = new ArrayList<>();
        for (String raw : normalized.split("\n")) {
            String line = raw.trim();
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return String.join("\n", lines).trim();
    }

    private String normalizeForCompare(String text) {
        return safeText(text)
                .replace("　", "")
                .replace(" ", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("（", "(")
                .replace("）", ")")
                .replace("：", ":")
                .toLowerCase(Locale.ROOT);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String fileNameOf(MultipartFile file) {
        return file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
    }

    private String fileNameOf(String fileName) {
        return fileName == null ? "" : fileName.trim();
    }

    private Integer parsePositiveInt(String value) {
        if (isBlank(value)) {
            return null;
        }
        Matcher matcher = HOURS_PATTERN.matcher(value);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private CoursePlanDtos.Issue issue(String code, String level, String message) {
        return new CoursePlanDtos.Issue(code, level, message);
    }

    private record DocxSnapshot(
            String text,
            List<List<String>> tableRows,
            String firstCourseTitle
    ) {
    }

    private record StandardSnapshot(
            String fileName,
            String text,
            List<List<String>> tableRows,
            String credits,
            Integer totalHours,
            Integer theoryHours,
            Integer practiceHours,
            String departmentFromTables,
            List<String> textbooksAndReferences,
            List<String> otherTeachingResources
    ) {
    }

    private record TemplateSnapshot(
            CoursePlanDtos.TemplateCheck templateCheck,
            String courseTitleText
    ) {
    }

    private record HoursFromTables(
            String credits,
            Integer totalHours,
            Integer theoryHours,
            Integer practiceHours,
            String department
    ) {
    }

    private record TeachingResources(
            List<String> textbooksAndReferences,
            List<String> otherTeachingResources
    ) {
    }

    private record MatchPoint(
            int index,
            int start,
            int end,
            String name,
            Integer hours
    ) {
    }

    private record UnitSection(
            Integer index,
            String name,
            Integer hours,
            String body
    ) {
    }

    private record UnitDraft(
            UnitSection section,
            List<String> contentItems,
            String requirementText,
            List<String> keyPoints,
            List<String> difficultPoints,
            List<String> implementationSuggestions,
            String projectText,
            List<String> resources,
            List<String> assessments,
            List<CoursePlanDtos.Issue> issues
    ) {
    }

    private record AssignmentPlan(
            int[] boundaries
    ) {
    }
}
