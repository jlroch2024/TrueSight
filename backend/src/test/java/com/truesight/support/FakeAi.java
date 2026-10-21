package com.truesight.support;

import com.truesight.ai.AiAnswer;

/** For tests that replace GeminiClient with a fake: wraps the answer text a fake returns. */
public final class FakeAi {

    private FakeAi() {
    }

    /** Gemini's answer, with no record of the call (the fake never records one). */
    public static AiAnswer answer(String text) {
        return new AiAnswer(text, null);
    }
}
