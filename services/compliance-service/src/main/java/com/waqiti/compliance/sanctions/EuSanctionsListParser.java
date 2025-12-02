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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * EU Consolidated Sanctions List Parser
 *
 * Parses the European Union consolidated sanctions list XML and converts to database entities.
 *
 * EU Sanctions List: https://webgate.ec.europa.eu/fsd/fsf
 * XML Download: https://webgate.ec.europa.eu/fsd/fsf/public/files/xmlFullSanctionsList_1_1/content
 *
 * CRITICAL: Implements XXE protection to prevent XML External Entity attacks.
 *
 * XML Structure:
 * <export>
 *   <sanctionEntity>
 *     <nameAlias>
 *       <wholeName>...</wholeName>
 *       <firstName>...</firstName>
 *       <lastName>...</lastName>
 *       <function>...</function>
 *       <gender>...</gender>
 *     </nameAlias>
 *     <birthdate>
 *       <birthdate>...</birthdate>
 *       <city>...</city>
 *       <countryIso2Code>...</countryIso2Code>
 *     </birthdate>
 *     <identification>
 *       <identificationTypeCode>...</identificationTypeCode>
 *       <number>...</number>
 *       <countryIso2Code>...</countryIso2Code>
 *     </identification>
 *     <address>
 *       <street>...</street>
 *       <city>...</city>
 *       <zipCode>...</zipCode>
 *       <countryIso2Code>...</countryIso2Code>
 *     </address>
 *     <regulation>
 *       <programme>...</programme>
 *       <publicationDate>...</publicationDate>
 *     </regulation>
 *   </sanctionEntity>
 * </export>
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Service
public class EuSanctionsListParser {

    private static final Logger logger = LoggerFactory.getLogger(EuSanctionsListParser.class);

    private static final String EU_SANCTIONS_URL =
        "https://webgate.ec.europa.eu/fsd/fsf/public/files/xmlFullSanctionsList_1_1/content";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ALT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Download and parse EU consolidated sanctions list
     *
     * @return Parsed sanctions list result
     * @throws SanctionsListParseException if download or parse fails
     */
    public SanctionsListParseResult downloadAndParse() throws SanctionsListParseException {
        logger.info("Starting EU sanctions list download from: {}", EU_SANCTIONS_URL);

        try {
            // Download XML file
            URL url = new URL(EU_SANCTIONS_URL);
            InputStream inputStream = url.openStream();

            // Parse XML
            SanctionsListParseResult result = parseXml(inputStream, "EU", "CONSOLIDATED");

            inputStream.close();

            logger.info("Successfully downloaded and parsed EU sanctions list: {} entities, {} aliases",
                    result.getEntities().size(), result.getTotalAliases());

            return result;

        } catch (IOException e) {
            logger.error("Failed to download EU sanctions list from URL: {}", EU_SANCTIONS_URL, e);
            throw new SanctionsListParseException("Failed to download EU sanctions list", e);
        }
    }

