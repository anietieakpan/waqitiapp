package com.waqiti.investment.controller;

import com.waqiti.investment.domain.enums.OrderStatus;
import com.waqiti.investment.dto.request.CreateOrderRequest;
import com.waqiti.investment.dto.response.InvestmentOrderDto;
import com.waqiti.investment.service.OrderExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST controller for investment order operations
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Investment order management API")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {
    "${cors.allowed-origins:http://localhost:3000,https://app.example.com,https://admin.example.com}"
})
public class OrderController {

    private final OrderExecutionService orderExecutionService;

    @PostMapping
    @Operation(summary = "Create order", description = "Create a new investment order")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#request.accountId)")
    public ResponseEntity<InvestmentOrderDto> createOrder(
            @Parameter(description = "Order details", required = true)
            @RequestBody @Valid CreateOrderRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} creating order: {}", userDetails.getUsername(), request);
        InvestmentOrderDto order = orderExecutionService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @PutMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel order", description = "Cancel a pending order")
    @PreAuthorize("@investmentSecurityService.canAccessOrder(#orderId)")
    public ResponseEntity<InvestmentOrderDto> cancelOrder(
            @Parameter(description = "Order ID", required = true)
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} cancelling order: {}", userDetails.getUsername(), orderId);
        InvestmentOrderDto order = orderExecutionService.cancelOrder(orderId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order", description = "Get order details by ID")
    @PreAuthorize("@investmentSecurityService.canAccessOrder(#orderId)")
    public ResponseEntity<InvestmentOrderDto> getOrder(
            @Parameter(description = "Order ID", required = true)
            @PathVariable Long orderId) {
        
        log.info("Getting order: {}", orderId);
        InvestmentOrderDto order = orderExecutionService.getOrder(orderId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get account orders", description = "Get all orders for an account")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<List<InvestmentOrderDto>> getAccountOrders(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId,
            @Parameter(description = "Filter by order status")
            @RequestParam(required = false) OrderStatus status) {
        
        log.info("Getting orders for account {} with status: {}", accountId, status);
        List<InvestmentOrderDto> orders = orderExecutionService.getAccountOrders(accountId, status);
        return ResponseEntity.ok(orders);
    }
}