package com.truesight.ai;

/** How a call to Gemini ended: an answer, an answer that could not be read, or no answer at all. */
public enum AiOutcome {
    ANSWERED,
    UNREADABLE,
    FAILED
}
