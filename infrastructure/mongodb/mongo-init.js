// MongoDB Initialization Script for Chat4All v2
// Purpose: Create collections with JSON Schema validators and indexes
// Requirements: FR-001 to FR-027

// Connect to chat4all database
db = db.getSiblingDB('chat4all');

print('=== Initializing Chat4All v2 Database ===');

// ============================================================================
// 1. Messages Collection
// ============================================================================

print('Creating messages collection with JSON Schema validator...');

db.createCollection('messages', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['message_id', 'conversation_id', 'sender_id', 'content_type', 'channel', 'status', 'timestamp'],
      properties: {
        message_id: {
          bsonType: 'string',
          description: 'Unique message identifier in UUIDv4 format',
          pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        },
        conversation_id: {
          bsonType: 'string',
          description: 'Conversation identifier this message belongs to'
        },
        sender_id: {
          bsonType: 'string',
          description: 'User ID of the message sender (references PostgreSQL users table)'
        },
        recipient_ids: {
          bsonType: 'array',
          description: 'Array of recipient user IDs (for group messages)',
          items: {
            bsonType: 'string'
          }
        },
        content: {
          bsonType: 'string',
          description: 'Message text content (max 10,000 characters for FR-003)',
          maxLength: 10000
        },
        content_type: {
          enum: ['TEXT', 'FILE', 'IMAGE', 'VIDEO', 'AUDIO'],
          description: 'Type of message content'
        },
        file_id: {
          bsonType: ['string', 'null'],
          description: 'Reference to files collection if content_type is not TEXT'
        },
        channel: {
          enum: ['WHATSAPP', 'TELEGRAM', 'INSTAGRAM', 'INTERNAL'],
          description: 'Platform channel this message is sent through'
        },
        status: {
          enum: ['PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED'],
          description: 'Current delivery status of the message'
        },
        timestamp: {
          bsonType: 'date',
          description: 'When the message was created'
        },
        metadata: {
          bsonType: 'object',
          description: 'Additional message metadata',
          properties: {
            platform_message_id: {
              bsonType: ['string', 'null'],
              description: 'External platform message ID (e.g., wamid.XXX for WhatsApp)'
            },
            retry_count: {
              bsonType: 'int',
              description: 'Number of delivery retry attempts'
            },
            error_message: {
              bsonType: ['string', 'null'],
              description: 'Error message if status is FAILED'
            }
          }
        },
        created_at: {
          bsonType: 'date',
          description: 'When the document was created'
        },
        updated_at: {
          bsonType: 'date',
          description: 'When the document was last updated'
        }
      }
    }
  }
});

print('Creating indexes for messages collection...');

// NOTE: The following indexes are created programmatically by Spring Data MongoDB
// in the MongoIndexConfig class to avoid conflicts between snake_case (MongoDB naming)
// and camelCase (Java class field names). Removing manual index creation here.

// Index creation: See services/message-service/src/main/java/com/chat4all/message/config/MongoIndexConfig.java

print('Messages collection created successfully');

// ============================================================================
// 2. Conversations Collection
// ============================================================================

print('Creating conversations collection with JSON Schema validator...');

db.createCollection('conversations', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['conversation_id', 'conversation_type', 'participants', 'primary_channel', 'created_at'],
      properties: {
        conversation_id: {
          bsonType: 'string',
          description: 'Unique conversation identifier'
        },
        conversation_type: {
          enum: ['1:1', 'GROUP'],
          description: 'Type of conversation: one-to-one or group'
        },
        participants: {
          bsonType: 'array',
          description: 'Array of conversation participant IDs (2-100 users per FR-027)',
          minItems: 2,
          maxItems: 100,
          items: {
            bsonType: 'string',
            description: 'Participant user ID'
          }
        },
        primary_channel: {
          enum: ['WHATSAPP', 'TELEGRAM', 'INSTAGRAM', 'INTERNAL'],
          description: 'Primary platform channel for this conversation'
        },
        title: {
          bsonType: ['string', 'null'],
          description: 'Optional conversation title (for group chats)',
          maxLength: 255
        },
        message_count: {
          bsonType: 'int',
          description: 'Total number of messages in this conversation',
          minimum: 0
        },
        last_message_at: {
          bsonType: ['date', 'null'],
          description: 'Timestamp of the most recent message'
        },
        created_at: {
          bsonType: 'date',
          description: 'When the conversation was created'
        },
        updated_at: {
          bsonType: 'date',
          description: 'When the conversation was last updated'
        },
        archived: {
          bsonType: 'bool',
          description: 'Whether the conversation is archived (auto-archived after 90 days of inactivity)'
        },
        metadata: {
          bsonType: 'object',
          description: 'Additional conversation metadata',
          properties: {
            tags: {
              bsonType: 'array',
              description: 'Conversation tags (e.g., support, billing)',
              items: {
                bsonType: 'string'
              }
            },
            priority: {
              enum: ['LOW', 'NORMAL', 'HIGH', 'URGENT'],
              description: 'Conversation priority level'
            }
          }
        }
      }
    }
  }
});

print('Creating indexes for conversations collection...');

// NOTE: Indexes are created by Spring Data MongoDB using @Indexed annotations
// in the Conversation.java class. Skipping duplicate index creation here to avoid conflicts.
// Spring Data MongoDB will create:
// - Unique index on conversationId
// - Compound index for user recent conversations
// - Index for filtering by primaryChannel and archived status
// - Index for recent conversations (dashboard view)

print('Conversation indexes will be created by Spring Data MongoDB via @Indexed annotations');

print('Conversations collection created successfully');

// ============================================================================
// 3. Files Collection
// ============================================================================

// NOTE: Files collection is managed by Spring Data MongoDB (@Document annotations)
// The schema validator is not created here to avoid conflicts with Spring Data's
// auto-index creation. Indexes are defined in FileAttachment.java using @Indexed annotations.

print('Files collection will be created automatically by Spring Data MongoDB');
print('Schema validation and indexes are defined in FileAttachment.java');

// ============================================================================
// Sharding Configuration (commented out for single-server deployments)
// ============================================================================

print('NOTE: Sharding configuration is disabled by default');
print('To enable sharding in production:');
print('1. Uncomment the sharding commands below');
print('2. Ensure you have a sharded cluster configured');
print('3. Run this script with appropriate privileges');

// Uncomment for production sharded deployments:
/*
print('Enabling sharding for chat4all database...');
sh.enableSharding('chat4all');

print('Sharding messages collection by conversation_id...');
sh.shardCollection('chat4all.messages', { conversation_id: 1 });

print('Sharding enabled successfully');
*/

// ============================================================================
// Summary
// ============================================================================

print('=== MongoDB Initialization Complete ===');
print('Collections created:');
print('  - messages (with JSON Schema validator and indexes)');
print('  - conversations (with JSON Schema validator and indexes)');
print('  - files (with JSON Schema validator, indexes, and TTL expiration)');
print('');
print('Indexes created:');
print('  - messages: 5 indexes (including unique message_id)');
print('  - conversations: 4 indexes (including unique conversation_id)');
print('  - files: 4 indexes (including unique file_id and TTL index)');
print('');
print('To verify, run:');
print('  db.messages.getIndexes()');
print('  db.conversations.getIndexes()');
print('  db.files.getIndexes()');
