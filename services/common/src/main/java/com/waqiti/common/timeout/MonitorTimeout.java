package com.waqiti.common.timeout;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to monitor HTTP client timeout behavior
 *
 * USAGE:
 * <pre>
 * {@literal @}Service
 * public class StripePaymentService {
 *
 *     {@literal @}MonitorTimeout(
 *         service = "stripe",
 *         operation = "create-payment-intent",
 *         warningThresholdMs = 5000
 *     )
 *     public PaymentIntent createPaymentIntent(PaymentRequest request) {
 *         // If this call takes > 5s, warning is logged
 *         // If timeout occurs, metric is incremented
 *         return stripeClient.createPaymentIntent(request);
 *     }
 * }
 * </pre>
 *
 * METRICS EXPOSED:
 * - http.client.timeout.total (tagged by service, operation, type)
 * - http.client.call.duration (tagged by service, operation)
 *
 * @author Waqiti Engineering Team
 * @version 3.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MonitorTimeout {

    /**
     * External service name (e.g., "stripe", "avalara", "ofac")
     */
    String service();

    /**
     * Operation being performed (e.g., "create-payment", "calculate-tax")
     */
    String operation();

    /**
     * Warning threshold in milliseconds
     * If call takes longer than this, warning is logged
     * Default: 5000ms (5 seconds)
     */
    long warningThresholdMs() default 5000;
}
