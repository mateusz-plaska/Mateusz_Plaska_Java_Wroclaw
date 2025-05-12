package org.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentSelectorTest {
    private PaymentMethod pointsPayment;
    private PaymentMethod cardPayment1;
    private PaymentMethod cardPayment2;
    private PaymentSelector selector;

    @BeforeEach
    void setUp() {
        Order order1 = new Order("O1", new BigDecimal("100"), List.of("C1"));
        Order order2 = new Order("O2", new BigDecimal("50"), List.of());
        pointsPayment = new PaymentMethod("PUNKTY", new BigDecimal("10"), new BigDecimal("80"));
        cardPayment1  = new PaymentMethod("C1",     new BigDecimal("20"), new BigDecimal("120"));
        cardPayment2  = new PaymentMethod("C2",     new BigDecimal("5"),  new BigDecimal("39"));
        selector = new PaymentSelector(List.of(order1, order2), List.of(pointsPayment, cardPayment1, cardPayment2));
    }

    @Test
    void isInvalidCardMethod_various() throws Exception {
        Method method = PaymentSelector.class.getDeclaredMethod("isInvalidCardMethod", PaymentMethod.class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(selector, (PaymentMethod) null));
        assertTrue((boolean) method.invoke(selector, pointsPayment));
        assertFalse((boolean) method.invoke(selector, cardPayment1));
        assertFalse((boolean) method.invoke(selector, cardPayment2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findMaxLimitCardBelow_selectsCorrect() throws Exception {
        Method method = PaymentSelector.class.getDeclaredMethod("findMaxLimitCardBelow", BigDecimal.class);
        method.setAccessible(true);

        Optional<PaymentMethod> pick = (Optional<PaymentMethod>) method.invoke(selector, new BigDecimal("90"));
        assertEquals("C2", pick.get().getId());

        pick = (Optional<PaymentMethod>) method.invoke(selector, new BigDecimal("120"));
        assertEquals("C2", pick.get().getId());

        pick = (Optional<PaymentMethod>) method.invoke(selector, new BigDecimal("121"));
        assertEquals("C1", pick.get().getId());

        pick = (Optional<PaymentMethod>) method.invoke(selector, new BigDecimal("39"));
        assertFalse(pick.isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateAllPossibleCandidates_counts() throws Exception {
        Method method = PaymentSelector.class.getDeclaredMethod("generateAllPossibleCandidates", PaymentMethod.class);
        method.setAccessible(true);

        List<Candidate> candidates = (List<Candidate>) method.invoke(selector, pointsPayment);

        long o1Count = candidates.stream().filter(c -> c.getOrder().id().equals("O1")).count();
        assertEquals(2, o1Count, "O1 should have 2 candidates: fullC1, partialC1");

        long o2Count = candidates.stream().filter(c -> c.getOrder().id().equals("O2")).count();
        assertEquals(2, o2Count, "O2 should have 2 candidates: fullPoints, partialC1");
    }

    @Test
    void payAllNoDiscount_usesPointsThenCards() throws Exception {
        Method method = PaymentSelector.class.getDeclaredMethod("payAllNoDiscount", PaymentMethod.class);
        method.setAccessible(true);

        method.invoke(selector, pointsPayment);

        assertEquals(new BigDecimal("80"), pointsPayment.getUsedAmount());
        assertEquals(new BigDecimal("50"), cardPayment1.getUsedAmount());
        assertEquals(new BigDecimal("20"), cardPayment2.getUsedAmount());
    }
}
