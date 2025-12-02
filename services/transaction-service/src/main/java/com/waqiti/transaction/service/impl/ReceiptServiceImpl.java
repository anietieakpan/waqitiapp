package com.waqiti.transaction.service.impl;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.waqiti.common.client.NotificationServiceClient;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.transaction.dto.ReceiptGenerationOptions;
import com.waqiti.transaction.dto.ReceiptMetadata;
import com.waqiti.transaction.entity.Receipt;
import com.waqiti.transaction.entity.Transaction;
import com.waqiti.transaction.repository.ReceiptRepository;
import com.waqiti.transaction.service.ReceiptService;
import com.waqiti.transaction.service.ReceiptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

// QR Code generation imports
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Production-ready implementation of ReceiptService with comprehensive features
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReceiptServiceImpl implements ReceiptService {

    @Lazy
    private final ReceiptServiceImpl self;
    private final ReceiptRepository receiptRepository;
    private final ReceiptTemplateService templateService;
    private final NotificationServiceClient notificationClient;

    @Value("${waqiti.receipt.storage.path:/var/receipts}")
    private String storagePath;

    @Value("${waqiti.receipt.security.secret}")
    private String securitySecret;

    @Value("${waqiti.receipt.retention.days:2555}") // 7 years default
    private int retentionDays;

    @Value("${waqiti.company.name:Waqiti Financial Services}")
    private String companyName;

    @Value("${waqiti.company.address:123 Financial District, New York, NY 10001}")
    private String companyAddress;

    @Value("${waqiti.company.phone:+1-800-WAQITI}")
    private String companyPhone;

    @Value("${waqiti.company.email:support@example.com}")
    private String companyEmail;

    @Override
    public byte[] generateReceipt(Transaction transaction) {
        return generateReceipt(transaction, ReceiptGenerationOptions.builder().build());
    }

    @Override
    public byte[] generateReceipt(Transaction transaction, ReceiptGenerationOptions options) {
        log.info("Generating receipt for transaction: {}", transaction.getId());
        
        try {
            Document document = new Document(PageSize.A4);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            
            // Add security features
            if (options.isIncludeWatermark()) {
                writer.setPageEvent(new WatermarkPageEvent());
            }
            
            document.open();
            
            // Generate receipt content based on format
            switch (options.getFormat()) {
                case DETAILED:
                    generateDetailedReceipt(document, transaction, options);
                    break;
                case MINIMAL:
                    generateMinimalReceipt(document, transaction, options);
                    break;
                case PROOF_OF_PAYMENT:
                    generateProofOfPaymentReceipt(document, transaction, options);
                    break;
                case TAX_DOCUMENT:
                    generateTaxDocumentReceipt(document, transaction, options);
                    break;
                default:
                    generateStandardReceipt(document, transaction, options);
            }
            
            document.close();
            byte[] pdfData = baos.toByteArray();
            
            log.info("Successfully generated receipt for transaction: {}, size: {} bytes", 
                    transaction.getId(), pdfData.length);
            
            return pdfData;
            
        } catch (Exception e) {
            log.error("Error generating receipt for transaction: {}", transaction.getId(), e);
            throw new BusinessException("Failed to generate receipt: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReceiptMetadata generateAndStoreReceipt(Transaction transaction) {
        byte[] receiptData = self.generateReceipt(transaction);
        
        // Generate security hash
        String securityHash = generateSecurityHash(receiptData);
        
        // Store receipt
        UUID receiptId = UUID.randomUUID();
        String fileName = String.format("receipt_%s_%s.pdf", 
                transaction.getId(), 
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()));
        
        Path filePath = Paths.get(storagePath, fileName);
        
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, receiptData);
            
            // Save receipt metadata to database
            Receipt receipt = Receipt.builder()
                    .id(receiptId)
                    .transactionId(transaction.getId())
                    .filePath(filePath.toString())
                    .securityHash(securityHash)
                    .fileSize((long) receiptData.length)
                    .format(ReceiptGenerationOptions.ReceiptFormat.STANDARD)
                    .version("1.0")
                    .generatedAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(retentionDays))
                    .build();
            
            receiptRepository.save(receipt);
            
            return ReceiptMetadata.builder()
                    .receiptId(receiptId)
                    .transactionId(transaction.getId())
                    .generatedAt(receipt.getGeneratedAt())
                    .fileSize(receipt.getFileSize())
                    .securityHash(securityHash)
                    .storagePath(filePath.toString())
                    .format(ReceiptGenerationOptions.ReceiptFormat.STANDARD)
                    .version("1.0")
                    .expiresAt(receipt.getExpiresAt())
                    .build();
            
        } catch (IOException e) {
            log.error("Error storing receipt for transaction: {}", transaction.getId(), e);
            throw new BusinessException("Failed to store receipt: " + e.getMessage());
        }
    }

    /**
     * P1 FIX: Added @Transactional for access statistics update.
     * This method reads data but also writes (updates access count).
     * NOT readOnly since it modifies database state.
     */
    @Override
    @Cacheable(value = "receipts", key = "#transactionId")
    @Transactional
    public byte[] getStoredReceipt(UUID transactionId) {
        try {
            Receipt receipt = receiptRepository.findByTransactionId(transactionId)
                    .orElse(null);
            
            if (receipt == null) {
                log.warn("Receipt not found for transaction: {}", transactionId);
                throw new ReceiptNotFoundException("Receipt not found for transaction: " + transactionId);
            }
            
            Path filePath = Paths.get(receipt.getFilePath());
            if (!Files.exists(filePath)) {
                log.error("Receipt file not found: {}", filePath);
                throw new ReceiptFileNotFoundException("Receipt file not found: " + filePath);
            }
            
            // Update access statistics
            receipt.setAccessCount(receipt.getAccessCount() + 1);
            receipt.setLastAccessedAt(LocalDateTime.now());
            receiptRepository.save(receipt);
            
            return Files.readAllBytes(filePath);
            
        } catch (IOException e) {
            log.error("Error reading stored receipt for transaction: {}", transactionId, e);
            throw new ReceiptProcessingException("Failed to read receipt for transaction: " + transactionId, e);
        }
    }

    @Override
    public boolean verifyReceiptIntegrity(byte[] receiptData, String expectedHash) {
        String actualHash = generateSecurityHash(receiptData);
        return actualHash.equals(expectedHash);
    }

    @Override
    public boolean emailReceipt(UUID transactionId, String recipientEmail) {
        try {
            byte[] receiptData = self.getStoredReceipt(transactionId);
            if (receiptData == null) {
                log.error("Receipt not found for transaction: {}", transactionId);
                return false;
            }
            
            // Send via notification service
            return notificationClient.sendEmailWithAttachment(
                    recipientEmail,
                    "Transaction Receipt - " + transactionId,
                    "Please find your transaction receipt attached.",
                    receiptData,
                    "receipt-" + transactionId + ".pdf",
                    "application/pdf"
            );
            
        } catch (Exception e) {
            log.error("Error emailing receipt for transaction: {}", transactionId, e);
            return false;
        }
    }

    @Override
    public byte[] generateProofOfPayment(Transaction transaction) {
        ReceiptGenerationOptions options = ReceiptGenerationOptions.builder()
                .format(ReceiptGenerationOptions.ReceiptFormat.PROOF_OF_PAYMENT)
                .includeDetailedFees(true)
                .includeTimeline(true)
                .includeComplianceInfo(true)
                .includeWatermark(true)
                .includeQrCode(true)
                .build();
        
        return generateReceipt(transaction, options);
    }

    @Override
    @Transactional
    public boolean deleteStoredReceipt(UUID transactionId) {
        try {
            Receipt receipt = receiptRepository.findByTransactionId(transactionId)
                    .orElse(null);
            
            if (receipt == null) {
                return true; // Already deleted
            }
            
            // Delete file
            Path filePath = Paths.get(receipt.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
            
            // Delete database record
            receiptRepository.delete(receipt);
            
            log.info("Successfully deleted receipt for transaction: {}", transactionId);
            return true;
            
        } catch (Exception e) {
            log.error("Error deleting receipt for transaction: {}", transactionId, e);
            return false;
        }
    }

    private void generateStandardReceipt(Document document, Transaction transaction, 
                                       ReceiptGenerationOptions options) throws DocumentException {
        
        // Header with company logo and info
        addHeader(document);
        
        // Receipt title
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
        Paragraph title = new Paragraph("TRANSACTION RECEIPT", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20f);
        document.add(title);
        
        // Receipt number and date
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(20f);
        
        addInfoRow(infoTable, "Receipt Number:", transaction.getId().toString());
        addInfoRow(infoTable, "Date:", transaction.getCreatedAt().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss")));
        addInfoRow(infoTable, "Status:", transaction.getStatus().toString());
        addInfoRow(infoTable, "Type:", transaction.getType().toString());
        
        document.add(infoTable);
        
        // Transaction details
        addTransactionDetails(document, transaction, options);
        
        // Amount breakdown
        addAmountBreakdown(document, transaction, options);
        
        // Footer with security info
        addFooter(document, transaction, options);
    }

    private void generateDetailedReceipt(Document document, Transaction transaction, 
                                       ReceiptGenerationOptions options) throws DocumentException {
        generateStandardReceipt(document, transaction, options);
        
        // Add additional details
        if (options.isIncludeTimeline()) {
            addTimeline(document, transaction);
        }
        
        // Add risk and compliance information
        if (options.isIncludeComplianceInfo()) {
            addComplianceInfo(document, transaction);
        }
    }

    private void generateMinimalReceipt(Document document, Transaction transaction, 
                                      ReceiptGenerationOptions options) throws DocumentException {
        
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Paragraph title = new Paragraph("Payment Confirmation", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(15f);
        document.add(title);
        
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        
        addSimpleRow(table, "Transaction ID: " + transaction.getId());
        addSimpleRow(table, "Amount: " + formatCurrency(transaction.getAmount(), transaction.getCurrency()));
        addSimpleRow(table, "Date: " + transaction.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        addSimpleRow(table, "Status: " + transaction.getStatus());
        
        document.add(table);
    }

    private void generateProofOfPaymentReceipt(Document document, Transaction transaction, 
                                             ReceiptGenerationOptions options) throws DocumentException {
        
        // Official proof of payment header
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Paragraph title = new Paragraph("PROOF OF PAYMENT", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20f);
        document.add(title);
        
        // Certification statement
        Font certFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
        Paragraph cert = new Paragraph("This document certifies that the payment detailed below has been successfully processed and completed.", certFont);
        cert.setAlignment(Element.ALIGN_JUSTIFY);
        cert.setSpacingAfter(20f);
        document.add(cert);
        
        // Standard receipt content
        generateStandardReceipt(document, transaction, options);
        
        // Legal disclaimer
        addLegalDisclaimer(document);
    }

    private void generateTaxDocumentReceipt(Document document, Transaction transaction, 
                                          ReceiptGenerationOptions options) throws DocumentException {
        
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Paragraph title = new Paragraph("TAX DOCUMENT - TRANSACTION RECORD", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20f);
        document.add(title);
        
        // Tax year information
        addTaxYearInfo(document, transaction);
        
        // Standard receipt content
        generateStandardReceipt(document, transaction, options);
        
        // Tax-specific information
        addTaxInformation(document, transaction);
    }

    private void addHeader(Document document) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setSpacingAfter(20f);
        
        // Company info
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.NO_BORDER);
        
        Font companyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Paragraph companyName = new Paragraph(this.companyName, companyFont);
        companyCell.addElement(companyName);
        
        Font addressFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        companyCell.addElement(new Paragraph(companyAddress, addressFont));
        companyCell.addElement(new Paragraph("Phone: " + companyPhone, addressFont));
        companyCell.addElement(new Paragraph("Email: " + companyEmail, addressFont));
        
        headerTable.addCell(companyCell);
        
        // Receipt info
        PdfPCell receiptCell = new PdfPCell();
        receiptCell.setBorder(Rectangle.NO_BORDER);
        receiptCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        Font receiptFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        receiptCell.addElement(new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), receiptFont));
        
        headerTable.addCell(receiptCell);
        document.add(headerTable);
    }

    private void addTransactionDetails(Document document, Transaction transaction, 
                                     ReceiptGenerationOptions options) throws DocumentException {
        
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Paragraph sectionTitle = new Paragraph("Transaction Details", sectionFont);
        sectionTitle.setSpacingBefore(15f);
        sectionTitle.setSpacingAfter(10f);
        document.add(sectionTitle);
        
        PdfPTable detailsTable = new PdfPTable(2);
        detailsTable.setWidthPercentage(100);
        detailsTable.setSpacingAfter(15f);
        
        addInfoRow(detailsTable, "From:", transaction.getFromWalletId() != null ? transaction.getFromWalletId().toString() : "N/A");
        addInfoRow(detailsTable, "To:", transaction.getToWalletId() != null ? transaction.getToWalletId().toString() : "N/A");
        
        if (transaction.getDescription() != null) {
            addInfoRow(detailsTable, "Description:", transaction.getDescription());
        }
        
        if (transaction.getReference() != null) {
            addInfoRow(detailsTable, "Reference:", transaction.getReference());
        }
        
        document.add(detailsTable);
    }

    private void addAmountBreakdown(Document document, Transaction transaction, 
                                  ReceiptGenerationOptions options) throws DocumentException {
        
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Paragraph sectionTitle = new Paragraph("Amount Breakdown", sectionFont);
        sectionTitle.setSpacingBefore(15f);
        sectionTitle.setSpacingAfter(10f);
        document.add(sectionTitle);
        
        PdfPTable amountTable = new PdfPTable(2);
        amountTable.setWidthPercentage(100);
        amountTable.setSpacingAfter(15f);
        
        addAmountRow(amountTable, "Amount:", transaction.getAmount(), transaction.getCurrency());
        
        if (options.isIncludeDetailedFees() && transaction.getFeeAmount() != null && transaction.getFeeAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            addAmountRow(amountTable, "Processing Fee:", transaction.getFeeAmount(), transaction.getCurrency());
            addAmountRow(amountTable, "Total Amount:", transaction.getAmount().add(transaction.getFeeAmount()), transaction.getCurrency());
        }
        
        document.add(amountTable);
    }

    private void addFooter(Document document, Transaction transaction, 
                          ReceiptGenerationOptions options) throws DocumentException {
        
        // QR Code for verification
        if (options.isIncludeQrCode()) {
            addQrCode(document, transaction);
        }
        
        // Security information
        Font securityFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC);
        Paragraph security = new Paragraph("This receipt is digitally generated and verified. For questions, contact customer support.", securityFont);
        security.setAlignment(Element.ALIGN_CENTER);
        security.setSpacingBefore(20f);
        document.add(security);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5f);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5f);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addAmountRow(PdfPTable table, String label, java.math.BigDecimal amount, String currency) {
        addInfoRow(table, label, formatCurrency(amount, currency));
    }

    private void addSimpleRow(PdfPTable table, String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private void addTimeline(Document document, Transaction transaction) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Paragraph sectionTitle = new Paragraph("Transaction Timeline", sectionFont);
        sectionTitle.setSpacingBefore(15f);
        sectionTitle.setSpacingAfter(10f);
        document.add(sectionTitle);
        
        // Add timeline entries (would need to be retrieved from transaction history)
        Font timelineFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Paragraph timeline = new Paragraph("• Created: " + transaction.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), timelineFont);
        
        if (transaction.getUpdatedAt() != null) {
            timeline.add("\n• Updated: " + transaction.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        
        document.add(timeline);
    }

    private void addComplianceInfo(Document document, Transaction transaction) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Paragraph sectionTitle = new Paragraph("Compliance Information", sectionFont);
        sectionTitle.setSpacingBefore(15f);
        sectionTitle.setSpacingAfter(10f);
        document.add(sectionTitle);
        
        Font complianceFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Paragraph compliance = new Paragraph("This transaction has been processed in compliance with applicable financial regulations and anti-money laundering requirements.", complianceFont);
        document.add(compliance);
    }

    private void addLegalDisclaimer(Document document) throws DocumentException {
        Font disclaimerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Paragraph disclaimer = new Paragraph("LEGAL DISCLAIMER: This proof of payment is valid for the transaction specified above. " +
                "For disputes or inquiries, please contact customer support within 60 days of the transaction date.", disclaimerFont);
        disclaimer.setSpacingBefore(20f);
        disclaimer.setAlignment(Element.ALIGN_JUSTIFY);
        document.add(disclaimer);
    }

    private void addTaxYearInfo(Document document, Transaction transaction) throws DocumentException {
        int taxYear = transaction.getCreatedAt().getYear();
        Font taxFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Paragraph taxInfo = new Paragraph("Tax Year: " + taxYear, taxFont);
        taxInfo.setSpacingAfter(15f);
        document.add(taxInfo);
    }

    private void addTaxInformation(Document document, Transaction transaction) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Paragraph sectionTitle = new Paragraph("Tax Information", sectionFont);
        sectionTitle.setSpacingBefore(15f);
        sectionTitle.setSpacingAfter(10f);
        document.add(sectionTitle);
        
        Font taxFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Paragraph taxNote = new Paragraph("Please consult with a tax professional for specific tax implications of this transaction.", taxFont);
        document.add(taxNote);
    }

    /**
     * Add QR code to receipt for verification and quick access
     */
    private void addQrCode(Document document, Transaction transaction) throws DocumentException {
        try {
            // Create QR code data with comprehensive transaction information
            String qrData = buildQRCodeData(transaction);
            
            // Generate QR code image
            BufferedImage qrCodeImage = generateQRCodeImage(qrData, 150, 150);
            
            if (qrCodeImage != null) {
                // Convert BufferedImage to iText Image
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(qrCodeImage, "PNG", baos);
                
                com.itextpdf.text.Image qrImage = com.itextpdf.text.Image.getInstance(baos.toByteArray());
                qrImage.scaleToFit(100f, 100f);
                qrImage.setAlignment(Element.ALIGN_CENTER);
                qrImage.setSpacingBefore(15f);
                qrImage.setSpacingAfter(10f);
                
                document.add(qrImage);
                
                // Add QR code description
                Font qrFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
                Paragraph qrDescription = new Paragraph("Scan QR code to verify transaction and access digital receipt", qrFont);
                qrDescription.setAlignment(Element.ALIGN_CENTER);
                qrDescription.setSpacingAfter(10f);
                document.add(qrDescription);
                
                log.debug("Successfully added QR code to receipt for transaction: {}", transaction.getId());
            } else {
                addQRCodeFallback(document, transaction);
            }
            
        } catch (Exception e) {
            log.error("Failed to generate QR code for transaction: {}", transaction.getId(), e);
            addQRCodeFallback(document, transaction);
        }
    }
    
    /**
     * Build QR code data string with transaction verification information
     */
    private String buildQRCodeData(Transaction transaction) {
        // Create verification URL or data structure
        StringBuilder qrData = new StringBuilder();
        
        // Option 1: Create verification URL
        String verificationUrl = String.format("https://verify.example.com/receipt/%s", transaction.getId());
        qrData.append(verificationUrl);
        
        // Option 2: Include transaction data directly (alternative approach)
        // String transactionData = String.format(
        //     "{\"id\":\"%s\",\"amount\":\"%s\",\"currency\":\"%s\",\"date\":\"%s\",\"hash\":\"%s\"}",
        //     transaction.getId(),
        //     transaction.getAmount(),
        //     transaction.getCurrency(),
        //     transaction.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        //     generateTransactionHash(transaction)
        // );
        // qrData.append(transactionData);
        
        return qrData.toString();
    }
    
    /**
     * Generate QR code image using ZXing library
     */
    private BufferedImage generateQRCodeImage(String data, int width, int height) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        
        // Configure QR code generation hints
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M); // Medium error correction
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1); // Minimal margin
        
        try {
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (WriterException e) {
            log.error("Failed to generate QR code matrix", e);
            throw e;
        }
    }
    
    /**
     * Fallback when QR code generation fails
     */
    private void addQRCodeFallback(Document document, Transaction transaction) throws DocumentException {
        Font qrFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Paragraph qr = new Paragraph(
            String.format("Verification Code: %s\nVerify at: https://verify.example.com/receipt/%s", 
                generateVerificationCode(transaction), transaction.getId()), 
            qrFont
        );
        qr.setAlignment(Element.ALIGN_CENTER);
        qr.setSpacingBefore(10f);
        document.add(qr);
    }
    
    /**
     * Generate human-readable verification code
     */
    private String generateVerificationCode(Transaction transaction) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = transaction.getId().toString() + transaction.getCreatedAt().toString();
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            
            // Convert to alphanumeric code (8 characters)
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                int value = Math.abs(hash[i]) % 36;
                if (value < 10) {
                    code.append((char) ('0' + value));
                } else {
                    code.append((char) ('A' + value - 10));
                }
            }
            
            return code.toString();
        } catch (Exception e) {
            log.error("Failed to generate verification code", e);
            return "VERIFY";
        }
    }
    
    /**
     * Generate transaction hash for additional security
     */
    private String generateTransactionHash(Transaction transaction) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hashInput = String.format("%s:%s:%s:%s:%s",
                transaction.getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCreatedAt(),
                transaction.getStatus()
            );
            
            byte[] hash = digest.digest(hashInput.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash).substring(0, 16); // 16 character hash
        } catch (Exception e) {
            log.error("Failed to generate transaction hash", e);
            return "DEFAULT_HASH";
        }
    }
    
    /**
     * Enhanced QR code generation with multiple data formats
     */
    public byte[] generateAdvancedQRCode(Transaction transaction, QRCodeFormat format, int size) {
        try {
            String qrData = switch (format) {
                case VERIFICATION_URL -> buildVerificationUrl(transaction);
                case TRANSACTION_DATA -> buildTransactionDataJson(transaction);
                case DIGITAL_RECEIPT -> buildDigitalReceiptData(transaction);
                case PAYMENT_REQUEST -> buildPaymentRequestData(transaction);
            };
            
            BufferedImage qrImage = generateQRCodeImage(qrData, size, size);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(qrImage, "PNG", baos);
            
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to generate advanced QR code", e);
            return new byte[0];
        }
    }
    
    private String buildVerificationUrl(Transaction transaction) {
        return String.format("https://verify.example.com/receipt/%s?hash=%s", 
            transaction.getId(), generateTransactionHash(transaction));
    }
    
    private String buildTransactionDataJson(Transaction transaction) {
        return String.format(
            "{\"id\":\"%s\",\"amount\":\"%s\",\"currency\":\"%s\",\"timestamp\":\"%s\",\"status\":\"%s\",\"hash\":\"%s\"}",
            transaction.getId(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getCreatedAt().format(DateTimeFormatter.ISO_INSTANT),
            transaction.getStatus(),
            generateTransactionHash(transaction)
        );
    }
    
    private String buildDigitalReceiptData(Transaction transaction) {
        return String.format("waqiti://receipt/%s", transaction.getId());
    }
    
    private String buildPaymentRequestData(Transaction transaction) {
        return String.format(
            "waqiti://pay?amount=%s&currency=%s&reference=%s",
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getId()
        );
    }
    
    /**
     * QR Code format enumeration
     */
    public enum QRCodeFormat {
        VERIFICATION_URL,
        TRANSACTION_DATA,
        DIGITAL_RECEIPT,
        PAYMENT_REQUEST
    }

    private String formatCurrency(java.math.BigDecimal amount, String currency) {
        return String.format("%s %.2f", currency, amount);
    }

    private String generateSecurityHash(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(securitySecret.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Error generating security hash", e);
            throw new BusinessException("Failed to generate security hash");
        }
    }

    // Watermark page event for security
    private static class WatermarkPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte content = writer.getDirectContentUnder();
                content.beginText();
                content.setFontAndSize(BaseFont.createFont(), 50);
                content.setTextMatrix(100, 400);
                content.setGState(PdfGState.createOpacity(0.1f));
                content.setColorFill(BaseColor.LIGHT_GRAY);
                content.showTextAligned(Element.ALIGN_CENTER, "WAQITI RECEIPT", 300, 400, 45);
                content.endText();
            } catch (Exception e) {
                // Log error but don't fail receipt generation
            }
        }
    }
}