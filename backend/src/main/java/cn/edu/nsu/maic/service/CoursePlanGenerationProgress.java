package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;

@FunctionalInterface
public interface CoursePlanGenerationProgress {

    CoursePlanGenerationProgress NOOP = (stage, current, total, message) -> {
    };

    void update(String stage, int current, int total, String message);

    default void snapshot(CoursePlanDtos.AnalysisResult analysis, CoursePlanDtos.DocumentContent content) {
    }

    default void assertNotCancelled() {
    }
}
