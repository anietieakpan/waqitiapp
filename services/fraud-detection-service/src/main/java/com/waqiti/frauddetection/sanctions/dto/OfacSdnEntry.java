package com.waqiti.frauddetection.sanctions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for OFAC SDN (Specially Designated Nationals) List Entry.
 *
 * Represents an individual or entity on the OFAC sanctions list.
 *
 * Data Structure from OFAC API:
 * - Individual: Person designated under OFAC sanctions programs
 * - Entity: Organization, company, or vessel designated
 * - Aircraft: Sanctioned aircraft
 * - Vessel: Sanctioned vessels
 *
 * Sources:
 * - U.S. Treasury OFAC SDN List
 * - EU Consolidated Sanctions List
 * - UN Security Council Sanctions List
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfacSdnEntry {

    /**
     * Unique identifier from OFAC database
     */
    private Long uid;

    /**
     * SDN type: Individual, Entity, Aircraft, Vessel
     */
    private String sdnType;

    /**
     * First name (for individuals)
     */
    private String firstName;

    /**
     * Last name (for individuals)
     */
    private String lastName;

    /**
     * Full name (for all types)
     */
    private String fullName;

    /**
     * Also known as (aliases)
     */
    private List<String> aliases;

    /**
     * Title or position (e.g., "President", "CEO")
     */
    private String title;

    /**
     * OFAC sanctions programs this entry is designated under
     * Examples: SDGT, SYRIA, IRAN, VENEZUELA, UKRAINE-EO13662
     */
    private List<String> programs;

    /**
     * Additional remarks from OFAC
     */
    private String remarks;

    /**
     * Nationality or citizenship (ISO 3166 country codes)
     */
    private String nationality;

    /**
     * Date of birth (for individuals)
     */
    private LocalDate dateOfBirth;

    /**
     * Place of birth
     */
    private String placeOfBirth;

    /**
     * Known addresses
     */
    private List<String> addresses;

    /**
     * Countries associated with this entry
     */
    private List<String> countries;

    /**
     * Identification numbers (passport, national ID, tax ID, etc.)
     */
    private List<String> identificationNumbers;

    /**
     * Passport numbers
     */
    private List<String> passportNumbers;

    /**
     * National ID numbers
     */
    private List<String> nationalIdNumbers;

    /**
     * Tax ID numbers (TIN, EIN, VAT, etc.)
     */
    private List<String> taxIdNumbers;

    /**
     * Website URLs
     */
    private List<String> websites;

    /**
     * Email addresses
     */
    private List<String> emails;

    /**
     * Phone numbers
     */
    private List<String> phoneNumbers;

    /**
     * Source list name (OFAC_SDN, EU_SANCTIONS, UN_SANCTIONS)
     */
    private String listName;

    /**
     * Source authority (US_TREASURY_OFAC, EU_EXTERNAL_ACTION, UN_SECURITY_COUNCIL)
     */
    private String listSource;

    /**
     * List publication date
     */
    private LocalDate publicationDate;

    /**
     * Last update date
     */
    private LocalDate lastUpdateDate;

    /**
     * Additional metadata (JSON)
     */
    private java.util.Map<String, Object> metadata;

    /**
     * Helper method to get primary name (fullName if exists, otherwise firstName + lastName)
     */
    public String getName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (fullName != null) {
            return fullName;
        }
        return "";
    }

    /**
     * Helper method to get entry ID as string
     */
    public String getEntryId() {
        return uid != null ? String.valueOf(uid) : null;
    }

    /**
     * Helper method to get primary program (first in list)
     */
    public String getProgram() {
        return (programs != null && !programs.isEmpty()) ? programs.get(0) : null;
    }

    /**
     * Helper method to get designation (from remarks or title)
     */
    public String getDesignation() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        // Extract designation from remarks if present
        if (remarks != null && remarks.toLowerCase().contains("designation")) {
            return remarks.substring(0, Math.min(100, remarks.length()));
        }
        return sdnType;
    }

    /**
     * Helper method to get listing date (publicationDate or lastUpdateDate)
     */
    public LocalDate getListingDate() {
        return publicationDate != null ? publicationDate : lastUpdateDate;
    }
}
