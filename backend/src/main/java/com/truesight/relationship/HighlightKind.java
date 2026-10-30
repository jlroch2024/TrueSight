package com.truesight.relationship;

/** What a highlighted phrase in a relationship's evidence is about. */
public enum HighlightKind {
    /** A company's name. */
    COMPANY,
    /** What is supplied. */
    SUPPLIED,
    /** How much the company relies on the other. */
    RELIANCE,
    /** A number, such as a share of sales. */
    FIGURE
}
