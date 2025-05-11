package org.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentMethodTest {
    private PaymentMethod paymentMethod;

    @BeforeEach
    void setUp() {
        paymentMethod = new PaymentMethod("CARD", new BigDecimal("15"), new BigDecimal("100.00"));
    }

    @Test
    void testInitialRemainingLimit() {
        assertEquals(new BigDecimal("100.00"), paymentMethod.getRemainingLimit());
    }

    @Test
    void testPayAmountReducesLimit() {
        paymentMethod.payAmount(new BigDecimal("30.50"));
        assertEquals(new BigDecimal("69.50"), paymentMethod.getRemainingLimit());
        paymentMethod.payAmount(new BigDecimal("0.50"));
        assertEquals(new BigDecimal("69.00"), paymentMethod.getRemainingLimit());
    }

    @Test
    void testPayAmountNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> paymentMethod.payAmount(new BigDecimal("-1")));
    }

    @Test
    void testRatioDiscountPercent() {
        assertEquals(new BigDecimal("0.15"), paymentMethod.calculateRatioDiscountPercent());

        PaymentMethod paymentMethod2 = new PaymentMethod("X", new BigDecimal("7"), new BigDecimal("50"));
        assertEquals(new BigDecimal("0.07"), paymentMethod2.calculateRatioDiscountPercent());
    }
}
