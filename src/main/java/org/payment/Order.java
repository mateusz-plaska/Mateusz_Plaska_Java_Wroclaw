package org.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public record Order(String id, BigDecimal amountToPay, List<String> promotions) {

    @JsonCreator
    public Order(
            @JsonProperty("id") String id,
            @JsonProperty("value") BigDecimal amountToPay,
            @JsonProperty("promotions") List<String> promotions) {
        this.id = id;
        this.amountToPay = amountToPay;
        this.promotions = promotions == null ? Collections.emptyList() : promotions;
    }
}
