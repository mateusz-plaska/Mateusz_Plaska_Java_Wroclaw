package org.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CandidateTest {
    private PaymentMethod pointsPayment;
    private PaymentMethod cardPayment;
    private Order order;

    @BeforeEach
    void setUp() {
        pointsPayment = new PaymentMethod("PUNKTY", new BigDecimal("10"), new BigDecimal("100.00"));
        cardPayment   = new PaymentMethod("CARD", new BigDecimal("20"), new BigDecimal("200.00"));
        order  = new Order("O1", new BigDecimal("50.00"), List.of("CARD"));
    }

    @Test
    void initFullCardAdjustsUseCardAmount() {
        BigDecimal discount = new BigDecimal("10.00");
        Candidate candidate = new Candidate(order, cardPayment,
                BigDecimal.ZERO,
                new BigDecimal("50.00"),
                discount,
                new BigDecimal("0.20")
        );
        assertEquals(new BigDecimal("40.00"), candidate.getUseCardAmount());
        assertEquals(BigDecimal.ZERO, candidate.getUsePoints());
    }

    @Test
    void initFullPointsAdjustsUsePoints() {
        BigDecimal discount = new BigDecimal("5.00");
        Candidate candidate = new Candidate(order, pointsPayment,
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                discount,
                new BigDecimal("0.10")
        );
        assertEquals(new BigDecimal("45.00"), candidate.getUsePoints());
        assertEquals(BigDecimal.ZERO, candidate.getUseCardAmount());
    }

    @Test
    void initPartialAdjustsUsePointsOnly() {
        BigDecimal pointsPart = new BigDecimal("5.00");
        BigDecimal cardPart   = new BigDecimal("45.00");
        Candidate candidate = new Candidate(order, cardPayment,
                pointsPart,
                cardPart,
                pointsPart,
                new BigDecimal("0.10")
        );
        assertEquals(pointsPart, candidate.getUsePoints());
        assertEquals(cardPart.subtract(pointsPart), candidate.getUseCardAmount());
    }


    @Test
    void canAffordRespectsLimits() {
        Candidate candidate = new Candidate(order, cardPayment,
                BigDecimal.ZERO,
                new BigDecimal("50.00"),
                new BigDecimal("10.00"),
                new BigDecimal("0.20")
        );
        assertTrue(candidate.canAfford(pointsPayment));
        cardPayment.payAmount(new BigDecimal("161.00"));
        assertFalse(candidate.canAfford(pointsPayment));
    }

    @Test
    void canAffordFailsWhenPointsInsufficient() {
        Candidate candidate = new Candidate(order, pointsPayment,
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("5.00"),
                new BigDecimal("0.10")
        );
        assertTrue(candidate.canAfford(pointsPayment));
        pointsPayment.payAmount(new BigDecimal("55.00"));
        assertTrue(candidate.canAfford(pointsPayment));
        pointsPayment.payAmount(new BigDecimal("0.01"));
        assertFalse(candidate.canAfford(pointsPayment));
    }

    @Test
    void canAffordFailsWhenBothInsufficient() {
        Candidate candidate = new Candidate(order, cardPayment,
                new BigDecimal("5.00"),
                new BigDecimal("45.00"),
                new BigDecimal("5.00"),
                new BigDecimal("0.10")
        );
        assertTrue(candidate.canAfford(pointsPayment));
        pointsPayment.payAmount(new BigDecimal("95.00"));
        cardPayment.payAmount(new BigDecimal("160.00"));
        assertTrue(candidate.canAfford(pointsPayment));
        pointsPayment.payAmount(new BigDecimal("0.01"));
        cardPayment.payAmount(new BigDecimal("0.01"));
        assertFalse(candidate.canAfford(pointsPayment));
    }

    @Test
    void payForOrderFullCard() {
        Candidate candidate = new Candidate(order, cardPayment,
                BigDecimal.ZERO,
                new BigDecimal("50.00"),
                new BigDecimal("10.00"),
                new BigDecimal("0.20")
        );
        candidate.payForOrder(pointsPayment);
        assertEquals(new BigDecimal("160.00"), cardPayment.getRemainingLimit());
    }

    @Test
    void payForOrderFullPoints() {
        Candidate candidate = new Candidate(order, pointsPayment,
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("5.00"),
                new BigDecimal("0.10")
        );
        candidate.payForOrder(pointsPayment);
        assertEquals(new BigDecimal("55.00"), pointsPayment.getRemainingLimit());
    }

    @Test
    void payForOrderPartial() {
        Candidate candidate = new Candidate(order, cardPayment,
                new BigDecimal("5.00"),
                new BigDecimal("45.00"),
                new BigDecimal("5.00"),
                new BigDecimal("0.10")
        );
        candidate.payForOrder(pointsPayment);
        assertEquals(new BigDecimal("95.00"), pointsPayment.getRemainingLimit());
        assertEquals(new BigDecimal("160.00"), cardPayment.getRemainingLimit());
    }
}
