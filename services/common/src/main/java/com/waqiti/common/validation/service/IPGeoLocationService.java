package com.waqiti.common.validation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * IP Geolocation Service
 * 
 * Production-ready service for IP address geolocation with accuracy tracking
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IPGeoLocationService {
    
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${ip.geolocation.api.url:https://api.ipgeolocation.io/ipgeo}")
    private String geoApiUrl;
    
    @Value("${ip.geolocation.api.key}")
    private String geoApiKey;
    
    @Value("${ip.geolocation.cache.ttl:3600}")
    private int cacheTtl;
    
    @Value("${ip.geolocation.batch.size:100}")
    private int batchSize;
    
    private final Map<String, GeoLocation> geoCache = new ConcurrentHashMap<>();
    private final Map<String, IPRange> ipRangeDatabase = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing IP Geolocation Service");
        loadIPRangeDatabase();
        loadKnownDatacenters();
    }
    
    /**
     * Get geolocation for IP address
     */
    @Transactional(readOnly = true)
    public GeoLocation getLocation(String ipAddress) {
        log.debug("Getting geolocation for IP: {}", ipAddress);
        
        // Validate IP format
        if (!isValidIP(ipAddress)) {
            return GeoLocation.builder()
                .ip(ipAddress)
                .error("Invalid IP address format")
                .build();
        }
        
        // Check cache
        GeoLocation cached = geoCache.get(ipAddress);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        
        // Check database
        GeoLocation dbLocation = getLocationFromDatabase(ipAddress);
        if (dbLocation != null) {
            geoCache.put(ipAddress, dbLocation);
            return dbLocation;
        }
        
        // Check IP range database
        GeoLocation rangeLocation = getLocationFromRange(ipAddress);
        if (rangeLocation != null) {
            return rangeLocation;
        }
        
        // Query external API
        return queryExternalGeoAPI(ipAddress);
    }
    
    /**
     * Get country code for IP
     */
    public String getCountryCode(String ipAddress) {
        GeoLocation location = getLocation(ipAddress);
        return location != null ? location.getCountryCode() : "UNKNOWN";
    }
    
    /**
     * Check if IP is from specific country
     */
    public boolean isFromCountry(String ipAddress, String countryCode) {
        String ipCountry = getCountryCode(ipAddress);
        return countryCode.equalsIgnoreCase(ipCountry);
    }
    
    /**
     * Check if IP is from high-risk country
     */
    public boolean isHighRiskCountry(String ipAddress) {
        String countryCode = getCountryCode(ipAddress);
        Set<String> highRiskCountries = Set.of(
            "KP", "IR", "SY", "CU", "SD", "ZW", "BY", "VE", "MM", "LY"
        );
        return highRiskCountries.contains(countryCode);
    }
    
    /**
     * Get distance between IP and reference location
     */
    public double getDistance(String ipAddress, double refLatitude, double refLongitude) {
        GeoLocation location = getLocation(ipAddress);
        if (location == null || location.getLatitude() == null || location.getLongitude() == null) {
            return -1;
        }
        
        return calculateDistance(
            location.getLatitude(), location.getLongitude(),
            refLatitude, refLongitude
        );
    }
    
    /**
     * Batch process IP locations
     */
    public Map<String, GeoLocation> getBatchLocations(List<String> ipAddresses) {
        Map<String, GeoLocation> results = new HashMap<>();
        List<String> uncachedIPs = new ArrayList<>();
        
        // Check cache first
        for (String ip : ipAddresses) {
            GeoLocation cached = geoCache.get(ip);
            if (cached != null && !cached.isExpired()) {
                results.put(ip, cached);
            } else {
                uncachedIPs.add(ip);
            }
        }
        
        // Batch query for uncached IPs
        if (!uncachedIPs.isEmpty()) {
            Map<String, GeoLocation> batchResults = batchQueryDatabase(uncachedIPs);
            results.putAll(batchResults);
            
            // Cache results
            batchResults.forEach((ip, location) -> geoCache.put(ip, location));
        }
        
        return results;
    }
    
    private boolean isValidIP(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private GeoLocation getLocationFromDatabase(String ipAddress) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT ip_address, country_code, country_name, region, city,
                       latitude, longitude, postal_code, timezone, isp, 
                       organization, as_number, accuracy_radius, is_datacenter,
                       last_updated
                FROM ip_geolocation
                WHERE ip_address = ? AND last_updated > DATE_SUB(NOW(), INTERVAL 30 DAY)
                """,
                (rs, rowNum) -> GeoLocation.builder()
                    .ip(rs.getString("ip_address"))
                    .countryCode(rs.getString("country_code"))
                    .countryName(rs.getString("country_name"))
                    .region(rs.getString("region"))
                    .city(rs.getString("city"))
                    .latitude(rs.getDouble("latitude"))
                    .longitude(rs.getDouble("longitude"))
                    .postalCode(rs.getString("postal_code"))
                    .timezone(rs.getString("timezone"))
                    .isp(rs.getString("isp"))
                    .organization(rs.getString("organization"))
                    .asNumber(rs.getString("as_number"))
                    .accuracyRadius(rs.getInt("accuracy_radius"))
                    .isDatacenter(rs.getBoolean("is_datacenter"))
                    .lastUpdated(rs.getTimestamp("last_updated").toLocalDateTime())
                    .build(),
                ipAddress
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    private GeoLocation getLocationFromRange(String ipAddress) {
        // Check if IP falls within known ranges
        long ipLong = ipToLong(ipAddress);
        
        for (IPRange range : ipRangeDatabase.values()) {
            if (ipLong >= range.getStartIP() && ipLong <= range.getEndIP()) {
                return GeoLocation.builder()
                    .ip(ipAddress)
                    .countryCode(range.getCountryCode())
                    .countryName(range.getCountryName())
                    .region(range.getRegion())
                    .city(range.getCity())
                    .isp(range.getIsp())
                    .isDatacenter(range.isDatacenter())
                    .lastUpdated(LocalDateTime.now())
                    .build();
            }
        }
        
        return null;
    }
    
    private GeoLocation queryExternalGeoAPI(String ipAddress) {
        try {
            String url = String.format("%s?apiKey=%s&ip=%s", geoApiUrl, geoApiKey, ipAddress);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                GeoLocation location = GeoLocation.builder()
                    .ip(ipAddress)
                    .countryCode((String) response.get("country_code2"))
                    .countryName((String) response.get("country_name"))
                    .region((String) response.get("state_prov"))
                    .city((String) response.get("city"))
                    .latitude(Double.parseDouble(response.get("latitude").toString()))
                    .longitude(Double.parseDouble(response.get("longitude").toString()))
                    .postalCode((String) response.get("zipcode"))
                    .timezone((String) response.get("time_zone"))
                    .isp((String) response.get("isp"))
                    .organization((String) response.get("organization"))
                    .lastUpdated(LocalDateTime.now())
                    .build();
                
                // Cache and save to database
                geoCache.put(ipAddress, location);
                saveToDatabase(location);
                
                return location;
            }
        } catch (Exception e) {
            log.error("Error querying geolocation API: {}", e.getMessage());
        }
        
        return GeoLocation.builder()
            .ip(ipAddress)
            .error("Unable to determine location")
            .build();
    }
    
    private Map<String, GeoLocation> batchQueryDatabase(List<String> ipAddresses) {
        Map<String, GeoLocation> results = new HashMap<>();
        
        try {
            String placeholders = String.join(",", Collections.nCopies(ipAddresses.size(), "?"));
            String sql = String.format(
                "SELECT * FROM ip_geolocation WHERE ip_address IN (%s) AND last_updated > DATE_SUB(NOW(), INTERVAL 30 DAY)",
                placeholders
            );
            
            jdbcTemplate.query(sql, ipAddresses.toArray(), (rs) -> {
                GeoLocation location = GeoLocation.builder()
                    .ip(rs.getString("ip_address"))
                    .countryCode(rs.getString("country_code"))
                    .countryName(rs.getString("country_name"))
                    .region(rs.getString("region"))
                    .city(rs.getString("city"))
                    .latitude(rs.getDouble("latitude"))
                    .longitude(rs.getDouble("longitude"))
                    .lastUpdated(rs.getTimestamp("last_updated").toLocalDateTime())
                    .build();
                
                results.put(rs.getString("ip_address"), location);
            });
        } catch (Exception e) {
            log.error("Error batch querying database: {}", e.getMessage());
        }
        
        return results;
    }
    
    private void loadIPRangeDatabase() {
        try {
            jdbcTemplate.query(
                "SELECT * FROM ip_ranges WHERE is_active = true",
                (rs) -> {
                    IPRange range = IPRange.builder()
                        .startIP(rs.getLong("start_ip"))
                        .endIP(rs.getLong("end_ip"))
                        .countryCode(rs.getString("country_code"))
                        .countryName(rs.getString("country_name"))
                        .region(rs.getString("region"))
                        .city(rs.getString("city"))
                        .isp(rs.getString("isp"))
                        .isDatacenter(rs.getBoolean("is_datacenter"))
                        .build();
                    
                    ipRangeDatabase.put(rs.getString("range_id"), range);
                }
            );
            
            log.info("Loaded {} IP ranges", ipRangeDatabase.size());
        } catch (Exception e) {
            log.error("Error loading IP range database: {}", e.getMessage());
        }
    }
    
    private void loadKnownDatacenters() {
        // Load known datacenter IP ranges (AWS, Google Cloud, Azure, etc.)
        try {
            jdbcTemplate.query(
                "SELECT * FROM datacenter_ips WHERE is_active = true",
                (rs) -> {
                    IPRange range = IPRange.builder()
                        .startIP(rs.getLong("start_ip"))
                        .endIP(rs.getLong("end_ip"))
                        .isp(rs.getString("provider"))
                        .isDatacenter(true)
                        .build();
                    
                    ipRangeDatabase.put("dc_" + rs.getString("id"), range);
                }
            );
        } catch (Exception e) {
            log.error("Error loading datacenter IPs: {}", e.getMessage());
        }
    }
    
    private void saveToDatabase(GeoLocation location) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO ip_geolocation (ip_address, country_code, country_name,
                    region, city, latitude, longitude, postal_code, timezone,
                    isp, organization, last_updated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    country_code = VALUES(country_code),
                    country_name = VALUES(country_name),
                    region = VALUES(region),
                    city = VALUES(city),
                    latitude = VALUES(latitude),
                    longitude = VALUES(longitude),
                    last_updated = VALUES(last_updated)
                """,
                location.getIp(), location.getCountryCode(), location.getCountryName(),
                location.getRegion(), location.getCity(), location.getLatitude(),
                location.getLongitude(), location.getPostalCode(), location.getTimezone(),
                location.getIsp(), location.getOrganization(), location.getLastUpdated()
            );
        } catch (Exception e) {
            log.error("Error saving geolocation to database: {}", e.getMessage());
        }
    }
    
    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return 0;
        
        try {
            return (Long.parseLong(parts[0]) << 24) +
                   (Long.parseLong(parts[1]) << 16) +
                   (Long.parseLong(parts[2]) << 8) +
                   Long.parseLong(parts[3]);
        } catch (Exception e) {
            return 0;
        }
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula for distance calculation
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
    
    @Scheduled(cron = "0 0 4 * * ?") // 4 AM daily
    public void cleanupOldData() {
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM ip_geolocation WHERE last_updated < DATE_SUB(NOW(), INTERVAL 90 DAY)"
            );
            log.info("Cleaned up {} old geolocation records", deleted);
        } catch (Exception e) {
            log.error("Error cleaning up old data: {}", e.getMessage());
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class GeoLocation {
        private String ip;
        private String countryCode;
        private String countryName;
        private String region;
        private String city;
        private Double latitude;
        private Double longitude;
        private String postalCode;
        private String timezone;
        private String isp;
        private String organization;
        private String asNumber;
        private Integer accuracyRadius;
        private boolean isDatacenter;
        private LocalDateTime lastUpdated;
        private String error;
        @lombok.Builder.Default
        private long ttlSeconds = 3600;
        
        public boolean isExpired() {
            if (lastUpdated == null) return true;
            return lastUpdated.plusSeconds(ttlSeconds).isBefore(LocalDateTime.now());
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class IPRange {
        private long startIP;
        private long endIP;
        private String countryCode;
        private String countryName;
        private String region;
        private String city;
        private String isp;
        private boolean isDatacenter;
    }
}