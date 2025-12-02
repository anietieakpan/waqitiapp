package com.waqiti.common.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Advanced mathematical operations for financial analytics and portfolio management.
 *
 * This class provides high-precision mathematical functions needed for:
 * - Portfolio analytics (Sharpe ratio, volatility calculations)
 * - Investment analysis (compound growth, present value)
 * - Risk management (Value at Risk, standard deviation)
 * - Cryptocurrency pricing (volatility modeling)
 *
 * All methods maintain precision throughout calculations to avoid
 * the precision loss that occurs when converting BigDecimal to double.
 *
 * Precision: 34 significant digits (equivalent to decimal128)
 * Rounding: HALF_UP (standard financial rounding)
 *
 * IMPORTANT: This class is for ANALYTICS, not basic money operations.
 * For basic money operations (add, subtract, multiply, divide), use {@link MoneyMath}.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since November 8, 2025
 * @see MoneyMath For basic money operations
 */
public final class AdvancedFinancialMath {
    
    public static final MathContext FINANCIAL_PRECISION = new MathContext(34, RoundingMode.HALF_UP);
    
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal EPSILON = new BigDecimal("0.0000000001");

    // Mathematical constants with high precision
    private static final BigDecimal LN_2 = new BigDecimal("0.693147180559945309417232121458176568");
    private static final BigDecimal LN_10 = new BigDecimal("2.302585092994045684017991454684364208");

    private AdvancedFinancialMath() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
    
