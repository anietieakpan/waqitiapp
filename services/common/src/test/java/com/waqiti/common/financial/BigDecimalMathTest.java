package com.waqiti.common.financial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BigDecimalMath High-Precision Mathematical Operations Tests")
class BigDecimalMathTest {
    
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_UP);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000000001");
    
    @Test
    @DisplayName("Should calculate square root of 2 with high precision")
    void shouldCalculateSqrt2WithHighPrecision() {
        BigDecimal two = new BigDecimal("2");
        
        BigDecimal result = BigDecimalMath.sqrt(two, MC);
        
        BigDecimal expected = new BigDecimal("1.4142135623730950488016887242096981");
        assertThat(result).isEqualByComparingTo(expected);
    }
    
    @Test
    @DisplayName("Should calculate square root of perfect squares accurately")
    void shouldCalculateSqrtOfPerfectSquares() {
        assertThat(BigDecimalMath.sqrt(new BigDecimal("4"))).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(BigDecimalMath.sqrt(new BigDecimal("9"))).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(BigDecimalMath.sqrt(new BigDecimal("16"))).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(BigDecimalMath.sqrt(new BigDecimal("25"))).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(BigDecimalMath.sqrt(new BigDecimal("100"))).isEqualByComparingTo(new BigDecimal("10"));
    }
    
    @Test
    @DisplayName("Should calculate square root of zero")
    void shouldCalculateSqrtOfZero() {
        assertThat(BigDecimalMath.sqrt(BigDecimal.ZERO)).isEqualByComparingTo(BigDecimal.ZERO);
    }
    
    @Test
    @DisplayName("Should calculate square root of one")
    void shouldCalculateSqrtOfOne() {
        assertThat(BigDecimalMath.sqrt(BigDecimal.ONE)).isEqualByComparingTo(BigDecimal.ONE);
    }
    
    @Test
    @DisplayName("Should throw exception for square root of negative number")
    void shouldThrowExceptionForSqrtOfNegative() {
        assertThatThrownBy(() -> BigDecimalMath.sqrt(new BigDecimal("-1")))
            .isInstanceOf(ArithmeticException.class)
            .hasMessageContaining("Cannot calculate square root of negative number");
    }
    
    @Test
    @DisplayName("Should calculate square root of 252 for annualized volatility")
    void shouldCalculateSqrt252ForVolatility() {
        BigDecimal twoFiftyTwo = new BigDecimal("252");
        
        BigDecimal result = BigDecimalMath.sqrt(twoFiftyTwo, MC);
        
        BigDecimal expected = new BigDecimal("15.874507866387544");
        assertThat(result.subtract(expected).abs()).isLessThan(TOLERANCE);
    }
    
    @Test
    @DisplayName("Should calculate square root of 365 for crypto volatility")
    void shouldCalculateSqrt365ForCryptoVolatility() {
        BigDecimal threeSixtyFive = new BigDecimal("365");
        
        BigDecimal result = BigDecimalMath.sqrt(threeSixtyFive, MC);
        
        BigDecimal expected = new BigDecimal("19.104973174542800");
        assertThat(result.subtract(expected).abs()).isLessThan(TOLERANCE);
    }
    
    @Test
    @DisplayName("Should calculate power for positive integer exponents")
    void shouldCalculatePowerForPositiveIntegers() {
        BigDecimal base = new BigDecimal("2");
        
        assertThat(BigDecimalMath.pow(base, 0)).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(BigDecimalMath.pow(base, 1)).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(BigDecimalMath.pow(base, 2)).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(BigDecimalMath.pow(base, 3)).isEqualByComparingTo(new BigDecimal("8"));
        assertThat(BigDecimalMath.pow(base, 10)).isEqualByComparingTo(new BigDecimal("1024"));
    }
    
    @Test
    @DisplayName("Should calculate power for negative exponents")
    void shouldCalculatePowerForNegativeExponents() {
        BigDecimal base = new BigDecimal("2");
        
        BigDecimal result = BigDecimalMath.pow(base, -1, MC);
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.5"));
        
        result = BigDecimalMath.pow(base, -2, MC);
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.25"));
    }
    
    @Test
    @DisplayName("Should calculate power of 10 for token decimals")
    void shouldCalculatePowerOf10ForTokenDecimals() {
        BigDecimal ten = BigDecimal.TEN;
        
        assertThat(BigDecimalMath.pow(ten, 6)).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(BigDecimalMath.pow(ten, 18)).isEqualByComparingTo(new BigDecimal("1000000000000000000"));
    }
    
    @Test
    @DisplayName("Should calculate compound returns accurately")
    void shouldCalculateCompoundReturnsAccurately() {
        BigDecimal returnRate = new BigDecimal("1.05");
        int periods = 252;
        
        BigDecimal result = BigDecimalMath.pow(returnRate, periods, MC);
        
        assertThat(result).isGreaterThan(BigDecimal.ONE);
    }
    
    @Test
    @DisplayName("Should calculate natural logarithm of e")
    void shouldCalculateLnOfE() {
        BigDecimal e = new BigDecimal("2.718281828459045235360287471352662");
        
        BigDecimal result = BigDecimalMath.ln(e, MC);
        
        assertThat(result.subtract(BigDecimal.ONE).abs()).isLessThan(TOLERANCE);
    }
    
    @Test
    @DisplayName("Should calculate natural logarithm of 1")
    void shouldCalculateLnOfOne() {
        assertThat(BigDecimalMath.ln(BigDecimal.ONE)).isEqualByComparingTo(BigDecimal.ZERO);
    }
    
    @Test
    @DisplayName("Should throw exception for ln of negative number")
    void shouldThrowExceptionForLnOfNegative() {
        assertThatThrownBy(() -> BigDecimalMath.ln(new BigDecimal("-1")))
            .isInstanceOf(ArithmeticException.class)
            .hasMessageContaining("Cannot calculate natural logarithm of non-positive number");
    }
    
    @Test
    @DisplayName("Should throw exception for ln of zero")
    void shouldThrowExceptionForLnOfZero() {
        assertThatThrownBy(() -> BigDecimalMath.ln(BigDecimal.ZERO))
            .isInstanceOf(ArithmeticException.class)
            .hasMessageContaining("Cannot calculate natural logarithm of non-positive number");
    }
    
    @Test
    @DisplayName("Should calculate Shannon entropy logarithm")
    void shouldCalculateShannonEntropyLogarithm() {
        BigDecimal probability = new BigDecimal("0.25");
        
        BigDecimal result = BigDecimalMath.ln(probability, MC);
        
        BigDecimal expected = new BigDecimal("-1.386294361119890618834464242916353");
        assertThat(result.subtract(expected).abs()).isLessThan(TOLERANCE);
    }
    
    @Test
    @DisplayName("Should calculate base-10 logarithm of 10")
    void shouldCalculateLog10Of10() {
        assertThat(BigDecimalMath.log10(BigDecimal.TEN)).isEqualByComparingTo(BigDecimal.ONE);
    }
    
    @Test
    @DisplayName("Should calculate base-10 logarithm of 100")
    void shouldCalculateLog10Of100() {
        BigDecimal hundred = new BigDecimal("100");
        
        BigDecimal result = BigDecimalMath.log10(hundred, MC);
        
        assertThat(result).isEqualByComparingTo(new BigDecimal("2"));
    }
    
    @Test
    @DisplayName("Should calculate exponential of zero")
    void shouldCalculateExpOfZero() {
        assertThat(BigDecimalMath.exp(BigDecimal.ZERO, MC)).isEqualByComparingTo(BigDecimal.ONE);
    }
    
    @Test
    @DisplayName("Should calculate exponential of one")
    void shouldCalculateExpOfOne() {
        BigDecimal result = BigDecimalMath.exp(BigDecimal.ONE, MC);
        
        BigDecimal e = new BigDecimal("2.718281828459045235360287471352662");
        assertThat(result.subtract(e).abs()).isLessThan(TOLERANCE);
    }
    
    @Test
    @DisplayName("Should maintain precision better than double conversion")
    void shouldMaintainPrecisionBetterThanDouble() {
        BigDecimal variance = new BigDecimal("100.123456789123456789");
        
        BigDecimal doubleBasedResult = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        BigDecimal bigDecimalResult = BigDecimalMath.sqrt(variance, MC);
        
        assertThat(bigDecimalResult.scale()).isGreaterThan(doubleBasedResult.scale());
        assertThat(bigDecimalResult.precision()).isGreaterThan(doubleBasedResult.precision());
    }
    
    @Test
    @DisplayName("Should calculate volatility annualization accurately")
    void shouldCalculateVolatilityAnnualizationAccurately() {
        BigDecimal dailyVolatility = new BigDecimal("0.0123456789");
        BigDecimal tradingDays = new BigDecimal("252");
        
        BigDecimal annualizationFactor = BigDecimalMath.sqrt(tradingDays, MC);
        BigDecimal annualizedVolatility = dailyVolatility.multiply(annualizationFactor, MC);
        
        assertThat(annualizedVolatility).isGreaterThan(dailyVolatility);
        assertThat(annualizedVolatility.scale()).isGreaterThanOrEqualTo(10);
    }
    
    @Test
    @DisplayName("Should calculate VaR scaling factors accurately")
    void shouldCalculateVaRScalingFactorsAccurately() {
        BigDecimal dailyVaR = new BigDecimal("1000.00");
        
        BigDecimal weeklyFactor = BigDecimalMath.sqrt(new BigDecimal("5"), MC);
        BigDecimal monthlyFactor = BigDecimalMath.sqrt(new BigDecimal("21"), MC);
        
        BigDecimal weeklyVaR = dailyVaR.multiply(weeklyFactor, MC);
        BigDecimal monthlyVaR = dailyVaR.multiply(monthlyFactor, MC);
        
        assertThat(weeklyVaR).isGreaterThan(dailyVaR);
        assertThat(monthlyVaR).isGreaterThan(weeklyVaR);
    }
    
    @Test
    @DisplayName("Should handle very large numbers")
    void shouldHandleVeryLargeNumbers() {
        BigDecimal largeNumber = new BigDecimal("1000000000000");
        
        BigDecimal result = BigDecimalMath.sqrt(largeNumber, MC);
        
        assertThat(result).isEqualByComparingTo(new BigDecimal("1000000"));
    }
    
    @Test
    @DisplayName("Should handle very small numbers")
    void shouldHandleVerySmallNumbers() {
        BigDecimal smallNumber = new BigDecimal("0.000000000001");
        
        BigDecimal result = BigDecimalMath.sqrt(smallNumber, MC);
        
        assertThat(result).isGreaterThan(BigDecimal.ZERO);
        assertThat(result).isLessThan(smallNumber);
    }
    
    @Test
    @DisplayName("Should calculate power for double exponents")
    void shouldCalculatePowerForDoubleExponents() {
        BigDecimal base = new BigDecimal("2");
        double exponent = 0.5;
        
        BigDecimal result = BigDecimalMath.pow(base, exponent, MC);
        
        BigDecimal expected = BigDecimalMath.sqrt(base, MC);
        assertThat(result.subtract(expected).abs()).isLessThan(TOLERANCE);
    }
    
    @Test
    @DisplayName("Should throw exception for zero raised to negative power")
    void shouldThrowExceptionForZeroRaisedToNegativePower() {
        assertThatThrownBy(() -> BigDecimalMath.pow(BigDecimal.ZERO, -1, MC))
            .isInstanceOf(ArithmeticException.class)
            .hasMessageContaining("Cannot raise zero to negative power");
    }
}