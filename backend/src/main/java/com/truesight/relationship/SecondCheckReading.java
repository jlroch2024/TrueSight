package com.truesight.relationship;

/**
 * What the second AI says a passage shows about the other company, before it is told what the first AI claimed. Code,
 * not the AI, compares this with the claim, so the AI cannot simply go along with what it is shown.
 */
public enum SecondCheckReading {
    SUPPLIES_THE_WRITER("Reads the Other Way Round."),
    BUYS_FROM_THE_WRITER("Reads the Other Way Round."),
    BOTH("Reads the Other Way Round."),
    COMPETITOR("Reads as a Competitor."),
    ENDED("Reads as Ended."),
    PLANNED("Reads as Only Planned."),
    OTHER_TIE("Reads as Another Kind of Tie."),
    NOT_STATED("Reads as Not Stated.");

    /** How a doubt from this reading opens, ahead of the AI's own sentence. */
    private final String doubt;

    SecondCheckReading(String doubt) {
        this.doubt = doubt;
    }

    /** Agrees only when this reading is what a relationship of this type claims. */
    public SecondCheckVerdict verdictOn(RelationshipType claim) {
        boolean same = this == BOTH
                || (this == SUPPLIES_THE_WRITER && claim == RelationshipType.SUPPLIER)
                || (this == BUYS_FROM_THE_WRITER && claim == RelationshipType.CUSTOMER);
        return same ? SecondCheckVerdict.AGREES : SecondCheckVerdict.DOUBTS;
    }

    /** The reason an analyst reads: the AI's sentence, opened by what kind of doubt it is. */
    public String reasonOn(RelationshipType claim, String sentence) {
        return verdictOn(claim) == SecondCheckVerdict.AGREES ? sentence : doubt + " " + sentence;
    }
}
