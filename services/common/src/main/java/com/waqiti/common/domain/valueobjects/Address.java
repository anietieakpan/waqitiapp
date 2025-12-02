package com.waqiti.common.domain.valueobjects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.Optional;

/**
 * Address Value Object - Immutable representation of physical addresses
 * Encapsulates address validation and formatting rules for African markets
 */
@Getter
@EqualsAndHashCode
public class Address {
    
    private final String street;
    private final String city;
    private final String state;
    private final String postalCode;
    private final String country;
    private final String landmark;
    
    @JsonCreator
    public Address(@JsonProperty("street") String street,
                   @JsonProperty("city") String city,
                   @JsonProperty("state") String state,
                   @JsonProperty("postalCode") String postalCode,
                   @JsonProperty("country") String country,
                   @JsonProperty("landmark") String landmark) {
        this.street = validateAndTrim(street, "Street");
        this.city = validateAndTrim(city, "City");
        this.state = validateAndTrim(state, "State");
        this.postalCode = normalizePostalCode(postalCode, country);
        this.country = validateCountry(country);
        this.landmark = landmark != null ? landmark.trim() : null;
    }
    
    public static Address of(String street, String city, String state, String postalCode, String country) {
        return new Address(street, city, state, postalCode, country, null);
    }
    
    public static Address withLandmark(String street, String city, String state, 
                                      String postalCode, String country, String landmark) {
        return new Address(street, city, state, postalCode, country, landmark);
    }
    
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(street);
        
        if (landmark != null && !landmark.isEmpty()) {
            sb.append(", ").append(landmark);
        }
        
        sb.append(", ").append(city);
        sb.append(", ").append(state);
        
        if (postalCode != null && !postalCode.isEmpty()) {
            sb.append(" ").append(postalCode);
        }
        
        sb.append(", ").append(country);
        
