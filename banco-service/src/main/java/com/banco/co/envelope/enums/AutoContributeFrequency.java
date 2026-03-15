package com.banco.co.envelope.enums;

import lombok.Getter;

@Getter
public enum AutoContributeFrequency {
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    MONTHLY("Monthly", 30),
    BIWEEKLY("BiWeekly", 14);

    private final String displayName;
    private final int days;

    AutoContributeFrequency(String displayName, int days) {
        this.displayName = displayName;
        this.days = days;
    }

}