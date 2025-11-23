package com.chat4all.common.constant;

/**
 * Supported messaging channels/platforms
 * 
 * Aligned with:
 * - FR-011: WhatsApp Business API integration
 * - FR-012: Telegram Bot API integration
 * - FR-013: Instagram Messaging API integration
 * - Data Model: channel field in messages collection
 * 
 * Task: T013
 */
public enum Channel {
    /**
     * WhatsApp Business API
     * Platform-specific message ID format: wamid.XXX
     */
    WHATSAPP("whatsapp"),

    /**
     * Telegram Bot API
     * Platform-specific user ID format: numeric ID or @username
     */
    TELEGRAM("telegram"),

    /**
     * Instagram Messaging API
     * Platform-specific user ID format: @handle
     */
    INSTAGRAM("instagram"),

    /**
     * Internal platform messages
     * Used for agent-to-agent communication and system notifications
     */
    INTERNAL("internal");
    
    private final String value;
    
    Channel(String value) {
        this.value = value;
    }
    
    /**
     * Get the string value of the channel
     * @return channel name in lowercase
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Parse channel from string value
     * @param value channel name (case-insensitive)
     * @return Channel enum value
     * @throws IllegalArgumentException if value is not a valid channel
     */
    public static Channel fromValue(String value) {
        for (Channel channel : Channel.values()) {
            if (channel.value.equalsIgnoreCase(value)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Invalid channel: " + value);
    }
}
