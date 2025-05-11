package org.payment;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class Candidate {
    private final Order order;
    private final PaymentMethod paymentMethod;
    private BigDecimal usePoints;
    private BigDecimal useCardAmount;
    private final BigDecimal discountAmount;
    private final BigDecimal ratioDiscountPercent;

    public Candidate(Order order, PaymentMethod paymentMethod, BigDecimal requiredPoints,
                     BigDecimal requiredCardAmount, BigDecimal discountAmount, BigDecimal ratioDiscountPercent) {
        this.order = order;
        this.paymentMethod = paymentMethod;
        this.discountAmount = discountAmount;
        this.ratioDiscountPercent = ratioDiscountPercent;
        this.usePoints = requiredPoints;
        this.useCardAmount = requiredCardAmount;
        initRequiredPaymentAmountAfterDiscount();
    }

    public void payForOrder(PaymentMethod pointsPayment) {
        if (usePoints.compareTo(BigDecimal.ZERO) > 0) {
            pointsPayment.payAmount(usePoints);
        }
        if (useCardAmount.compareTo(BigDecimal.ZERO) > 0) {
            paymentMethod.payAmount(useCardAmount);
        }
    }

    public boolean canAfford(PaymentMethod pointsMethod) {
        return isPointsEnough(pointsMethod) && isCardEnough();
    }

    private boolean isPointsEnough(PaymentMethod pointsMethod) {
        return usePoints.compareTo(pointsMethod.getRemainingLimit()) <= 0;
    }

    private boolean isCardEnough() {
        return useCardAmount.compareTo(paymentMethod.getRemainingLimit()) <= 0;
    }

    private boolean isFullyPaidWithPoints() {
        return useCardAmount.compareTo(BigDecimal.ZERO) == 0;
    }

    private void initRequiredPaymentAmountAfterDiscount() {
        if (isFullyPaidWithPoints()) {
            usePoints = usePoints.subtract(discountAmount);
        } else {
            useCardAmount = useCardAmount.subtract(discountAmount);
        }
    }

    @Override
    public String toString() {
        return "Candidate [order=" + order + ", paymentMethod=" + paymentMethod + ", usePoints=" + usePoints +
                ", useCardAmount=" + useCardAmount + ", discountAmount=" + discountAmount +
                ", ratioDiscountPercent=" + ratioDiscountPercent + "]";
    }
}
