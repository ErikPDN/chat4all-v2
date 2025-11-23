// mongo-init.js
// MongoDB initialization script for Chat4All messaging platform
// Creates: messages, conversations, files collections with JSON Schema validators and indexes

db = db.getSiblingDB('chat4all');

// ============================================================================
// 1. messages Collection
// ============================================================================
db.createCollection("messages", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["message_id", "conversation_id", "sender_id", "content_type", "channel", "status", "timestamp"],
      properties: {
        message_id: {
          bsonType: "string",
          description: "UUIDv4 format required",
          pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        },
        conversation_id: {
          bsonType: "string",
          description: "References conversations collection"
        },
        sender_id: {
          bsonType: "string",
          description: "References users.user_id in PostgreSQL"
        },
        recipient_ids: {
          bsonType: "array",
          description: "Array of user IDs (references PostgreSQL users table)",
          items: { bsonType: "string" }
        },
        content: {
          bsonType: "string",
          description: "Message text content (max 10,000 characters)",
          maxLength: 10000
        },
        content_type: {
          enum: ["TEXT", "FILE", "IMAGE", "VIDEO", "AUDIO"],
          description: "Type of message content"
        },
        file_id: {
          bsonType: ["string", "null"],
          description: "Optional reference to files collection"
        },
        channel: {
          enum: ["WHATSAPP", "TELEGRAM", "INSTAGRAM", "INTERNAL"],
          description: "Source channel for the message"
        },
        status: {
          enum: ["PENDING", "SENT", "DELIVERED", "READ", "FAILED"],
          description: "Message delivery status"
        },
        timestamp: {
          bsonType: "date",
          description: "Message creation timestamp"
        },
        updated_at: {
          bsonType: "date",
          description: "Last status update timestamp"
        },
        metadata: {
          bsonType: "object",
          description: "Extensible field for platform-specific data"
        }
      }
    }
  }
});

// Messages indexes
db.messages.createIndex({ message_id: 1 }, { unique: true });
db.messages.createIndex({ conversation_id: 1, timestamp: -1 }); // Sorted conversation history
db.messages.createIndex({ sender_id: 1, timestamp: -1 });
db.messages.createIndex({ status: 1, updated_at: 1 }); // For retry/monitoring queries

print("✅ messages collection created with JSON Schema validator and indexes");

// ============================================================================
// 2. conversations Collection
// ============================================================================
db.createCollection("conversations", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["conversation_id", "conversation_type", "participants", "primary_channel", "created_at"],
      properties: {
        conversation_id: {
          bsonType: "string",
          description: "Unique conversation identifier"
        },
        conversation_type: {
          enum: ["1:1", "GROUP"],
          description: "Conversation type (1:1 requires 2 participants, GROUP allows 3-100)"
        },
        participants: {
          bsonType: "array",
          description: "Array of participant objects",
          minItems: 2,
          maxItems: 100,
          items: {
            bsonType: "object",
            required: ["user_id", "user_type", "joined_at"],
            properties: {
              user_id: {
                bsonType: "string",
                description: "References PostgreSQL users.user_id"
              },
              user_type: {
                enum: ["CUSTOMER", "AGENT", "SYSTEM"],
                description: "User role type"
              },
              joined_at: {
                bsonType: "date",
                description: "Timestamp when user joined conversation"
              }
            }
          }
        },
        primary_channel: {
          enum: ["WHATSAPP", "TELEGRAM", "INSTAGRAM", "INTERNAL"],
          description: "Primary messaging channel for this conversation"
        },
        message_count: {
          bsonType: "int",
          minimum: 0,
          description: "Atomic counter for total messages in conversation"
        },
        last_message_at: {
          bsonType: "date",
          description: "Timestamp of most recent message"
        },
        created_at: {
          bsonType: "date",
          description: "Conversation creation timestamp"
        },
        updated_at: {
          bsonType: "date",
          description: "Last modification timestamp"
        },
        archived: {
          bsonType: "bool",
          description: "Auto-set to true after 90 days of inactivity"
        },
        metadata: {
          bsonType: "object",
          description: "Extensible field (tags, priority, etc.)"
        }
      }
    }
  }
});

// Conversations indexes
db.conversations.createIndex({ conversation_id: 1 }, { unique: true });
db.conversations.createIndex({ "participants.user_id": 1, last_message_at: -1 });
db.conversations.createIndex({ primary_channel: 1, archived: 1 });
db.conversations.createIndex({ last_message_at: -1 }); // Recent conversations

print("✅ conversations collection created with JSON Schema validator and indexes");

// ============================================================================
// 3. files Collection
// ============================================================================
db.createCollection("files", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["file_id", "message_id", "filename", "file_size", "mime_type", "storage_url", "uploaded_at", "scan_status"],
      properties: {
        file_id: {
          bsonType: "string",
          description: "UUIDv4 format required",
          pattern: "^file-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$"
        },
        message_id: {
          bsonType: "string",
          description: "References messages.message_id"
        },
        filename: {
          bsonType: "string",
          description: "Original filename"
        },
        file_size: {
          bsonType: "long",
          minimum: 1,
          maximum: 2147483648, // 2GB max (FR-019)
          description: "File size in bytes (max 2GB)"
        },
        mime_type: {
          bsonType: "string",
          description: "MIME type validated against whitelist (FR-022)",
          enum: [
            "image/jpeg", "image/png", "image/gif",
            "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "video/mp4", "video/quicktime"
          ]
        },
        storage_url: {
          bsonType: "string",
          description: "S3 storage URL (s3://chat4all-files/...)"
        },
        thumbnail_url: {
          bsonType: ["string", "null"],
          description: "Thumbnail URL for images (optional)"
        },
        uploaded_at: {
          bsonType: "date",
          description: "Upload timestamp"
        },
        expires_at: {
          bsonType: "date",
          description: "24-hour expiration timestamp (FR-021)"
        },
        scan_status: {
          enum: ["PENDING", "CLEAN", "INFECTED"],
          description: "Malware scan status (FR-023)"
        },
        metadata: {
          bsonType: "object",
          description: "Extensible field (uploader_ip, original_filename, etc.)"
        }
      }
    }
  }
});

// Files indexes
db.files.createIndex({ file_id: 1 }, { unique: true });
db.files.createIndex({ message_id: 1 });
db.files.createIndex({ expires_at: 1 }, { expireAfterSeconds: 0 }); // TTL index for auto-deletion
db.files.createIndex({ scan_status: 1 });

print("✅ files collection created with JSON Schema validator and indexes (including TTL index on expires_at)");

// ============================================================================
// Sharding Configuration
// ============================================================================
// Enable sharding on the chat4all database
sh.enableSharding("chat4all");

// Shard messages collection by conversation_id (aligns with Kafka partitioning)
sh.shardCollection("chat4all.messages", { conversation_id: 1 });

print("✅ Sharding enabled on chat4all database");
print("✅ messages collection sharded by conversation_id");

print("\n========================================");
print("MongoDB initialization complete!");
print("Collections created: messages, conversations, files");
print("JSON Schema validators: ✅");
print("Indexes: ✅");
print("Sharding: ✅");
print("========================================");
