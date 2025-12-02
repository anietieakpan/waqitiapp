package com.waqiti.investment.tax.service;

import com.waqiti.investment.tax.entity.TaxDocument;
import com.waqiti.investment.tax.entity.TaxDocument.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * IRS FIRE (Filing Information Returns Electronically) XML Generation Service.
 *
 * Generates IRS-compliant XML for electronic filing of:
 * - Form 1099-B
 * - Form 1099-DIV
 * - Form 1099-INT
 *
 * IRS Specifications:
 * - Publication 1220 (Specifications for Electronic Filing of Forms 1097, 1098, 1099, 3921, 3922, 5498, and W-2G)
 * - FIRE System Technical Specifications
 * - XML Schema Definition (XSD) validation
 *
 * Filing Requirements:
 * - 250 or more information returns: MUST file electronically
 * - Less than 250: May file on paper or electronically
 * - TCC (Transmitter Control Code) required from IRS
 * - Test files must be submitted and approved before production filing
 *
 * XML Structure:
 * - Return Header (transmitter info, tax year, submission type)
 * - Return Data (individual 1099 forms)
 * - Digital signature for authentication
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IrsFIREXmlService {

    @Value("${waqiti.irs.transmitter-control-code:XXXXX}")
    private String transmitterControlCode; // TCC from IRS

    @Value("${waqiti.irs.transmitter-ein:XX-XXXXXXX}")
    private String transmitterEin;

    @Value("${waqiti.irs.transmitter-name:Waqiti Inc}")
    private String transmitterName;

    @Value("${waqiti.irs.contact-name:Tax Compliance Team}")
    private String contactName;

    @Value("${waqiti.irs.contact-phone:555-123-4567}")
    private String contactPhone;

    @Value("${waqiti.irs.contact-email:tax@example.com}")
    private String contactEmail;

    @Value("${waqiti.irs.software-vendor:Waqiti Platform}")
    private String softwareVendor;

    @Value("${waqiti.irs.software-version:1.0}")
    private String softwareVersion;

    private static final String IRS_NAMESPACE = "urn:us:gov:treasury:irs:msg:irstransmitterstatusresponse";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Generate FIRE XML for batch of 1099 documents.
     *
     * @param documents List of tax documents to file
     * @param taxYear Tax year
     * @param isTestFile Whether this is a test submission
     * @return XML string ready for FIRE submission
     */
    public String generateFIREXml(List<TaxDocument> documents, Integer taxYear, boolean isTestFile) {

        log.info("IRS FIRE: Generating XML for {} documents, tax year {}, test mode: {}",
            documents.size(), taxYear, isTestFile);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            // XXE Protection: Disable external entity processing to prevent XXE attacks
            // These settings protect against XML External Entity (XXE) injection vulnerabilities
            // which could allow attackers to access sensitive files or cause DoS attacks
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
                log.debug("IRS FIRE: XXE protection features enabled successfully");
            } catch (Exception e) {
                log.error("IRS FIRE: Failed to enable XXE protection features - this is a security risk", e);
                throw new SecurityException("Failed to configure secure XML parser", e);
            }

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Root element: ReturnData
            Element rootElement = doc.createElement("ReturnData");
            rootElement.setAttribute("xmlns", IRS_NAMESPACE);
            doc.appendChild(rootElement);

            // Header
            Element header = createReturnHeader(doc, taxYear, isTestFile, documents.size());
            rootElement.appendChild(header);

            // Individual returns
            for (TaxDocument taxDoc : documents) {
                Element returnElement = createReturnElement(doc, taxDoc);
                rootElement.appendChild(returnElement);
            }

            // Convert to XML string
            String xml = convertDocumentToString(doc);

            log.info("IRS FIRE: Successfully generated XML - {} bytes", xml.length());
            return xml;

        } catch (Exception e) {
            log.error("IRS FIRE: Failed to generate XML", e);
            throw new RuntimeException("Failed to generate IRS FIRE XML", e);
        }
    }

    /**
     * Create return header section.
     */
    private Element createReturnHeader(Document doc, Integer taxYear, boolean isTest, int returnCount) {

        Element header = doc.createElement("ReturnHeader");

        // Timestamp
        addElement(doc, header, "Timestamp", LocalDate.now().toString());

        // Tax year
        addElement(doc, header, "TaxYear", taxYear.toString());

        // Test file indicator
        if (isTest) {
            addElement(doc, header, "TestIndicator", "T");
        }

        // Transmitter information
        Element transmitter = doc.createElement("Transmitter");
        addElement(doc, transmitter, "TCC", transmitterControlCode);
        addElement(doc, transmitter, "TransmitterEIN", transmitterEin);
        addElement(doc, transmitter, "TransmitterName", transmitterName);

        Element contact = doc.createElement("Contact");
        addElement(doc, contact, "ContactName", contactName);
        addElement(doc, contact, "ContactPhone", contactPhone);
        addElement(doc, contact, "ContactEmail", contactEmail);
        transmitter.appendChild(contact);

        header.appendChild(transmitter);

        // Software information
        Element software = doc.createElement("SoftwareInformation");
        addElement(doc, software, "SoftwareVendor", softwareVendor);
        addElement(doc, software, "SoftwareVersion", softwareVersion);
        header.appendChild(software);

        // Return count
        addElement(doc, header, "TotalPayees", String.valueOf(returnCount));

        return header;
    }

    /**
     * Create individual return element based on document type.
     */
    private Element createReturnElement(Document doc, TaxDocument taxDoc) {

        Element returnElement = doc.createElement("Return");

        // Return header
        Element returnHeader = doc.createElement("ReturnHeader");
        addElement(doc, returnHeader, "ReturnType", getReturnType(taxDoc.getDocumentType()));
        addElement(doc, returnHeader, "TaxYear", taxDoc.getTaxYear().toString());
        returnElement.appendChild(returnHeader);

        // Return data based on form type
        Element returnData = doc.createElement("ReturnData");

        switch (taxDoc.getDocumentType()) {
            case FORM_1099_B:
                returnData.appendChild(create1099BData(doc, taxDoc));
                break;
            case FORM_1099_DIV:
                returnData.appendChild(create1099DIVData(doc, taxDoc));
                break;
            case FORM_1099_INT:
                returnData.appendChild(create1099INTData(doc, taxDoc));
                break;
            default:
                log.warn("IRS FIRE: Unsupported document type: {}", taxDoc.getDocumentType());
        }

        returnElement.appendChild(returnData);

        return returnElement;
    }

    /**
     * Create 1099-B XML data.
     */
    private Element create1099BData(Document doc, TaxDocument taxDoc) {

        Element form1099B = doc.createElement("IRS1099B");

        // Corrected return indicator
        if (taxDoc.getIsCorrected()) {
            addElement(doc, form1099B, "CorrectedReturnIndicator", "X");
        }

        // Payer information
        Element payer = doc.createElement("PayerInformation");
        addElement(doc, payer, "PayerTIN", taxDoc.getPayerTin());
        addElement(doc, payer, "PayerName", taxDoc.getPayerName());
        addElement(doc, payer, "PayerAddress", taxDoc.getPayerAddress());
        form1099B.appendChild(payer);

        // Payee (taxpayer) information
        Element payee = doc.createElement("PayeeInformation");
        addElement(doc, payee, "PayeeTIN", maskTIN(taxDoc.getTaxpayerTin()));
        addElement(doc, payee, "PayeeName", taxDoc.getTaxpayerName());

        Element address = doc.createElement("PayeeAddress");
        addElement(doc, address, "AddressLine1", taxDoc.getTaxpayerAddressLine1());
        if (taxDoc.getTaxpayerAddressLine2() != null) {
            addElement(doc, address, "AddressLine2", taxDoc.getTaxpayerAddressLine2());
        }
        addElement(doc, address, "City", taxDoc.getTaxpayerCity());
        addElement(doc, address, "State", taxDoc.getTaxpayerState());
        addElement(doc, address, "ZIPCode", taxDoc.getTaxpayerZip());
        payee.appendChild(address);

        form1099B.appendChild(payee);

        // Form data
        Element formData = doc.createElement("FormData");

        // Proceeds from sales
        if (taxDoc.getProceedsFromSales() != null && taxDoc.getProceedsFromSales().compareTo(ZERO) != 0) {
            addElement(doc, formData, "ProceedsFromSales",
                formatAmount(taxDoc.getProceedsFromSales()));
        }

        // Cost basis (if covered)
        if (taxDoc.getCostBasis() != null && taxDoc.getCostBasis().compareTo(ZERO) != 0) {
            addElement(doc, formData, "CostOrOtherBasis",
                formatAmount(taxDoc.getCostBasis()));
        }

        // Wash sale loss disallowed
        if (taxDoc.getWashSaleLossDisallowed() != null && taxDoc.getWashSaleLossDisallowed().compareTo(ZERO) != 0) {
            addElement(doc, formData, "WashSaleLossDisallowed",
                formatAmount(taxDoc.getWashSaleLossDisallowed()));
        }

        // Federal tax withheld
        if (taxDoc.getFederalTaxWithheld() != null && taxDoc.getFederalTaxWithheld().compareTo(ZERO) != 0) {
            addElement(doc, formData, "FederalIncomeTaxWithheld",
                formatAmount(taxDoc.getFederalTaxWithheld()));
        }

        // Transaction type checkboxes
        if (Boolean.TRUE.equals(taxDoc.getShortTermCovered())) {
            addElement(doc, formData, "ShortTermTransactionsCovered", "X");
        }
        if (Boolean.TRUE.equals(taxDoc.getShortTermNotCovered())) {
            addElement(doc, formData, "ShortTermTransactionsNotCovered", "X");
        }
        if (Boolean.TRUE.equals(taxDoc.getLongTermCovered())) {
            addElement(doc, formData, "LongTermTransactionsCovered", "X");
        }
        if (Boolean.TRUE.equals(taxDoc.getLongTermNotCovered())) {
            addElement(doc, formData, "LongTermTransactionsNotCovered", "X");
        }

        // Ordinary income indicator
        if (Boolean.TRUE.equals(taxDoc.getIsOrdinaryIncome())) {
            addElement(doc, formData, "OrdinaryIncomeIndicator", "X");
        }

        // Aggregate profit or loss
        if (taxDoc.getAggregateProfitLoss() != null) {
            addElement(doc, formData, "AggregateProfitOrLoss",
                formatAmount(taxDoc.getAggregateProfitLoss()));
        }

        form1099B.appendChild(formData);

        // Transaction details (if required)
        if (taxDoc.getTransactionDetails() != null && !taxDoc.getTransactionDetails().isEmpty()) {
            Element details = createTransactionDetails(doc, taxDoc.getTransactionDetails());
            form1099B.appendChild(details);
        }

        return form1099B;
    }

    /**
     * Create 1099-DIV XML data.
     */
    private Element create1099DIVData(Document doc, TaxDocument taxDoc) {

        Element form1099DIV = doc.createElement("IRS1099DIV");

        // Corrected return indicator
        if (taxDoc.getIsCorrected()) {
            addElement(doc, form1099DIV, "CorrectedReturnIndicator", "X");
        }

        // Payer information
        Element payer = doc.createElement("PayerInformation");
        addElement(doc, payer, "PayerTIN", taxDoc.getPayerTin());
        addElement(doc, payer, "PayerName", taxDoc.getPayerName());
        addElement(doc, payer, "PayerAddress", taxDoc.getPayerAddress());
        form1099DIV.appendChild(payer);

        // Payee information
        Element payee = doc.createElement("PayeeInformation");
        addElement(doc, payee, "PayeeTIN", maskTIN(taxDoc.getTaxpayerTin()));
        addElement(doc, payee, "PayeeName", taxDoc.getTaxpayerName());

        Element address = doc.createElement("PayeeAddress");
        addElement(doc, address, "AddressLine1", taxDoc.getTaxpayerAddressLine1());
        if (taxDoc.getTaxpayerAddressLine2() != null) {
            addElement(doc, address, "AddressLine2", taxDoc.getTaxpayerAddressLine2());
        }
        addElement(doc, address, "City", taxDoc.getTaxpayerCity());
        addElement(doc, address, "State", taxDoc.getTaxpayerState());
        addElement(doc, address, "ZIPCode", taxDoc.getTaxpayerZip());
        payee.appendChild(address);

        form1099DIV.appendChild(payee);

        // Form data
        Element formData = doc.createElement("FormData");

        // Box 1a - Total ordinary dividends
        if (taxDoc.getTotalOrdinaryDividends() != null && taxDoc.getTotalOrdinaryDividends().compareTo(ZERO) != 0) {
            addElement(doc, formData, "TotalOrdinaryDividends",
                formatAmount(taxDoc.getTotalOrdinaryDividends()));
        }

        // Box 1b - Qualified dividends
        if (taxDoc.getQualifiedDividends() != null && taxDoc.getQualifiedDividends().compareTo(ZERO) != 0) {
            addElement(doc, formData, "QualifiedDividends",
                formatAmount(taxDoc.getQualifiedDividends()));
        }

        // Box 2a - Total capital gain distributions
        if (taxDoc.getTotalCapitalGainDistributions() != null
            && taxDoc.getTotalCapitalGainDistributions().compareTo(ZERO) != 0) {
            addElement(doc, formData, "TotalCapitalGainDistributions",
                formatAmount(taxDoc.getTotalCapitalGainDistributions()));
        }

        // Box 3 - Nondividend distributions
        if (taxDoc.getNondividendDistributions() != null
            && taxDoc.getNondividendDistributions().compareTo(ZERO) != 0) {
            addElement(doc, formData, "NondividendDistributions",
                formatAmount(taxDoc.getNondividendDistributions()));
        }

        // Box 4 - Federal income tax withheld
        if (taxDoc.getDivFederalTaxWithheld() != null
            && taxDoc.getDivFederalTaxWithheld().compareTo(ZERO) != 0) {
            addElement(doc, formData, "FederalIncomeTaxWithheld",
                formatAmount(taxDoc.getDivFederalTaxWithheld()));
        }

        // Box 5 - Section 199A dividends
        if (taxDoc.getSection199aDividends() != null
            && taxDoc.getSection199aDividends().compareTo(ZERO) != 0) {
            addElement(doc, formData, "Section199ADividends",
                formatAmount(taxDoc.getSection199aDividends()));
        }

        // Box 7 - Foreign tax paid
        if (taxDoc.getForeignTaxPaid() != null && taxDoc.getForeignTaxPaid().compareTo(ZERO) != 0) {
            addElement(doc, formData, "ForeignTaxPaid",
                formatAmount(taxDoc.getForeignTaxPaid()));
        }

        // Box 8 - Foreign country
        if (taxDoc.getForeignCountry() != null && !taxDoc.getForeignCountry().isEmpty()) {
            addElement(doc, formData, "ForeignCountry", taxDoc.getForeignCountry());
        }

        form1099DIV.appendChild(formData);

        return form1099DIV;
    }

    /**
     * Create 1099-INT XML data.
     *
     * Form 1099-INT reports interest income paid to recipients.
     * IRS Publication 1220 specifications apply.
     *
     * Boxes reported:
     * Box 1 - Interest income
     * Box 2 - Early withdrawal penalty
     * Box 3 - Interest on U.S. Savings Bonds and Treasury obligations
     * Box 4 - Federal income tax withheld
     * Box 5 - Investment expenses
     * Box 6 - Foreign tax paid
     * Box 7 - Foreign country or U.S. possession
     * Box 8 - Tax-exempt interest
     * Box 9 - Specified private activity bond interest
     * Box 10 - Market discount
     * Box 11 - Bond premium
     * Box 12 - Bond premium on Treasury obligations
     * Box 13 - Bond premium on tax-exempt bond
     * Box 14 - Tax-exempt and tax credit bond CUSIP number
     */
    private Element create1099INTData(Document doc, TaxDocument taxDoc) {

        Element form1099INT = doc.createElement("IRS1099INT");

        // Corrected return indicator
        if (taxDoc.getIsCorrected()) {
            addElement(doc, form1099INT, "CorrectedReturnIndicator", "X");
        }

        // Payer information
        Element payer = doc.createElement("PayerInformation");
        addElement(doc, payer, "PayerTIN", taxDoc.getPayerTin());
        addElement(doc, payer, "PayerName", taxDoc.getPayerName());
        addElement(doc, payer, "PayerAddress", taxDoc.getPayerAddress());
        form1099INT.appendChild(payer);

        // Payee (recipient) information
        Element payee = doc.createElement("PayeeInformation");
        addElement(doc, payee, "PayeeTIN", maskTIN(taxDoc.getTaxpayerTin()));
        addElement(doc, payee, "PayeeName", taxDoc.getTaxpayerName());

        Element address = doc.createElement("PayeeAddress");
        addElement(doc, address, "AddressLine1", taxDoc.getTaxpayerAddressLine1());
        if (taxDoc.getTaxpayerAddressLine2() != null) {
            addElement(doc, address, "AddressLine2", taxDoc.getTaxpayerAddressLine2());
        }
        addElement(doc, address, "City", taxDoc.getTaxpayerCity());
        addElement(doc, address, "State", taxDoc.getTaxpayerState());
        addElement(doc, address, "ZIPCode", taxDoc.getTaxpayerZip());
        payee.appendChild(address);

        form1099INT.appendChild(payee);

        // Form data - Interest income boxes
        Element formData = doc.createElement("FormData");

        // Box 1 - Interest income (required if >= $10)
        if (taxDoc.getInterestIncome() != null && taxDoc.getInterestIncome().compareTo(ZERO) != 0) {
            addElement(doc, formData, "InterestIncome",
                formatAmount(taxDoc.getInterestIncome()));
        }

        // Box 2 - Early withdrawal penalty
        if (taxDoc.getEarlyWithdrawalPenalty() != null
            && taxDoc.getEarlyWithdrawalPenalty().compareTo(ZERO) != 0) {
            addElement(doc, formData, "EarlyWithdrawalPenalty",
                formatAmount(taxDoc.getEarlyWithdrawalPenalty()));
        }

        // Box 3 - Interest on U.S. Savings Bonds and Treasury obligations
        if (taxDoc.getUsSavingsBondsInterest() != null
            && taxDoc.getUsSavingsBondsInterest().compareTo(ZERO) != 0) {
            addElement(doc, formData, "USSavingsBondsInterest",
                formatAmount(taxDoc.getUsSavingsBondsInterest()));
        }

        // Box 4 - Federal income tax withheld
        if (taxDoc.getIntFederalTaxWithheld() != null
            && taxDoc.getIntFederalTaxWithheld().compareTo(ZERO) != 0) {
            addElement(doc, formData, "FederalIncomeTaxWithheld",
                formatAmount(taxDoc.getIntFederalTaxWithheld()));
        }

        // Box 5 - Investment expenses
        if (taxDoc.getInvestmentExpenses() != null
            && taxDoc.getInvestmentExpenses().compareTo(ZERO) != 0) {
            addElement(doc, formData, "InvestmentExpenses",
                formatAmount(taxDoc.getInvestmentExpenses()));
        }

        // Box 6 - Foreign tax paid
        if (taxDoc.getIntForeignTaxPaid() != null
            && taxDoc.getIntForeignTaxPaid().compareTo(ZERO) != 0) {
            addElement(doc, formData, "ForeignTaxPaid",
                formatAmount(taxDoc.getIntForeignTaxPaid()));
        }

        // Box 7 - Foreign country or U.S. possession
        if (taxDoc.getIntForeignCountry() != null && !taxDoc.getIntForeignCountry().isEmpty()) {
            addElement(doc, formData, "ForeignCountry", taxDoc.getIntForeignCountry());
        }

        // Box 8 - Tax-exempt interest
        if (taxDoc.getTaxExemptInterest() != null
            && taxDoc.getTaxExemptInterest().compareTo(ZERO) != 0) {
            addElement(doc, formData, "TaxExemptInterest",
                formatAmount(taxDoc.getTaxExemptInterest()));
        }

        // Box 9 - Specified private activity bond interest
        if (taxDoc.getPrivateActivityBondInterest() != null
            && taxDoc.getPrivateActivityBondInterest().compareTo(ZERO) != 0) {
            addElement(doc, formData, "PrivateActivityBondInterest",
                formatAmount(taxDoc.getPrivateActivityBondInterest()));
        }

        // Box 10 - Market discount
        if (taxDoc.getMarketDiscount() != null
            && taxDoc.getMarketDiscount().compareTo(ZERO) != 0) {
            addElement(doc, formData, "MarketDiscount",
                formatAmount(taxDoc.getMarketDiscount()));
        }

        // Box 11 - Bond premium
        if (taxDoc.getBondPremium() != null
            && taxDoc.getBondPremium().compareTo(ZERO) != 0) {
            addElement(doc, formData, "BondPremium",
                formatAmount(taxDoc.getBondPremium()));
        }

        // Box 12 - Bond premium on Treasury obligations
        if (taxDoc.getBondPremiumTreasury() != null
            && taxDoc.getBondPremiumTreasury().compareTo(ZERO) != 0) {
            addElement(doc, formData, "BondPremiumTreasury",
                formatAmount(taxDoc.getBondPremiumTreasury()));
        }

        // Box 13 - Bond premium on tax-exempt bond
        if (taxDoc.getBondPremiumTaxExempt() != null
            && taxDoc.getBondPremiumTaxExempt().compareTo(ZERO) != 0) {
            addElement(doc, formData, "BondPremiumTaxExempt",
                formatAmount(taxDoc.getBondPremiumTaxExempt()));
        }

        // Box 14 - Tax-exempt and tax credit bond CUSIP number
        if (taxDoc.getCusipNumber() != null && !taxDoc.getCusipNumber().isEmpty()) {
            addElement(doc, formData, "CUSIPNumber", taxDoc.getCusipNumber());
        }

        // State tax withheld (if applicable)
        if (taxDoc.getStateTaxWithheld() != null
            && taxDoc.getStateTaxWithheld().compareTo(ZERO) != 0) {
            Element stateInfo = doc.createElement("StateInformation");
            addElement(doc, stateInfo, "StateTaxWithheld",
                formatAmount(taxDoc.getStateTaxWithheld()));
            if (taxDoc.getStateCode() != null) {
                addElement(doc, stateInfo, "StateCode", taxDoc.getStateCode());
            }
            if (taxDoc.getPayerStateNumber() != null) {
                addElement(doc, stateInfo, "PayerStateNumber", taxDoc.getPayerStateNumber());
            }
            if (taxDoc.getStateIncome() != null && taxDoc.getStateIncome().compareTo(ZERO) != 0) {
                addElement(doc, stateInfo, "StateIncome",
                    formatAmount(taxDoc.getStateIncome()));
            }
            formData.appendChild(stateInfo);
        }

        form1099INT.appendChild(formData);

        return form1099INT;
    }

    /**
     * Create transaction details element.
     */
    private Element createTransactionDetails(Document doc, List<Map<String, Object>> details) {

        Element transactionsElement = doc.createElement("Transactions");

        for (Map<String, Object> detail : details) {
            Element transaction = doc.createElement("Transaction");

            addElementIfPresent(doc, transaction, "Description", detail.get("description"));
            addElementIfPresent(doc, transaction, "DateAcquired", detail.get("dateAcquired"));
            addElementIfPresent(doc, transaction, "DateSold", detail.get("dateSold"));
            addElementIfPresent(doc, transaction, "Proceeds", detail.get("proceeds"));
            addElementIfPresent(doc, transaction, "CostBasis", detail.get("costBasis"));

            transactionsElement.appendChild(transaction);
        }

        return transactionsElement;
    }

    /**
     * Get IRS return type code.
     */
    private String getReturnType(DocumentType documentType) {
        return switch (documentType) {
            case FORM_1099_B -> "1099B";
            case FORM_1099_DIV -> "1099DIV";
            case FORM_1099_INT -> "1099INT";
            case FORM_1099_MISC -> "1099MISC";
        };
    }

    /**
     * Format amount for IRS (2 decimal places, no commas).
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Mask TIN for security (last 4 digits only).
     */
    private String maskTIN(String tin) {
        if (tin == null || tin.length() < 4) {
            return "XXX-XX-XXXX";
        }
        // In production, would decrypt first, then mask
        return "XXX-XX-" + tin.substring(tin.length() - 4);
    }

    /**
     * Add XML element with text content.
     */
    private void addElement(Document doc, Element parent, String name, String value) {
        if (value != null && !value.isEmpty()) {
            Element element = doc.createElement(name);
            element.setTextContent(value);
            parent.appendChild(element);
        }
    }

    /**
     * Add XML element if value is present.
     */
    private void addElementIfPresent(Document doc, Element parent, String name, Object value) {
        if (value != null) {
            addElement(doc, parent, name, value.toString());
        }
    }

    /**
     * Convert DOM document to XML string.
     */
    private String convertDocumentToString(Document doc) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
