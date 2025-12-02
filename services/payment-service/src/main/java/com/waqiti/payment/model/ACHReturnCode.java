package com.waqiti.payment.model;

/**
 * ACH Return Codes
 * Standard NACHA return reason codes
 *
 * @author Waqiti Platform Team
 * @since October 2025
 */
public enum ACHReturnCode {

    R01("R01", "Insufficient Funds"),
    R02("R02", "Account Closed"),
    R03("R03", "No Account/Unable to Locate Account"),
    R04("R04", "Invalid Account Number"),
    R05("R05", "Unauthorized Debit to Consumer Account"),
    R06("R06", "Returned per ODFI Request"),
    R07("R07", "Authorization Revoked by Customer"),
    R08("R08", "Payment Stopped"),
    R09("R09", "Uncollected Funds"),
    R10("R10", "Customer Advises Not Authorized"),
    R11("R11", "Check Truncation Entry Return"),
    R12("R12", "Account Sold to Another DFI"),
    R13("R13", "Invalid ACH Routing Number"),
    R14("R14", "Representative Payee Deceased or Unable to Continue"),
    R15("R15", "Beneficiary or Account Holder Deceased"),
    R16("R16", "Account Frozen"),
    R17("R17", "File Record Edit Criteria"),
    R20("R20", "Non-Transaction Account"),
    R21("R21", "Invalid Company Identification"),
    R22("R22", "Invalid Individual ID Number"),
    R23("R23", "Credit Entry Refused by Receiver"),
    R24("R24", "Duplicate Entry"),
    R29("R29", "Corporate Customer Advises Not Authorized"),
    R31("R31", "Permissible Return Entry"),
    R33("R33", "Return of XCK Entry");

    private final String code;
    private final String description;

    ACHReturnCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ACHReturnCode fromCode(String code) {
        for (ACHReturnCode returnCode : values()) {
            if (returnCode.code.equals(code)) {
                return returnCode;
            }
        }
        throw new IllegalArgumentException("Unknown ACH return code: " + code);
    }
}
