package com.waqiti.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.waqiti.merchant.domain.Merchant;
import com.waqiti.merchant.dto.QRCodeData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QRCodeGenerationService {

    private final ObjectMapper objectMapper;
    
    @Value("${app.qr-code.base-url:https://api.example.com}")
    private String baseUrl;
    
    @Value("${app.qr-code.size:300}")
    private int qrCodeSize;
    
    @Value("${app.qr-code.logo-path:/static/images/waqiti-logo.png}")
    private String logoPath;

    public String generateMerchantQRCode(String merchantId) {
        log.info("Generating QR code for merchant: {}", merchantId);
        
        try {
            QRCodeData qrData = QRCodeData.builder()
                    .type("MERCHANT_PAYMENT")
                    .merchantId(merchantId)
                    .paymentUrl(baseUrl + "/pay/" + merchantId)
                    .version("1.0")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            String qrCodeData = objectMapper.writeValueAsString(qrData);
            return generateQRCodeImage(qrCodeData);
            
        } catch (Exception e) {
            log.error("Error generating QR code for merchant: {}", merchantId, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    public String generatePaymentQRCode(String merchantId, BigDecimal amount, String currency, String description) {
        log.info("Generating payment QR code for merchant: {} amount: {}", merchantId, amount);
        
        try {
            QRCodeData qrData = QRCodeData.builder()
                    .type("PAYMENT_REQUEST")
                    .merchantId(merchantId)
                    .amount(amount)
                    .currency(currency)
                    .description(description)
                    .paymentUrl(baseUrl + "/pay/" + merchantId + "?amount=" + amount + "&currency=" + currency)
                    .version("1.0")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            String qrCodeData = objectMapper.writeValueAsString(qrData);
            return generateQRCodeImage(qrCodeData);
            
        } catch (Exception e) {
            log.error("Error generating payment QR code for merchant: {}", merchantId, e);
            throw new RuntimeException("Failed to generate payment QR code", e);
        }
    }

    public String generateDynamicQRCode(String merchantId, String orderId, BigDecimal amount, 
                                       String currency, String description, Map<String, Object> metadata) {
        log.info("Generating dynamic QR code for merchant: {} order: {}", merchantId, orderId);
        
        try {
            String paymentId = "QR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            
            QRCodeData qrData = QRCodeData.builder()
                    .type("DYNAMIC_PAYMENT")
                    .merchantId(merchantId)
                    .paymentId(paymentId)
                    .orderId(orderId)
                    .amount(amount)
                    .currency(currency)
                    .description(description)
                    .metadata(metadata)
                    .paymentUrl(baseUrl + "/qr-pay/" + paymentId)
                    .expiresAt(LocalDateTime.now().plusHours(24)) // 24 hour expiry
                    .version("1.0")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            String qrCodeData = objectMapper.writeValueAsString(qrData);
            return generateQRCodeImage(qrCodeData);
            
        } catch (Exception e) {
            log.error("Error generating dynamic QR code for merchant: {}", merchantId, e);
            throw new RuntimeException("Failed to generate dynamic QR code", e);
        }
    }

    public String generateTableQRCode(String merchantId, String tableNumber, String location) {
        log.info("Generating table QR code for merchant: {} table: {}", merchantId, tableNumber);
        
        try {
            QRCodeData qrData = QRCodeData.builder()
                    .type("TABLE_PAYMENT")
                    .merchantId(merchantId)
                    .tableNumber(tableNumber)
                    .location(location)
                    .paymentUrl(baseUrl + "/table-pay/" + merchantId + "?table=" + tableNumber)
                    .version("1.0")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            String qrCodeData = objectMapper.writeValueAsString(qrData);
            return generateQRCodeImage(qrCodeData);
            
        } catch (Exception e) {
            log.error("Error generating table QR code for merchant: {}", merchantId, e);
            throw new RuntimeException("Failed to generate table QR code", e);
        }
    }

    public String generateTipQRCode(String merchantId, String employeeId, String employeeName) {
        log.info("Generating tip QR code for merchant: {} employee: {}", merchantId, employeeId);
        
        try {
            QRCodeData qrData = QRCodeData.builder()
                    .type("TIP_PAYMENT")
                    .merchantId(merchantId)
                    .employeeId(employeeId)
                    .employeeName(employeeName)
                    .paymentUrl(baseUrl + "/tip/" + merchantId + "?employee=" + employeeId)
                    .version("1.0")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            String qrCodeData = objectMapper.writeValueAsString(qrData);
            return generateQRCodeImage(qrCodeData);
            
        } catch (Exception e) {
            log.error("Error generating tip QR code for merchant: {}", merchantId, e);
            throw new RuntimeException("Failed to generate tip QR code", e);
        }
    }

    public String generateBulkPaymentQRCode(String merchantId, String batchId, 
                                          BigDecimal totalAmount, String currency) {
        log.info("Generating bulk payment QR code for merchant: {} batch: {}", merchantId, batchId);
        
        try {
            QRCodeData qrData = QRCodeData.builder()
                    .type("BULK_PAYMENT")
                    .merchantId(merchantId)
                    .batchId(batchId)
                    .amount(totalAmount)
                    .currency(currency)
                    .paymentUrl(baseUrl + "/bulk-pay/" + batchId)
                    .version("1.0")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            String qrCodeData = objectMapper.writeValueAsString(qrData);
            return generateQRCodeImage(qrCodeData);
            
        } catch (Exception e) {
            log.error("Error generating bulk payment QR code for merchant: {}", merchantId, e);
            throw new RuntimeException("Failed to generate bulk payment QR code", e);
        }
    }

    private String generateQRCodeImage(String data) throws Exception {
        // Set QR code parameters
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        // Generate QR code
        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize, hints);

        // Create image
        BufferedImage qrImage = new BufferedImage(qrCodeSize, qrCodeSize, BufferedImage.TYPE_INT_RGB);
        qrImage.createGraphics();

        Graphics2D graphics = (Graphics2D) qrImage.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, qrCodeSize, qrCodeSize);
        graphics.setColor(Color.BLACK);

        // Draw QR code
        for (int i = 0; i < bitMatrix.getWidth(); i++) {
            for (int j = 0; j < bitMatrix.getHeight(); j++) {
                if (bitMatrix.get(i, j)) {
                    graphics.fillRect(i, j, 1, 1);
                }
            }
        }

        // Add logo in center (optional)
        addLogoToQRCode(graphics, qrImage);

        // Convert to base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();
        
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private void addLogoToQRCode(Graphics2D graphics, BufferedImage qrImage) {
        try {
            // Calculate logo size (10% of QR code size)
            int logoSize = qrCodeSize / 10;
            int logoX = (qrCodeSize - logoSize) / 2;
            int logoY = (qrCodeSize - logoSize) / 2;

            // Create a simple circular logo placeholder
            graphics.setColor(Color.WHITE);
            graphics.fillOval(logoX - 5, logoY - 5, logoSize + 10, logoSize + 10);
            
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(2));
            graphics.drawOval(logoX - 5, logoY - 5, logoSize + 10, logoSize + 10);
            
            // Add "W" for Waqiti
            graphics.setFont(new Font("Arial", Font.BOLD, logoSize / 2));
            FontMetrics fm = graphics.getFontMetrics();
            int textX = logoX + (logoSize - fm.stringWidth("W")) / 2;
            int textY = logoY + ((logoSize - fm.getHeight()) / 2) + fm.getAscent();
            graphics.drawString("W", textX, textY);
            
        } catch (Exception e) {
            log.warn("Failed to add logo to QR code", e);
            // Continue without logo if there's an error
        }
    }

    public QRCodeData parseQRCodeData(String qrCodeData) {
        try {
            return objectMapper.readValue(qrCodeData, QRCodeData.class);
        } catch (Exception e) {
            log.error("Error parsing QR code data: {}", qrCodeData, e);
            throw new RuntimeException("Invalid QR code data", e);
        }
    }

    public boolean validateQRCode(String qrCodeData) {
        try {
            QRCodeData data = parseQRCodeData(qrCodeData);
            
            // Check version compatibility
            if (!"1.0".equals(data.getVersion())) {
                log.warn("Unsupported QR code version: {}", data.getVersion());
                return false;
            }
            
            // Check expiration
            if (data.getExpiresAt() != null && LocalDateTime.now().isAfter(data.getExpiresAt())) {
                log.warn("QR code has expired: {}", data.getExpiresAt());
                return false;
            }
            
            // Validate required fields based on type
            switch (data.getType()) {
                case "MERCHANT_PAYMENT":
                case "TABLE_PAYMENT":
                case "TIP_PAYMENT":
                    return data.getMerchantId() != null && !data.getMerchantId().isEmpty();
                    
                case "PAYMENT_REQUEST":
                case "DYNAMIC_PAYMENT":
                    return data.getMerchantId() != null && !data.getMerchantId().isEmpty() &&
                           data.getAmount() != null && data.getAmount().compareTo(BigDecimal.ZERO) > 0;
                           
                case "BULK_PAYMENT":
                    return data.getMerchantId() != null && !data.getMerchantId().isEmpty() &&
                           data.getBatchId() != null && !data.getBatchId().isEmpty();
                           
                default:
                    log.warn("Unknown QR code type: {}", data.getType());
                    return false;
            }
            
        } catch (Exception e) {
            log.error("Error validating QR code", e);
            return false;
        }
    }

    public String generateQRCodeImageUrl(String qrCodeData) {
        // This would typically upload to a CDN and return the URL
        // For now, return a placeholder URL
        String encodedData = Base64.getUrlEncoder().encodeToString(qrCodeData.getBytes());
        return baseUrl + "/api/v1/qr-codes/image/" + encodedData;
    }
}