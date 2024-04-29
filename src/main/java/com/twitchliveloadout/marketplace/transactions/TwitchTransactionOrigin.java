package com.twitchliveloadout.marketplace.transactions;

import lombok.Getter;

@Getter
public enum TwitchTransactionOrigin {
    EBS("ebs"),
    EVENT_SUB("event_sub"),
    MANUAL("manual"),
    TEST("test"),
    CHARITY("charity"),
    ;

    final String type;

    TwitchTransactionOrigin(String type) {
        this.type = type;
    }
}
