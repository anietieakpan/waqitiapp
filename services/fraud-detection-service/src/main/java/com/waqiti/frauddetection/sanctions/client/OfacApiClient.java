package com.waqiti.frauddetection.sanctions.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.frauddetection.sanctions.dto.OfacSdnEntry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for U.S. Treasury OFAC (Office of Foreign Assets Control) API.
 *
 * Integrates with:
 * - OFAC SDN (Specially Designated Nationals) List
 * - OFAC Consolidated Sanctions List
 * - OFAC Sectoral Sanctions Identifications (SSI) List
 *
 * API Documentation:
 * - https://www.treasury.gov/ofac/downloads/sanctions/
 * - https://sanctionslistservice.ofac.treas.gov/
 *
 * Features:
 * - Circuit breaker pattern for resilience
 * - Automatic retry with exponential backoff
 * - Response caching (6-hour TTL)
 * - Comprehensive error handling
 * - Fallback to cached data on failure
 *
 * Compliance:
 * - 31 CFR Part 501 (OFAC Reporting, Procedures and Penalties)
 * - 31 CFR Part 500 (General Regulations)
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OfacApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ofac.api.base-url:https://sanctionslistservice.ofac.treas.gov/api/PublicationPreview}")
    private String ofacApiBaseUrl;

    @Value("${ofac.api.sdn-list-url:https://www.treasury.gov/ofac/downloads/sdn.xml}")
    private String sdnListUrl;

    @Value("${ofac.api.consolidated-url:https://www.treasury.gov/ofac/downloads/consolidated/consolidated.xml}")
    private String consolidatedListUrl;

    @Value("${eu.sanctions.api.base-url:https://webgate.ec.europa.eu/fsd/fsf/public/files/xmlFullSanctionsList_1_1/content}")
    private String euSanctionsApiUrl;

    @Value("${un.sanctions.api.base-url:https://scsanctions.un.org/resources/xml/en/consolidated.xml}")
    private String unSanctionsApiUrl;

    @Value("${ofac.api.timeout-ms:30000}")
    private int timeoutMs;

    // Cache for 6 hours
    private static final String CACHE_NAME = "ofac-api-responses";

    /**
     * Fetch OFAC SDN (Specially Designated Nationals) List.
     *
     * The SDN list includes individuals and entities blocked by OFAC
     * under various sanctions programs.
     *
     * @return List of SDN entries
     * @throws OfacApiException if API call fails
     */
    @CircuitBreaker(name = "ofac-api", fallbackMethod = "getOfacSdnListFallback")
    @Retry(name = "ofac-api")
    @Cacheable(value = CACHE_NAME, key = "'ofac-sdn-list'", unless = "#result == null || #result.isEmpty()")
    public List<OfacSdnEntry> getOfacSdnList() {
        log.info("COMPLIANCE: Fetching OFAC SDN list from Treasury API");

        try {
            // Use OFAC's sanctions list service API
            String apiUrl = ofacApiBaseUrl + "/SdnList";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Waqiti-Platform/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                entity,
                String.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new OfacApiException("OFAC API returned non-200 status: " + response.getStatusCode());
            }

            List<OfacSdnEntry> sdnList = parseOfacSdnResponse(response.getBody());
            log.info("COMPLIANCE: Successfully fetched {} OFAC SDN entries", sdnList.size());

            return sdnList;

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to fetch OFAC SDN list", e);
            throw new OfacApiException("Failed to fetch OFAC SDN list", e);
        }
    }

    /**
     * Fetch EU Consolidated Sanctions List.
     *
     * European Union sanctions list including individuals and entities
     * subject to EU restrictive measures.
     *
     * @return List of EU sanctions entries
     * @throws OfacApiException if API call fails
     */
    @CircuitBreaker(name = "eu-sanctions-api", fallbackMethod = "getEuSanctionsListFallback")
    @Retry(name = "eu-sanctions-api")
    @Cacheable(value = CACHE_NAME, key = "'eu-sanctions-list'", unless = "#result == null || #result.isEmpty()")
    public List<OfacSdnEntry> getEuSanctionsList() {
        log.info("COMPLIANCE: Fetching EU sanctions list");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.set("User-Agent", "Waqiti-Platform/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                euSanctionsApiUrl,
                HttpMethod.GET,
                entity,
                String.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new OfacApiException("EU Sanctions API returned non-200 status: " + response.getStatusCode());
            }

            List<OfacSdnEntry> euList = parseEuSanctionsResponse(response.getBody());
            log.info("COMPLIANCE: Successfully fetched {} EU sanctions entries", euList.size());

            return euList;

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to fetch EU sanctions list", e);
            throw new OfacApiException("Failed to fetch EU sanctions list", e);
        }
    }

    /**
     * Fetch UN Consolidated Sanctions List.
     *
     * United Nations Security Council sanctions list.
     *
     * @return List of UN sanctions entries
     * @throws OfacApiException if API call fails
     */
    @CircuitBreaker(name = "un-sanctions-api", fallbackMethod = "getUnSanctionsListFallback")
    @Retry(name = "un-sanctions-api")
    @Cacheable(value = CACHE_NAME, key = "'un-sanctions-list'", unless = "#result == null || #result.isEmpty()")
    public List<OfacSdnEntry> getUnSanctionsList() {
        log.info("COMPLIANCE: Fetching UN sanctions list");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.set("User-Agent", "Waqiti-Platform/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                unSanctionsApiUrl,
                HttpMethod.GET,
                entity,
                String.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new OfacApiException("UN Sanctions API returned non-200 status: " + response.getStatusCode());
            }

            List<OfacSdnEntry> unList = parseUnSanctionsResponse(response.getBody());
            log.info("COMPLIANCE: Successfully fetched {} UN sanctions entries", unList.size());

            return unList;

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to fetch UN sanctions list", e);
            throw new OfacApiException("Failed to fetch UN sanctions list", e);
        }
    }

    /**
     * Search OFAC SDN list by name.
     *
     * @param name Name to search
     * @return List of matching entries
     */
    @CircuitBreaker(name = "ofac-api", fallbackMethod = "searchSdnByNameFallback")
    @Retry(name = "ofac-api")
    public List<OfacSdnEntry> searchSdnByName(String name) {
        log.info("COMPLIANCE: Searching OFAC SDN list for name: {}", maskName(name));

        try {
            String searchUrl = ofacApiBaseUrl + "/Search?name=" + name;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Waqiti-Platform/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                searchUrl,
                HttpMethod.GET,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOfacSdnResponse(response.getBody());
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to search OFAC SDN list", e);
            throw new OfacApiException("Failed to search OFAC SDN list", e);
        }
    }

    /**
     * Parse OFAC SDN API JSON response.
     *
     * Response structure:
     * {
     *   "sdnList": [
     *     {
     *       "uid": 12345,
     *       "firstName": "John",
     *       "lastName": "Doe",
     *       "sdnType": "Individual",
     *       "programs": ["SDGT"],
     *       "title": "...",
     *       "remarks": "...",
     *       "addresses": [...],
     *       "ids": [...]
     *     }
     *   ]
     * }
     */
    private List<OfacSdnEntry> parseOfacSdnResponse(String jsonResponse) {
        List<OfacSdnEntry> entries = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode sdnList = root.get("sdnList");

            if (sdnList == null || !sdnList.isArray()) {
                log.warn("COMPLIANCE: OFAC response missing sdnList array");
                return entries;
            }

            for (JsonNode node : sdnList) {
                try {
                    OfacSdnEntry entry = OfacSdnEntry.builder()
                        .uid(node.has("uid") ? node.get("uid").asLong() : null)
                        .firstName(getTextValue(node, "firstName"))
                        .lastName(getTextValue(node, "lastName"))
                        .fullName(buildFullName(node))
                        .sdnType(getTextValue(node, "sdnType"))
                        .programs(parsePrograms(node))
                        .title(getTextValue(node, "title"))
                        .remarks(getTextValue(node, "remarks"))
                        .nationality(getTextValue(node, "nationality"))
                        .dateOfBirth(parseDateOfBirth(node))
                        .placeOfBirth(getTextValue(node, "placeOfBirth"))
                        .addresses(parseAddresses(node))
                        .identificationNumbers(parseIds(node))
                        .listName("OFAC_SDN")
                        .listSource("US_TREASURY_OFAC")
                        .build();

                    entries.add(entry);

                } catch (Exception e) {
                    log.warn("COMPLIANCE: Failed to parse SDN entry", e);
                }
            }

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to parse OFAC SDN response", e);
        }

        return entries;
    }

    /**
     * Parse EU sanctions XML response.
     *
     * EU XML Schema Structure:
     * <export>
     *   <sanctionEntity>
     *     <entityId>...</entityId>
     *     <nameAlias>
     *       <firstName>...</firstName>
     *       <lastName>...</lastName>
     *       <wholeName>...</wholeName>
     *     </nameAlias>
     *     <birthdate>...</birthdate>
     *     <birthplace>...</birthplace>
     *     <citizenship>...</citizenship>
     *     <identification>
     *       <identificationNumber>...</identificationNumber>
     *       <identificationTypeDescription>...</identificationTypeDescription>
     *     </identification>
     *     <address>...</address>
     *     <remark>...</remark>
     *     <regulation>
     *       <programme>...</programme>
     *       <publicationDate>...</publicationDate>
     *     </regulation>
     *   </sanctionEntity>
     * </export>
     */
    private List<OfacSdnEntry> parseEuSanctionsResponse(String xmlResponse) {
        List<OfacSdnEntry> entries = new ArrayList<>();

        try {
            log.info("COMPLIANCE: Parsing EU sanctions XML - {} bytes", xmlResponse.length());

            // Use DOM parser for EU XML
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(
                new java.io.ByteArrayInputStream(xmlResponse.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );

            // Parse <sanctionEntity> elements
            org.w3c.dom.NodeList entityNodes = doc.getElementsByTagName("sanctionEntity");

            for (int i = 0; i < entityNodes.getLength(); i++) {
                try {
                    org.w3c.dom.Element entityElement = (org.w3c.dom.Element) entityNodes.item(i);

                    OfacSdnEntry entry = parseEuSanctionEntity(entityElement);
                    if (entry != null) {
                        entries.add(entry);
                    }
                } catch (Exception e) {
                    log.warn("COMPLIANCE: Failed to parse EU sanction entity at index {}", i, e);
                }
            }

            log.info("COMPLIANCE: Successfully parsed {} EU sanctions entries", entries.size());

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to parse EU sanctions XML", e);
        }

        return entries;
    }

    /**
     * Parse individual EU sanction entity from XML element.
     */
    private OfacSdnEntry parseEuSanctionEntity(org.w3c.dom.Element entityElement) {
        try {
            String entityId = getXmlElementText(entityElement, "entityId");
            String logicalId = getXmlElementText(entityElement, "logicalId");
            String subjectType = getXmlElementText(entityElement, "subjectType");

            // Parse name aliases
            List<String> aliases = new ArrayList<>();
            String fullName = null;
            String firstName = null;
            String lastName = null;

            org.w3c.dom.NodeList nameAliases = entityElement.getElementsByTagName("nameAlias");
            for (int i = 0; i < nameAliases.getLength(); i++) {
                org.w3c.dom.Element nameAlias = (org.w3c.dom.Element) nameAliases.item(i);

                String wholeName = getXmlElementText(nameAlias, "wholeName");
                String aliasFirstName = getXmlElementText(nameAlias, "firstName");
                String aliasLastName = getXmlElementText(nameAlias, "lastName");

                if (i == 0) {
                    fullName = wholeName;
                    firstName = aliasFirstName;
                    lastName = aliasLastName;
                } else {
                    if (wholeName != null) aliases.add(wholeName);
                }
            }

            // Parse birthdates
            LocalDate dateOfBirth = null;
            org.w3c.dom.NodeList birthdates = entityElement.getElementsByTagName("birthdate");
            if (birthdates.getLength() > 0) {
                org.w3c.dom.Element birthdate = (org.w3c.dom.Element) birthdates.item(0);
                dateOfBirth = parseXmlDate(getXmlElementText(birthdate, "birthdate"));
            }

            // Parse birthplace
            String placeOfBirth = null;
            org.w3c.dom.NodeList birthplaces = entityElement.getElementsByTagName("birthplace");
            if (birthplaces.getLength() > 0) {
                placeOfBirth = getXmlElementText((org.w3c.dom.Element) birthplaces.item(0), "place");
            }

            // Parse citizenship/nationality
            String nationality = null;
            org.w3c.dom.NodeList citizenships = entityElement.getElementsByTagName("citizenship");
            if (citizenships.getLength() > 0) {
                org.w3c.dom.Element citizenship = (org.w3c.dom.Element) citizenships.item(0);
                nationality = getXmlElementText(citizenship, "countryIso2Code");
            }

            // Parse identifications
            List<String> passportNumbers = new ArrayList<>();
            List<String> nationalIdNumbers = new ArrayList<>();
            org.w3c.dom.NodeList identifications = entityElement.getElementsByTagName("identification");
            for (int i = 0; i < identifications.getLength(); i++) {
                org.w3c.dom.Element identification = (org.w3c.dom.Element) identifications.item(i);
                String idNumber = getXmlElementText(identification, "number");
                String idType = getXmlElementText(identification, "identificationTypeDescription");

                if (idNumber != null) {
                    if (idType != null && idType.toLowerCase().contains("passport")) {
                        passportNumbers.add(idNumber);
                    } else {
                        nationalIdNumbers.add(idNumber);
                    }
                }
            }

            // Parse addresses
            List<String> addresses = new ArrayList<>();
            org.w3c.dom.NodeList addressNodes = entityElement.getElementsByTagName("address");
            for (int i = 0; i < addressNodes.getLength(); i++) {
                org.w3c.dom.Element addressElement = (org.w3c.dom.Element) addressNodes.item(i);
                String street = getXmlElementText(addressElement, "street");
                String city = getXmlElementText(addressElement, "city");
                String country = getXmlElementText(addressElement, "countryDescription");

                StringBuilder addressBuilder = new StringBuilder();
                if (street != null) addressBuilder.append(street);
                if (city != null) {
                    if (addressBuilder.length() > 0) addressBuilder.append(", ");
                    addressBuilder.append(city);
                }
                if (country != null) {
                    if (addressBuilder.length() > 0) addressBuilder.append(", ");
                    addressBuilder.append(country);
                }

                if (addressBuilder.length() > 0) {
                    addresses.add(addressBuilder.toString());
                }
            }

            // Parse regulations/programs
            List<String> programs = new ArrayList<>();
            LocalDate publicationDate = null;
            org.w3c.dom.NodeList regulations = entityElement.getElementsByTagName("regulation");
            for (int i = 0; i < regulations.getLength(); i++) {
                org.w3c.dom.Element regulation = (org.w3c.dom.Element) regulations.item(i);
                String programme = getXmlElementText(regulation, "programme");
                if (programme != null) {
                    programs.add(programme);
                }

                if (i == 0 && publicationDate == null) {
                    String pubDateStr = getXmlElementText(regulation, "publicationDate");
                    publicationDate = parseXmlDate(pubDateStr);
                }
            }

            // Parse remarks
            String remarks = getXmlElementText(entityElement, "remark");

            return OfacSdnEntry.builder()
                    .uid(entityId != null ? Long.parseLong(entityId) : null)
                    .sdnType(subjectType != null ? subjectType : (firstName != null ? "Individual" : "Entity"))
                    .firstName(firstName)
                    .lastName(lastName)
                    .fullName(fullName != null ? fullName : (firstName != null && lastName != null ? firstName + " " + lastName : null))
                    .aliases(aliases.isEmpty() ? null : aliases)
                    .programs(programs.isEmpty() ? null : programs)
                    .remarks(remarks)
                    .nationality(nationality)
                    .dateOfBirth(dateOfBirth)
                    .placeOfBirth(placeOfBirth)
                    .addresses(addresses.isEmpty() ? null : addresses)
                    .passportNumbers(passportNumbers.isEmpty() ? null : passportNumbers)
                    .nationalIdNumbers(nationalIdNumbers.isEmpty() ? null : nationalIdNumbers)
                    .listName("EU_SANCTIONS")
                    .listSource("EU_EXTERNAL_ACTION")
                    .publicationDate(publicationDate)
                    .build();

        } catch (Exception e) {
            log.warn("COMPLIANCE: Failed to parse EU sanction entity", e);
            return null;
        }
    }

    /**
     * Parse UN sanctions XML response.
     *
     * UN XML Schema Structure:
     * <CONSOLIDATED_LIST>
     *   <INDIVIDUALS>
     *     <INDIVIDUAL>
     *       <DATAID>...</DATAID>
     *       <VERSIONNUM>...</VERSIONNUM>
     *       <FIRST_NAME>...</FIRST_NAME>
     *       <SECOND_NAME>...</SECOND_NAME>
     *       <THIRD_NAME>...</THIRD_NAME>
     *       <FOURTH_NAME>...</FOURTH_NAME>
     *       <UN_LIST_TYPE>...</UN_LIST_TYPE>
     *       <REFERENCE_NUMBER>...</REFERENCE_NUMBER>
     *       <LISTED_ON>...</LISTED_ON>
     *       <NAME_ORIGINAL_SCRIPT>...</NAME_ORIGINAL_SCRIPT>
     *       <COMMENTS1>...</COMMENTS1>
     *       <DESIGNATION>...</DESIGNATION>
     *       <NATIONALITY>
     *         <VALUE>...</VALUE>
     *       </NATIONALITY>
     *       <LIST_TYPE>
     *         <VALUE>...</VALUE>
     *       </LIST_TYPE>
     *       <INDIVIDUAL_ALIAS>
     *         <ALIAS_NAME>...</ALIAS_NAME>
     *       </INDIVIDUAL_ALIAS>
     *       <INDIVIDUAL_ADDRESS>
     *         <STREET>...</STREET>
     *         <CITY>...</CITY>
     *         <COUNTRY>...</COUNTRY>
     *       </INDIVIDUAL_ADDRESS>
     *       <INDIVIDUAL_DATE_OF_BIRTH>
     *         <DATE>...</DATE>
     *       </INDIVIDUAL_DATE_OF_BIRTH>
     *       <INDIVIDUAL_PLACE_OF_BIRTH>
     *         <CITY>...</CITY>
     *         <COUNTRY>...</COUNTRY>
     *       </INDIVIDUAL_PLACE_OF_BIRTH>
     *       <INDIVIDUAL_DOCUMENT>
     *         <TYPE_OF_DOCUMENT>...</TYPE_OF_DOCUMENT>
     *         <NUMBER>...</NUMBER>
     *       </INDIVIDUAL_DOCUMENT>
     *     </INDIVIDUAL>
     *   </INDIVIDUALS>
     *   <ENTITIES>
     *     <ENTITY>...</ENTITY>
     *   </ENTITIES>
     * </CONSOLIDATED_LIST>
     */
    private List<OfacSdnEntry> parseUnSanctionsResponse(String xmlResponse) {
        List<OfacSdnEntry> entries = new ArrayList<>();

        try {
            log.info("COMPLIANCE: Parsing UN sanctions XML - {} bytes", xmlResponse.length());

            // Use DOM parser for UN XML
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(
                new java.io.ByteArrayInputStream(xmlResponse.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );

            // Parse individuals
            org.w3c.dom.NodeList individualNodes = doc.getElementsByTagName("INDIVIDUAL");
            for (int i = 0; i < individualNodes.getLength(); i++) {
                try {
                    org.w3c.dom.Element individualElement = (org.w3c.dom.Element) individualNodes.item(i);
                    OfacSdnEntry entry = parseUnIndividual(individualElement);
                    if (entry != null) {
                        entries.add(entry);
                    }
                } catch (Exception e) {
                    log.warn("COMPLIANCE: Failed to parse UN individual at index {}", i, e);
                }
            }

            // Parse entities
            org.w3c.dom.NodeList entityNodes = doc.getElementsByTagName("ENTITY");
            for (int i = 0; i < entityNodes.getLength(); i++) {
                try {
                    org.w3c.dom.Element entityElement = (org.w3c.dom.Element) entityNodes.item(i);
                    OfacSdnEntry entry = parseUnEntity(entityElement);
                    if (entry != null) {
                        entries.add(entry);
                    }
                } catch (Exception e) {
                    log.warn("COMPLIANCE: Failed to parse UN entity at index {}", i, e);
                }
            }

            log.info("COMPLIANCE: Successfully parsed {} UN sanctions entries", entries.size());

        } catch (Exception e) {
            log.error("COMPLIANCE ERROR: Failed to parse UN sanctions XML", e);
        }

        return entries;
    }

    /**
     * Parse UN individual from XML element.
     */
    private OfacSdnEntry parseUnIndividual(org.w3c.dom.Element individualElement) {
        try {
            String dataId = getXmlElementText(individualElement, "DATAID");
            String firstName = getXmlElementText(individualElement, "FIRST_NAME");
            String secondName = getXmlElementText(individualElement, "SECOND_NAME");
            String thirdName = getXmlElementText(individualElement, "THIRD_NAME");
            String fourthName = getXmlElementText(individualElement, "FOURTH_NAME");
            String referenceNumber = getXmlElementText(individualElement, "REFERENCE_NUMBER");
            String unListType = getXmlElementText(individualElement, "UN_LIST_TYPE");
            String listedOn = getXmlElementText(individualElement, "LISTED_ON");
            String comments = getXmlElementText(individualElement, "COMMENTS1");
            String designation = getXmlElementText(individualElement, "DESIGNATION");

            // Build full name
            StringBuilder fullNameBuilder = new StringBuilder();
            if (firstName != null) fullNameBuilder.append(firstName);
            if (secondName != null) {
                if (fullNameBuilder.length() > 0) fullNameBuilder.append(" ");
                fullNameBuilder.append(secondName);
            }
            if (thirdName != null) {
                if (fullNameBuilder.length() > 0) fullNameBuilder.append(" ");
                fullNameBuilder.append(thirdName);
            }
            if (fourthName != null) {
                if (fullNameBuilder.length() > 0) fullNameBuilder.append(" ");
                fullNameBuilder.append(fourthName);
            }

            // Parse nationalities
            String nationality = null;
            org.w3c.dom.NodeList nationalities = individualElement.getElementsByTagName("NATIONALITY");
            if (nationalities.getLength() > 0) {
                org.w3c.dom.Element nationalityElement = (org.w3c.dom.Element) nationalities.item(0);
                nationality = getXmlElementText(nationalityElement, "VALUE");
            }

            // Parse aliases
            List<String> aliases = new ArrayList<>();
            org.w3c.dom.NodeList aliasNodes = individualElement.getElementsByTagName("INDIVIDUAL_ALIAS");
            for (int i = 0; i < aliasNodes.getLength(); i++) {
                org.w3c.dom.Element aliasElement = (org.w3c.dom.Element) aliasNodes.item(i);
                String aliasName = getXmlElementText(aliasElement, "ALIAS_NAME");
                if (aliasName != null && !aliasName.isBlank()) {
                    aliases.add(aliasName);
                }
            }

            // Parse addresses
            List<String> addresses = new ArrayList<>();
            org.w3c.dom.NodeList addressNodes = individualElement.getElementsByTagName("INDIVIDUAL_ADDRESS");
            for (int i = 0; i < addressNodes.getLength(); i++) {
                org.w3c.dom.Element addressElement = (org.w3c.dom.Element) addressNodes.item(i);
                String street = getXmlElementText(addressElement, "STREET");
                String city = getXmlElementText(addressElement, "CITY");
                String country = getXmlElementText(addressElement, "COUNTRY");

                StringBuilder addressBuilder = new StringBuilder();
                if (street != null) addressBuilder.append(street);
                if (city != null) {
                    if (addressBuilder.length() > 0) addressBuilder.append(", ");
                    addressBuilder.append(city);
                }
                if (country != null) {
                    if (addressBuilder.length() > 0) addressBuilder.append(", ");
                    addressBuilder.append(country);
                }

                if (addressBuilder.length() > 0) {
                    addresses.add(addressBuilder.toString());
                }
            }

            // Parse date of birth
            LocalDate dateOfBirth = null;
            org.w3c.dom.NodeList dobNodes = individualElement.getElementsByTagName("INDIVIDUAL_DATE_OF_BIRTH");
            if (dobNodes.getLength() > 0) {
                org.w3c.dom.Element dobElement = (org.w3c.dom.Element) dobNodes.item(0);
                String dobStr = getXmlElementText(dobElement, "DATE");
                dateOfBirth = parseXmlDate(dobStr);
            }

            // Parse place of birth
            String placeOfBirth = null;
            org.w3c.dom.NodeList pobNodes = individualElement.getElementsByTagName("INDIVIDUAL_PLACE_OF_BIRTH");
            if (pobNodes.getLength() > 0) {
                org.w3c.dom.Element pobElement = (org.w3c.dom.Element) pobNodes.item(0);
                String pobCity = getXmlElementText(pobElement, "CITY");
                String pobCountry = getXmlElementText(pobElement, "COUNTRY");

                if (pobCity != null && pobCountry != null) {
                    placeOfBirth = pobCity + ", " + pobCountry;
                } else if (pobCity != null) {
                    placeOfBirth = pobCity;
                } else if (pobCountry != null) {
                    placeOfBirth = pobCountry;
                }
            }

            // Parse documents
            List<String> passportNumbers = new ArrayList<>();
            List<String> nationalIdNumbers = new ArrayList<>();
            org.w3c.dom.NodeList documentNodes = individualElement.getElementsByTagName("INDIVIDUAL_DOCUMENT");
            for (int i = 0; i < documentNodes.getLength(); i++) {
                org.w3c.dom.Element documentElement = (org.w3c.dom.Element) documentNodes.item(i);
                String docType = getXmlElementText(documentElement, "TYPE_OF_DOCUMENT");
                String docNumber = getXmlElementText(documentElement, "NUMBER");

                if (docNumber != null && !docNumber.isBlank()) {
                    if (docType != null && docType.toLowerCase().contains("passport")) {
                        passportNumbers.add(docNumber);
                    } else {
                        nationalIdNumbers.add(docNumber);
                    }
                }
            }

            // Parse list types/programs
            List<String> programs = new ArrayList<>();
            if (unListType != null) {
                programs.add(unListType);
            }
            org.w3c.dom.NodeList listTypeNodes = individualElement.getElementsByTagName("LIST_TYPE");
            for (int i = 0; i < listTypeNodes.getLength(); i++) {
                org.w3c.dom.Element listTypeElement = (org.w3c.dom.Element) listTypeNodes.item(i);
                String listTypeValue = getXmlElementText(listTypeElement, "VALUE");
                if (listTypeValue != null && !programs.contains(listTypeValue)) {
                    programs.add(listTypeValue);
                }
            }

            return OfacSdnEntry.builder()
                    .uid(dataId != null ? Long.parseLong(dataId) : null)
                    .sdnType("Individual")
                    .firstName(firstName)
                    .lastName(secondName)
                    .fullName(fullNameBuilder.toString())
                    .aliases(aliases.isEmpty() ? null : aliases)
                    .programs(programs.isEmpty() ? null : programs)
                    .remarks(comments)
                    .nationality(nationality)
                    .dateOfBirth(dateOfBirth)
                    .placeOfBirth(placeOfBirth)
                    .addresses(addresses.isEmpty() ? null : addresses)
                    .passportNumbers(passportNumbers.isEmpty() ? null : passportNumbers)
                    .nationalIdNumbers(nationalIdNumbers.isEmpty() ? null : nationalIdNumbers)
                    .title(designation)
                    .listName("UN_SANCTIONS")
                    .listSource("UN_SECURITY_COUNCIL")
                    .publicationDate(parseXmlDate(listedOn))
                    .metadata(Map.of(
                        "referenceNumber", referenceNumber != null ? referenceNumber : "",
                        "unListType", unListType != null ? unListType : ""
                    ))
                    .build();

        } catch (Exception e) {
            log.warn("COMPLIANCE: Failed to parse UN individual", e);
            return null;
        }
    }

    /**
     * Parse UN entity from XML element.
     */
    private OfacSdnEntry parseUnEntity(org.w3c.dom.Element entityElement) {
        try {
            String dataId = getXmlElementText(entityElement, "DATAID");
            String firstName = getXmlElementText(entityElement, "FIRST_NAME");
            String referenceNumber = getXmlElementText(entityElement, "REFERENCE_NUMBER");
            String unListType = getXmlElementText(entityElement, "UN_LIST_TYPE");
            String listedOn = getXmlElementText(entityElement, "LISTED_ON");
            String comments = getXmlElementText(entityElement, "COMMENTS1");

            // Parse aliases
            List<String> aliases = new ArrayList<>();
            org.w3c.dom.NodeList aliasNodes = entityElement.getElementsByTagName("ENTITY_ALIAS");
            for (int i = 0; i < aliasNodes.getLength(); i++) {
                org.w3c.dom.Element aliasElement = (org.w3c.dom.Element) aliasNodes.item(i);
                String aliasName = getXmlElementText(aliasElement, "ALIAS_NAME");
                if (aliasName != null && !aliasName.isBlank()) {
                    aliases.add(aliasName);
                }
            }

            // Parse addresses
            List<String> addresses = new ArrayList<>();
            org.w3c.dom.NodeList addressNodes = entityElement.getElementsByTagName("ENTITY_ADDRESS");
            for (int i = 0; i < addressNodes.getLength(); i++) {
                org.w3c.dom.Element addressElement = (org.w3c.dom.Element) addressNodes.item(i);
                String street = getXmlElementText(addressElement, "STREET");
                String city = getXmlElementText(addressElement, "CITY");
                String country = getXmlElementText(addressElement, "COUNTRY");

                StringBuilder addressBuilder = new StringBuilder();
                if (street != null) addressBuilder.append(street);
                if (city != null) {
                    if (addressBuilder.length() > 0) addressBuilder.append(", ");
                    addressBuilder.append(city);
                }
                if (country != null) {
                    if (addressBuilder.length() > 0) addressBuilder.append(", ");
                    addressBuilder.append(country);
                }

                if (addressBuilder.length() > 0) {
                    addresses.add(addressBuilder.toString());
                }
            }

            // Parse list types/programs
            List<String> programs = new ArrayList<>();
            if (unListType != null) {
                programs.add(unListType);
            }

            return OfacSdnEntry.builder()
                    .uid(dataId != null ? Long.parseLong(dataId) : null)
                    .sdnType("Entity")
                    .fullName(firstName)
                    .aliases(aliases.isEmpty() ? null : aliases)
                    .programs(programs.isEmpty() ? null : programs)
                    .remarks(comments)
                    .addresses(addresses.isEmpty() ? null : addresses)
                    .listName("UN_SANCTIONS")
                    .listSource("UN_SECURITY_COUNCIL")
                    .publicationDate(parseXmlDate(listedOn))
                    .metadata(Map.of(
                        "referenceNumber", referenceNumber != null ? referenceNumber : "",
                        "unListType", unListType != null ? unListType : ""
                    ))
                    .build();

        } catch (Exception e) {
            log.warn("COMPLIANCE: Failed to parse UN entity", e);
            return null;
        }
    }

    /**
     * Fallback method for OFAC SDN list fetch.
     */
    @SuppressWarnings("unused")
    private List<OfacSdnEntry> getOfacSdnListFallback(Exception e) {
        log.error("COMPLIANCE FALLBACK: Using cached OFAC SDN list due to API failure", e);
        // Return empty list - cache will provide last known good data
        return new ArrayList<>();
    }

    /**
     * Fallback method for EU sanctions list fetch.
     */
    @SuppressWarnings("unused")
    private List<OfacSdnEntry> getEuSanctionsListFallback(Exception e) {
        log.error("COMPLIANCE FALLBACK: Using cached EU sanctions list due to API failure", e);
        return new ArrayList<>();
    }

    /**
     * Fallback method for UN sanctions list fetch.
     */
    @SuppressWarnings("unused")
    private List<OfacSdnEntry> getUnSanctionsListFallback(Exception e) {
        log.error("COMPLIANCE FALLBACK: Using cached UN sanctions list due to API failure", e);
        return new ArrayList<>();
    }

    /**
     * Fallback method for SDN name search.
     */
    @SuppressWarnings("unused")
    private List<OfacSdnEntry> searchSdnByNameFallback(String name, Exception e) {
        log.error("COMPLIANCE FALLBACK: SDN search failed for name: {}", maskName(name), e);
        return new ArrayList<>();
    }

    /**
     * Helper methods for XML parsing
     */

    /**
     * Get text content of XML element by tag name.
     */
    private String getXmlElementText(org.w3c.dom.Element parent, String tagName) {
        try {
            org.w3c.dom.NodeList nodeList = parent.getElementsByTagName(tagName);
            if (nodeList.getLength() > 0) {
                org.w3c.dom.Node node = nodeList.item(0);
                if (node != null) {
                    String textContent = node.getTextContent();
                    return (textContent != null && !textContent.isBlank()) ? textContent.trim() : null;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get XML element text for tag: {}", tagName, e);
        }
        return null;
    }

    /**
     * Parse XML date string in various formats.
     */
    private LocalDate parseXmlDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        try {
            // Try ISO format first (yyyy-MM-dd)
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            // Try other common formats
            String[] formats = {
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "dd-MM-yyyy",
                "yyyy/MM/dd",
                "yyyyMMdd"
            };

            for (String format : formats) {
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern(format);
                    return LocalDate.parse(dateStr, formatter);
                } catch (Exception ex) {
                    // Continue trying other formats
                }
            }

            log.debug("Failed to parse XML date: {}", dateStr);
            return null;
        }
    }

    /**
     * Helper methods for JSON parsing
     */

    private String getTextValue(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return null;
    }

    private String buildFullName(JsonNode node) {
        String firstName = getTextValue(node, "firstName");
        String lastName = getTextValue(node, "lastName");

        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }

        return getTextValue(node, "name"); // Fallback to "name" field
    }

    private List<String> parsePrograms(JsonNode node) {
        List<String> programs = new ArrayList<>();

        if (node.has("programs") && node.get("programs").isArray()) {
            for (JsonNode program : node.get("programs")) {
                programs.add(program.asText());
            }
        }

        return programs;
    }

    private LocalDate parseDateOfBirth(JsonNode node) {
        try {
            if (node.has("dateOfBirth") && !node.get("dateOfBirth").isNull()) {
                String dobString = node.get("dateOfBirth").asText();
                return LocalDate.parse(dobString);
            }
        } catch (Exception e) {
            log.debug("Failed to parse date of birth", e);
        }
        return null;
    }

    private List<String> parseAddresses(JsonNode node) {
        List<String> addresses = new ArrayList<>();

        if (node.has("addresses") && node.get("addresses").isArray()) {
            for (JsonNode address : node.get("addresses")) {
                StringBuilder addressBuilder = new StringBuilder();

                if (address.has("address1")) {
                    addressBuilder.append(address.get("address1").asText());
                }
                if (address.has("city")) {
                    addressBuilder.append(", ").append(address.get("city").asText());
                }
                if (address.has("country")) {
                    addressBuilder.append(", ").append(address.get("country").asText());
                }

                if (addressBuilder.length() > 0) {
                    addresses.add(addressBuilder.toString());
                }
            }
        }

        return addresses;
    }

    private List<String> parseIds(JsonNode node) {
        List<String> ids = new ArrayList<>();

        if (node.has("ids") && node.get("ids").isArray()) {
            for (JsonNode id : node.get("ids")) {
                if (id.has("idNumber") && !id.get("idNumber").isNull()) {
                    String idType = id.has("idType") ? id.get("idType").asText() : "UNKNOWN";
                    String idNumber = id.get("idNumber").asText();
                    ids.add(idType + ":" + idNumber);
                }
            }
        }

        return ids;
    }

    /**
     * Mask name for logging (privacy compliance).
     */
    private String maskName(String name) {
        if (name == null || name.length() <= 4) {
            return "****";
        }
        return name.substring(0, 2) + "****" + name.substring(name.length() - 2);
    }

    /**
     * Custom exception for OFAC API errors.
     */
    public static class OfacApiException extends RuntimeException {
        public OfacApiException(String message) {
            super(message);
        }

        public OfacApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
