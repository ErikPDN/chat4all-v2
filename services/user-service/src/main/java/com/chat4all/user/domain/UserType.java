package com.chat4all.user.domain;

/**
 * UserType Enum - Categorizes users by their role in the platform
 * 
 * Values:
 * - AGENT: Internal staff member handling customer conversations
 * - CUSTOMER: External user communicating via WhatsApp/Telegram/Instagram
 * - SYSTEM: Automated user for system-generated messages (chatbots, notifications)
 * 
 * Usage:
 * - Authorization: Agents can access all conversations, customers only their own
 * - Routing: Messages from customers routed to agents
 * - Analytics: Segment metrics by user type
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
public enum UserType {
    /**
     * Internal agent/staff member
     * - Has access to admin dashboard
     * - Can view and respond to customer conversations
     * - Can be assigned to conversations
     */
    AGENT,

    /**
     * External customer
     * - Communicates via external platforms (WhatsApp, Telegram, Instagram)
     * - Can only view their own conversations
     * - Messages routed to agents
     */
    CUSTOMER,

    /**
     * System/automated user
     * - Used for chatbots, automated responses, system notifications
     * - No login credentials
     * - Messages generated programmatically
     */
    SYSTEM
}
