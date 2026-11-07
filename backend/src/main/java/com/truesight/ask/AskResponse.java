package com.truesight.ask;

import java.util.List;

/**
 * The answer, and the items of the portfolio it rests on. {@code id} is the item's own id: a relationship's id, a
 * holding's id, or, for an alert event, its lead alert's id (the website finds the event whose alerts include it).
 * {@code label} is the words the source button shows. A refusal ("TrueSight's data does not answer that.") has none.
 */
public record AskResponse(String answer, List<Source> sources) {

    public record Source(Kind kind, Long id, String label) {
    }

    public enum Kind {
        RELATIONSHIP,
        ALERT,
        HOLDING
    }
}
