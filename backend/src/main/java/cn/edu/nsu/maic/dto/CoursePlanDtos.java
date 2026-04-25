package cn.edu.nsu.maic.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class CoursePlanDtos {

    private CoursePlanDtos() {
    }

    public record Issue(
            String code,
            String level,
            String message
    ) {
    }

    public record TemplateCheck(
            String fileName,
            boolean courseCoverDetected,
            boolean unitCoverDetected,
            boolean teachingDesignDetected,
            boolean valid,
            List<Issue> issues
    ) {
    }

    public record BasicInfo(
            String school,
            String department,
            String courseName,
            String courseCode,
            String targetStudents,
            String courseNature,
            String credits,
            Integer totalHours,
            Integer theoryHours,
            Integer practiceHours,
            String prerequisites,
            String followUpCourses,
            String teacherName,
            String responsibleTeacher,
            String semester
    ) {
    }

    public record PptMaterial(
            String fileName,
            String title,
            Integer slideCount,
            Integer chapterNumber,
            List<String> headings,
            String excerpt,
            String extractedText
    ) {
    }

    public record ReferenceMaterial(
            String fileName,
            String fileType,
            String excerpt,
            String extractedText
    ) {
    }

    public record TeachingCalendarEntry(
            String week,
            String session,
            Integer periodCount,
            String lessonType,
            String topic,
            String rawText
    ) {
    }

    public record TeachingCalendar(
            String fileName,
            String fileType,
            Integer rowCount,
            String excerpt,
            String uploadedAt,
            List<TeachingCalendarEntry> entries
    ) {
    }

    public record UnitAnalysis(
            Integer index,
            String code,
            String name,
            Integer hours,
            Integer teachingDesignCount,
            List<String> contentItems,
            String requirementText,
            List<String> keyPoints,
            List<String> difficultPoints,
            List<String> implementationSuggestions,
            String projectText,
            List<String> resources,
            List<String> assessments,
            List<String> matchedPptFiles,
            List<String> matchedPptTitles,
            List<String> slideHeadings,
            List<TeachingCalendarEntry> teachingCalendarEntries,
            String status,
            List<Issue> issues
    ) {
    }

    public record SourceContext(
            String courseStandardFileName,
            String courseStandardText,
            List<PptMaterial> pptMaterials,
            List<ReferenceMaterial> referenceMaterials,
            TeachingCalendar teachingCalendar,
            List<String> textbooksAndReferences,
            List<String> otherTeachingResources
    ) {
    }

    public record AnalysisResult(
            String templateFileName,
            BasicInfo basicInfo,
            TemplateCheck templateCheck,
            List<UnitAnalysis> units,
            List<Issue> conflicts,
            boolean valid,
            String teacherRequirements,
            SourceContext sourceContext
    ) {
    }

    public record MainContentBlock(
            String title,
            Integer minutes,
            List<String> points
    ) {
    }

    public record TeachingDesign(
            Integer index,
            String title,
            String focus,
            Integer totalMinutes,
            Integer afterClassReviewMinutes,
            String afterClassReview,
            Integer introductionMinutes,
            String introduction,
            List<MainContentBlock> mainContentBlocks,
            Integer summaryMinutes,
            String summary,
            Integer assignmentMinutes,
            List<String> assignments,
            List<String> matchedSlides,
            List<String> remarks
    ) {
    }

    public record GeneratedUnit(
            Integer index,
            String code,
            String name,
            Integer hours,
            Integer teachingDesignCount,
            String environmentDesign,
            String projectName,
            List<String> theoryObjectives,
            List<String> skillObjectives,
            List<String> qualityObjectives,
            List<String> keyPoints,
            List<String> difficultPoints,
            String teachingMethods,
            String teachingOrganization,
            String projectIntroduction,
            List<String> matchedPpts,
            List<TeachingDesign> teachingDesigns,
            List<String> resources,
            List<String> assessments
    ) {
    }

    public record DocumentContent(
            BasicInfo basicInfo,
            String title,
            String teacherRequirements,
            String textbooksAndReferences,
            String otherTeachingResources,
            String courseEnvironment,
            List<GeneratedUnit> units,
            List<Issue> warnings
    ) {
    }

    public record GenerateRequest(
            AnalysisResult analysis,
            String teacherRequirements
    ) {
    }

    public record SaveRequest(
            AnalysisResult analysis,
            DocumentContent content,
            String teacherRequirements,
            String status
    ) {
    }

    public record ReanalyzeRequest(
            String teacherRequirements
    ) {
    }

    public record GenerationError(
            Integer unitIndex,
            Integer lessonIndex,
            String lessonTitle,
            String section,
            String blockTitle,
            Integer actualChars,
            Integer requiredChars,
            String message
    ) {
    }

    public record GenerationJobSummary(
            Long id,
            String status,
            String stage,
            Integer current,
            Integer total,
            String message,
            Long coursePlanId,
            GenerationError error,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record MaterialSummary(
            Long id,
            String role,
            String fileName,
            String fileType,
            Integer sortOrder,
            Long size,
            LocalDateTime createdAt
    ) {
    }

    public record Detail(
            Long id,
            Long userId,
            String title,
            String courseName,
            String status,
            String teacherRequirements,
            AnalysisResult analysis,
            DocumentContent content,
            List<MaterialSummary> materials,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CoursePlanSummary(
            Long id,
            String title,
            String courseName,
            String status,
            LocalDateTime updatedAt,
            String type
    ) {
    }
}
