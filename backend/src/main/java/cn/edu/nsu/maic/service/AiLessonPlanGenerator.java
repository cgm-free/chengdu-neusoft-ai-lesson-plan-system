package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.LessonPlanRequest;

public interface AiLessonPlanGenerator {

    LessonPlanGeneration generate(LessonPlanRequest request);
}

