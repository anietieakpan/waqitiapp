package com.waqiti.crypto.controller;

import com.waqiti.crypto.lightning.LightningNetworkService;
import com.waqiti.crypto.lightning.LightningNetworkService.*;
import com.waqiti.crypto.dto.lightning.*;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade Lightning Network REST API Controller
 * Provides comprehensive Lightning Network payment capabilities
 * with enterprise security, monitoring, and resilience patterns
 */
@RestController
@RequestMapping("/api/v1/lightning")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Lightning Network", description = "Bitcoin Lightning Network payment operations")
@SecurityRequirement(name = "bearer-jwt")
public class LightningController {

    private final LightningNetworkService lightningService;
    private final LightningInvoiceService invoiceService;
    private final LightningChannelService channelService;
    private final LightningPaymentService paymentService;
    private final LightningAnalyticsService analyticsService;
    private final LightningSecurityService securityService;
    private final LightningAuditService auditService;
    private final LightningWebhookService webhookService;
    private final LightningBackupService backupService;
    private final LightningRoutingService routingService;

    // ======================== INVOICE OPERATIONS ========================

    /**
     * Create a Lightning invoice for receiving payments
     */
    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Create Lightning invoice", description = "Generate a new Lightning invoice for receiving payments")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Invoice created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Lightning node error")
    })
    @Timed(value = "lightning.invoice.create", histogram = true)
    @CircuitBreaker(name = "lightning-node", fallbackMethod = "createInvoiceFallback")
    @RateLimiter(name = "lightning-operations")
    public ResponseEntity<LightningInvoiceResponse> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request,
            Authentication authentication) {
        
        log.info("Creating Lightning invoice for user: {}, amount: {} sats", 
                authentication.getName(), request.getAmountSat());
        
        try {
            // Validate request
            validateInvoiceRequest(request, authentication);
            
            // Apply security checks
            securityService.validateInvoiceCreation(authentication.getName(), request);
            
            // Create invoice with enhanced metadata
            Map<String, String> metadata = enrichInvoiceMetadata(request, authentication);
            
            LightningInvoice invoice = lightningService.createInvoice(
                request.getAmountSat(),
                request.getDescription(),
                request.getExpirySeconds() != null ? request.getExpirySeconds() : 3600,
                metadata
            );
            
            // Store invoice details for tracking
            InvoiceEntity invoiceEntity = invoiceService.saveInvoice(invoice, authentication.getName());
            
            // Create webhook for payment notification if requested
            if (request.getWebhookUrl() != null) {
                webhookService.registerWebhook(invoice.getPaymentHash(), request.getWebhookUrl());
            }
            
            // Audit log
            auditService.logInvoiceCreation(authentication.getName(), invoice);
            
            // Build response
            LightningInvoiceResponse response = LightningInvoiceResponse.builder()
                .invoiceId(invoiceEntity.getId())
                .paymentRequest(invoice.getPaymentRequest())
                .paymentHash(invoice.getPaymentHash())
                .amountSat(invoice.getAmountSat())
                .description(invoice.getDescription())
                .expiry(invoice.getExpiry())
                .createdAt(Instant.now())
                .qrCode(generateQRCode(invoice.getPaymentRequest()))
                .lightningAddress(generateLightningAddress(authentication.getName()))
                .build();
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating Lightning invoice", e);
            throw new BusinessException(ErrorCode.SYS_INTERNAL_ERROR, "Failed to create Lightning invoice");
        }
    }

    /**
     * Get invoice details by ID
     */
    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get invoice details", description = "Retrieve Lightning invoice details by ID")
    @Cacheable(value = "lightning-invoices", key = "#invoiceId")
    public ResponseEntity<InvoiceDetailsResponse> getInvoice(
            @PathVariable String invoiceId,
            Authentication authentication) {
        
        log.debug("Fetching invoice details: {}", invoiceId);
        
        InvoiceEntity invoice = invoiceService.getInvoice(invoiceId);
        
        // Check ownership or admin
        if (!invoice.getUserId().equals(authentication.getName()) && 
            !authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, 
                "You don't have permission to view this invoice");
        }
        
        InvoiceDetailsResponse response = mapToInvoiceDetails(invoice);
        return ResponseEntity.ok(response);
    }

    /**
     * List user's Lightning invoices with pagination
     */
    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "List invoices", description = "Get paginated list of user's Lightning invoices")
    public ResponseEntity<Page<InvoiceDetailsResponse>> listInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        
        Page<InvoiceEntity> invoices = invoiceService.getUserInvoices(
            authentication.getName(), status, fromDate, toDate, pageable);
        
        Page<InvoiceDetailsResponse> response = invoices.map(this::mapToInvoiceDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a pending invoice
     */
    @DeleteMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Cancel invoice", description = "Cancel a pending Lightning invoice")
    @CacheEvict(value = "lightning-invoices", key = "#invoiceId")
    public ResponseEntity<Void> cancelInvoice(
            @PathVariable String invoiceId,
            Authentication authentication) {
        
        log.info("Cancelling invoice: {} for user: {}", invoiceId, authentication.getName());
        
        InvoiceEntity invoice = invoiceService.getInvoice(invoiceId);
        
        // Verify ownership
        if (!invoice.getUserId().equals(authentication.getName())) {
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, 
                "You can only cancel your own invoices");
        }
        
        invoiceService.cancelInvoice(invoiceId);
        auditService.logInvoiceCancellation(authentication.getName(), invoiceId);
        
        return ResponseEntity.noContent().build();
    }

    // ======================== PAYMENT OPERATIONS ========================

    /**
     * Pay a Lightning invoice
     */
    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Pay Lightning invoice", description = "Send payment for a Lightning invoice")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment successful"),
        @ApiResponse(responseCode = "400", description = "Invalid invoice or insufficient balance"),
        @ApiResponse(responseCode = "402", description = "Payment required - insufficient channel balance"),
        @ApiResponse(responseCode = "500", description = "Payment processing error")
    })
    @Timed(value = "lightning.payment.send", histogram = true)
    @CircuitBreaker(name = "lightning-payments", fallbackMethod = "payInvoiceFallback")
    @Retry(name = "lightning-payments")
    public ResponseEntity<PaymentResultResponse> payInvoice(
            @Valid @RequestBody PayInvoiceRequest request,
            Authentication authentication) {
        
        log.info("Processing Lightning payment for user: {}", authentication.getName());
        
        try {
            // Validate payment request
            validatePaymentRequest(request, authentication);
            
            // Check user limits and compliance
            securityService.validatePaymentCompliance(authentication.getName(), request);
            
            // Decode and validate invoice
            InvoiceValidation validation = paymentService.validateInvoice(request.getPaymentRequest());
            if (!validation.isValid()) {
                throw new BusinessException(ErrorCode.PAYMENT_INVALID_AMOUNT, validation.getError());
            }
            
            // Check balance and routing
            RoutingInfo routing = routingService.findBestRoute(
                validation.getDestination(), 
                validation.getAmountSat()
            );
            
            if (!routing.isRoutable()) {
                throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_FAILED, 
                    "No route available for payment");
            }
            
            // Execute payment with monitoring
            PaymentResult result = executePaymentWithMonitoring(request, routing, authentication);
            
            // Handle payment result
            if (result.isSuccess()) {
                // Save successful payment
                PaymentEntity payment = paymentService.savePayment(result, authentication.getName());
                
                // Send webhook notification if configured
                webhookService.notifyPaymentSuccess(payment);
                
                // Audit log
                auditService.logPaymentSuccess(authentication.getName(), payment);
                
                PaymentResultResponse response = PaymentResultResponse.builder()
                    .paymentId(payment.getId())
                    .success(true)
                    .paymentHash(result.getPaymentHash())
                    .paymentPreimage(result.getPaymentPreimage())
                    .feeSat(result.getFeeSat())
                    .route(result.getRoute())
                    .duration(result.getDuration().toMillis())
                    .timestamp(Instant.now())
                    .build();
                
                return ResponseEntity.ok(response);
                
            } else {
                // Handle payment failure
                auditService.logPaymentFailure(authentication.getName(), request.getPaymentRequest(), result.getError());
                
                throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_FAILED, 
                    "Payment failed: " + result.getError());
            }
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing Lightning payment", e);
            throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_FAILED, 
                "Failed to process Lightning payment");
        }
    }

    /**
     * Send keysend payment (no invoice required)
     */
    @PostMapping("/payments/keysend")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Send keysend payment", description = "Send spontaneous payment without invoice")
    @RateLimiter(name = "lightning-operations")
    public ResponseEntity<PaymentResultResponse> sendKeysend(
            @Valid @RequestBody KeysendRequest request,
            Authentication authentication) {
        
        log.info("Sending keysend payment from user: {} to node: {}", 
                authentication.getName(), request.getDestinationPubkey());
        
        // Validate keysend request
        validateKeysendRequest(request, authentication);
        
        // Security checks
        securityService.validateKeysendPayment(authentication.getName(), request);
        
        // Execute keysend
        PaymentResult result = lightningService.sendKeysend(
            request.getDestinationPubkey(),
            request.getAmountSat(),
            request.getCustomData() != null ? request.getCustomData().getBytes() : null
        );
        
        if (result.isSuccess()) {
            // Save payment
            PaymentEntity payment = paymentService.saveKeysendPayment(result, request, authentication.getName());
            
            // Audit
            auditService.logKeysendSuccess(authentication.getName(), payment);
            
            PaymentResultResponse response = PaymentResultResponse.builder()
                .paymentId(payment.getId())
                .success(true)
                .paymentHash(result.getPaymentHash())
                .paymentPreimage(result.getPaymentPreimage())
                .feeSat(result.getFeeSat())
                .timestamp(Instant.now())
                .build();
            
            return ResponseEntity.ok(response);
        } else {
            throw new BusinessException(ErrorCode.PAYMENT_PROCESSING_FAILED, 
                "Keysend payment failed: " + result.getError());
        }
    }

    /**
     * Get payment details
     */
    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get payment details", description = "Retrieve Lightning payment details by ID")
    @Cacheable(value = "lightning-payments", key = "#paymentId")
    public ResponseEntity<PaymentDetailsResponse> getPayment(
            @PathVariable String paymentId,
            Authentication authentication) {
        
        PaymentEntity payment = paymentService.getPayment(paymentId);
        
        // Check ownership or admin
        if (!payment.getUserId().equals(authentication.getName()) && 
            !authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, 
                "You don't have permission to view this payment");
        }
        
        PaymentDetailsResponse response = mapToPaymentDetails(payment);
        return ResponseEntity.ok(response);
    }

    /**
     * List user's payments with advanced filtering
     */
    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "List payments", description = "Get paginated list of user's Lightning payments")
    public ResponseEntity<Page<PaymentDetailsResponse>> listPayments(
            @RequestParam(required = false) PaymentType type,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) @Min(0) Long minAmount,
            @RequestParam(required = false) @Min(0) Long maxAmount,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        
        PaymentFilter filter = PaymentFilter.builder()
            .userId(authentication.getName())
            .type(type)
            .status(status)
            .fromDate(fromDate)
            .toDate(toDate)
            .minAmount(minAmount)
            .maxAmount(maxAmount)
            .build();
        
        Page<PaymentEntity> payments = paymentService.searchPayments(filter, pageable);
        Page<PaymentDetailsResponse> response = payments.map(this::mapToPaymentDetails);
        
        return ResponseEntity.ok(response);
    }

    // ======================== CHANNEL MANAGEMENT ========================

    /**
     * Open a new Lightning channel
     */
    @PostMapping("/channels")
    @PreAuthorize("hasRole('MERCHANT') or hasRole('ADMIN')")
    @Operation(summary = "Open Lightning channel", description = "Open a new Lightning channel with a peer")
    @Timed(value = "lightning.channel.open", histogram = true)
    public ResponseEntity<ChannelOpenResponse> openChannel(
            @Valid @RequestBody OpenChannelRequest request,
            Authentication authentication) {
        
        log.info("Opening Lightning channel for user: {} with node: {}", 
                authentication.getName(), request.getNodePubkey());
        
        // Validate channel request
        validateChannelRequest(request, authentication);
        
        // Check user permissions and limits
        securityService.validateChannelOpen(authentication.getName(), request);
        
        // Open channel
        String channelId = lightningService.openChannel(
            request.getNodePubkey(),
            request.getLocalFundingAmount(),
            request.getPushSat() != null ? request.getPushSat() : 0,
            request.isPrivate()
        );
        
        // Save channel info
        ChannelEntity channel = channelService.saveChannel(channelId, request, authentication.getName());
        
        // Audit
        auditService.logChannelOpen(authentication.getName(), channel);
        
        ChannelOpenResponse response = ChannelOpenResponse.builder()
            .channelId(channelId)
            .fundingTxId(channel.getFundingTxId())
            .outputIndex(channel.getOutputIndex())
            .capacity(request.getLocalFundingAmount())
            .status(ChannelStatus.PENDING_OPEN)
            .estimatedOpenBlock(channel.getEstimatedOpenBlock())
            .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Close a Lightning channel
     */
    @DeleteMapping("/channels/{channelId}")
    @PreAuthorize("hasRole('MERCHANT') or hasRole('ADMIN')")
    @Operation(summary = "Close Lightning channel", description = "Close an existing Lightning channel")
    public ResponseEntity<ChannelCloseResponse> closeChannel(
            @PathVariable String channelId,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(required = false) String closeAddress,
            Authentication authentication) {
        
        log.info("Closing Lightning channel: {} for user: {}, force: {}", 
                channelId, authentication.getName(), force);
        
        // Verify channel ownership
        ChannelEntity channel = channelService.getChannel(channelId);
        if (!channel.getUserId().equals(authentication.getName()) && 
            !authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, 
                "You don't have permission to close this channel");
        }
        
        // Close channel
        String closingTxId = lightningService.closeChannel(channelId, force);
        
        // Update channel status
        channelService.updateChannelStatus(channelId, 
            force ? ChannelStatus.FORCE_CLOSING : ChannelStatus.CLOSING);
        
        // Audit
        auditService.logChannelClose(authentication.getName(), channelId, force);
        
        ChannelCloseResponse response = ChannelCloseResponse.builder()
            .channelId(channelId)
            .closingTxId(closingTxId)
            .force(force)
            .estimatedSettlementBlock(calculateSettlementBlock(force))
            .closeAddress(closeAddress)
            .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * List Lightning channels with detailed information
     */
    @GetMapping("/channels")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "List channels", description = "Get list of Lightning channels")
    public ResponseEntity<List<ChannelDetailsResponse>> listChannels(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) String peerPubkey,
            Authentication authentication) {
        
        List<ChannelInfo> channels = lightningService.listChannels(activeOnly);
        
        // Filter by user if not admin
        if (!authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            channels = channelService.filterUserChannels(channels, authentication.getName());
        }
        
        // Filter by peer if specified
        if (peerPubkey != null) {
            channels = channels.stream()
                .filter(c -> c.getRemotePubkey().equals(peerPubkey))
                .collect(Collectors.toList());
        }
        
        List<ChannelDetailsResponse> response = channels.stream()
            .map(this::mapToChannelDetails)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Rebalance a Lightning channel
     */
    @PostMapping("/channels/{channelId}/rebalance")
    @PreAuthorize("hasRole('MERCHANT') or hasRole('ADMIN')")
    @Operation(summary = "Rebalance channel", description = "Rebalance Lightning channel liquidity")
    @RateLimiter(name = "channel-operations")
    public ResponseEntity<RebalanceResultResponse> rebalanceChannel(
            @PathVariable String channelId,
            @Valid @RequestBody RebalanceRequest request,
            Authentication authentication) {
        
        log.info("Rebalancing channel: {} for user: {}", channelId, authentication.getName());
        
        // Verify ownership
        verifyChannelOwnership(channelId, authentication);
        
        // Execute rebalance
        PaymentResult result = lightningService.rebalanceChannel(
            channelId, 
            request.getAmountSat() != null ? request.getAmountSat() : calculateOptimalRebalanceAmount(channelId)
        );
        
        RebalanceResultResponse response = RebalanceResultResponse.builder()
            .channelId(channelId)
            .success(result.isSuccess())
            .amountSat(request.getAmountSat())
            .feeSat(result.getFeeSat())
            .newLocalBalance(channelService.getChannel(channelId).getLocalBalance())
            .newRemoteBalance(channelService.getChannel(channelId).getRemoteBalance())
            .error(result.getError())
            .build();
        
        if (result.isSuccess()) {
            auditService.logChannelRebalance(authentication.getName(), channelId, request.getAmountSat());
        }
        
        return ResponseEntity.ok(response);
    }

    // ======================== STREAMING PAYMENTS ========================

    /**
     * Start payment streaming (recurring payments)
     */
    @PostMapping("/streams")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Start payment stream", description = "Start recurring Lightning payments")
    public ResponseEntity<PaymentStreamResponse> startPaymentStream(
            @Valid @RequestBody StartStreamRequest request,
            Authentication authentication) {
        
        log.info("Starting payment stream for user: {} to: {}", 
                authentication.getName(), request.getDestination());
        
        // Validate stream request
        validateStreamRequest(request, authentication);
        
        // Security checks
        securityService.validatePaymentStream(authentication.getName(), request);
        
        // Start stream
        String streamId = lightningService.startPaymentStream(
            request.getDestination(),
            request.getAmountPerInterval(),
            Duration.parse(request.getInterval()),
            Duration.parse(request.getTotalDuration())
        );
        
        // Save stream info
        StreamEntity stream = paymentService.savePaymentStream(streamId, request, authentication.getName());
        
        // Audit
        auditService.logStreamStart(authentication.getName(), stream);
        
        PaymentStreamResponse response = PaymentStreamResponse.builder()
            .streamId(streamId)
            .destination(request.getDestination())
            .amountPerInterval(request.getAmountPerInterval())
            .interval(request.getInterval())
            .totalDuration(request.getTotalDuration())
            .status(StreamStatus.ACTIVE)
            .startedAt(Instant.now())
            .estimatedEndTime(Instant.now().plus(Duration.parse(request.getTotalDuration())))
            .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Stop payment streaming
     */
    @DeleteMapping("/streams/{streamId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Stop payment stream", description = "Stop recurring Lightning payments")
    public ResponseEntity<StreamStopResponse> stopPaymentStream(
            @PathVariable String streamId,
            Authentication authentication) {
        
        log.info("Stopping payment stream: {} for user: {}", streamId, authentication.getName());
        
        // Verify ownership
        StreamEntity stream = paymentService.getStream(streamId);
        if (!stream.getUserId().equals(authentication.getName())) {
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, 
                "You can only stop your own payment streams");
        }
        
        // Stop stream
        lightningService.stopPaymentStream(streamId);
        
        // Update stream status
        StreamStatistics stats = paymentService.stopStream(streamId);
        
        // Audit
        auditService.logStreamStop(authentication.getName(), streamId);
        
        StreamStopResponse response = StreamStopResponse.builder()
            .streamId(streamId)
            .totalPayments(stats.getTotalPayments())
            .totalAmountSat(stats.getTotalAmount())
            .duration(stats.getDuration())
            .stoppedAt(Instant.now())
            .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * List active payment streams
     */
    @GetMapping("/streams")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "List payment streams", description = "Get list of payment streams")
    public ResponseEntity<List<StreamDetailsResponse>> listStreams(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            Authentication authentication) {
        
        List<StreamEntity> streams = paymentService.getUserStreams(authentication.getName(), activeOnly);
        
        List<StreamDetailsResponse> response = streams.stream()
            .map(this::mapToStreamDetails)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    // ======================== LNURL OPERATIONS ========================

    /**
     * Generate LNURL-pay code
     */
    @PostMapping("/lnurl/pay")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Generate LNURL-pay", description = "Generate LNURL for receiving payments")
    public ResponseEntity<LnurlPayResponse> generateLnurlPay(
            @Valid @RequestBody LnurlPayRequest request,
            Authentication authentication) {
        
        log.info("Generating LNURL-pay for user: {}", authentication.getName());
        
        // Generate LNURL
        String lnurl = paymentService.generateLnurlPay(
            authentication.getName(),
            request.getMinSendable(),
            request.getMaxSendable(),
            request.getMetadata(),
            request.getCallbackUrl()
        );
        
        LnurlPayResponse response = LnurlPayResponse.builder()
            .lnurl(lnurl)
            .minSendable(request.getMinSendable())
            .maxSendable(request.getMaxSendable())
            .metadata(request.getMetadata())
            .qrCode(generateQRCode(lnurl))
            .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * Process LNURL-pay callback
     */
    @GetMapping("/lnurl/pay/callback")
    @Operation(summary = "LNURL-pay callback", description = "Process LNURL-pay callback request")
    public ResponseEntity<Map<String, Object>> lnurlPayCallback(
            @RequestParam String k,
            @RequestParam(required = false) Long amount) {
        
        log.debug("Processing LNURL-pay callback: k={}, amount={}", k, amount);
        
        if (amount == null) {
            // First callback - return payment parameters
            Map<String, Object> response = paymentService.getLnurlPayParams(k);
            return ResponseEntity.ok(response);
        } else {
            // Second callback - create invoice
            String paymentRequest = paymentService.createLnurlInvoice(k, amount);
            
            Map<String, Object> response = new HashMap<>();
            response.put("pr", paymentRequest);
            response.put("routes", Collections.emptyList());
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Generate Lightning Address
     */
    @PostMapping("/address")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Generate Lightning Address", description = "Create human-readable Lightning address")
    public ResponseEntity<LightningAddressResponse> generateLightningAddress(
            @Valid @RequestBody GenerateAddressRequest request,
            Authentication authentication) {
        
        log.info("Generating Lightning address for user: {}", authentication.getName());
        
        // Validate username availability
        if (paymentService.isAddressTaken(request.getUsername())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, 
                "Lightning address already taken");
        }
        
        String lightningAddress = lightningService.generateLightningAddress(request.getUsername());
        
        // Save address mapping
        paymentService.saveLightningAddress(authentication.getName(), lightningAddress);
        
        LightningAddressResponse response = LightningAddressResponse.builder()
            .lightningAddress(lightningAddress)
            .username(request.getUsername())
            .domain("pay.waqiti.com")
            .lnurlPay(generateLnurlFromAddress(lightningAddress))
            .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ======================== SUBMARINE SWAPS ========================

    /**
     * Initiate submarine swap (on-chain to Lightning)
     */
    @PostMapping("/swaps/submarine")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Submarine swap", description = "Swap on-chain Bitcoin to Lightning")
    @RateLimiter(name = "swap-operations")
    public ResponseEntity<SubmarineSwapResponse> initiateSubmarineSwap(
            @Valid @RequestBody SubmarineSwapRequest request,
            Authentication authentication) {
        
        log.info("Initiating submarine swap for user: {}, amount: {} sats", 
                authentication.getName(), request.getAmountSat());
        
        // Validate swap request
        validateSwapRequest(request, authentication);
        
        // Security and compliance checks
        securityService.validateSwap(authentication.getName(), request);
        
        // Create swap
        SubmarineSwapResult swap = lightningService.performSubmarineSwap(
            request.getBtcAddress(),
            request.getAmountSat(),
            request.getLightningInvoice()
        );
        
        // Save swap details
        SwapEntity swapEntity = paymentService.saveSwap(swap, authentication.getName());
        
        // Audit
        auditService.logSwapInitiation(authentication.getName(), swapEntity);
        
        SubmarineSwapResponse response = SubmarineSwapResponse.builder()
            .swapId(swap.getSwapId())
            .onchainAddress(swap.getOnchainAddress())
            .lightningInvoice(swap.getLightningInvoice())
            .amountSat(swap.getAmountSat())
            .estimatedFee(swap.getEstimatedFee())
            .status(swap.getStatus())
            .expiry(swap.getExpiry())
            .minConfirmations(3)
            .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get swap status
     */
    @GetMapping("/swaps/{swapId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Get swap status", description = "Check submarine swap status")
    public ResponseEntity<SwapStatusResponse> getSwapStatus(
            @PathVariable String swapId,
            Authentication authentication) {
        
        SwapEntity swap = paymentService.getSwap(swapId);
        
        // Verify ownership
        if (!swap.getUserId().equals(authentication.getName())) {
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, 
                "You can only view your own swaps");
        }
        
        SwapStatusResponse response = SwapStatusResponse.builder()
            .swapId(swapId)
            .status(swap.getStatus())
            .confirmations(swap.getConfirmations())
            .requiredConfirmations(3)
            .onchainTxId(swap.getOnchainTxId())
            .lightningPaymentHash(swap.getLightningPaymentHash())
            .completedAt(swap.getCompletedAt())
            .build();
        
        return ResponseEntity.ok(response);
    }

    // ======================== ANALYTICS & REPORTING ========================

    /**
     * Get Lightning network statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get statistics", description = "Get Lightning network usage statistics")
    @Cacheable(value = "lightning-stats", key = "#period")
    public ResponseEntity<LightningStatistics> getStatistics(
            @RequestParam(defaultValue = "24h") String period,
            Authentication authentication) {
        
        Duration duration = parsePeriod(period);
        
        // Get network stats
        Map<String, Object> networkStats = lightningService.getNetworkStats();
        
        // Get user-specific stats if not admin
        UserStatistics userStats = null;
        if (!authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            userStats = analyticsService.getUserStatistics(authentication.getName(), duration);
        }
        
        // Get aggregate analytics
        AggregateAnalytics analytics = analyticsService.getAggregateAnalytics(duration);
        
        LightningStatistics response = LightningStatistics.builder()
            .period(period)
            .networkStats(networkStats)
            .userStats(userStats)
            .totalVolume(analytics.getTotalVolume())
            .totalPayments(analytics.getTotalPayments())
            .successRate(analytics.getSuccessRate())
            .averageFee(analytics.getAverageFee())
            .activeChannels(analytics.getActiveChannels())
            .totalCapacity(analytics.getTotalCapacity())
            .topRoutes(analytics.getTopRoutes())
            .hourlyVolume(analytics.getHourlyVolume())
            .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * Export transaction history
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Export transactions", description = "Export Lightning transaction history")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime toDate,
            @RequestParam(defaultValue = "CSV") ExportFormat format,
            Authentication authentication) {
        
        log.info("Exporting transactions for user: {} from {} to {} in {} format", 
                authentication.getName(), fromDate, toDate, format);
        
        byte[] exportData = analyticsService.exportUserTransactions(
            authentication.getName(), fromDate, toDate, format);
        
        String filename = String.format("lightning_transactions_%s_%s.%s",
            fromDate.toLocalDate(), toDate.toLocalDate(), format.toString().toLowerCase());
        
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .contentType(getMediaType(format))
            .body(exportData);
    }

    // ======================== NODE MANAGEMENT ========================

    /**
     * Get Lightning node information
     */
    @GetMapping("/node/info")
    @Operation(summary = "Get node info", description = "Get Lightning node information")
    @Cacheable(value = "node-info", key = "'info'")
    public ResponseEntity<NodeInfoResponse> getNodeInfo() {
        
        Map<String, Object> nodeInfo = lightningService.getNodeInfo();
        
        NodeInfoResponse response = NodeInfoResponse.builder()
            .pubkey((String) nodeInfo.get("pubkey"))
            .alias((String) nodeInfo.get("alias"))
            .version((String) nodeInfo.get("version"))
            .blockHeight((Integer) nodeInfo.get("blockHeight"))
            .numActiveChannels((Integer) nodeInfo.get("numActiveChannels"))
            .numPendingChannels((Integer) nodeInfo.get("numPendingChannels"))
            .numPeers((Integer) nodeInfo.get("numPeers"))
            .syncedToChain((Boolean) nodeInfo.get("syncedToChain"))
            .uris((List<String>) nodeInfo.get("uris"))
            .features(getNodeFeatures())
            .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * Connect to a Lightning peer
     */
    @PostMapping("/node/peers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Connect to peer", description = "Connect to a Lightning Network peer")
    public ResponseEntity<PeerConnectionResponse> connectPeer(
            @Valid @RequestBody ConnectPeerRequest request,
            Authentication authentication) {
        
        log.info("Admin {} connecting to peer: {}", authentication.getName(), request.getPubkey());
        
        boolean connected = channelService.connectToPeer(request.getPubkey(), request.getHost());
        
        PeerConnectionResponse response = PeerConnectionResponse.builder()
            .pubkey(request.getPubkey())
            .connected(connected)
            .timestamp(Instant.now())
            .build();
        
        auditService.logPeerConnection(authentication.getName(), request.getPubkey(), connected);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Backup Lightning channels
     */
    @PostMapping("/backup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Backup channels", description = "Create backup of Lightning channels")
    public ResponseEntity<BackupResponse> createBackup(Authentication authentication) {
        
        log.info("Admin {} creating channel backup", authentication.getName());
        
        BackupResult backup = backupService.createChannelBackup();
        
        BackupResponse response = BackupResponse.builder()
            .backupId(backup.getBackupId())
            .timestamp(backup.getTimestamp())
            .channelCount(backup.getChannelCount())
            .encryptedBackup(backup.getEncryptedData())
            .storageLocation(backup.getStorageLocation())
            .build();
        
        auditService.logBackupCreation(authentication.getName(), backup.getBackupId());
        
        return ResponseEntity.ok(response);
    }

    // ======================== WEBHOOKS ========================

    /**
     * Register webhook for Lightning events
     */
    @PostMapping("/webhooks")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Register webhook", description = "Register webhook for Lightning events")
    public ResponseEntity<WebhookRegistrationResponse> registerWebhook(
            @Valid @RequestBody RegisterWebhookRequest request,
            Authentication authentication) {
        
        log.info("Registering webhook for user: {}, events: {}", 
                authentication.getName(), request.getEvents());
        
        String webhookId = webhookService.registerWebhook(
            authentication.getName(),
            request.getUrl(),
            request.getEvents(),
            request.getSecret()
        );
        
        WebhookRegistrationResponse response = WebhookRegistrationResponse.builder()
            .webhookId(webhookId)
            .url(request.getUrl())
            .events(request.getEvents())
            .status(WebhookStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Delete webhook
     */
    @DeleteMapping("/webhooks/{webhookId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Delete webhook", description = "Remove webhook registration")
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable String webhookId,
            Authentication authentication) {
        
        log.info("Deleting webhook: {} for user: {}", webhookId, authentication.getName());
        
        webhookService.deleteWebhook(webhookId, authentication.getName());
        
        return ResponseEntity.noContent().build();
    }

    // ======================== HELPER METHODS ========================

    private void validateInvoiceRequest(CreateInvoiceRequest request, Authentication authentication) {
        if (request.getAmountSat() <= 0) {
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Amount must be positive");
        }
        
        if (request.getAmountSat() > 4294967295L) { // Max invoice amount
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Amount exceeds maximum");
        }
        
        if (request.getExpirySeconds() != null && request.getExpirySeconds() < 60) {
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Expiry must be at least 60 seconds");
        }
    }

    private void validatePaymentRequest(PayInvoiceRequest request, Authentication authentication) {
        if (request.getPaymentRequest() == null || request.getPaymentRequest().isEmpty()) {
            throw new BusinessException(ErrorCode.VAL_REQUIRED_FIELD, "Payment request is required");
        }
        
        if (request.getMaxFeeSat() != null && request.getMaxFeeSat() < 0) {
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Max fee cannot be negative");
        }
    }

    private void validateKeysendRequest(KeysendRequest request, Authentication authentication) {
        if (request.getDestinationPubkey() == null || request.getDestinationPubkey().length() != 66) {
            throw new BusinessException(ErrorCode.VAL_INVALID_FORMAT, "Invalid destination pubkey");
        }
        
        if (request.getAmountSat() <= 0) {
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Amount must be positive");
        }
    }

    private void validateChannelRequest(OpenChannelRequest request, Authentication authentication) {
        if (request.getLocalFundingAmount() < 20000) { // Min channel size
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Minimum channel size is 20000 sats");
        }
        
        if (request.getLocalFundingAmount() > 16777215) { // Max channel size
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Maximum channel size is 16777215 sats");
        }
    }

    private void validateStreamRequest(StartStreamRequest request, Authentication authentication) {
        if (request.getAmountPerInterval() <= 0) {
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Amount per interval must be positive");
        }
        
        try {
            Duration interval = Duration.parse(request.getInterval());
            if (interval.toSeconds() < 60) {
                throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Minimum interval is 60 seconds");
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VAL_INVALID_FORMAT, "Invalid interval format");
        }
    }

    private void validateSwapRequest(SubmarineSwapRequest request, Authentication authentication) {
        if (request.getAmountSat() < 10000) { // Min swap amount
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Minimum swap amount is 10000 sats");
        }
        
        if (request.getAmountSat() > 1000000) { // Max swap amount
            throw new BusinessException(ErrorCode.VAL_OUT_OF_RANGE, "Maximum swap amount is 1000000 sats");
        }
    }

    private void verifyChannelOwnership(String channelId, Authentication authentication) {
        ChannelEntity channel = channelService.getChannel(channelId);
        if (!channel.getUserId().equals(authentication.getName()) && 
            !authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, 
                "You don't have permission to manage this channel");
        }
    }

    private PaymentResult executePaymentWithMonitoring(PayInvoiceRequest request, 
                                                      RoutingInfo routing, 
                                                      Authentication authentication) {
        // Add monitoring and tracing
        long startTime = System.currentTimeMillis();
        
        try {
            PaymentResult result = lightningService.payInvoice(
                request.getPaymentRequest(),
                request.getMaxFeeSat() != null ? request.getMaxFeeSat() : calculateMaxFee(routing)
            );
            
            // Record metrics
            long duration = System.currentTimeMillis() - startTime;
            analyticsService.recordPaymentMetrics(authentication.getName(), result, duration);
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            analyticsService.recordPaymentFailure(authentication.getName(), e.getMessage(), duration);
            throw e;
        }
    }

    private Map<String, String> enrichInvoiceMetadata(CreateInvoiceRequest request, 
                                                      Authentication authentication) {
        Map<String, String> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        
        metadata.put("userId", authentication.getName());
        metadata.put("createdAt", Instant.now().toString());
        metadata.put("platform", "waqiti");
        
        return metadata;
    }

    private String generateQRCode(String data) {
        // Generate QR code for Lightning invoice/LNURL
        return "data:image/png;base64," + QRCodeGenerator.generateBase64(data);
    }

    private String generateLightningAddress(String username) {
        return username + "@pay.waqiti.com";
    }

    private String generateLnurlFromAddress(String lightningAddress) {
        String url = "https://pay.example.com/.well-known/lnurlp/" + 
                    lightningAddress.split("@")[0];
        return "lightning:" + Base64.getEncoder().encodeToString(url.getBytes());
    }

    private long calculateOptimalRebalanceAmount(String channelId) {
        ChannelInfo channel = channelService.getChannelInfo(channelId);
        long targetBalance = channel.getCapacity() / 2;
        return Math.abs(channel.getLocalBalance() - targetBalance);
    }

    private long calculateMaxFee(RoutingInfo routing) {
        // Calculate max acceptable fee based on route and amount
        return (long)(routing.getAmount() * 0.01); // 1% max fee
    }

    private int calculateSettlementBlock(boolean forceClose) {
        // Estimate settlement block based on close type
        return forceClose ? 144 : 6; // ~1 day for force, ~1 hour for cooperative
    }

    private Duration parsePeriod(String period) {
        return switch (period) {
            case "1h" -> Duration.ofHours(1);
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            default -> Duration.ofHours(24);
        };
    }

    private MediaType getMediaType(ExportFormat format) {
        return switch (format) {
            case CSV -> MediaType.parseMediaType("text/csv");
            case JSON -> MediaType.APPLICATION_JSON;
            case PDF -> MediaType.APPLICATION_PDF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private List<String> getNodeFeatures() {
        return Arrays.asList(
            "PAYMENT_SPLITTING",
            "KEYSEND",
            "AMP",
            "WUMBO_CHANNELS",
            "ANCHOR_COMMITMENTS",
            "ONION_MESSAGES"
        );
    }

    private InvoiceDetailsResponse mapToInvoiceDetails(InvoiceEntity invoice) {
        return InvoiceDetailsResponse.builder()
            .invoiceId(invoice.getId())
            .paymentRequest(invoice.getPaymentRequest())
            .paymentHash(invoice.getPaymentHash())
            .amountSat(invoice.getAmountSat())
            .description(invoice.getDescription())
            .status(invoice.getStatus())
            .createdAt(invoice.getCreatedAt())
            .settledAt(invoice.getSettledAt())
            .expiresAt(invoice.getExpiresAt())
            .build();
    }

    private PaymentDetailsResponse mapToPaymentDetails(PaymentEntity payment) {
        return PaymentDetailsResponse.builder()
            .paymentId(payment.getId())
            .paymentHash(payment.getPaymentHash())
            .paymentPreimage(payment.getPaymentPreimage())
            .amountSat(payment.getAmountSat())
            .feeSat(payment.getFeeSat())
            .status(payment.getStatus())
            .createdAt(payment.getCreatedAt())
            .completedAt(payment.getCompletedAt())
            .route(payment.getRoute())
            .build();
    }

    private ChannelDetailsResponse mapToChannelDetails(ChannelInfo channel) {
        return ChannelDetailsResponse.builder()
            .channelId(channel.getChannelId())
            .remotePubkey(channel.getRemotePubkey())
            .capacity(channel.getCapacity())
            .localBalance(channel.getLocalBalance())
            .remoteBalance(channel.getRemoteBalance())
            .active(channel.isActive())
            .balanceRatio(channel.getBalanceRatio())
            .needsRebalancing(channel.needsRebalancing(0.2))
            .build();
    }

    private StreamDetailsResponse mapToStreamDetails(StreamEntity stream) {
        return StreamDetailsResponse.builder()
            .streamId(stream.getId())
            .destination(stream.getDestination())
            .amountPerInterval(stream.getAmountPerInterval())
            .interval(stream.getInterval())
            .status(stream.getStatus())
            .totalPaid(stream.getTotalPaid())
            .paymentCount(stream.getPaymentCount())
            .startedAt(stream.getStartedAt())
            .build();
    }

    // Fallback methods for circuit breaker
    private ResponseEntity<LightningInvoiceResponse> createInvoiceFallback(
            CreateInvoiceRequest request, Authentication authentication, Exception ex) {
        log.error("Circuit breaker activated for invoice creation", ex);
        throw new BusinessException(ErrorCode.INT_SERVICE_UNAVAILABLE, 
            "Lightning service temporarily unavailable");
    }

    private ResponseEntity<PaymentResultResponse> payInvoiceFallback(
            PayInvoiceRequest request, Authentication authentication, Exception ex) {
        log.error("Circuit breaker activated for payment", ex);
        throw new BusinessException(ErrorCode.INT_SERVICE_UNAVAILABLE, 
            "Lightning payment service temporarily unavailable");
    }
}