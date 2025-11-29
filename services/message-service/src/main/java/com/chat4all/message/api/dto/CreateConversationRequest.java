package com.chat4all.message.api.dto;

import com.chat4all.common.constant.Channel;
import com.chat4all.message.domain.ConversationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a new conversation
 * 
 * Aligned with:
 * - FR-027: Group conversations support (2-100 participants)
 * - User Story 4: Group Conversation Support
 * 
 * Validation Rules:
 * - ONE_TO_ONE: exactly 2 participants
 * - GROUP: 3-100 participants
 * - Title: optional for ONE_TO_ONE, recommended for GROUP
 * 
 * Task: T076
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateConversationRequest {

    /**
     * Conversation title (optional for ONE_TO_ONE, recommended for GROUP)
     * Max 200 characters
     */
    @Size(max = 200, message = "Conversation title cannot exceed 200 characters")
    private String title;

    /**
     * Conversation type (ONE_TO_ONE or GROUP)
     * Default: ONE_TO_ONE if not specified
     */
    @Builder.Default
    private ConversationType type = ConversationType.ONE_TO_ONE;

    /**
     * List of participant IDs (user IDs, phone numbers, or external identifiers)
     * 
     * Required: minimum 2 participants
     * Maximum: 100 participants per FR-027
     * 
     * For ONE_TO_ONE: exactly 2 participants
     * For GROUP: 3-100 participants
     */
    @NotNull(message = "Participants list is required")
    @Size(min = 2, max = 100, message = "Conversation must have between 2 and 100 participants")
    private List<String> participants;

    /**
     * Primary channel for this conversation
     * Optional - if not specified, will be set based on first message
     */
    private Channel primaryChannel;
}
