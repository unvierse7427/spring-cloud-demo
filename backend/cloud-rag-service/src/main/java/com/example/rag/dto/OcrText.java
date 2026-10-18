package com.example.rag.dto;

/**
 * 图片文字识别结果，供 OCR 工具直接返回给前端
 */
public class OcrText {

    /** 识别出的文本，按行以 \n 拼接 */
    private String text;

    /** 识别出的文字行数 */
    private int lineCount;

    /** 各行的平均置信度，取值 0~1 */
    private double avgScore;

    /** OCR 服务端识别耗时（毫秒） */
    private long elapsedMs;

    /** 按坐标重建的文本，表格区域已渲染为 Markdown 表格 */
    private String markdown;

    /** 是否在图片中识别出表格 */
    private boolean tableDetected;

    /** 识别出的表格行数，未识别出表格时为 0 */
    private int tableRows;

    /** 识别出的表格列数，未识别出表格时为 0 */
    private int tableCols;

    public static OcrText of(String text, int lineCount, double avgScore, long elapsedMs) {
        OcrText result = new OcrText();
        result.setText(text);
        result.setLineCount(lineCount);
        result.setAvgScore(avgScore);
        result.setElapsedMs(elapsedMs);
        return result;
    }

    /** 入库时使用：识别出表格则用 Markdown，否则沿用扁平文本 */
    public String preferredText() {
        return tableDetected && markdown != null && !markdown.isBlank() ? markdown : text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public double getAvgScore() {
        return avgScore;
    }

    public void setAvgScore(double avgScore) {
        this.avgScore = avgScore;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public boolean isTableDetected() {
        return tableDetected;
    }

    public void setTableDetected(boolean tableDetected) {
        this.tableDetected = tableDetected;
    }

    public int getTableRows() {
        return tableRows;
    }

    public void setTableRows(int tableRows) {
        this.tableRows = tableRows;
    }

    public int getTableCols() {
        return tableCols;
    }

    public void setTableCols(int tableCols) {
        this.tableCols = tableCols;
    }
}
