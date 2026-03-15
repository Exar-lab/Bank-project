package com.banco.co.envelope.enums;

import lombok.Getter;

@Getter
public enum EnvelopeType {
    SAVINGS("Savings", "General savings"),
    VACATION("Vacation", "Vacation fund"),
    EMERGENCY("Emergency", "Emergency fund"),
    PURCHASE("Purchase", "Save for a purchase"),
    EDUCATION("Education", "Education fund"),
    CUSTOM("Custom", "Custom envelope"),
    INVESTMENT("Investment", "Investment fund"),
    HOME("Home","Home requirements"),
    CAR("Car","Buy a car");
    private final String displayName;
    private final String description;

    EnvelopeType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}

