package org.payment;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class PaymentSelector {
    private final List<Order> orders;
    private final List<PaymentMethod> paymentMethods;
    private final Map<String, PaymentMethod> methodMap;

    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.1");
    private static final BigDecimal NINETY_PERCENT = new BigDecimal("0.9");
    private static final String POINTS_ID = "PUNKTY";

    public PaymentSelector(List<Order> orders, List<PaymentMethod> methods) {
        this.orders = orders;
        this.paymentMethods = methods;
        this.methodMap = methods.stream().collect(Collectors.toMap(PaymentMethod::getId, pm -> pm));
    }

    public void runSelector() {
        PaymentMethod pointsPayment = methodMap.get(POINTS_ID);
        Set<String> paidOrders = new HashSet<>();

        try {
            payGreedily(pointsPayment, paidOrders);

            assignFullCardNoDiscount(paidOrders);

            payForUnpaidOrders(pointsPayment, paidOrders);
        } catch (Exception ex) {
            System.err.println("ERROR during optimized payment: " + ex.getMessage());
            System.err.println("Falling back to no-discount payment for ALL orders");

            paidOrders.clear();
            paymentMethods.forEach(p -> p.setUsedAmount(BigDecimal.ZERO));
            payAllNoDiscount(pointsPayment, paidOrders);
        }

        printResults(paidOrders);
    }

    /**
     * Jeśli cokolwiek zawiedzie, opłaca wszystkie zamówienia BEZ ŻADNYCH rabatów.
     * Najpierw używa punktów (całe ile się da), potem dopłaca pierwszą kartą z limitem.
     */
    private void payAllNoDiscount(PaymentMethod pointsMethod, Set<String> paidOrders) {
        for (Order o : orders) {
            BigDecimal amountToPay = o.amountToPay();

            BigDecimal usePoints = pointsMethod.getRemainingLimit().min(amountToPay);
            if (usePoints.signum() > 0) {
                pointsMethod.payAmount(usePoints);
                amountToPay = amountToPay.subtract(usePoints);
            }

            if (amountToPay.signum() > 0) {
                BigDecimal a = amountToPay;
                PaymentMethod anyCard = paymentMethods.stream()
                        .filter(m -> !m.getId().equals(POINTS_ID))
                        .filter(m -> m.getRemainingLimit().compareTo(a) >= 0)
                        .min(Comparator.comparing(m -> m.getRemainingLimit().subtract(a)))
                        .orElseThrow(() -> new IllegalStateException("No card available for fallback"));
                anyCard.payAmount(amountToPay);
            }

            paidOrders.add(o.id());
        }
    }


    private void assignFullCardNoDiscount(Set<String> paidOrders) {
        orders.stream()
                .filter(order -> !paidOrders.contains(order.id()))
                .sorted(Comparator.comparing(Order::amountToPay).reversed())
                .forEach(order -> {
                    BigDecimal totalAmountToPay = order.amountToPay();

                    paymentMethods.stream()
                            .filter(m -> !m.getId().equals(POINTS_ID))
                            .filter(m -> m.getRemainingLimit().compareTo(totalAmountToPay) >= 0)
                            .min(Comparator.comparing(m -> m.getRemainingLimit().subtract(totalAmountToPay)))
                            .ifPresent(card -> {
                                card.payAmount(totalAmountToPay);

                                Candidate c = new Candidate(order, card, BigDecimal.ZERO, totalAmountToPay,
                                        BigDecimal.ZERO, BigDecimal.ZERO);
                                paidOrders.add(order.id());
                            });
                });
    }

    private List<Candidate> generateAllPossibleCandidates(PaymentMethod pointsPayment) {
        List<Candidate> candidates = new ArrayList<>();

        for(Order order : orders) {
            BigDecimal orderAmountToPay = order.amountToPay();

            for(String promotionId : order.promotions()) {
                PaymentMethod paymentMethod = methodMap.get(promotionId);
                if (isInvalidCardMethod(paymentMethod) || paymentMethod.getLimit().compareTo(orderAmountToPay) < 0) {
                    continue;
                }

                BigDecimal ratioDiscountPercent = paymentMethod.calculateRatioDiscountPercent();
                BigDecimal discountAmount = orderAmountToPay.multiply(ratioDiscountPercent);

                candidates.add(new Candidate(order, paymentMethod, BigDecimal.ZERO, orderAmountToPay,
                        discountAmount, ratioDiscountPercent));
            }

            if (pointsPayment.getLimit().compareTo(orderAmountToPay) >= 0) {
                BigDecimal ratioDiscountPercent = pointsPayment.calculateRatioDiscountPercent();
                BigDecimal discountAmount = orderAmountToPay.multiply(ratioDiscountPercent);

                candidates.add(new Candidate(order, pointsPayment, orderAmountToPay, BigDecimal.ZERO,
                        discountAmount, ratioDiscountPercent));
            }


            for(PaymentMethod paymentMethod : paymentMethods) {
                if (isInvalidCardMethod(paymentMethod)) {
                    continue;
                }

                BigDecimal pointsAmount = orderAmountToPay.multiply(TEN_PERCENT);
                BigDecimal cardAmount = orderAmountToPay.multiply(NINETY_PERCENT);

                if (pointsPayment.getLimit().compareTo(pointsAmount) < 0 || paymentMethod.getLimit().compareTo(cardAmount) < 0) {
                    continue;
                }

                candidates.add(new Candidate(order, paymentMethod, pointsAmount, cardAmount, pointsAmount, TEN_PERCENT));
            }
        }

        return candidates;
    }

    private boolean isInvalidCardMethod(PaymentMethod method) {
        return method == null || method.getId().equals(POINTS_ID);
    }

    private void payGreedily(PaymentMethod pointsPayment, Set<String> paidOrders) {
        List<Candidate> candidates = generateAllPossibleCandidates(pointsPayment);

        candidates.sort(Comparator
                        .comparing(Candidate::getRatioDiscountPercent)
                        .thenComparing(Candidate::getDiscountAmount).reversed());

        for(Candidate candidate : candidates) {
            String orderId = candidate.getOrder().id();
            if (paidOrders.contains(orderId) || !candidate.canAfford(pointsPayment)) {
                continue;
            }

            candidate.payForOrder(pointsPayment);
            paidOrders.add(orderId);
        }
    }

    private void payForUnpaidOrders(PaymentMethod pointsMethod, Set<String> paidOrders) {
        orders.stream()
                .filter(o -> !paidOrders.contains(o.id()))
                .sorted(Comparator.comparing(Order::amountToPay).reversed())
                .forEach(o -> payOrderFallback(o, pointsMethod, paidOrders));
    }

    private void payOrderFallback(Order order, PaymentMethod pointsPayment, Set<String> paidOrders) {
        BigDecimal amountToPay = order.amountToPay();
        PaymentMethod cardPayment = findMaxLimitCardBelow(amountToPay);
        BigDecimal cardRemainingLimit = cardPayment.getRemainingLimit();
        BigDecimal requiredPoints = amountToPay.subtract(cardRemainingLimit);

        if (requiredPoints.compareTo(pointsPayment.getRemainingLimit()) > 0) {
            throw new IllegalStateException(
                    "Failed to pay for order" + order.id()
                            + ": required points =" + requiredPoints
                            + ", available=" + pointsPayment.getRemainingLimit()
            );
        }

        cardPayment.payAmount(cardRemainingLimit);
        pointsPayment.payAmount(requiredPoints);

        paidOrders.add(order.id());
    }

    private PaymentMethod findMaxLimitCardBelow(BigDecimal fullAmount) {
        return paymentMethods.stream()
                .filter(m -> !m.getId().equals(POINTS_ID))
                .filter(m -> m.getRemainingLimit().compareTo(fullAmount) < 0)
                .max(Comparator.comparing(PaymentMethod::getRemainingLimit))
                .orElseThrow(() -> new IllegalStateException(
                        "No possible card found with limit < " + fullAmount));
    }

    private void printResults(Set<String> paidOrders) {
        System.out.println("\n--- Payment Methods Usage ---");
        for (PaymentMethod method : paymentMethods) {
            System.out.println(method);
        }

        System.out.println("\n--- Paid Orders ---");
        paidOrders.forEach(System.out::println);
    }
}
