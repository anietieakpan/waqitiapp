package com.waqiti.compliance.sanctions;

import com.waqiti.compliance.model.sanctions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * OFAC SDN (Specially Designated Nationals) List Parser
 *
 * Parses the OFAC SDN XML file from US Treasury and converts it to database entities.
 *
 * OFAC SDN List: https://sanctionssearch.ofac.treas.gov/
 * XML Download: https://sanctionssearch.ofac.treas.gov/sdn.xml
 *
 * CRITICAL: This parser implements XXE protection to prevent XML External Entity attacks.
 *
 * XML Structure:
 * <sdnList>
 *   <publshInformation>
 *     <Publish_Date>...</Publish_Date>
 *     <Record_Count>...</Record_Count>
 *   </publshInformation>
 *   <sdnEntry>
 *     <uid>12345</uid>
 *     <firstName>...</firstName>
 *     <lastName>...</lastName>
 *     <sdnType>Individual</sdnType>
 *     <programList>
 *       <program>UKRAINE-EO13661</program>
 *     </programList>
 *     <akaList>
 *       <aka>
 *         <uid>12345-1</uid>
 *         <type>a.k.a.</type>
 *         <firstName>...</firstName>
 *         <lastName>...</lastName>
 *       </aka>
 *     </akaList>
 *     <addressList>...</addressList>
 *     <dateOfBirthList>...</dateOfBirthList>
 *     <placeOfBirthList>...</placeOfBirthList>
 *     <idList>...</idList>
 *   </sdnEntry>
 * </sdnList>
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Service
public class OfacSdnListParser {

    private static final Logger logger = LoggerFactory.getLogger(OfacSdnListParser.class);

    private static final String OFAC_SDN_URL = "https://sanctionssearch.ofac.treas.gov/sdn.xml";
    private static final String OFAC_SDN_ADVANCED_URL = "https://sanctionssearch.ofac.treas.gov/sdn_advanced.xml";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ALT_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Download and parse OFAC SDN list
     *
     * @return Parsed sanctions list result
     * @throws SanctionsListParseException if download or parse fails
     */
    public SanctionsListParseResult downloadAndParse() throws SanctionsListParseException {
        logger.info("Starting OFAC SDN list download from: {}", OFAC_SDN_URL);

        try {
            // Download XML file
            URL url = new URL(OFAC_SDN_URL);
            InputStream inputStream = url.openStream();

            // Parse XML
            SanctionsListParseResult result = parseXml(inputStream, "OFAC", "SDN");

            inputStream.close();

            logger.info("Successfully downloaded and parsed OFAC SDN list: {} entities, {} aliases",
                    result.getEntities().size(), result.getTotalAliases());

            return result;

        } catch (IOException e) {
            logger.error("Failed to download OFAC SDN list from URL: {}", OFAC_SDN_URL, e);
            throw new SanctionsListParseException("Failed to download OFAC SDN list", e);
        }
    }

