package com.banco.co.outbox.enums;

public enum KafkaTopic {
    ACCOUNT_EVENTS("banco.account.events"),
    TRANSACTION_EVENTS("banco.transaction.events"),
    ENVELOPE_EVENTS("banco.envelope.events"),
    USER_EVENTS("banco.user.events"),
    CARD_EVENTS("banco.card.events");

    private final String topicName;

    KafkaTopic(String topicName) {
        this.topicName = topicName;
    }

    public String getTopicName() {
        return topicName;
    }
}
