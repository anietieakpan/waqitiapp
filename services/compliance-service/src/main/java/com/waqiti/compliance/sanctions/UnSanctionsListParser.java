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
import java.util.*;

/**
 * UN Security Council Consolidated Sanctions List Parser
 *
 * Parses the United Nations Security Council consolidated sanctions list XML.
 *
 * UN Sanctions List: https://scsanctions.un.org
 * XML Download: https://scsanctions.un.org/resources/xml/en/consolidated.xml
 *
 * CRITICAL: Implements XXE protection.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Service
public class UnSanctionsListParser {

    private static final Logger logger = LoggerFactory.getLogger(UnSanctionsListParser.class);

    private static final String UN_SANCTIONS_URL =
        "https://scsanctions.un.org/resources/xml/en/consolidated.xml";

    /**
     * Download and parse UN consolidated sanctions list
     */
    public SanctionsListParseResult downloadAndParse() throws SanctionsListParseException {
        logger.info("Starting UN sanctions list download from: {}", UN_SANCTIONS_URL);

        try {
            URL url = new URL(UN_SANCTIONS_URL);
            InputStream inputStream = url.openStream();
            SanctionsListParseResult result = parseXml(inputStream, "UN", "CONSOLIDATED");
            inputStream.close();

            logger.info("Successfully downloaded and parsed UN sanctions list: {} entities",
                    result.getEntities().size());

            return result;
        } catch (IOException e) {
            logger.error("Failed to download UN sanctions list", e);
            throw new SanctionsListParseException("Failed to download UN sanctions list", e);
        }
    }

    /**
     * Parse UN sanctions XML with XXE protection
     */
    public SanctionsListParseResult parseXml(InputStream inputStream, String listSource, String listType)
            throws SanctionsListParseException {

        try {
            // Secure DocumentBuilderFactory with XXE protection
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            SanctionsListMetadata metadata = createMetadata(listSource, listType);
            List<SanctionedEntity> entities = new ArrayList<>();
            List<SanctionedEntityAlias> allAliases = new ArrayList<>();

            NodeList individuals = document.getElementsByTagName("INDIVIDUAL");
            NodeList entitiesNodes = document.getElementsByTagName("ENTITY");

            // Parse individuals
            for (int i = 0; i < individuals.getLength(); i++) {
                Element individual = (Element) individuals.item(i);
                SanctionedEntity entity = parseIndividual(individual, metadata);
                entities.add(entity);
                allAliases.addAll(parseAliasesFromElement(individual, entity));
            }

            // Parse entities
            for (int i = 0; i < entitiesNodes.getLength(); i++) {
                Element entityNode = (Element) entitiesNodes.item(i);
                SanctionedEntity entity = parseEntity(entityNode, metadata);
                entities.add(entity);
                allAliases.addAll(parseAliasesFromElement(entityNode, entity));
            }

            metadata.setTotalEntries(entities.size());
            metadata.setProcessingStatus("COMPLETED");
            metadata.setProcessingCompletedAt(LocalDateTime.now());

            SanctionsListParseResult result = new SanctionsListParseResult();
            result.setMetadata(metadata);
            result.setEntities(entities);
            result.setAliases(allAliases);

            return result;

        } catch (Exception e) {
            logger.error("Failed to parse UN sanctions list", e);
            throw new SanctionsListParseException("Failed to parse UN sanctions list", e);
        }
    }

    private SanctionsListMetadata createMetadata(String listSource, String listType) {
        SanctionsListMetadata metadata = new SanctionsListMetadata();
        metadata.setListSource(listSource);
        metadata.setListType(listType);
        metadata.setListName("UN Security Council Consolidated Sanctions List");
        metadata.setDownloadTimestamp(LocalDateTime.now());
        metadata.setVersionDate(LocalDateTime.now());
        metadata.setVersionId(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        metadata.setProcessingStatus("PROCESSING");
        metadata.setProcessingStartedAt(LocalDateTime.now());
        metadata.setSourceUrl(UN_SANCTIONS_URL);
        return metadata;
    }

    private SanctionedEntity parseIndividual(Element element, SanctionsListMetadata metadata) {
        SanctionedEntity entity = new SanctionedEntity();
        entity.setListMetadataId(metadata.getId());
        entity.setSourceId(getElementText(element, "REFERENCE_NUMBER"));
        entity.setEntityType("INDIVIDUAL");
        entity.setFirstName(getElementText(element, "FIRST_NAME"));
        entity.setLastName(getElementText(element, "SECOND_NAME"));

        String fullName = (entity.getFirstName() != null ? entity.getFirstName() + " " : "") +
                         (entity.getLastName() != null ? entity.getLastName() : "");
        entity.setPrimaryName(fullName.trim());
        entity.setNameNormalized(normalizeNameForMatching(entity.getPrimaryName()));
        entity.setSanctionsType("UN_SANCTIONS");
        entity.setRiskLevel("HIGH");
        entity.setMatchScoreThreshold(new BigDecimal("85.00"));
        entity.setIsActive(true);
        entity.setCreatedAt(LocalDateTime.now());

        return entity;
    }

    private SanctionedEntity parseEntity(Element element, SanctionsListMetadata metadata) {
        SanctionedEntity entity = new SanctionedEntity();
        entity.setListMetadataId(metadata.getId());
        entity.setSourceId(getElementText(element, "REFERENCE_NUMBER"));
        entity.setEntityType("ENTITY");

        String name = getElementText(element, "FIRST_NAME");
        entity.setPrimaryName(name != null ? name : "");
        entity.setNameNormalized(normalizeNameForMatching(entity.getPrimaryName()));
        entity.setSanctionsType("UN_SANCTIONS");
        entity.setRiskLevel("HIGH");
        entity.setMatchScoreThreshold(new BigDecimal("85.00"));
        entity.setIsActive(true);
        entity.setCreatedAt(LocalDateTime.now());

        return entity;
    }

    private List<SanctionedEntityAlias> parseAliasesFromElement(Element element, SanctionedEntity entity) {
        List<SanctionedEntityAlias> aliases = new ArrayList<>();
        NodeList aliasNodes = element.getElementsByTagName("INDIVIDUAL_ALIAS");

        for (int i = 0; i < aliasNodes.getLength(); i++) {
            Element aliasNode = (Element) aliasNodes.item(i);
            SanctionedEntityAlias alias = new SanctionedEntityAlias();
            alias.setSanctionedEntityId(entity.getId());
            alias.setAliasType("AKA");
            alias.setAliasQuality("STRONG");

            String aliasName = (getElementText(aliasNode, "ALIAS_NAME") != null) ?
                              getElementText(aliasNode, "ALIAS_NAME") : "";
            alias.setAliasName(aliasName);
            alias.setAliasNameNormalized(normalizeNameForMatching(aliasName));
            alias.setCreatedAt(LocalDateTime.now());

            if (!aliasName.isEmpty()) {
                aliases.add(alias);
            }
        }

        return aliases;
    }

    private String normalizeNameForMatching(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replaceAll("[àáâãäå]", "a")
                .replaceAll("[èéêë]", "e")
                .replaceAll("[ìíîï]", "i")
                .replaceAll("[òóôõö]", "o")
                .replaceAll("[ùúûü]", "u")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String getElementText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String text = nodeList.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }
}
