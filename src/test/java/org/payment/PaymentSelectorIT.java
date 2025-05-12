package org.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentSelectorIT {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exampleFromPrompt() throws Exception {
        String ordersJson = """
            [
                {"id":"ORDER1","value":"100.00","promotions":["mZysk"]},
                {"id":"ORDER2","value":"200.00","promotions":["BosBankrut"]},
                {"id":"ORDER3","value":"150.00","promotions":["mZysk","BosBankrut"]},
                {"id":"ORDER4","value":"50.00"}
            ]
            """;
        String methodsJson = """
            [
                {"id":"PUNKTY","discount":"15","limit":"100.00"},
                {"id":"mZysk","discount":"10","limit":"180.00"},
                {"id":"BosBankrut","discount":"5","limit":"200.00"}
            ]
            """;

        List<Order> orders = mapper.readValue(ordersJson, new TypeReference<>() {});
        List<PaymentMethod> methods = mapper.readValue(methodsJson, new TypeReference<>() {});
        PaymentSelector selector = new PaymentSelector(orders, methods);

        selector.runSelector();

        PaymentMethod mZyskPayment = methods.stream().filter(m -> m.getId().equals("mZysk")).findFirst().get();
        PaymentMethod bosPayment = methods.stream().filter(m -> m.getId().equals("BosBankrut")).findFirst().get();
        PaymentMethod pointsPayment = methods.stream().filter(m -> m.getId().equals("PUNKTY")).findFirst().get();

        assertEquals(new BigDecimal("175.00"), mZyskPayment.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("190.00"), bosPayment.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("90.00"), pointsPayment.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void fullPoints() throws Exception {
        String ordersJson = """
            [
                {"id":"X","value":"50.00","promotions":[]}
            ]
            """;
        String methodsJson = """
            [
                {"id":"PUNKTY","discount":"10","limit":"45.00"},
                {"id":"CARD","discount":"10","limit":"100.00"}
            ]
            """;

        List<Order> orders = mapper.readValue(ordersJson, new TypeReference<>() {});
        List<PaymentMethod> methods = mapper.readValue(methodsJson, new TypeReference<>() {});
        PaymentSelector selector = new PaymentSelector(orders, methods);

        selector.runSelector();

        PaymentMethod pointsPayment  = methods.stream().filter(m -> m.getId().equals("PUNKTY")).findFirst().get();
        PaymentMethod card = methods.stream().filter(m -> m.getId().equals("CARD")).findFirst().get();

        assertEquals(new BigDecimal("45.00"), pointsPayment.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(BigDecimal.ZERO, card.getUsedAmount());
    }

    @Test
    void mixedPartialPointsWithCardPromotions() throws Exception {
        String ordersJson = """
            [
                {"id":"A","value":"120.00","promotions":["C"]},
                {"id":"B","value":"80.00","promotions":[]}
            ]
            """;
        String methodsJson = """
            [
                {"id":"PUNKTY","discount":"10","limit":"20.00"},
                {"id":"C","discount":"15","limit":"95.00"},
                {"id":"D","discount":"5","limit":"96.00"}
            ]
            """;

        List<Order> orders = mapper.readValue(ordersJson, new TypeReference<>() {});
        List<PaymentMethod> methods = mapper.readValue(methodsJson, new TypeReference<>() {});
        PaymentSelector selector = new PaymentSelector(orders, methods);

        selector.runSelector();

        PaymentMethod pointsPayment  = methods.stream().filter(m -> m.getId().equals("PUNKTY")).findFirst().get();
        PaymentMethod card1 = methods.stream().filter(m -> m.getId().equals("C")).findFirst().get();
        PaymentMethod card2 = methods.stream().filter(m -> m.getId().equals("D")).findFirst().get();

        // A: partial (12 pts + 108 card), B: full-card no discount fallback (80)
        assertEquals(new BigDecimal("20.00"), pointsPayment.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("64.00"), card1.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("96.00"), card2.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void fullCardNoDiscount_phaseOnly() throws Exception {
        String ordersJson = """
                [
                    {"id":"X","value":"60.00","promotions":[]}
                ]
                """;
        String methodsJson = """
            [
              {"id":"PUNKTY","discount":"10","limit":"5.00"},
              {"id":"CARD","discount":"15","limit":"100.00"}
            ]
            """;

        List<Order> orders = mapper.readValue(ordersJson, new TypeReference<>() {});
        List<PaymentMethod> methods = mapper.readValue(methodsJson, new TypeReference<>() {});
        PaymentSelector selector = new PaymentSelector(orders, methods);

        selector.runSelector();

        PaymentMethod card = methods.stream().filter(m -> m.getId().equals("CARD")).findFirst().get();
        assertEquals(new BigDecimal("60.00"), card.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
    }
}
