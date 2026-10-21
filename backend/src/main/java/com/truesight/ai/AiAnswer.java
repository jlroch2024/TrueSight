package com.truesight.ai;

/**
 * Gemini's answer, unread, and the id of the {@link AiCall} that records it. The caller passes {@code callId} to
 * {@link AiCallLog#unreadable} if it cannot read the answer, or to {@link AiCallLog#finish} with what it kept.
 */
public record AiAnswer(String text, Long callId) {
}
