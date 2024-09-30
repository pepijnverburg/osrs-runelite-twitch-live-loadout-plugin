package com.twitchliveloadout.marketplace.transactions;

import lombok.Getter;

@Getter
public enum TwitchTransactionProductType {
    BITS_IN_EXTENSION("BITS_IN_EXTENSION"),
    TEST("TEST"),
    FREE("FREE"),
    MANUAL("MANUAL"),
    ;

    final String type;

    TwitchTransactionProductType(String type) {
        this.type = type;
    }
}
