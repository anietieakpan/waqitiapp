package com.waqiti.card.service;

import com.waqiti.card.dto.CardProductResponse;
import com.waqiti.card.entity.CardProduct;
import com.waqiti.card.repository.CardProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CardProductService - Card product management
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardProductService {

    private final CardProductRepository productRepository;

    @Transactional(readOnly = true)
    public CardProductResponse getProductById(String productId) {
        CardProduct product = productRepository.findByProductId(productId)
            .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        return mapToProductResponse(product);
    }

    @Transactional(readOnly = true)
    public List<CardProductResponse> getAllActiveProducts() {
        List<CardProduct> products = productRepository.findAllActiveProducts();
        return products.stream()
            .map(this::mapToProductResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CardProductResponse> getCurrentlyEffectiveProducts() {
        List<CardProduct> products = productRepository.findCurrentlyEffectiveProducts(LocalDate.now());
        return products.stream()
            .map(this::mapToProductResponse)
            .collect(Collectors.toList());
    }

    private CardProductResponse mapToProductResponse(CardProduct product) {
        return CardProductResponse.builder()
            .productId(product.getProductId())
            .productName(product.getProductName())
            .productDescription(product.getProductDescription())
            .productType(product.getProductType())
            .cardNetwork(product.getCardNetwork())
            .issuerName(product.getIssuerName())
            .currencyCode(product.getCurrencyCode())
            .defaultCreditLimit(product.getDefaultCreditLimit())
            .minimumCreditLimit(product.getMinimumCreditLimit())
            .maximumCreditLimit(product.getMaximumCreditLimit())
            .annualFee(product.getAnnualFee())
            .monthlyFee(product.getMonthlyFee())
            .issuanceFee(product.getIssuanceFee())
            .replacementFee(product.getReplacementFee())
            .defaultInterestRate(product.getDefaultInterestRate())
            .rewardsEnabled(product.getRewardsEnabled())
            .cashbackRate(product.getCashbackRate())
            .contactlessEnabled(product.getContactlessEnabled())
            .virtualCardEnabled(product.getVirtualCardEnabled())
            .internationalEnabled(product.getInternationalEnabled())
            .onlineTransactionsEnabled(product.getOnlineTransactionsEnabled())
            .cardValidityYears(product.getCardValidityYears())
            .isActive(product.getIsActive())
            .build();
    }
}
