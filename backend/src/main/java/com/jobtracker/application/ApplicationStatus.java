package com.jobtracker.application;

public enum ApplicationStatus {
    WISHLIST,
    APPLIED,
    INTERVIEW,
    OFFER,
    REJECTED;

    /** Statuses where the user is still waiting on the company, so a follow-up makes sense. */
    public boolean isAwaitingReply() {
        return this == APPLIED || this == INTERVIEW;
    }

    /** Statuses that mean the company has answered. */
    public boolean isResponse() {
        return this == INTERVIEW || this == OFFER || this == REJECTED;
    }
}
