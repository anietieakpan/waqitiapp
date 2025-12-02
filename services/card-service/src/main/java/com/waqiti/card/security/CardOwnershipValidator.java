package com.waqiti.card.security;

import com.waqiti.card.entity.Card;
import com.waqiti.card.entity.CardAuthorization;
import com.waqiti.card.entity.CardDispute;
import com.waqiti.card.entity.CardStatement;
import com.waqiti.card.entity.CardTransaction;
import com.waqiti.card.repository.CardAuthorizationRepository;
import com.waqiti.card.repository.CardDisputeRepository;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardStatementRepository;
import com.waqiti.card.repository.CardTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * CardOwnershipValidator - Security validator for @PreAuthorize expressions
 *
 * Validates that authenticated users can only access their own cards and related resources
 *
 * Used in @PreAuthorize expressions:
 * - @PreAuthorize("@cardOwnershipValidator.isCardOwner(#cardId)")
 * - @PreAuthorize("@cardOwnershipValidator.isTransactionOwner(#transactionId)")
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Component("cardOwnershipValidator")
@Slf4j
@RequiredArgsConstructor
public class CardOwnershipValidator {

    private final CardRepository cardRepository;
    private final CardTransactionRepository transactionRepository;
    private final CardAuthorizationRepository authorizationRepository;
    private final CardDisputeRepository disputeRepository;
    private final CardStatementRepository statementRepository;

    /**
     * Check if current user owns the card
     *
     * @param cardId Card ID (can be UUID or String)
     * @return true if user owns card, false otherwise
     */
    public boolean isCardOwner(Object cardId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            log.warn("No authenticated user found for card ownership check");
            return false;
        }

        try {
            String cardIdStr = convertToString(cardId);
            Card card = cardRepository.findByCardIdAndNotDeleted(cardIdStr).orElse(null);

            if (card == null) {
                log.warn("Card not found for ownership check: {}", cardIdStr);
                return false;
            }

            boolean isOwner = card.getUserId().equals(userId);

            if (!isOwner) {
                log.warn("User {} attempted to access card {} owned by {}",
                        userId, cardIdStr, card.getUserId());
            }

            return isOwner;

        } catch (Exception e) {
            log.error("Error checking card ownership", e);
            return false;
        }
    }

    /**
     * Check if current user owns the transaction's card
     *
     * @param transactionId Transaction ID
     * @return true if user owns the card associated with transaction
     */
    public boolean isTransactionOwner(Object transactionId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }

        try {
            UUID transId = convertToUUID(transactionId);
            CardTransaction transaction = transactionRepository.findById(transId).orElse(null);

            if (transaction == null) {
                log.warn("Transaction not found for ownership check: {}", transId);
                return false;
            }

            // Get card and check ownership
            Card card = cardRepository.findByCardIdAndNotDeleted(transaction.getCardId().toString()).orElse(null);

            if (card == null) {
                log.warn("Card not found for transaction ownership check");
                return false;
            }

            boolean isOwner = card.getUserId().equals(userId);

            if (!isOwner) {
                log.warn("User {} attempted to access transaction {} on card owned by {}",
                        userId, transId, card.getUserId());
            }

            return isOwner;

        } catch (Exception e) {
            log.error("Error checking transaction ownership", e);
            return false;
        }
    }

    /**
     * Check if current user owns the authorization's card
     *
     * @param authorizationId Authorization ID
     * @return true if user owns the card
     */
    public boolean isAuthorizationOwner(Object authorizationId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }

        try {
            UUID authId = convertToUUID(authorizationId);
            CardAuthorization authorization = authorizationRepository.findById(authId).orElse(null);

            if (authorization == null) {
                log.warn("Authorization not found for ownership check: {}", authId);
                return false;
            }

            Card card = cardRepository.findByCardIdAndNotDeleted(authorization.getCardId().toString()).orElse(null);

            if (card == null) {
                return false;
            }

            return card.getUserId().equals(userId);

        } catch (Exception e) {
            log.error("Error checking authorization ownership", e);
            return false;
        }
    }

    /**
     * Check if current user owns the dispute's card
     *
     * @param disputeId Dispute ID
     * @return true if user owns the card
     */
    public boolean isDisputeOwner(Object disputeId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }

        try {
            UUID dispId = convertToUUID(disputeId);
            CardDispute dispute = disputeRepository.findById(dispId).orElse(null);

            if (dispute == null) {
                log.warn("Dispute not found for ownership check: {}", dispId);
                return false;
            }

            Card card = cardRepository.findByCardIdAndNotDeleted(dispute.getCardId().toString()).orElse(null);

            if (card == null) {
                return false;
            }

            return card.getUserId().equals(userId);

        } catch (Exception e) {
            log.error("Error checking dispute ownership", e);
            return false;
        }
    }

    /**
     * Check if current user owns the statement's card
     *
     * @param statementId Statement ID
     * @return true if user owns the card
     */
    public boolean isStatementOwner(Object statementId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }

        try {
            String stmtId = convertToString(statementId);
            CardStatement statement = statementRepository.findByStatementId(stmtId).orElse(null);

            if (statement == null) {
                log.warn("Statement not found for ownership check: {}", stmtId);
                return false;
            }

            Card card = cardRepository.findByCardIdAndNotDeleted(statement.getCardId().toString()).orElse(null);

            if (card == null) {
                return false;
            }

            return card.getUserId().equals(userId);

        } catch (Exception e) {
            log.error("Error checking statement ownership", e);
            return false;
        }
    }

    /**
     * Check if user has admin role
     *
     * @return true if user is admin
     */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") ||
                                 auth.getAuthority().equals("ROLE_SYSTEM"));
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // Extract user ID from principal (adjust based on your authentication setup)
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            // If username is UUID, parse it
            try {
                return UUID.fromString(username);
            } catch (IllegalArgumentException e) {
                log.warn("Username is not a UUID: {}", username);
                return null;
            }
        } else if (principal instanceof String) {
            try {
                return UUID.fromString((String) principal);
            } catch (IllegalArgumentException e) {
                log.warn("Principal is not a UUID: {}", principal);
                return null;
            }
        }

        log.warn("Unsupported principal type: {}", principal.getClass());
        return null;
    }

    private UUID convertToUUID(Object id) {
        if (id instanceof UUID) {
            return (UUID) id;
        } else if (id instanceof String) {
            return UUID.fromString((String) id);
        } else {
            throw new IllegalArgumentException("Cannot convert " + id.getClass() + " to UUID");
        }
    }

    private String convertToString(Object id) {
        if (id instanceof UUID) {
            return id.toString();
        } else if (id instanceof String) {
            return (String) id;
        } else {
            throw new IllegalArgumentException("Cannot convert " + id.getClass() + " to String");
        }
    }
}
