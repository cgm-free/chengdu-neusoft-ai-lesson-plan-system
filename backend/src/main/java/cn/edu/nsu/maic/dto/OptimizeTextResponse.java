package cn.edu.nsu.maic.dto;

public class OptimizeTextResponse {

    private String text;

    public OptimizeTextResponse() {
    }

    public OptimizeTextResponse(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}

