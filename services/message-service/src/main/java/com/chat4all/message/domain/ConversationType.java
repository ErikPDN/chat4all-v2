package com.chat4all.message.domain;

/**
 * Conversation Type Enumeration
 * 
 * Defines the types of conversations supported by the platform.
 * 
 * Aligned with:
 * - FR-027: Group conversations support (3-100 participants)
 * - User Story 4: Group Conversation Support
 * 
 * Task: T074
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
public enum ConversationType {
    
    /**
     * One-to-one conversation (exactly 2 participants)
     * Default type for direct messaging between two users
     */
    ONE_TO_ONE("1:1"),
    
    /**
     * Group conversation (3-100 participants per FR-027)
     * Supports multi-party messaging with broadcast delivery
     */
    GROUP("GROUP");
    
    private final String value;
    
    ConversationType(String value) {
        this.value = value;
    }
    
    /**
     * Get the string representation of the conversation type
     * Used for MongoDB storage and API serialization
     * 
     * @return String value ("1:1" or "GROUP")
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Parse string value to ConversationType enum
     * 
     * @param value String value to parse
     * @return ConversationType enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static ConversationType fromValue(String value) {
        for (ConversationType type : ConversationType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid conversation type: " + value);
    }
}
