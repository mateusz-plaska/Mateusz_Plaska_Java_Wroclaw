package org.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Main {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        if (args.length != 2) {
            printUsageAndExit();
        }

        try {
            List<Order> orders = readJsonFile(args[0], new TypeReference<>() {});
            List<PaymentMethod> methods = readJsonFile(args[1], new TypeReference<>() {});

            new PaymentSelector(orders, methods).runSelector();
        } catch (IOException e) {
            System.err.println("Failed to load input files: " + e.getMessage());
            System.exit(2);
        }
    }

    private static <T> T readJsonFile(String path, TypeReference<T> typeRef) throws IOException {
        return MAPPER.readValue(new File(path), typeRef);
    }

    private static void printUsageAndExit() {
        System.err.println("Usage: java -jar app.jar <orders.json> <paymentmethods.json>");
        System.exit(1);
    }
}