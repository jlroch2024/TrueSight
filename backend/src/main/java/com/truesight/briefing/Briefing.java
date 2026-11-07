package com.truesight.briefing;

/** A finished briefing: the file's name and its bytes. */
public record Briefing(String filename, byte[] pdf) {
}
