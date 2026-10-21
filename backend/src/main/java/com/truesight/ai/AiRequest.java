package com.truesight.ai;

/**
 * One question for Gemini.
 *
 * @param purpose        why it is asked; every call names one, so the audit trail can tell them apart
 * @param prompt         exactly what Gemini is sent
 * @param reportId       the report the prompt quotes, or null
 * @param recordedPrompt what the audit trail keeps: the prompt with any report text replaced by
 *                       {@link #reportMarker}, so a report is never copied into the record
 */
public record AiRequest(AiPurpose purpose, String prompt, Long reportId, String recordedPrompt) {

    /** A request whose prompt quotes no report, so it is recorded as it is. */
    public static AiRequest of(AiPurpose purpose, String prompt) {
        return new AiRequest(purpose, prompt, null, prompt);
    }

    /** What stands in for a report's text in a recorded prompt: {@code [report 12: 90628 characters]}. */
    public static String reportMarker(Long reportId, String reportText) {
        return "[report %d: %d characters]".formatted(reportId, reportText.length());
    }
}