    /**
     * Parse OFAC SDN XML from input stream
     *
     * CRITICAL: Implements XXE (XML External Entity) protection
     *
     * @param inputStream XML input stream
     * @param listSource List source identifier
     * @param listType List type
     * @return Parsed result
     * @throws SanctionsListParseException if parsing fails
     */
    public SanctionsListParseResult parseXml(InputStream inputStream, String listSource, String listType)
            throws SanctionsListParseException {

        logger.info("Parsing {} {} list from XML", listSource, listType);

        try {
            // Create secure DocumentBuilderFactory with XXE protection
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // CRITICAL SECURITY: XXE Protection
            // Disable DOCTYPE declarations completely
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            // Disable external general entities
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);

            // Disable external parameter entities
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            // Disable external DTDs
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            // Disable XInclude
            factory.setXIncludeAware(false);

            // Disable entity expansion
            factory.setExpandEntityReferences(false);

            // Create secure DocumentBuilder
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Parse XML document
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            // Extract publication information
            SanctionsListMetadata metadata = extractPublicationInfo(document, listSource, listType);

            // Parse SDN entries
            List<SanctionedEntity> entities = new ArrayList<>();
            List<SanctionedEntityAlias> allAliases = new ArrayList<>();
            Set<SanctionsProgram> programs = new HashSet<>();

            NodeList sdnEntries = document.getElementsByTagName("sdnEntry");
            logger.info("Found {} SDN entries to parse", sdnEntries.getLength());

            for (int i = 0; i < sdnEntries.getLength(); i++) {
                Element sdnEntry = (Element) sdnEntries.item(i);

                try {
                    SanctionedEntity entity = parseSdnEntry(sdnEntry, metadata);
                    entities.add(entity);

                    // Parse aliases
                    List<SanctionedEntityAlias> aliases = parseAliases(sdnEntry, entity);
                    allAliases.addAll(aliases);

                    // Parse programs
                    Set<SanctionsProgram> entityPrograms = parsePrograms(sdnEntry);
                    programs.addAll(entityPrograms);

                } catch (Exception e) {
                    logger.error("Failed to parse SDN entry at index {}: {}", i, e.getMessage(), e);
                    // Continue parsing other entries
                }
            }

            metadata.setTotalEntries(entities.size());
            metadata.setProcessingStatus("COMPLETED");
            metadata.setProcessingCompletedAt(LocalDateTime.now());

            SanctionsListParseResult result = new SanctionsListParseResult();
            result.setMetadata(metadata);
            result.setEntities(entities);
            result.setAliases(allAliases);
            result.setPrograms(new ArrayList<>(programs));

            logger.info("Successfully parsed {} list: {} entities, {} aliases, {} programs",
                    listSource, entities.size(), allAliases.size(), programs.size());

            return result;

        } catch (ParserConfigurationException | SAXException | IOException e) {
            logger.error("Failed to parse {} {} list XML", listSource, listType, e);
            throw new SanctionsListParseException("Failed to parse sanctions list XML", e);
        }
    }

    /**
     * Extract publication information from XML document
     */
    private SanctionsListMetadata extractPublicationInfo(Document document, String listSource, String listType) {
        SanctionsListMetadata metadata = new SanctionsListMetadata();
        metadata.setListSource(listSource);
        metadata.setListType(listType);
        metadata.setListName(listSource + " " + listType + " List");
        metadata.setDownloadTimestamp(LocalDateTime.now());
        metadata.setProcessingStatus("PROCESSING");
        metadata.setProcessingStartedAt(LocalDateTime.now());

        try {
            NodeList publishInfo = document.getElementsByTagName("publshInformation");
            if (publishInfo.getLength() > 0) {
                Element pubElement = (Element) publishInfo.item(0);

                // Extract publish date
                String publishDate = getElementText(pubElement, "Publish_Date");
                if (publishDate != null && !publishDate.isEmpty()) {
                    LocalDateTime versionDate = parseDate(publishDate);
                    metadata.setVersionDate(versionDate);
                    metadata.setVersionId(versionDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                }

                // Extract record count
                String recordCount = getElementText(pubElement, "Record_Count");
                if (recordCount != null && !recordCount.isEmpty()) {
                    metadata.setTotalEntries(Integer.parseInt(recordCount));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract publication info, using defaults: {}", e.getMessage());
            metadata.setVersionDate(LocalDateTime.now());
            metadata.setVersionId(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        return metadata;
    }

    /**
     * Parse individual SDN entry
     */
    private SanctionedEntity parseSdnEntry(Element sdnEntry, SanctionsListMetadata metadata) {
        SanctionedEntity entity = new SanctionedEntity();
        entity.setListMetadataId(metadata.getId());

        // UID (Source ID)
        String uid = getElementText(sdnEntry, "uid");
        entity.setSourceId(uid);

        // SDN Type
        String sdnType = getElementText(sdnEntry, "sdnType");
        entity.setEntityType(mapSdnType(sdnType));

        // Names
        String firstName = getElementText(sdnEntry, "firstName");
        String lastName = getElementText(sdnEntry, "lastName");
        String title = getElementText(sdnEntry, "title");

        entity.setFirstName(firstName);
        entity.setLastName(lastName);
        entity.setTitle(title);

        // Build primary name
        StringBuilder primaryName = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            primaryName.append(title).append(" ");
        }
        if (firstName != null && !firstName.isEmpty()) {
            primaryName.append(firstName).append(" ");
        }
        if (lastName != null && !lastName.isEmpty()) {
            primaryName.append(lastName);
        }
        entity.setPrimaryName(primaryName.toString().trim());
        entity.setNameNormalized(normalizeNameForMatching(entity.getPrimaryName()));

        // Program
        String program = getElementText(sdnEntry, "programList");
        entity.setProgramName(program);

        // Remarks
        String remarks = getElementText(sdnEntry, "remarks");
        entity.setRemarks(remarks);

        // Sanctions type
        entity.setSanctionsType("SDN"); // OFAC SDN list

        // Risk level
        entity.setRiskLevel("HIGH");
        entity.setMatchScoreThreshold(new BigDecimal("85.00"));

        // Parse dates of birth
        parseDate OfBirth(sdnEntry, entity);

        // Parse places of birth
        parsePlaceOfBirth(sdnEntry, entity);

        // Parse addresses
        parseAddresses(sdnEntry, entity);

        // Parse IDs
        parseIdentificationDocuments(sdnEntry, entity);

        // Set active status
        entity.setIsActive(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setLastVerifiedAt(LocalDateTime.now());

        return entity;
    }

    /**
     * Parse aliases (AKA list)
     */
    private List<SanctionedEntityAlias> parseAliases(Element sdnEntry, SanctionedEntity entity) {
        List<SanctionedEntityAlias> aliases = new ArrayList<>();

        NodeList akaList = sdnEntry.getElementsByTagName("aka");
        for (int i = 0; i < akaList.getLength(); i++) {
            Element aka = (Element) akaList.item(i);

            SanctionedEntityAlias alias = new SanctionedEntityAlias();
            alias.setSanctionedEntityId(entity.getId());

            String akaType = getElementText(aka, "type");
            alias.setAliasType(akaType != null ? akaType.toUpperCase().replace(".", "") : "AKA");

            String category = getElementText(aka, "category");
            if ("weak".equalsIgnoreCase(category)) {
                alias.setAliasQuality("WEAK");
            } else if ("strong".equalsIgnoreCase(category)) {
                alias.setAliasQuality("STRONG");
            } else {
                alias.setAliasQuality("STRONG"); // Default
            }

            // Build alias name
            String firstName = getElementText(aka, "firstName");
            String lastName = getElementText(aka, "lastName");

            StringBuilder aliasName = new StringBuilder();
            if (firstName != null && !firstName.isEmpty()) {
                aliasName.append(firstName).append(" ");
            }
            if (lastName != null && !lastName.isEmpty()) {
                aliasName.append(lastName);
            }

            alias.setAliasName(aliasName.toString().trim());
            alias.setAliasNameNormalized(normalizeNameForMatching(alias.getAliasName()));
            alias.setCreatedAt(LocalDateTime.now());

            if (!alias.getAliasName().isEmpty()) {
                aliases.add(alias);
            }
        }

        return aliases;
    }

    /**
     * Parse sanctions programs
     */
    private Set<SanctionsProgram> parsePrograms(Element sdnEntry) {
        Set<SanctionsProgram> programs = new HashSet<>();

        NodeList programNodes = sdnEntry.getElementsByTagName("program");
        for (int i = 0; i < programNodes.getLength(); i++) {
            String programCode = programNodes.item(i).getTextContent().trim();

            if (!programCode.isEmpty()) {
                SanctionsProgram program = new SanctionsProgram();
                program.setProgramCode(programCode);
                program.setProgramName(programCode); // Will be updated with full name later
                program.setJurisdiction("US");
                program.setIssuingAuthority("OFAC");
                program.setIsActive(true);
                program.setCreatedAt(LocalDateTime.now());

                programs.add(program);
            }
        }

        return programs;
    }

    /**
     * Parse date of birth
     */
    private void parseDateOfBirth(Element sdnEntry, SanctionedEntity entity) {
        NodeList dobList = sdnEntry.getElementsByTagName("dateOfBirthItem");
        if (dobList.getLength() > 0) {
            Element dobItem = (Element) dobList.item(0);
            String dobString = getElementText(dobItem, "dateOfBirth");

            if (dobString != null && !dobString.isEmpty()) {
                try {
                    LocalDate dob = LocalDate.parse(dobString, DATE_FORMATTER);
                    entity.setDateOfBirth(dob);
                } catch (DateTimeParseException e) {
                    try {
                        LocalDate dob = LocalDate.parse(dobString, ALT_DATE_FORMATTER);
                        entity.setDateOfBirth(dob);
                    } catch (DateTimeParseException e2) {
                        logger.debug("Could not parse date of birth: {}", dobString);
                    }
                }
            }
        }
    }

    /**
     * Parse place of birth
     */
    private void parsePlaceOfBirth(Element sdnEntry, SanctionedEntity entity) {
        NodeList pobList = sdnEntry.getElementsByTagName("placeOfBirthItem");
        if (pobList.getLength() > 0) {
            Element pobItem = (Element) pobList.item(0);
            String placeOfBirth = getElementText(pobItem, "placeOfBirth");
            entity.setPlaceOfBirth(placeOfBirth);
        }
    }

    /**
     * Parse addresses
     */
    private void parseAddresses(Element sdnEntry, SanctionedEntity entity) {
        NodeList addressList = sdnEntry.getElementsByTagName("address");
        if (addressList.getLength() > 0) {
            Element address = (Element) addressList.item(0);

            String address1 = getElementText(address, "address1");
            String address2 = getElementText(address, "address2");
            String city = getElementText(address, "city");
            String stateProvince = getElementText(address, "stateOrProvince");
            String postalCode = getElementText(address, "postalCode");
            String country = getElementText(address, "country");

            // Build full address
            StringBuilder fullAddress = new StringBuilder();
            if (address1 != null) fullAddress.append(address1).append(", ");
            if (address2 != null) fullAddress.append(address2).append(", ");

            entity.setAddress(fullAddress.toString());
            entity.setCity(city);
            entity.setStateProvince(stateProvince);
            entity.setPostalCode(postalCode);
            entity.setCountry(country);
        }
    }

    /**
     * Parse identification documents
     */
    private void parseIdentificationDocuments(Element sdnEntry, SanctionedEntity entity) {
        NodeList idList = sdnEntry.getElementsByTagName("id");

        for (int i = 0; i < idList.getLength(); i++) {
            Element id = (Element) idList.item(i);

            String idType = getElementText(id, "idType");
            String idNumber = getElementText(id, "idNumber");
            String idCountry = getElementText(id, "idCountry");

            if (idType != null && idNumber != null) {
                if (idType.contains("Passport")) {
                    entity.setPassportNumber(idNumber);
                    entity.setPassportCountry(idCountry);
                } else if (idType.contains("National ID")) {
                    entity.setNationalIdNumber(idNumber);
                    entity.setNationalIdCountry(idCountry);
                } else if (idType.contains("Tax")) {
                    entity.setTaxIdNumber(idNumber);
                } else if (idType.contains("SSN") || idType.contains("Social Security")) {
                    entity.setSsn(idNumber);
                }
            }
        }
    }

    /**
     * Map SDN type to entity type
     */
    private String mapSdnType(String sdnType) {
        if (sdnType == null) return "ENTITY";

        switch (sdnType.toLowerCase()) {
            case "individual":
                return "INDIVIDUAL";
            case "vessel":
                return "VESSEL";
            case "aircraft":
                return "AIRCRAFT";
            case "entity":
            default:
                return "ENTITY";
        }
    }

    /**
     * Normalize name for fuzzy matching
     * - Convert to lowercase
     * - Remove diacritics
     * - Remove special characters
     * - Normalize whitespace
     */
    private String normalizeNameForMatching(String name) {
        if (name == null) return "";

        return name.toLowerCase()
                .replaceAll("[àáâãäå]", "a")
                .replaceAll("[èéêë]", "e")
                .replaceAll("[ìíîï]", "i")
                .replaceAll("[òóôõö]", "o")
                .replaceAll("[ùúûü]", "u")
                .replaceAll("[ýÿ]", "y")
                .replaceAll("[ñ]", "n")
                .replaceAll("[ç]", "c")
                .replaceAll("[^a-z0-9\\s]", " ") // Remove special chars
                .replaceAll("\\s+", " ") // Normalize whitespace
                .trim();
    }

    /**
     * Parse date string with multiple format support
     */
    private LocalDateTime parseDate(String dateStr) {
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e1) {
            try {
                LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
                return date.atStartOfDay();
            } catch (DateTimeParseException e2) {
                try {
                    LocalDate date = LocalDate.parse(dateStr, ALT_DATE_FORMATTER);
                    return date.atStartOfDay();
                } catch (DateTimeParseException e3) {
                    logger.warn("Could not parse date: {}", dateStr);
                    return LocalDateTime.now();
                }
            }
        }
    }

    /**
     * Get text content of element by tag name
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            String text = node.getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    /**
     * Calculate SHA-256 hash of content
     */
    public String calculateHash(byte[] content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }
}
