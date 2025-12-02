package com.waqiti.merchant.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * Store entity representing individual merchant store locations.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Entity
@Table(name = "merchant_stores", indexes = {
    @Index(name = "idx_stores_merchant", columnList = "merchant_id"),
    @Index(name = "idx_stores_status", columnList = "status"),
    @Index(name = "idx_stores_type", columnList = "store_type"),
    @Index(name = "idx_stores_location", columnList = "city, country")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Store {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "store_type", nullable = false)
    private StoreType storeType;
    
    @Embedded
    private StoreAddress address;
    
    @Embedded
    private StoreContactInfo contactInfo;
    
    @Embedded
    private OperatingHours operatingHours;
    
    @Column(name = "timezone", nullable = false)
    private ZoneId timezone;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StoreStatus status;
    
    @Embedded
    private StoreSettings settings;
    
    @Embedded
    private StoreBranding branding;
    
    @ElementCollection
    @CollectionTable(name = "store_payment_methods", joinColumns = @JoinColumn(name = "store_id"))
    @Column(name = "payment_method")
    @Enumerated(EnumType.STRING)
    private Set<PaymentMethod> paymentMethods;
    
    @ElementCollection
    @CollectionTable(name = "store_categories", joinColumns = @JoinColumn(name = "store_id"))
    @Column(name = "category")
    private Set<String> categories;
    
    @Column(name = "total_sales", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;
    
    @Column(name = "total_transactions")
    @Builder.Default
    private Long totalTransactions = 0L;
    
    @Column(name = "average_transaction_value", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal averageTransactionValue = BigDecimal.ZERO;
    
    @Column(name = "last_transaction_at")
    private Instant lastTransactionAt;
    
    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<StoreInventory> inventory;
    
    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<StoreEmployee> employees;
    
    @ElementCollection
    @CollectionTable(name = "store_metadata", joinColumns = @JoinColumn(name = "store_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if store is currently open based on operating hours.
     */
    public boolean isCurrentlyOpen() {
        if (operatingHours == null) return false;
        return operatingHours.isCurrentlyOpen(timezone);
    }
    
    /**
     * Update sales statistics after a transaction.
     */
    public void updateTransactionStats(BigDecimal transactionAmount) {
        this.totalSales = this.totalSales.add(transactionAmount);
        this.totalTransactions = this.totalTransactions + 1;
        this.averageTransactionValue = this.totalSales.divide(
            BigDecimal.valueOf(this.totalTransactions), 2, java.math.RoundingMode.HALF_UP);
        this.lastTransactionAt = Instant.now();
    }
}

/**
 * Store address information.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class StoreAddress {
    
    @Column(name = "street_address")
    private String streetAddress;
    
    @Column(name = "city")
    private String city;
    
    @Column(name = "state_province")
    private String stateProvince;
    
    @Column(name = "postal_code")
    private String postalCode;
    
    @Column(name = "country")
    private String country;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    public String getFormattedAddress() {
        return String.format("%s, %s, %s %s, %s", 
            streetAddress, city, stateProvince, postalCode, country);
    }
}

/**
 * Store contact information.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class StoreContactInfo {
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "email")
    private String email;
    
    @Column(name = "website")
    private String website;
    
    @Column(name = "social_media_handles", length = 1000)
    private String socialMediaHandles; // JSON string
}

/**
 * Store operating hours.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class OperatingHours {
    
    @Column(name = "monday_hours")
    private String mondayHours;
    
    @Column(name = "tuesday_hours")
    private String tuesdayHours;
    
    @Column(name = "wednesday_hours")
    private String wednesdayHours;
    
    @Column(name = "thursday_hours")
    private String thursdayHours;
    
    @Column(name = "friday_hours")
    private String fridayHours;
    
    @Column(name = "saturday_hours")
    private String saturdayHours;
    
    @Column(name = "sunday_hours")
    private String sundayHours;
    
    @Column(name = "holiday_hours")
    private String holidayHours;
    
    @Column(name = "special_hours", length = 1000)
    private String specialHours; // JSON for special dates
    
    public boolean isCurrentlyOpen(ZoneId timezone) {
        // Implementation would check current time against operating hours
        return true; // Simplified for now
    }
}

/**
 * Store settings and configuration.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class StoreSettings {
    
    @Column(name = "auto_accept_orders")
    @Builder.Default
    private boolean autoAcceptOrders = true;
    
    @Column(name = "order_preparation_time")
    @Builder.Default
    private Integer orderPreparationTime = 15; // minutes
    
    @Column(name = "delivery_enabled")
    private boolean deliveryEnabled;
    
    @Column(name = "pickup_enabled")
    @Builder.Default
    private boolean pickupEnabled = true;
    
    @Column(name = "online_ordering")
    @Builder.Default
    private boolean onlineOrdering = true;
    
    @Column(name = "inventory_tracking")
    @Builder.Default
    private boolean inventoryTracking = true;
    
    @Column(name = "loyalty_program")
    private boolean loyaltyProgram;
    
    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;
    
    @Column(name = "service_charge", precision = 5, scale = 4)
    private BigDecimal serviceCharge;
    
    @Column(name = "minimum_order_value", precision = 19, scale = 4)
    private BigDecimal minimumOrderValue;
    
    @Column(name = "maximum_order_value", precision = 19, scale = 4)
    private BigDecimal maximumOrderValue;
}

/**
 * Store branding information.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class StoreBranding {
    
    @Column(name = "logo_url")
    private String logoUrl;
    
    @Column(name = "banner_url")
    private String bannerUrl;
    
    @Column(name = "primary_color")
    private String primaryColor;
    
    @Column(name = "secondary_color")
    private String secondaryColor;
    
    @Column(name = "theme")
    private String theme;
    
    @Column(name = "custom_css", length = 5000)
    private String customCss;
}

/**
 * Store inventory item.
 */
@Data
@Entity
@Table(name = "store_inventory", indexes = {
    @Index(name = "idx_inventory_store", columnList = "store_id"),
    @Index(name = "idx_inventory_sku", columnList = "sku"),
    @Index(name = "idx_inventory_category", columnList = "category")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
class StoreInventory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;
    
    @Column(name = "sku", nullable = false)
    private String sku;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;
    
    @Column(name = "cost", precision = 19, scale = 4)
    private BigDecimal cost;
    
    @Column(name = "quantity_available")
    private Integer quantityAvailable;
    
    @Column(name = "quantity_reserved")
    @Builder.Default
    private Integer quantityReserved = 0;
    
    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;
    
    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "image_urls", length = 2000)
    private String imageUrls; // JSON array
    
    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

/**
 * Store employee information.
 */
@Data
@Entity
@Table(name = "store_employees", indexes = {
    @Index(name = "idx_employees_store", columnList = "store_id"),
    @Index(name = "idx_employees_role", columnList = "role")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
class StoreEmployee {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;
    
    @Column(name = "employee_id", nullable = false)
    private String employeeId;
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name", nullable = false)
    private String lastName;
    
    @Column(name = "email", nullable = false)
    private String email;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private EmployeeRole role;
    
    @Column(name = "permissions", length = 1000)
    private String permissions; // JSON array
    
    @Column(name = "hourly_wage", precision = 19, scale = 4)
    private BigDecimal hourlyWage;
    
    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "hire_date")
    private Instant hireDate;
    
    @Column(name = "last_login")
    private Instant lastLogin;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}

/**
 * Store type enumeration.
 */
enum StoreType {
    PHYSICAL,       // Brick and mortar store
    ONLINE,         // E-commerce only
    HYBRID,         // Both physical and online
    MOBILE,         // Food truck, mobile service
    POP_UP,         // Temporary location
    KIOSK,          // Small retail kiosk
    WAREHOUSE       // Fulfillment center
}

/**
 * Store status enumeration.
 */
enum StoreStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING_SETUP,
    TEMPORARILY_CLOSED,
    PERMANENTLY_CLOSED
}

/**
 * Payment method enumeration.
 */
enum PaymentMethod {
    CASH,
    CREDIT_CARD,
    DEBIT_CARD,
    DIGITAL_WALLET,
    BANK_TRANSFER,
    CRYPTOCURRENCY,
    BUY_NOW_PAY_LATER,
    GIFT_CARD,
    LOYALTY_POINTS
}

/**
 * Employee role enumeration.
 */
enum EmployeeRole {
    MANAGER,
    CASHIER,
    SALES_ASSOCIATE,
    INVENTORY_CLERK,
    CHEF,
    BARISTA,
    DELIVERY_DRIVER,
    CUSTOMER_SERVICE,
    ADMIN
}