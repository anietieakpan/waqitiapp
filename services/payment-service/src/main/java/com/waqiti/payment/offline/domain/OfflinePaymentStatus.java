package com.waqiti.payment.offline.domain;

public enum OfflinePaymentStatus {
    PENDING_SYNC,       // Created offline, waiting to sync
    ACCEPTED_OFFLINE,   // Accepted by recipient offline
    SYNCING,           // Currently being synced to server
    SYNCED,            // Successfully synced to server
    SYNC_FAILED,       // Failed to sync (will retry)
    COMPLETED,         // Payment fully processed
    CANCELLED,         // Cancelled by sender
    EXPIRED,           // Expired before sync
    REJECTED           // Rejected during sync (insufficient funds, etc.)
}