    /**
     * Calculates the square root of a BigDecimal using Newton-Raphson method.
     * 
     * @param value The value to calculate square root for
     * @param mc The MathContext for precision control
     * @return Square root of the value
     * @throws ArithmeticException if value is negative
     */
    public static BigDecimal sqrt(BigDecimal value, MathContext mc) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ArithmeticException("Cannot calculate square root of negative number: " + value);
        }
        
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        if (value.compareTo(BigDecimal.ONE) == 0) {
            return BigDecimal.ONE;
        }
        
        BigDecimal x = value.divide(TWO, mc);
        BigDecimal last;
        int iterations = 0;
        int maxIterations = 100;
        
        do {
            last = x;
            x = value.divide(x, mc).add(x).divide(TWO, mc);
            iterations++;
            
            if (iterations > maxIterations) {
                throw new ArithmeticException("Square root calculation did not converge after " + maxIterations + " iterations");
            }
        } while (x.subtract(last).abs().compareTo(EPSILON) > 0);
        
        return x.round(mc);
    }
    
    /**
     * Calculates the square root using financial precision (34 digits).
     * 
     * @param value The value to calculate square root for
     * @return Square root with financial precision
     */
    public static BigDecimal sqrt(BigDecimal value) {
        return sqrt(value, FINANCIAL_PRECISION);
    }
    
    /**
     * Calculates power for integer exponents using repeated multiplication.
     * Uses binary exponentiation for efficiency.
     * 
     * @param base The base value
     * @param exponent The integer exponent
     * @param mc The MathContext for precision control
     * @return base raised to the power of exponent
     */
    public static BigDecimal pow(BigDecimal base, int exponent, MathContext mc) {
        if (exponent == 0) {
            return BigDecimal.ONE;
        }
        
        if (exponent == 1) {
            return base.round(mc);
        }
        
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            if (exponent > 0) {
                return BigDecimal.ZERO;
            } else {
                throw new ArithmeticException("Cannot raise zero to negative power");
            }
        }
        
        BigDecimal result = BigDecimal.ONE;
        BigDecimal currentBase = base;
        int currentExponent = Math.abs(exponent);
        
        while (currentExponent > 0) {
            if (currentExponent % 2 == 1) {
                result = result.multiply(currentBase, mc);
            }
            currentBase = currentBase.multiply(currentBase, mc);
            currentExponent /= 2;
        }
        
        if (exponent < 0) {
            return BigDecimal.ONE.divide(result, mc);
        }
        
        return result.round(mc);
    }
    
    /**
     * Calculates power using financial precision (34 digits).
     * 
     * @param base The base value
     * @param exponent The integer exponent
     * @return base raised to the power of exponent
     */
    public static BigDecimal pow(BigDecimal base, int exponent) {
        return pow(base, exponent, FINANCIAL_PRECISION);
    }
    
    /**
     * Calculates power for double exponents (approximation for non-integer exponents).
     * Uses exp(exponent * ln(base)) formula.
     * 
     * @param base The base value
     * @param exponent The double exponent
     * @param mc The MathContext for precision control
     * @return base raised to the power of exponent (approximation)
     */
    public static BigDecimal pow(BigDecimal base, double exponent, MathContext mc) {
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ArithmeticException("Cannot calculate power of non-positive number for non-integer exponent");
        }
        
        if (exponent == 0.0) {
            return BigDecimal.ONE;
        }
        
        if (exponent == 1.0) {
            return base.round(mc);
        }
        
        BigDecimal lnBase = ln(base, mc);
        BigDecimal exponentBD = new BigDecimal(exponent, mc);
        BigDecimal product = lnBase.multiply(exponentBD, mc);
        
        return exp(product, mc);
    }
    
    /**
     * Calculates natural logarithm (ln) using Taylor series expansion.
     * ln(x) = 2 * sum((1/(2n+1)) * ((x-1)/(x+1))^(2n+1))
     * 
     * @param value The value to calculate ln for (must be positive)
     * @param mc The MathContext for precision control
     * @return Natural logarithm of the value
     */
    public static BigDecimal ln(BigDecimal value, MathContext mc) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ArithmeticException("Cannot calculate natural logarithm of non-positive number: " + value);
        }
        
        if (value.compareTo(BigDecimal.ONE) == 0) {
            return BigDecimal.ZERO;
        }
        
        int scale = value.scale();
        BigDecimal x = value;
        BigDecimal result = BigDecimal.ZERO;
        
        int factor = 0;
        while (x.compareTo(BigDecimal.ONE) > 0) {
            x = x.divide(TWO, mc);
            factor++;
        }
        
        while (x.compareTo(new BigDecimal("0.5")) < 0) {
            x = x.multiply(TWO, mc);
            factor--;
        }
        
        BigDecimal term = x.subtract(BigDecimal.ONE).divide(x.add(BigDecimal.ONE), mc);
        BigDecimal termSquared = term.multiply(term, mc);
        BigDecimal sum = term;
        BigDecimal currentTerm = term;
        
        for (int n = 1; n < 100; n++) {
            currentTerm = currentTerm.multiply(termSquared, mc);
            BigDecimal divisor = new BigDecimal(2 * n + 1);
            BigDecimal addend = currentTerm.divide(divisor, mc);
            
            if (addend.abs().compareTo(EPSILON) < 0) {
                break;
            }
            
            sum = sum.add(addend, mc);
        }
        
        result = TWO.multiply(sum, mc);
        
        // Add normalization factor: ln(x*2^n) = ln(x) + n*ln(2)
        if (factor != 0) {
            result = result.add(LN_2.multiply(new BigDecimal(factor), mc), mc);
        }
        
        return result.round(mc);
    }
    
    /**
     * Calculates natural logarithm using financial precision.
     * 
     * @param value The value to calculate ln for
     * @return Natural logarithm with financial precision
     */
    public static BigDecimal ln(BigDecimal value) {
        return ln(value, FINANCIAL_PRECISION);
    }
    
    /**
     * Calculates e^x using Taylor series expansion.
     * e^x = 1 + x + x^2/2! + x^3/3! + ...
     * 
     * @param x The exponent
     * @param mc The MathContext for precision control
     * @return e raised to the power of x
     */
    public static BigDecimal exp(BigDecimal x, MathContext mc) {
        if (x.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        
        BigDecimal result = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        BigDecimal factorial = BigDecimal.ONE;
        
        for (int n = 1; n < 100; n++) {
            factorial = factorial.multiply(new BigDecimal(n), mc);
            term = x.pow(n, mc).divide(factorial, mc);
            
            if (term.abs().compareTo(EPSILON) < 0) {
                break;
            }
            
            result = result.add(term, mc);
        }
        
        return result.round(mc);
    }
    
    /**
     * Calculates base-10 logarithm (log10).
     * log10(x) = ln(x) / ln(10)
     * 
     * @param value The value to calculate log10 for
     * @param mc The MathContext for precision control
     * @return Base-10 logarithm of the value
     */
    public static BigDecimal log10(BigDecimal value, MathContext mc) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ArithmeticException("Cannot calculate logarithm of non-positive number: " + value);
        }
        
        return ln(value, mc).divide(LN_10, mc);
    }
    
    /**
     * Calculates base-10 logarithm using financial precision.
     * 
     * @param value The value to calculate log10 for
     * @return Base-10 logarithm with financial precision
     */
    public static BigDecimal log10(BigDecimal value) {
        return log10(value, FINANCIAL_PRECISION);
    }
    
    /**
     * Creates a BigDecimal from a double value that represents a mathematical constant.
     * This is safe for constants like sqrt(252), sqrt(365), etc.
     * 
     * @param doubleValue The double value
     * @return BigDecimal with appropriate precision
     */
    public static BigDecimal fromDouble(double doubleValue) {
        return BigDecimal.valueOf(doubleValue).round(FINANCIAL_PRECISION);
    }
}