    /**
     * Parse EU sanctions XML from input stream
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
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            // Create secure DocumentBuilder
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Parse XML document
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            // Create metadata
            SanctionsListMetadata metadata = createMetadata(listSource, listType);

            // Parse sanction entities
            List<SanctionedEntity> entities = new ArrayList<>();
            List<SanctionedEntityAlias> allAliases = new ArrayList<>();
            Set<SanctionsProgram> programs = new HashSet<>();

            NodeList sanctionEntities = document.getElementsByTagName("sanctionEntity");
            logger.info("Found {} EU sanction entities to parse", sanctionEntities.getLength());

            for (int i = 0; i < sanctionEntities.getLength(); i++) {
                Element sanctionEntity = (Element) sanctionEntities.item(i);

                try {
                    SanctionedEntity entity = parseEntity(sanctionEntity, metadata);
                    entities.add(entity);

                    // Parse aliases from nameAlias elements
                    List<SanctionedEntityAlias> aliases = parseAliases(sanctionEntity, entity);
                    allAliases.addAll(aliases);

                    // Parse programs from regulation elements
                    Set<SanctionsProgram> entityPrograms = parsePrograms(sanctionEntity);
                    programs.addAll(entityPrograms);

                } catch (Exception e) {
                    logger.error("Failed to parse EU sanction entity at index {}: {}", i, e.getMessage(), e);
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
            throw new SanctionsListParseException("Failed to parse EU sanctions list XML", e);
        }
    }

    /**
     * Create metadata for EU list
     */
    private SanctionsListMetadata createMetadata(String listSource, String listType) {
        SanctionsListMetadata metadata = new SanctionsListMetadata();
        metadata.setListSource(listSource);
        metadata.setListType(listType);
        metadata.setListName("EU Consolidated Sanctions List");
        metadata.setDownloadTimestamp(LocalDateTime.now());
        metadata.setVersionDate(LocalDateTime.now());
        metadata.setVersionId(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        metadata.setProcessingStatus("PROCESSING");
        metadata.setProcessingStartedAt(LocalDateTime.now());
        metadata.setSourceUrl(EU_SANCTIONS_URL);

        return metadata;
    }

    /**
     * Parse individual sanction entity
     */
    private SanctionedEntity parseEntity(Element entityElement, SanctionsListMetadata metadata) {
        SanctionedEntity entity = new SanctionedEntity();
        entity.setListMetadataId(metadata.getId());

        // Entity ID
        String entityId = getElementText(entityElement, "euReferenceNumber");
        if (entityId == null) {
            entityId = getElementText(entityElement, "logicalId");
        }
        entity.setSourceId(entityId != null ? entityId : UUID.randomUUID().toString());

        // Entity type
        String subjectType = getElementText(entityElement, "subjectType");
        entity.setEntityType(mapSubjectType(subjectType));

        // Parse primary name from first nameAlias
        NodeList nameAliases = entityElement.getElementsByTagName("nameAlias");
        if (nameAliases.getLength() > 0) {
            Element nameAlias = (Element) nameAliases.item(0);
            parseNameAlias(nameAlias, entity, true);
        }

        // Parse birthdate
        parseBirthdate(entityElement, entity);

        // Parse identification documents
        parseIdentifications(entityElement, entity);

        // Parse addresses
        parseAddresses(entityElement, entity);

        // Parse citizenship
        parseCitizenship(entityElement, entity);

        // Sanctions details
        entity.setSanctionsType("ASSET_FREEZE");
        entity.setRiskLevel("HIGH");
        entity.setMatchScoreThreshold(new BigDecimal("85.00"));
        entity.setIsActive(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setLastVerifiedAt(LocalDateTime.now());

        return entity;
    }

    /**
     * Parse name alias and set to entity
     */
    private void parseNameAlias(Element nameAlias, SanctionedEntity entity, boolean isPrimary) {
        String wholeName = getElementText(nameAlias, "wholeName");
        String firstName = getElementText(nameAlias, "firstName");
        String lastName = getElementText(nameAlias, "lastName");
        String middleName = getElementText(nameAlias, "middleName");
        String title = getElementText(nameAlias, "title");
        String gender = getElementText(nameAlias, "gender");
        String function = getElementText(nameAlias, "function");

        if (isPrimary) {
            entity.setFirstName(firstName);
            entity.setLastName(lastName);
            entity.setMiddleName(middleName);
            entity.setTitle(title);
            entity.setGender(gender);

            // Use wholeName if available, otherwise build from parts
            if (wholeName != null && !wholeName.isEmpty()) {
                entity.setPrimaryName(wholeName);
            } else {
                StringBuilder primaryName = new StringBuilder();
                if (title != null) primaryName.append(title).append(" ");
                if (firstName != null) primaryName.append(firstName).append(" ");
                if (middleName != null) primaryName.append(middleName).append(" ");
                if (lastName != null) primaryName.append(lastName);
                entity.setPrimaryName(primaryName.toString().trim());
            }

            entity.setNameNormalized(normalizeNameForMatching(entity.getPrimaryName()));

            // Store function/position in organization type for entities
            if (function != null && "ENTITY".equals(entity.getEntityType())) {
                entity.setOrganizationType(function);
            }
        }
    }

    /**
     * Parse aliases (additional nameAlias elements)
     */
    private List<SanctionedEntityAlias> parseAliases(Element entityElement, SanctionedEntity entity) {
        List<SanctionedEntityAlias> aliases = new ArrayList<>();

        NodeList nameAliases = entityElement.getElementsByTagName("nameAlias");

        // Skip first one (used for primary name), process rest as aliases
        for (int i = 1; i < nameAliases.getLength(); i++) {
            Element nameAlias = (Element) nameAliases.item(i);

            SanctionedEntityAlias alias = new SanctionedEntityAlias();
            alias.setSanctionedEntityId(entity.getId());
            alias.setAliasType("AKA");
            alias.setAliasQuality("STRONG");

            String wholeName = getElementText(nameAlias, "wholeName");
            String firstName = getElementText(nameAlias, "firstName");
            String lastName = getElementText(nameAlias, "lastName");
            String middleName = getElementText(nameAlias, "middleName");

            if (wholeName != null && !wholeName.isEmpty()) {
                alias.setAliasName(wholeName);
            } else {
                StringBuilder aliasName = new StringBuilder();
                if (firstName != null) aliasName.append(firstName).append(" ");
                if (middleName != null) aliasName.append(middleName).append(" ");
                if (lastName != null) aliasName.append(lastName);
                alias.setAliasName(aliasName.toString().trim());
            }

            alias.setAliasNameNormalized(normalizeNameForMatching(alias.getAliasName()));
            alias.setCreatedAt(LocalDateTime.now());

            if (!alias.getAliasName().isEmpty()) {
                aliases.add(alias);
            }
        }

        return aliases;
    }

    /**
     * Parse birthdate information
     */
    private void parseBirthdate(Element entityElement, SanctionedEntity entity) {
        NodeList birthdates = entityElement.getElementsByTagName("birthdate");

        if (birthdates.getLength() > 0) {
            Element birthdate = (Element) birthdates.item(0);

            String dateStr = getElementText(birthdate, "birthdate");
            if (dateStr != null) {
                try {
                    LocalDate dob = LocalDate.parse(dateStr, DATE_FORMATTER);
                    entity.setDateOfBirth(dob);
                } catch (DateTimeParseException e) {
                    try {
                        LocalDate dob = LocalDate.parse(dateStr, ALT_DATE_FORMATTER);
                        entity.setDateOfBirth(dob);
                    } catch (DateTimeParseException e2) {
                        logger.debug("Could not parse birth date: {}", dateStr);
                    }
                }
            }

            String city = getElementText(birthdate, "city");
            String countryCode = getElementText(birthdate, "countryIso2Code");

            if (city != null || countryCode != null) {
                StringBuilder placeOfBirth = new StringBuilder();
                if (city != null) placeOfBirth.append(city);
                if (countryCode != null) {
                    if (placeOfBirth.length() > 0) placeOfBirth.append(", ");
                    placeOfBirth.append(countryCode);
                }
                entity.setPlaceOfBirth(placeOfBirth.toString());
            }
        }
    }

    /**
     * Parse identification documents
     */
    private void parseIdentifications(Element entityElement, SanctionedEntity entity) {
        NodeList identifications = entityElement.getElementsByTagName("identification");

        for (int i = 0; i < identifications.getLength(); i++) {
            Element identification = (Element) identifications.item(i);

            String idType = getElementText(identification, "identificationTypeCode");
            String number = getElementText(identification, "number");
            String country = getElementText(identification, "countryIso2Code");

            if (idType != null && number != null) {
                if (idType.toLowerCase().contains("passport")) {
                    entity.setPassportNumber(number);
                    entity.setPassportCountry(country);
                } else if (idType.toLowerCase().contains("national") || idType.toLowerCase().contains("id")) {
                    entity.setNationalIdNumber(number);
                    entity.setNationalIdCountry(country);
                } else if (idType.toLowerCase().contains("tax")) {
                    entity.setTaxIdNumber(number);
                }
            }
        }
    }

    /**
     * Parse addresses
     */
    private void parseAddresses(Element entityElement, SanctionedEntity entity) {
        NodeList addresses = entityElement.getElementsByTagName("address");

        if (addresses.getLength() > 0) {
            Element address = (Element) addresses.item(0);

            String street = getElementText(address, "street");
            String city = getElementText(address, "city");
            String zipCode = getElementText(address, "zipCode");
            String countryCode = getElementText(address, "countryIso2Code");

            StringBuilder fullAddress = new StringBuilder();
            if (street != null) fullAddress.append(street).append(", ");

            entity.setAddress(fullAddress.toString());
            entity.setCity(city);
            entity.setPostalCode(zipCode);
            entity.setCountry(countryCode);
        }
    }

    /**
     * Parse citizenship/nationality
     */
    private void parseCitizenship(Element entityElement, SanctionedEntity entity) {
        NodeList citizenships = entityElement.getElementsByTagName("citizenship");

        if (citizenships.getLength() > 0) {
            Element citizenship = (Element) citizenships.item(0);
            String countryCode = getElementText(citizenship, "countryIso2Code");
            entity.setNationality(countryCode);
            entity.setCountryOfResidence(countryCode);
        }
    }

    /**
     * Parse sanctions programs
     */
    private Set<SanctionsProgram> parsePrograms(Element entityElement) {
        Set<SanctionsProgram> programs = new HashSet<>();

        NodeList regulations = entityElement.getElementsByTagName("regulation");

        for (int i = 0; i < regulations.getLength(); i++) {
            Element regulation = (Element) regulations.item(i);

            String programme = getElementText(regulation, "programme");
            String publicationDate = getElementText(regulation, "publicationDate");

            if (programme != null && !programme.isEmpty()) {
                SanctionsProgram program = new SanctionsProgram();
                program.setProgramCode(programme);
                program.setProgramName(programme);
                program.setJurisdiction("EU");
                program.setIssuingAuthority("European Union");
                program.setIsActive(true);
                program.setCreatedAt(LocalDateTime.now());

                if (publicationDate != null) {
                    try {
                        LocalDate date = LocalDate.parse(publicationDate, DATE_FORMATTER);
                        program.setEffectiveDate(date);
                    } catch (DateTimeParseException e) {
                        logger.debug("Could not parse publication date: {}", publicationDate);
                    }
                }

                programs.add(program);
            }
        }

        return programs;
    }

    /**
     * Map EU subject type to entity type
     */
    private String mapSubjectType(String subjectType) {
        if (subjectType == null) return "ENTITY";

        switch (subjectType.toLowerCase()) {
            case "person":
                return "INDIVIDUAL";
            case "enterprise":
            case "entity":
                return "ENTITY";
            default:
                return "ENTITY";
        }
    }

    /**
     * Normalize name for fuzzy matching
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
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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
}
