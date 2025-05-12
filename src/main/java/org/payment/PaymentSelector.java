package org.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public class PaymentSelector {
    private static final BigDecimal TEN_PERCENT = BigDecimal.valueOf(0.1);
    private static final BigDecimal NINETY_PERCENT = BigDecimal.valueOf(0.9);
    private static final String POINTS_ID = "PUNKTY";

    private final List<Order> orders;
    private final List<PaymentMethod> paymentMethods;
    private final Map<String, PaymentMethod> methodMap;
    private final Set<String> paidOrders;

    public PaymentSelector(List<Order> orders, List<PaymentMethod> methods) {
        this.orders = orders;
        this.paymentMethods = methods;
        this.methodMap = methods.stream().collect(Collectors.toMap(PaymentMethod::getId, pm -> pm));
        this.paidOrders = new HashSet<>();
    }

    public void runSelector() {
        PaymentMethod pointsPayment = methodMap.get(POINTS_ID);

        try {
            payGreedily(pointsPayment);
            assignFullCardNoDiscount();
            payForUnpaidOrders(pointsPayment);
        } catch (Exception ex) {
            System.err.println("ERROR during optimized payment: " + ex.getMessage());
            System.err.println("Fallback to no-discount payment for ALL orders");
            clearUsage();
            payAllNoDiscount(pointsPayment);
        }

        printResults();
    }

    private void payGreedily(PaymentMethod pointsPayment) {
        List<Candidate> candidates = generateAllPossibleCandidates(pointsPayment);
        candidates.sort(Comparator
                .comparing(Candidate::getRatioDiscountPercent)
                .thenComparing(Candidate::getDiscountAmount).reversed());

        for(Candidate candidate : candidates) {
            String orderId = candidate.getOrder().id();
            if (!paidOrders.contains(orderId) && candidate.canAfford(pointsPayment)) {
                candidate.payForOrder(pointsPayment);
                paidOrders.add(orderId);
            }
        }
    }

    private void assignFullCardNoDiscount() {
        orders.stream()
                .filter(order -> !paidOrders.contains(order.id()))
                .sorted(Comparator.comparing(Order::amountToPay).reversed())
                .forEach(order -> {
                    BigDecimal totalAmountToPay = order.amountToPay();
                    findMinFitCard(totalAmountToPay).ifPresent(card -> {
                        card.payAmount(totalAmountToPay);
                        paidOrders.add(order.id());
                    });
                });
    }

    private void payForUnpaidOrders(PaymentMethod pointsMethod) {
        orders.stream()
                .filter(order -> !paidOrders.contains(order.id()))
                .sorted(Comparator.comparing(Order::amountToPay).reversed())
                .forEach(order -> payOrderFallbackPartial(order, pointsMethod));
    }

    private void payOrderFallbackPartial(Order order, PaymentMethod pointsPayment) {
        BigDecimal amountToPay = order.amountToPay();
        PaymentMethod cardPayment = findMaxLimitCardBelow(amountToPay)
                .orElseThrow(() -> new IllegalStateException("No possible card found for " + order.id() + " with limit < " + amountToPay));;
        BigDecimal cardRemainingLimit = cardPayment.getRemainingLimit();
        BigDecimal requiredPoints = amountToPay.subtract(cardRemainingLimit);

        if (requiredPoints.compareTo(pointsPayment.getRemainingLimit()) > 0) {
            throw new IllegalStateException("Failed to pay for order" + order.id()
                            + ": required points =" + requiredPoints
                            + ", available=" + pointsPayment.getRemainingLimit()
            );
        }

        cardPayment.payAmount(cardRemainingLimit);
        pointsPayment.payAmount(requiredPoints);
        paidOrders.add(order.id());
    }

    private void payAllNoDiscount(PaymentMethod pointsMethod) {
        for (Order order : orders) {
            BigDecimal amountToPay = order.amountToPay();
            BigDecimal usePoints = pointsMethod.getRemainingLimit().min(amountToPay);
            if (usePoints.signum() > 0) {
                pointsMethod.payAmount(usePoints);
                amountToPay = amountToPay.subtract(usePoints);
            }

            if (amountToPay.signum() > 0) {
                PaymentMethod card = findMinFitCard(amountToPay)
                        .orElseThrow(() -> new IllegalStateException("No card available to pay for " + order.id()));
                card.payAmount(amountToPay);
            }
            paidOrders.add(order.id());
        }
    }

    private List<Candidate> generateAllPossibleCandidates(PaymentMethod pointsPayment) {
        List<Candidate> candidates = new ArrayList<>();
        for(Order order : orders) {
            BigDecimal amountToPay = order.amountToPay();
            for(String promotionId : order.promotions()) {
                PaymentMethod paymentMethod = methodMap.get(promotionId);
                if (isCardMeetsConditions(paymentMethod, amountToPay)) {
                    addCandidate(candidates, order, paymentMethod, BigDecimal.ZERO, amountToPay, paymentMethod.calculateRatioDiscountPercent());
                }
            }

            if(isPaymentMeetsLimitConditions(pointsPayment, amountToPay)) {
                addCandidate(candidates, order, pointsPayment, amountToPay, BigDecimal.ZERO, pointsPayment.calculateRatioDiscountPercent());
            }

            BigDecimal pointsAmount = amountToPay.multiply(TEN_PERCENT);
            BigDecimal cardAmount = amountToPay.multiply(NINETY_PERCENT);
            for(PaymentMethod paymentMethod : paymentMethods) {
                BigDecimal cardAmountAfterDiscount = cardAmount.subtract(pointsAmount);
                if (!isInvalidCardMethod(paymentMethod)
                        && pointsPayment.getLimit().compareTo(pointsAmount) >= 0
                        && paymentMethod.getLimit().compareTo(cardAmountAfterDiscount) >= 0) {
                    addCandidate(candidates, order, paymentMethod, pointsAmount, cardAmount, TEN_PERCENT);
                }
            }
        }

        return candidates;
    }

    private void addCandidate(List<Candidate> candidates, Order order, PaymentMethod paymentMethod,
                              BigDecimal requiredPointsAmount, BigDecimal requiredCardAmount, BigDecimal ratioDiscountPercent) {
        BigDecimal discountAmount = order.amountToPay().multiply(ratioDiscountPercent);
        candidates.add(new Candidate(order, paymentMethod, requiredPointsAmount, requiredCardAmount,
                discountAmount, ratioDiscountPercent));
    }

    private boolean isCardMeetsConditions(PaymentMethod paymentMethod, BigDecimal amountToPay) {
        if (isInvalidCardMethod(paymentMethod)) {
            return false;
        }
        return isPaymentMeetsLimitConditions(paymentMethod, amountToPay);
    }

    private boolean isPaymentMeetsLimitConditions(PaymentMethod paymentMethod, BigDecimal amountToPay) {
        BigDecimal discountAmount = amountToPay.multiply(paymentMethod.calculateRatioDiscountPercent());
        BigDecimal amountToPayAfterDiscount = amountToPay.subtract(discountAmount);
        return paymentMethod.getLimit().compareTo(amountToPayAfterDiscount) >= 0;
    }

    private boolean isInvalidCardMethod(PaymentMethod method) {
        return method == null || method.getId().equals(POINTS_ID);
    }

    private Optional<PaymentMethod> findMinFitCard(BigDecimal amountToPay) {
        return paymentMethods.stream()
                .filter(m -> !m.getId().equals(POINTS_ID))
                .filter(m -> m.getRemainingLimit().compareTo(amountToPay) >= 0)
                .min(Comparator.comparing(m -> m.getRemainingLimit().subtract(amountToPay)));
    }

    private Optional<PaymentMethod> findMaxLimitCardBelow(BigDecimal amountToPay) {
        return paymentMethods.stream()
                .filter(m -> !m.getId().equals(POINTS_ID))
                .filter(m -> m.getRemainingLimit().compareTo(amountToPay) < 0)
                .max(Comparator.comparing(PaymentMethod::getRemainingLimit));
    }

    private void clearUsage() {
        paidOrders.clear();
        paymentMethods.forEach(m -> m.setUsedAmount(BigDecimal.ZERO));
    }

    private void printResults() {
        for (PaymentMethod method : paymentMethods) {
            System.out.println(method.getId() + " " + method.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
        }
    }
}
