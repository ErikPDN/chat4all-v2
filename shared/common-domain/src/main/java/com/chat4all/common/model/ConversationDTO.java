package com.chat4all.common.model;

import com.chat4all.common.constant.Channel;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Conversation Data Transfer Object
 * Represents a message thread (1:1 or group conversation)
 * 
 * Aligned with:
 * - FR-027: Group conversations support (3-100 participants)
 * - Data Model: conversations collection in MongoDB
 * 
 * Task: T012
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationDTO {

    /**
     * Unique identifier for the conversation
     */
    @NotBlank(message = "Conversation ID cannot be blank")
    private String conversationId;

    /**
     * Conversation type: 1:1 or GROUP
     * - 1:1 requires exactly 2 participants
     * - GROUP requires 3-100 participants (per FR-027)
     */
    @NotNull(message = "Conversation type is required")
    private ConversationType conversationType;

    /**
     * List of conversation participants
     * Each participant has: userId, userType, joinedAt
     */
    @NotNull(message = "Participants cannot be null")
    @Size(min = 2, max = 100, message = "Conversation must have 2-100 participants")
    private List<ParticipantDTO> participants;

    /**
     * Simple list of participant IDs (alternative to detailed participants)
     * For simple use cases where only IDs are needed (e.g., group messaging)
     */
    @Size(max = 100, message = "Maximum 100 participants allowed")
    private List<String> participantIds;

    /**
     * Current number of participants in the conversation
     * Useful for displaying participant count in UI without loading full participant list
     * Task: T079
     */
    private Integer participantCount;

    /**
     * Primary channel for this conversation
     */
    @NotNull(message = "Primary channel cannot be null")
    private Channel primaryChannel;

    /**
     * Optional conversation title (for groups)
     * Required for GROUP conversations, optional for ONE_TO_ONE
     */
    private String title;

    /**
     * Conversation creation timestamp
     */
    @NotNull(message = "Created at cannot be null")
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Total number of messages in this conversation
     * Incremented atomically on each new message
     */
    @Builder.Default
    private Long messageCount = 0L;

    /**
     * Timestamp of the last message sent in this conversation
     * Used for sorting conversations by activity
     */
    private Instant lastMessageAt;

    /**
     * Conversation last update timestamp
     */
    private Instant updatedAt;

    /**
     * Archive status
     * Auto-set to true after 90 days of inactivity (configurable)
     */
    @Builder.Default
    private Boolean archived = false;

    /**
     * Additional metadata
     * - tags: List of tags (e.g., "support", "billing")
     * - priority: Conversation priority (HIGH, MEDIUM, LOW)
     */
    private Map<String, Object> metadata;

    /**
     * Conversation type enumeration
     */
    public enum ConversationType {
        /**
         * One-to-one conversation (exactly 2 participants)
         */
        ONE_TO_ONE("1:1"),

        /**
         * Group conversation (3-100 participants)
         */
        GROUP("GROUP");

        private final String value;

        ConversationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Conversation participant details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantDTO {

        /**
         * Internal user ID
         * References users.user_id in PostgreSQL
         */
        @NotNull(message = "User ID is required")
        private String userId;

        /**
         * User type: AGENT or CUSTOMER
         */
        @NotNull(message = "User type is required")
        private String userType;

        /**
         * Timestamp when participant joined the conversation
         */
        @NotNull(message = "Joined at is required")
        private Instant joinedAt;
    }
}
