package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;

public class CoursePlanGenerationException extends IllegalStateException {

    private final CoursePlanDtos.GenerationError error;

    public CoursePlanGenerationException(CoursePlanDtos.GenerationError error) {
        super(error == null ? "课程教案生成失败" : error.message());
        this.error = error;
    }

    public CoursePlanDtos.GenerationError error() {
        return error;
    }
}
