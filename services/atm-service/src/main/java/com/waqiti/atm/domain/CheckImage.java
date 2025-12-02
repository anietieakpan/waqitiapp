package com.waqiti.atm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "check_images")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deposit_id", nullable = false)
    private UUID depositId;

    @Column(name = "check_number")
    private Integer checkNumber;

    @Lob
    @Column(name = "front_image", nullable = false)
    private byte[] frontImage;

    @Lob
    @Column(name = "back_image", nullable = false)
    private byte[] backImage;

    @Column(name = "image_format", length = 10)
    private String imageFormat;

    @Column(name = "front_image_size")
    private Integer frontImageSize;

    @Column(name = "back_image_size")
    private Integer backImageSize;

    @Column(name = "routing_number", length = 9)
    private String routingNumber;

    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Column(name = "courtesy_amount", precision = 19, scale = 2)
    private BigDecimal courtesyAmount;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "processing_status")
    @Enumerated(EnumType.STRING)
    private ProcessingStatus processingStatus;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    public enum ProcessingStatus {
        CAPTURED, OCR_COMPLETED, PENDING_REVIEW, PROCESSED, REJECTED
    }
}
