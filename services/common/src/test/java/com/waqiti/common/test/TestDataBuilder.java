package com.waqiti.common.test;

import com.github.javafaker.Faker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Builder pattern utility for creating test data with realistic values.
 *
 * Provides fluent API for building test entities with:
 * - Realistic fake data (names, emails, addresses)
 * - Financial data (amounts, currencies, account numbers)
 * - Customizable builders for different entity types
 * - Consistent and reproducible test data
 *
 * Usage:
 * <pre>
 * {@code
 * Payment payment = TestDataBuilder.aPayment()
 *     .withUserId(userId)
 *     .withAmount(new BigDecimal("100.00"))
 *     .withStatus(PaymentStatus.PENDING)
 *     .build();
 *
 * User user = TestDataBuilder.aUser()
 *     .withEmail("test@example.com")
 *     .withKycCompleted(true)
 *     .build();
 * }
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
public class TestDataBuilder {

    private static final Faker faker = new Faker();
    private static final Random random = new Random();

    /**
     * Generate a random UUID.
     *
     * @return Random UUID
     */
    public static UUID randomUUID() {
        return UUID.randomUUID();
    }

    /**
     * Generate a random email address.
     *
     * @return Random email
     */
    public static String randomEmail() {
        return faker.internet().emailAddress();
    }

    /**
     * Generate a random phone number.
     *
     * @return Random phone number
     */
    public static String randomPhoneNumber() {
        return faker.phoneNumber().cellPhone();
    }

    /**
     * Generate a random first name.
     *
     * @return Random first name
     */
    public static String randomFirstName() {
        return faker.name().firstName();
    }

    /**
     * Generate a random last name.
     *
     * @return Random last name
     */
    public static String randomLastName() {
        return faker.name().lastName();
    }

    /**
     * Generate a random full name.
     *
     * @return Random full name
     */
    public static String randomFullName() {
        return faker.name().fullName();
    }

    /**
     * Generate a random street address.
     *
     * @return Random address
     */
    public static String randomAddress() {
        return faker.address().streetAddress();
    }

    /**
     * Generate a random city name.
     *
     * @return Random city
     */
    public static String randomCity() {
        return faker.address().city();
    }

    /**
     * Generate a random state code.
     *
     * @return Random state
     */
    public static String randomState() {
        return faker.address().stateAbbr();
    }

    /**
     * Generate a random ZIP code.
     *
     * @return Random ZIP
     */
    public static String randomZipCode() {
        return faker.address().zipCode();
    }

    /**
     * Generate a random country code.
     *
     * @return Random country code
     */
    public static String randomCountryCode() {
        return faker.address().countryCode();
    }

