package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class CoursePlanSchedulePlannerService {

    public static final String STRATEGY_FIXED_TWO_HOURS = "FIXED_TWO_HOURS";
    public static final String STRATEGY_FLEXIBLE_HOURS = "FLEXIBLE_HOURS";

    public PlannedResult plan(
            List<CoursePlanDtos.UnitAnalysis> units,
            CoursePlanDtos.TeachingCalendar teachingCalendar,
            Integer totalHours
    ) {
        List<CoursePlanDtos.UnitAnalysis> sourceUnits = units == null ? List.of() : units;
        List<CoursePlanDtos.TeachingCalendarEntry> calendarEntries = semanticCalendarEntries(teachingCalendar);
        String splitStrategy = determineSplitStrategy(sourceUnits, calendarEntries);
        List<CoursePlanDtos.Issue> conflicts = new ArrayList<>();
        List<CoursePlanDtos.UnitAnalysis> plannedUnits = STRATEGY_FIXED_TWO_HOURS.equals(splitStrategy)
                ? planFixedTwoHours(sourceUnits, calendarEntries, conflicts)
                : planFlexibleHours(sourceUnits, calendarEntries, totalHours, conflicts);
        return new PlannedResult(splitStrategy, plannedUnits, deduplicateIssues(conflicts));
    }

    private String determineSplitStrategy(
            List<CoursePlanDtos.UnitAnalysis> units,
            List<CoursePlanDtos.TeachingCalendarEntry> calendarEntries
    ) {
        boolean allEven = units.stream()
                .map(CoursePlanDtos.UnitAnalysis::hours)
                .filter(Objects::nonNull)
                .allMatch(hours -> hours > 0 && hours % 2 == 0);
        int dominantPeriodCount = dominantPeriodCount(calendarEntries);
        if (allEven && (dominantPeriodCount <= 0 || dominantPeriodCount == 2)) {
            return STRATEGY_FIXED_TWO_HOURS;
        }
        return STRATEGY_FLEXIBLE_HOURS;
    }

    private List<CoursePlanDtos.UnitAnalysis> planFixedTwoHours(
            List<CoursePlanDtos.UnitAnalysis> units,
            List<CoursePlanDtos.TeachingCalendarEntry> calendarEntries,
            List<CoursePlanDtos.Issue> conflicts
    ) {
        int calendarCursor = 0;
        List<CoursePlanDtos.UnitAnalysis> planned = new ArrayList<>();
        for (CoursePlanDtos.UnitAnalysis unit : units) {
            Integer hours = positive(unit.hours());
            List<CoursePlanDtos.Issue> issues = new ArrayList<>(baseIssues(unit));
            List<Integer> teachingDesignHours = new ArrayList<>();
            if (hours == null) {
                issues.add(issue("unit.hoursMissing", "error", "单元“" + text(unit.name()) + "”未识别到有效学时。"));
            } else if (hours % 2 != 0) {
                issues.add(issue("unit.hoursNotDivisible", "error", "单元“" + text(unit.name()) + "”学时为 " + hours + "，无法按 2 学时拆分教学设计。"));
            } else {
                for (int cursor = 0; cursor < hours; cursor += 2) {
                    teachingDesignHours.add(2);
                }
            }

            List<CoursePlanDtos.TeachingCalendarEntry> unitEntries = List.of();
            String weekRange = "";
            if (!calendarEntries.isEmpty() && !teachingDesignHours.isEmpty()) {
                int end = Math.min(calendarEntries.size(), calendarCursor + teachingDesignHours.size());
                unitEntries = new ArrayList<>(calendarEntries.subList(calendarCursor, end));
                calendarCursor = end;
                weekRange = buildWeekRange(unitEntries);
            }

            planned.add(rebuildUnit(
                    unit,
                    teachingDesignHours.size(),
                    teachingDesignHours,
                    unitEntries,
                    weekRange,
                    issues
            ));
        }

        int expectedCount = planned.stream()
                .map(CoursePlanDtos.UnitAnalysis::teachingDesignCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (!calendarEntries.isEmpty() && expectedCount > 0 && calendarEntries.size() != expectedCount) {
            conflicts.add(issue(
                    "teachingCalendar.countMismatch",
                    "error",
                    "教学日历识别到 " + calendarEntries.size() + " 次课，与课程标准按 2 学时拆分得到的教学设计数 " + expectedCount + " 不一致。"
            ));
        }
        return planned;
    }

    private List<CoursePlanDtos.UnitAnalysis> planFlexibleHours(
            List<CoursePlanDtos.UnitAnalysis> units,
            List<CoursePlanDtos.TeachingCalendarEntry> calendarEntries,
            Integer totalHours,
            List<CoursePlanDtos.Issue> conflicts
    ) {
        int preferredHours = dominantPeriodCount(calendarEntries);
        if (preferredHours <= 0) {
            preferredHours = 3;
        }
        List<Coverage> coverages = buildCalendarCoverages(units, calendarEntries, preferredHours);
        List<CoursePlanDtos.UnitAnalysis> planned = new ArrayList<>();
        int calendarHours = calendarEntries.stream()
                .map(CoursePlanDtos.TeachingCalendarEntry::periodCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (!calendarEntries.isEmpty() && positive(totalHours) != null && calendarHours != totalHours) {
            conflicts.add(issue(
                    "teachingCalendar.hoursMismatch",
                    "error",
                    "教学日历累计学时为 " + calendarHours + "，与课程总学时 " + totalHours + " 不一致。"
            ));
        }

        for (int index = 0; index < units.size(); index++) {
            CoursePlanDtos.UnitAnalysis unit = units.get(index);
            Coverage coverage = index < coverages.size() ? coverages.get(index) : Coverage.empty();
            Integer hours = positive(unit.hours());
            List<CoursePlanDtos.Issue> issues = new ArrayList<>(baseIssues(unit));
            List<Integer> teachingDesignHours = coverage.entries().isEmpty()
                    ? smartSplitHours(hours, preferredHours)
                    : coverage.entries().stream()
                    .map(CoursePlanDtos.TeachingCalendarEntry::allocatedHours)
                    .filter(value -> value != null && value > 0)
                    .toList();
            if (hours == null) {
                issues.add(issue("unit.hoursMissing", "error", "单元“" + text(unit.name()) + "”未识别到有效学时。"));
            } else if (teachingDesignHours.stream().mapToInt(Integer::intValue).sum() != hours) {
                issues.add(issue("unit.splitPlanInvalid", "error", "单元“" + text(unit.name()) + "”无法生成有效的教学设计学时分配。"));
            }
            if (!calendarEntries.isEmpty() && hours != null && coverage.allocatedHours() > 0 && coverage.allocatedHours() != hours) {
                issues.add(issue(
                        "unit.calendarHoursMismatch",
                        "error",
                        "单元“" + text(unit.name()) + "”从教学日历分配到 " + coverage.allocatedHours() + " 学时，与单元学时 " + hours + " 不一致。"
                ));
            }

            planned.add(rebuildUnit(
                    unit,
                    teachingDesignHours.size(),
                    teachingDesignHours,
                    coverage.entries(),
                    coverage.weekRange(),
                    issues
            ));
        }
        return planned;
    }

    private List<Coverage> buildCalendarCoverages(
            List<CoursePlanDtos.UnitAnalysis> units,
            List<CoursePlanDtos.TeachingCalendarEntry> calendarEntries,
            int preferredHours
    ) {
        if (calendarEntries.isEmpty()) {
            return units.stream().map(unit -> Coverage.empty()).toList();
        }
        List<Coverage> result = new ArrayList<>();
        int cursor = 0;
        int remainingHours = periodCountOrDefault(calendarEntries.get(0), preferredHours);
        for (CoursePlanDtos.UnitAnalysis unit : units) {
            Integer requiredHours = positive(unit.hours());
            if (requiredHours == null) {
                result.add(Coverage.empty());
                continue;
            }
            int remainingUnitHours = requiredHours;
            List<CoursePlanDtos.TeachingCalendarEntry> unitEntries = new ArrayList<>();
            int allocatedHours = 0;
            while (remainingUnitHours > 0 && cursor < calendarEntries.size()) {
                CoursePlanDtos.TeachingCalendarEntry entry = calendarEntries.get(cursor);
                int currentEntryHours = periodCountOrDefault(entry, preferredHours);
                if (remainingHours <= 0) {
                    remainingHours = currentEntryHours;
                }
                int allocated = Math.min(remainingUnitHours, remainingHours);
                if (allocated <= 0) {
                    cursor++;
                    if (cursor < calendarEntries.size()) {
                        remainingHours = periodCountOrDefault(calendarEntries.get(cursor), preferredHours);
                    }
                    continue;
                }
                unitEntries.add(copyEntry(entry, allocated));
                remainingUnitHours -= allocated;
                allocatedHours += allocated;
                remainingHours -= allocated;
                if (remainingHours <= 0) {
                    cursor++;
                    if (cursor < calendarEntries.size()) {
                        remainingHours = periodCountOrDefault(calendarEntries.get(cursor), preferredHours);
                    }
                }
            }
            result.add(new Coverage(unitEntries, buildWeekRange(unitEntries), allocatedHours));
        }
        return result;
    }

    private CoursePlanDtos.TeachingCalendarEntry copyEntry(CoursePlanDtos.TeachingCalendarEntry entry, int allocatedHours) {
        return new CoursePlanDtos.TeachingCalendarEntry(
                text(entry.week()),
                text(entry.session()),
                entry.periodCount(),
                text(entry.lessonType()),
                text(entry.topic()),
                text(entry.rawText()),
                allocatedHours
        );
    }

    private CoursePlanDtos.UnitAnalysis rebuildUnit(
            CoursePlanDtos.UnitAnalysis unit,
            int teachingDesignCount,
            List<Integer> teachingDesignHours,
            List<CoursePlanDtos.TeachingCalendarEntry> unitEntries,
            String weekRange,
            List<CoursePlanDtos.Issue> issues
    ) {
        String status = issues.stream().anyMatch(item -> "error".equalsIgnoreCase(item.level())) ? "blocked" : "ready";
        return new CoursePlanDtos.UnitAnalysis(
                unit.index(),
                unit.code(),
                unit.name(),
                unit.hours(),
                teachingDesignCount,
                List.copyOf(teachingDesignHours),
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
                unitEntries,
                weekRange,
                status,
                deduplicateIssues(issues)
        );
    }

    private List<CoursePlanDtos.Issue> baseIssues(CoursePlanDtos.UnitAnalysis unit) {
        return safeList(unit.issues()).stream()
                .filter(item -> item != null && !SCHEDULE_ISSUE_CODES.contains(text(item.code())))
                .toList();
    }

    private int dominantPeriodCount(List<CoursePlanDtos.TeachingCalendarEntry> entries) {
        return entries.stream()
                .map(CoursePlanDtos.TeachingCalendarEntry::periodCount)
                .filter(value -> value != null && value > 0)
                .collect(java.util.stream.Collectors.groupingBy(
                        Integer::intValue,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Comparator.<java.util.Map.Entry<Integer, Long>>comparingLong(java.util.Map.Entry::getValue)
                        .thenComparingInt(java.util.Map.Entry::getKey))
                .map(java.util.Map.Entry::getKey)
                .orElse(0);
    }

    private List<Integer> smartSplitHours(Integer hours, int preferredHours) {
        if (hours == null || hours <= 0) {
            return List.of();
        }
        List<Integer> candidates = new ArrayList<>(new LinkedHashSet<>(List.of(preferredHours, 3, 2, 1)));
        candidates.removeIf(value -> value == null || value <= 0);
        SplitChoice choice = chooseSplit(hours, candidates, new ArrayList<>());
        return choice == null ? List.of(hours) : choice.parts();
    }

    private SplitChoice chooseSplit(int remainingHours, List<Integer> candidates, List<Integer> current) {
        if (remainingHours == 0) {
            return new SplitChoice(List.copyOf(current));
        }
        SplitChoice best = null;
        for (Integer candidate : candidates) {
            if (candidate == null || candidate <= 0 || candidate > remainingHours) {
                continue;
            }
            current.add(candidate);
            SplitChoice choice = chooseSplit(remainingHours - candidate, candidates, current);
            current.remove(current.size() - 1);
            if (choice == null) {
                continue;
            }
            if (best == null || choice.betterThan(best, candidates.get(0))) {
                best = choice;
            }
        }
        return best;
    }

    private String buildWeekRange(List<CoursePlanDtos.TeachingCalendarEntry> entries) {
        List<String> weeks = entries.stream()
                .map(CoursePlanDtos.TeachingCalendarEntry::week)
                .map(this::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (weeks.isEmpty()) {
            return "";
        }
        if (weeks.size() == 1) {
            return weeks.get(0);
        }
        return weeks.get(0) + "-" + weeks.get(weeks.size() - 1);
    }

    private List<CoursePlanDtos.TeachingCalendarEntry> semanticCalendarEntries(CoursePlanDtos.TeachingCalendar teachingCalendar) {
        if (teachingCalendar == null || teachingCalendar.entries() == null) {
            return List.of();
        }
        return teachingCalendar.entries().stream()
                .filter(entry -> entry != null && !text(entry.topic()).isBlank())
                .toList();
    }

    private Integer positive(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private int periodCountOrDefault(CoursePlanDtos.TeachingCalendarEntry entry, int defaultValue) {
        Integer periodCount = entry == null ? null : entry.periodCount();
        if (periodCount != null && periodCount > 0) {
            return periodCount;
        }
        Integer allocatedHours = entry == null ? null : entry.allocatedHours();
        if (allocatedHours != null && allocatedHours > 0) {
            return allocatedHours;
        }
        return defaultValue > 0 ? defaultValue : 2;
    }

    private CoursePlanDtos.Issue issue(String code, String level, String message) {
        return new CoursePlanDtos.Issue(code, level, message);
    }

    private List<CoursePlanDtos.Issue> deduplicateIssues(List<CoursePlanDtos.Issue> issues) {
        List<CoursePlanDtos.Issue> safe = safeList(issues);
        List<CoursePlanDtos.Issue> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (CoursePlanDtos.Issue issue : safe) {
            if (issue == null) {
                continue;
            }
            String key = text(issue.code()) + "|" + text(issue.message());
            if (seen.add(key)) {
                result.add(issue);
            }
        }
        return result;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record PlannedResult(
            String splitStrategy,
            List<CoursePlanDtos.UnitAnalysis> units,
            List<CoursePlanDtos.Issue> conflicts
    ) {
    }

    private record Coverage(
            List<CoursePlanDtos.TeachingCalendarEntry> entries,
            String weekRange,
            int allocatedHours
    ) {
        private static Coverage empty() {
            return new Coverage(List.of(), "", 0);
        }
    }

    private record SplitChoice(List<Integer> parts) {
        private boolean betterThan(SplitChoice other, int preferredHours) {
            if (parts.size() != other.parts.size()) {
                return parts.size() < other.parts.size();
            }
            int currentOnes = oneHourCount(parts);
            int otherOnes = oneHourCount(other.parts);
            if (currentOnes != otherOnes) {
                return currentOnes < otherOnes;
            }
            int currentPreferredScore = preferredScore(parts, preferredHours);
            int otherPreferredScore = preferredScore(other.parts, preferredHours);
            if (currentPreferredScore != otherPreferredScore) {
                return currentPreferredScore > otherPreferredScore;
            }
            return lexicalSignature(parts).compareTo(lexicalSignature(other.parts)) < 0;
        }

        private int oneHourCount(List<Integer> values) {
            return (int) values.stream().filter(value -> value == 1).count();
        }

        private int preferredScore(List<Integer> values, int preferredHours) {
            return (int) values.stream().filter(value -> value == preferredHours).count();
        }

        private String lexicalSignature(List<Integer> values) {
            return values.stream()
                    .map(value -> String.format(Locale.ROOT, "%02d", value))
                    .reduce((left, right) -> left + "-" + right)
                    .orElse("");
        }
    }

    private static final List<String> SCHEDULE_ISSUE_CODES = List.of(
            "unit.hoursMissing",
            "unit.hoursNotDivisible",
            "unit.splitPlanInvalid",
            "unit.calendarHoursMismatch"
    );
}
