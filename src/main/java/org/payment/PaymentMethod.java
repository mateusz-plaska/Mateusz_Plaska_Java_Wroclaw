package org.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
public class PaymentMethod {
    private final String id;
    private final BigDecimal discountPercent;
    private final BigDecimal limit;
    private BigDecimal usedAmount;

    @JsonCreator
    public PaymentMethod(
            @JsonProperty("id") String id,
            @JsonProperty("discount") BigDecimal discount,
            @JsonProperty("limit") BigDecimal limit) {
        this.id = id;
        this.discountPercent = discount;
        this.limit = limit;
        this.usedAmount = BigDecimal.ZERO;
    }

    public BigDecimal getRemainingLimit() {
        return limit.subtract(usedAmount);
    }

    public void payAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        usedAmount = usedAmount.add(amount);
    }

    public BigDecimal calculateRatioDiscountPercent() {
        return discountPercent.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return "PaymentMethod [id=" + id + ", discountPercent=" + discountPercent + ", limit=" + limit + ", used=" + usedAmount + "]";
    }
}