    /**
     * Generate a random amount between min and max.
     *
     * @param min Minimum amount
     * @param max Maximum amount
     * @return Random amount
     */
    public static BigDecimal randomAmount(double min, double max) {
        double value = ThreadLocalRandom.current().nextDouble(min, max);
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Generate a random payment amount (between $1 and $10,000).
     *
     * @return Random payment amount
     */
    public static BigDecimal randomPaymentAmount() {
        return randomAmount(1.0, 10000.0);
    }

    /**
     * Generate a random small amount (between $0.01 and $100).
     *
     * @return Random small amount
     */
    public static BigDecimal randomSmallAmount() {
        return randomAmount(0.01, 100.0);
    }

    /**
     * Generate a random large amount (between $10,000 and $1,000,000).
     *
     * @return Random large amount
     */
    public static BigDecimal randomLargeAmount() {
        return randomAmount(10000.0, 1000000.0);
    }

    /**
     * Generate a random currency code.
     *
     * @return Random currency
     */
    public static String randomCurrency() {
        String[] currencies = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF"};
        return currencies[random.nextInt(currencies.length)];
    }

    /**
     * Generate a random account number.
     *
     * @return Random account number
     */
    public static String randomAccountNumber() {
        return String.format("%012d", random.nextLong(1000000000000L));
    }

    /**
     * Generate a random routing number.
     *
     * @return Random routing number
     */
    public static String randomRoutingNumber() {
        return String.format("%09d", random.nextInt(1000000000));
    }

    /**
     * Generate a random IBAN.
     *
     * @return Random IBAN
     */
    public static String randomIban() {
        return faker.finance().iban();
    }

    /**
     * Generate a random credit card number.
     *
     * @return Random credit card number
     */
    public static String randomCreditCardNumber() {
        return faker.finance().creditCard();
    }

    /**
     * Generate a random SSN.
     *
     * @return Random SSN
     */
    public static String randomSSN() {
        return String.format("%03d-%02d-%04d",
            random.nextInt(900) + 100,
            random.nextInt(100),
            random.nextInt(10000));
    }

    /**
     * Generate a random tax ID (EIN).
     *
     * @return Random EIN
     */
    public static String randomEIN() {
        return String.format("%02d-%07d",
            random.nextInt(100),
            random.nextInt(10000000));
    }

    /**
     * Generate a random IP address.
     *
     * @return Random IP address
     */
    public static String randomIpAddress() {
        return faker.internet().ipV4Address();
    }

    /**
     * Generate a random user agent.
     *
     * @return Random user agent
     */
    public static String randomUserAgent() {
        return faker.internet().userAgentAny();
    }

    /**
     * Generate a random company name.
     *
     * @return Random company name
     */
    public static String randomCompanyName() {
        return faker.company().name();
    }

    /**
     * Generate a random boolean value.
     *
     * @return Random boolean
     */
    public static boolean randomBoolean() {
        return random.nextBoolean();
    }

    /**
     * Generate a random integer between min and max (inclusive).
     *
     * @param min Minimum value
     * @param max Maximum value
     * @return Random integer
     */
    public static int randomInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    /**
     * Generate a random long between min and max (inclusive).
     *
     * @param min Minimum value
     * @param max Maximum value
     * @return Random long
     */
    public static long randomLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    /**
     * Generate a random date-time in the past.
     *
     * @param daysBack Maximum days back
     * @return Random past date-time
     */
    public static LocalDateTime randomPastDateTime(int daysBack) {
        long secondsBack = daysBack * 24L * 60 * 60;
        long randomSeconds = ThreadLocalRandom.current().nextLong(0, secondsBack);
        return LocalDateTime.now().minusSeconds(randomSeconds);
    }

    /**
     * Generate a random date-time in the future.
     *
     * @param daysAhead Maximum days ahead
     * @return Random future date-time
     */
    public static LocalDateTime randomFutureDateTime(int daysAhead) {
        long secondsAhead = daysAhead * 24L * 60 * 60;
        long randomSeconds = ThreadLocalRandom.current().nextLong(0, secondsAhead);
        return LocalDateTime.now().plusSeconds(randomSeconds);
    }

    /**
     * Generate a random description.
     *
     * @return Random description
     */
    public static String randomDescription() {
        return faker.lorem().sentence();
    }

    /**
     * Generate random words.
     *
     * @param count Number of words
     * @return Random words
     */
    public static String randomWords(int count) {
        return String.join(" ", faker.lorem().words(count));
    }

    /**
     * Pick a random element from an array.
     *
     * @param items Array of items
     * @param <T> Type of items
     * @return Random item
     */
    @SafeVarargs
    public static <T> T randomFrom(T... items) {
        return items[random.nextInt(items.length)];
    }

    /**
     * Pick a random element from a list.
     *
     * @param items List of items
     * @param <T> Type of items
     * @return Random item
     */
    public static <T> T randomFrom(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    /**
     * Generate a list of items using a supplier.
     *
     * @param count Number of items
     * @param supplier Item supplier
     * @param <T> Type of items
     * @return List of items
     */
    public static <T> List<T> listOf(int count, Supplier<T> supplier) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(supplier.get());
        }
        return list;
    }

    /**
     * Generate a set of items using a supplier.
     *
     * @param count Number of items
     * @param supplier Item supplier
     * @param <T> Type of items
     * @return Set of items
     */
    public static <T> Set<T> setOf(int count, Supplier<T> supplier) {
        Set<T> set = new HashSet<>();
        while (set.size() < count) {
            set.add(supplier.get());
        }
        return set;
    }

    /**
     * Create a map with random key-value pairs.
     *
     * @param count Number of entries
     * @param keySupplier Key supplier
     * @param valueSupplier Value supplier
     * @param <K> Key type
     * @param <V> Value type
     * @return Map of entries
     */
    public static <K, V> Map<K, V> mapOf(int count, Supplier<K> keySupplier, Supplier<V> valueSupplier) {
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < count; i++) {
            map.put(keySupplier.get(), valueSupplier.get());
        }
        return map;
    }

    /**
     * Get the Faker instance for advanced usage.
     *
     * @return Faker instance
     */
    public static Faker getFaker() {
        return faker;
    }

    /**
     * Get the Random instance for advanced usage.
     *
     * @return Random instance
     */
    public static Random getRandom() {
        return random;
    }
}
