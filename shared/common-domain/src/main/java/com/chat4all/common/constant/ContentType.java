package com.chat4all.common.constant;

/**
 * Message Content Type Enumeration
 * 
 * Defines the type of content contained in a message.
 * Used for validation, routing, and UI rendering decisions.
 * 
 * Aligned with:
 * - FR-003: Message content validation
 * - FR-019: File attachment support
 * - Platform capabilities (WhatsApp, Telegram, Instagram)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
public enum ContentType {
    /**
     * Plain text message
     * Default content type for most messages
     */
    TEXT,

    /**
     * Image file (JPEG, PNG, GIF, etc.)
     * Requires fileIds to be populated
     */
    IMAGE,

    /**
     * Video file (MP4, AVI, MOV, etc.)
     * Requires fileIds to be populated
     */
    VIDEO,

    /**
     * Audio file (MP3, WAV, OGG, etc.)
     * Includes voice messages
     */
    AUDIO,

    /**
     * Document file (PDF, DOCX, XLSX, etc.)
     * More specific than generic "FILE" type
     */
    DOCUMENT,

    /**
     * Geographic location coordinates
     * Contains latitude/longitude in metadata
     */
    LOCATION,

    /**
     * Contact information
     * VCard format or similar contact details
     */
    CONTACT,

    /**
     * WhatsApp Business template message
     * Predefined message format with variables
     */
    TEMPLATE,

    /**
     * Unknown or unsupported content type
     * Fallback for unrecognized types
     */
    UNKNOWN
}
