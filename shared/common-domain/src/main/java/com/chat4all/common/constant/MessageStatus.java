package com.chat4all.common.constant;

/**
 * Message delivery status enumeration
 * 
 * Aligned with:
 * - FR-004: Track message delivery status
 * - Data Model: status field in messages collection
 * 
 * Status transitions:
 * - PENDING → SENT → DELIVERED → READ (normal flow)
 * - PENDING/SENT/DELIVERED → FAILED (on error)
 * - Status can only progress forward except to FAILED (idempotent)
 * 
 * Task: T014
 */
public enum MessageStatus {
    /**
     * Message received by platform, awaiting delivery
     * Initial status when message is first created
     */
    PENDING("pending"),

    /**
     * Message received from external platform (inbound)
     * Used for messages FROM customers via webhooks
     */
    RECEIVED("received"),

    /**
     * Message sent to external platform's API
     * Platform acknowledged receipt but not yet delivered to recipient
     */
    SENT("sent"),

    /**
     * Message delivered to recipient's device
     * Confirmed by external platform delivery webhook
     */
    DELIVERED("delivered"),

    /**
     * Message read by recipient
     * Confirmed by external platform read receipt (if supported)
     */
    READ("read"),

    /**
     * Message delivery failed
     * Can transition from any previous state
     * Contains error details in message metadata
     */
    FAILED("failed");

    private final String value;

    MessageStatus(String value) {
        this.value = value;
    }

    /**
     * Get the string value of the status
     * @return status name in lowercase
     */
    public String getValue() {
        return value;
    }

    /**
     * Parse status from string value
     * @param value status name (case-insensitive)
     * @return MessageStatus enum value
     * @throws IllegalArgumentException if value is not a valid status
     */
    public static MessageStatus fromValue(String value) {
        for (MessageStatus status : MessageStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid message status: " + value);
    }

    /**
     * Check if transition to new status is valid
     * Status can only progress forward (except to FAILED)
     * 
     * Outbound flow: PENDING → SENT → DELIVERED → READ
     * Inbound flow: RECEIVED (terminal state for inbound messages)
     * 
     * @param newStatus target status
     * @return true if transition is valid
     */
    public boolean canTransitionTo(MessageStatus newStatus) {
        // Can always transition to FAILED
        if (newStatus == FAILED) {
            return true;
        }

        // Can't transition from FAILED to anything else
        if (this == FAILED) {
            return false;
        }

        // Can't transition from READ to any other status (terminal state)
        if (this == READ) {
            return false;
        }

        // RECEIVED is terminal for inbound messages (no further transitions except FAILED)
        if (this == RECEIVED) {
            return false;
        }

        // Can only progress forward: PENDING → SENT → DELIVERED → READ
        return newStatus.ordinal() > this.ordinal();
    }

    /**
     * Check if this is a terminal status (no further transitions allowed)
     * @return true if terminal status (READ, RECEIVED, or FAILED)
     */
    public boolean isTerminal() {
        return this == READ || this == RECEIVED || this == FAILED;
    }
}