        return sb.toString();
    }
    
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(street).append("\n");
        
        if (landmark != null && !landmark.isEmpty()) {
            sb.append(landmark).append("\n");
        }
        
        sb.append(city).append(", ").append(state);
        
        if (postalCode != null && !postalCode.isEmpty()) {
            sb.append(" ").append(postalCode);
        }
        
        sb.append("\n").append(country);
        
        return sb.toString();
    }
    
    public Address withStreet(String newStreet) {
        return new Address(newStreet, city, state, postalCode, country, landmark);
    }
    
    public Address withCity(String newCity) {
        return new Address(street, newCity, state, postalCode, country, landmark);
    }
    
    public Address withState(String newState) {
        return new Address(street, city, newState, postalCode, country, landmark);
    }
    
    public Address withPostalCode(String newPostalCode) {
        return new Address(street, city, state, newPostalCode, country, landmark);
    }
    
    public Address withCountry(String newCountry) {
        return new Address(street, city, state, postalCode, newCountry, landmark);
    }
    
    public Address withLandmark(String newLandmark) {
        return new Address(street, city, state, postalCode, country, newLandmark);
    }
    
    public boolean isInCountry(String countryCode) {
        return country.equalsIgnoreCase(countryCode);
    }
    
    public boolean isInState(String stateName) {
        return state.equalsIgnoreCase(stateName);
    }
    
    public boolean isInCity(String cityName) {
        return city.equalsIgnoreCase(cityName);
    }
    
    public Optional<String> getLandmark() {
        return Optional.ofNullable(landmark);
    }
    
    public boolean hasPostalCode() {
        return postalCode != null && !postalCode.isEmpty();
    }
    
    public boolean hasLandmark() {
        return landmark != null && !landmark.isEmpty();
    }
    
    private String validateAndTrim(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        String trimmed = value.trim();
        
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException(fieldName + " cannot exceed 100 characters");
        }
        
        return trimmed;
    }
    
    private String validateCountry(String country) {
        String trimmed = validateAndTrim(country, "Country");
        
        // Validate against known African countries (ISO 3166-1 alpha-2)
        if (trimmed.length() == 2) {
            String upper = trimmed.toUpperCase();
            if (isValidCountryCode(upper)) {
                return upper;
            }
        }
        
        // If full country name, convert to code
        return convertCountryNameToCode(trimmed);
    }
    
    private boolean isValidCountryCode(String code) {
        return switch (code) {
            case "NG", "GH", "KE", "ZA", "EG", "MA", "TZ", "UG", "RW", "SN", 
                 "CI", "CM", "BF", "ML", "MW", "ZM", "ZW", "BW", "MZ", "MG",
                 "US", "GB", "CA", "FR", "DE" -> true;
            default -> false;
        };
    }
    
    private String convertCountryNameToCode(String countryName) {
        String lower = countryName.toLowerCase();
        
        return switch (lower) {
            case "nigeria" -> "NG";
            case "ghana" -> "GH";
            case "kenya" -> "KE";
            case "south africa" -> "ZA";
            case "egypt" -> "EG";
            case "morocco" -> "MA";
            case "tanzania" -> "TZ";
            case "uganda" -> "UG";
            case "rwanda" -> "RW";
            case "senegal" -> "SN";
            case "ivory coast", "côte d'ivoire" -> "CI";
            case "cameroon" -> "CM";
            case "burkina faso" -> "BF";
            case "mali" -> "ML";
            case "malawi" -> "MW";
            case "zambia" -> "ZM";
            case "zimbabwe" -> "ZW";
            case "botswana" -> "BW";
            case "mozambique" -> "MZ";
            case "madagascar" -> "MG";
            case "united states", "usa" -> "US";
            case "united kingdom", "uk" -> "GB";
            case "canada" -> "CA";
            case "france" -> "FR";
            case "germany" -> "DE";
            default -> throw new IllegalArgumentException("Unsupported country: " + countryName);
        };
    }
    
    private String normalizePostalCode(String postalCode, String country) {
        if (postalCode == null || postalCode.trim().isEmpty()) {
            // Some African countries don't use postal codes consistently
            if (isAfricanCountry(country)) {
                return null;
            }
            return null; // Allow null postal codes
        }
        
        String trimmed = postalCode.trim().toUpperCase();
        
        // Country-specific postal code validation
        if (country != null) {
            return switch (country.toUpperCase()) {
                case "NG" -> validateNigerianPostalCode(trimmed);
                case "GH" -> validateGhanaianPostalCode(trimmed);
                case "ZA" -> validateSouthAfricanPostalCode(trimmed);
                case "US" -> validateUSPostalCode(trimmed);
                case "GB" -> validateUKPostalCode(trimmed);
                default -> trimmed; // Accept as-is for other countries
            };
        }
        
        return trimmed;
    }
    
    private boolean isAfricanCountry(String country) {
        if (country == null) return false;
        String code = country.length() == 2 ? country : convertCountryNameToCode(country);
        return switch (code) {
            case "NG", "GH", "KE", "ZA", "EG", "MA", "TZ", "UG", "RW", "SN", 
                 "CI", "CM", "BF", "ML", "MW", "ZM", "ZW", "BW", "MZ", "MG" -> true;
            default -> false;
        };
    }
    
    private String validateNigerianPostalCode(String code) {
        // Nigerian postal codes are 6 digits
        if (code.matches("^\\d{6}$")) {
            return code;
        }
        return null; // Allow null for Nigeria as postal codes aren't widely used
    }
    
    private String validateGhanaianPostalCode(String code) {
        // Ghanaian postal codes are alphanumeric
        if (code.matches("^[A-Z0-9]{5,8}$")) {
            return code;
        }
        return null;
    }
    
    private String validateSouthAfricanPostalCode(String code) {
        // South African postal codes are 4 digits
        if (code.matches("^\\d{4}$")) {
            return code;
        }
        return null;
    }
    
    private String validateUSPostalCode(String code) {
        // US ZIP codes
        if (code.matches("^\\d{5}(-\\d{4})?$")) {
            return code;
        }
        throw new IllegalArgumentException("Invalid US postal code format: " + code);
    }
    
    private String validateUKPostalCode(String code) {
        // UK postal codes
        if (code.matches("^[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2}$")) {
            return code;
        }
        throw new IllegalArgumentException("Invalid UK postal code format: " + code);
    }
    
    @Override
    public String toString() {
        return getFullAddress();
    }
}