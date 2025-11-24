package com.chat4all.message.domain;

import com.chat4all.common.constant.Channel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Conversation Entity for MongoDB
 * 
 * Represents a conversation (one-to-one or group) in the unified messaging platform.
 * 
 * Collection: conversations
 * 
 * Validation (enforced by MongoDB JSON Schema validator):
 * - conversation_type: ENUM ('1:1', 'GROUP')
 * - participants: Array with 2-100 members (FR-027)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(name = "idx_participants_lastmessage", def = "{'participants.userId': 1, 'lastMessageAt': -1}"),
    @CompoundIndex(name = "idx_channel_archived", def = "{'primaryChannel': 1, 'archived': 1}")
})
public class Conversation {

    /**
     * MongoDB document ID (internal)
     */
    @Id
    private String id;

    /**
     * Unique conversation identifier
     */
    @Indexed(unique = true)
    @Field("conversation_id")
    private String conversationId;

    /**
     * Conversation type
     * Enum: '1:1' or 'GROUP'
     */
    @Field("conversation_type")
    private String conversationType;

    /**
     * List of conversation participants
     * Minimum 2, maximum 100 participants (FR-027)
     */
    private List<Participant> participants;

    /**
     * Primary platform channel for this conversation
     * Enum: WHATSAPP, TELEGRAM, INSTAGRAM, INTERNAL
     */
    @Field("primary_channel")
    private Channel primaryChannel;

    /**
     * Optional conversation title (for groups)
     */
    private String title;

    /**
     * Total number of messages in this conversation
     * Incremented atomically on each new message
     */
    @Builder.Default
    @Field("message_count")
    private Integer messageCount = 0;

    /**
     * Timestamp of the most recent message
     */
    @Indexed
    @Field("last_message_at")
    private Instant lastMessageAt;

    /**
     * Conversation creation timestamp
     */
    @Field("created_at")
    private Instant createdAt;

    /**
     * Conversation last update timestamp
     */
    @Field("updated_at")
    private Instant updatedAt;

    /**
     * Whether the conversation is archived
     * Auto-archived after 90 days of inactivity (configurable)
     */
    @Builder.Default
    private Boolean archived = false;

    /**
     * Additional conversation metadata
     */
    private ConversationMetadata metadata;

    /**
     * Conversation participant nested object
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participant {
        /**
         * User ID (references PostgreSQL users.user_id)
         */
        @Field("user_id")
        private String userId;

        /**
         * User type (AGENT or CUSTOMER)
         */
        @Field("user_type")
        private String userType;

        /**
         * When the user joined this conversation
         */
        @Field("joined_at")
        private Instant joinedAt;

        /**
         * When the user left this conversation (null if still active)
         */
        @Field("left_at")
        private Instant leftAt;
    }

    /**
     * Conversation metadata nested object
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMetadata {
        /**
         * Conversation tags (e.g., ["support", "billing"])
         */
        private List<String> tags;

        /**
         * Conversation priority level
         * Enum: LOW, NORMAL, HIGH, URGENT
         */
        private String priority;

        /**
         * Additional platform-specific metadata
         */
        @Field("additional_data")
        private Map<String, Object> additionalData;
    }
}
