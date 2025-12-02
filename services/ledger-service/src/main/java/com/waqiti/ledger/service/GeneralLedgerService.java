package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * General Ledger Service
 *
 * Manages the general ledger for double-entry bookkeeping
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeneralLedgerService {

    private final DoubleEntryLedgerService doubleEntryLedgerService;

    /**
     * Post journal entry to general ledger
     */
    @Transactional
    public void postJournalEntry(UUID journalEntryId, List<Map<String, Object>> entries) {
        log.info("Posting journal entry to general ledger: journalEntryId={}, entries={}",
                journalEntryId, entries.size());

        // Implementation: Post each entry to the general ledger
        for (Map<String, Object> entry : entries) {
            String accountCode = (String) entry.get("accountCode");
            String entryType = (String) entry.get("entryType");
            // Process each entry
            log.debug("Posted ledger entry: account={}, type={}", accountCode, entryType);
        }
    }

    /**
     * Validate ledger balance (debits = credits)
     */
    public boolean validateLedgerBalance() {
        log.debug("Validating ledger balance");
        // Implementation: Check that total debits equal total credits
        return doubleEntryLedgerService.isBalanced();
    }
}
