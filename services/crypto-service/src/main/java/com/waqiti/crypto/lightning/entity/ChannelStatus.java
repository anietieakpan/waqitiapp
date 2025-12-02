package com.waqiti.crypto.lightning.entity;

/**
 * Lightning channel status enumeration
 */
public enum ChannelStatus {
    /**
     * Channel is waiting for funding transaction confirmation
     */
    PENDING_OPEN,
    
    /**
     * Channel is active and can be used for payments
     */
    ACTIVE,
    
    /**
     * Channel is inactive (peer offline or other issues)
     */
    INACTIVE,
    
    /**
     * Channel is in the process of cooperative closing
     */
    CLOSING,
    
    /**
     * Channel is being force closed
     */
    FORCE_CLOSING,
    
    /**
     * Channel has been closed cooperatively
     */
    CLOSED,
    
    /**
     * Channel has been force closed
     */
    FORCE_CLOSED,
    
    /**
     * Channel is waiting for closing transaction confirmation
     */
    WAITING_CLOSE,
    
    /**
     * Channel needs intervention (stuck, disputed, etc.)
     */
    NEEDS_ATTENTION
}