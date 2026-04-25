package cn.edu.nsu.maic.service;

public class LessonPlanGeneration {

    private final String provider;
    private final String modelName;
    private final String prompt;
    private final String contentJson;
    private final long durationMs;

    public LessonPlanGeneration(String provider, String modelName, String prompt, String contentJson, long durationMs) {
        this.provider = provider;
        this.modelName = modelName;
        this.prompt = prompt;
        this.contentJson = contentJson;
        this.durationMs = durationMs;
    }

    public String getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getContentJson() {
        return contentJson;
    }

    public long getDurationMs() {
        return durationMs;
    }
}

