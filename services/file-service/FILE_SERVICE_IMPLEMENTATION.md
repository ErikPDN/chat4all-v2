# User Story 3 - File Attachments Implementation Summary

## ✅ Tasks Completed (T062-T065)

### T062 ✅ - FileAttachment Entity
**File**: `services/file-service/src/main/java/com/chat4all/file/domain/FileAttachment.java`

**Features**:
- MongoDB @Document mapping to `files` collection
- Complete file metadata tracking
- File lifecycle statuses: PENDING → UPLOADED → PROCESSING → READY / FAILED
- TTL index on `expiresAt` field (24h default)
- Malware scan result nested object
- Support for thumbnails and additional metadata

**Key Fields**:
- `fileId` (UUID): Unique identifier
- `messageId`: Optional link to message
- `filename`, `fileSize`, `mimeType`: File metadata
- `status`: Lifecycle tracking
- `storageUrl`, `bucketName`, `objectKey`: S3 location
- `expiresAt`: TTL for automatic cleanup
- `scanResult`: Malware scan information

---

### T063 ✅ - FileRepository
**File**: `services/file-service/src/main/java/com/chat4all/file/repository/FileRepository.java`

**Methods**:
- `findByFileId(String fileId)`: Get file by ID
- `findByMessageId(String messageId)`: Get message attachments
- `findByStatus(String status)`: Batch processing queries
- `findByUploadedAtBefore(Instant)`: Cleanup queries
- `countByStatus(String status)`: Metrics
- `deleteByMessageId(String messageId)`: Cascade delete

---

### T064 ✅ - S3StorageService
**File**: `services/file-service/src/main/java/com/chat4all/file/storage/S3StorageService.java`

**Methods**:
- ✅ `generatePresignedUploadUrl()`: PUT URL for client uploads (15min TTL)
- ✅ `generatePresignedDownloadUrl()`: GET URL for downloads (1h TTL)
- `deleteObject()`: Remove files from S3
- `buildS3Url()`: Internal S3 URL format
- `extractObjectKey()`: Parse S3 URLs

**Configuration** (`S3Config.java`):
- AWS SDK v2 with MinIO compatibility
- Endpoint: `http://localhost:9000`
- Credentials: `minioadmin` / `minioadmin`
- Auto-create bucket: `chat4all-files`
- Force path-style: Required for MinIO

---

### T065 ✅ - FileController
**File**: `services/file-service/src/main/java/com/chat4all/file/api/FileController.java`

**Endpoints**:

#### 1. POST /api/files/initiate
**Purpose**: Initiate file upload (client-side upload flow)

**Request** (`InitiateUploadRequest`):
```json
{
  "filename": "photo.jpg",
  "fileSize": 2048576,
  "mimeType": "image/jpeg",
  "messageId": "optional-message-id",
  "metadata": {}
}
```

**Response** (`InitiateUploadResponse`):
```json
{
  "fileId": "550e8400-e29b-41d4-a716-446655440000",
  "uploadUrl": "http://localhost:9000/chat4all-files/files/2025/11/...?X-Amz-...",
  "objectKey": "files/2025/11/550e8400.../photo.jpg",
  "expiresAt": "2025-11-24T22:15:00Z",
  "status": "PENDING",
  "message": "Upload file using PUT request to uploadUrl..."
}
```

**Flow**:
1. Client sends metadata
2. Backend generates `fileId` and S3 object key
3. Backend creates MongoDB record (status=PENDING)
4. Backend generates presigned PUT URL
5. Client uploads file directly to S3 using PUT

#### 2. GET /api/files/{id}
**Purpose**: Get file metadata

**Response**: Full `FileAttachment` object

#### 3. GET /api/files/{id}/download
**Purpose**: Get presigned download URL

**Response**:
```json
{
  "fileId": "...",
  "downloadUrl": "http://localhost:9000/chat4all-files/...?X-Amz-...",
  "filename": "photo.jpg",
  "mimeType": "image/jpeg",
  "fileSize": 2048576,
  "expiresAt": "2025-11-24T23:00:00Z",
  "status": "READY"
}
```

---

## 📁 Project Structure

```
services/file-service/
├── pom.xml                              ✅ AWS SDK v2, Spring WebFlux, MongoDB
├── src/main/
│   ├── java/com/chat4all/file/
│   │   ├── FileServiceApplication.java  ✅ Main application
│   │   ├── api/
│   │   │   ├── FileController.java      ✅ T065 - REST endpoints
│   │   │   └── dto/
│   │   │       ├── InitiateUploadRequest.java   ✅ Upload request DTO
│   │   │       └── InitiateUploadResponse.java  ✅ Upload response DTO
│   │   ├── config/
│   │   │   └── S3Config.java            ✅ MinIO/S3 configuration
│   │   ├── domain/
│   │   │   └── FileAttachment.java      ✅ T062 - MongoDB entity
│   │   ├── repository/
│   │   │   └── FileRepository.java      ✅ T063 - MongoDB repository
│   │   └── storage/
│   │       └── S3StorageService.java    ✅ T064 - Presigned URLs
│   └── resources/
│       └── application.yml              ✅ Configuration
```

---

## 🔧 Configuration

### application.yml
```yaml
spring:
  application:
    name: file-service
  data:
    mongodb:
      uri: mongodb://localhost:27017/chat4all

s3:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: chat4all-files
  upload-url-expiration: PT15M   # 15 minutes
  download-url-expiration: PT1H  # 1 hour

file:
  ttl-hours: 24
  max-file-size: 104857600  # 100MB
  allowed-mime-types:
    - image/jpeg
    - image/png
    - application/pdf
    - video/mp4
    # ...more

server:
  port: 8083
```

---

## 🧪 Testing Flow

### 1. Start Services
```bash
# Start MongoDB + MinIO
docker-compose up -d mongodb minio

# Start file-service
cd services/file-service
mvn spring-boot:run
```

### 2. Initiate Upload
```bash
curl -X POST http://localhost:8083/api/files/initiate \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "test-photo.jpg",
    "fileSize": 102400,
    "mimeType": "image/jpeg"
  }'
```

**Response**:
```json
{
  "fileId": "abc-123",
  "uploadUrl": "http://localhost:9000/chat4all-files/files/2025/11/abc-123/test-photo.jpg?X-Amz-...",
  "objectKey": "files/2025/11/abc-123/test-photo.jpg",
  "expiresAt": "2025-11-24T22:15:00Z",
  "status": "PENDING"
}
```

### 3. Upload File to S3
```bash
curl -X PUT "http://localhost:9000/chat4all-files/..." \
  -H "Content-Type: image/jpeg" \
  --data-binary "@test-photo.jpg"
```

### 4. Get File Metadata
```bash
curl http://localhost:8083/api/files/abc-123
```

### 5. Get Download URL
```bash
curl http://localhost:8083/api/files/abc-123/download
```

---

## 📊 Architecture Diagram

```
┌─────────────┐                ┌──────────────┐
│   Client    │                │ File Service │
│  (Frontend) │                │  (Port 8083) │
└──────┬──────┘                └───────┬──────┘
       │                               │
       │ 1. POST /api/files/initiate   │
       │ { filename, size, mimeType }  │
       │───────────────────────────────>│
       │                               │
       │   2. Response: fileId +       │
       │      presignedUploadUrl       │
       │<───────────────────────────────│
       │                               │
       │                               ├────────────┐
       │                               │ 3. Save    │
       │                               │   PENDING  │
       │                               │   to Mongo │
       │                               │<───────────┘
       │                               │
       │                        ┌──────┴──────┐
       │  4. PUT file to S3     │   MinIO/S3  │
       │  (presigned URL)       │ (localhost:│
       │────────────────────────>│    9000)   │
       │                        └──────┬──────┘
       │                               │
       │  5. GET /api/files/{id}       │
       │───────────────────────────────>│
       │                               │
       │  6. Metadata response         │
       │<───────────────────────────────│
```

---

## 🚀 Next Steps (Future Tasks)

### Pending Implementation:
- ⏳ **T066**: Multipart upload (>100MB files)
- ⏳ **T067**: File type validation (whitelist)
- ⏳ **T068**: Malware scanning (ClamAV integration)
- ⏳ **T069**: Thumbnail generation (images/videos)
- ⏳ **T070**: FileUploadCompleteEvent (Kafka)
- ⏳ **T071**: Message.fileAttachments field
- ⏳ **T072**: SendMessageRequest.fileIds array
- ⏳ **T073**: MongoDB TTL index (mongo-init.js)

### Integration with Message Service:
1. Client initiates upload → gets fileId
2. Client uploads file to S3
3. Client sends message with fileIds array
4. Message Service links files to message
5. File status updates to READY
6. Message delivered with file download URLs

---

## ✅ Compilation Status

```
[INFO] Building File Service 1.0.0-SNAPSHOT
[INFO] Compiling 8 source files
[INFO] BUILD SUCCESS
[INFO] Total time:  1.854 s
```

**Files Compiled**:
1. FileServiceApplication.java
2. FileAttachment.java (domain)
3. FileRepository.java
4. S3Config.java
5. S3StorageService.java
6. FileController.java
7. InitiateUploadRequest.java (DTO)
8. InitiateUploadResponse.java (DTO)

**Warnings**: 1 deprecation (expireAfterSeconds in @Indexed - non-critical)

---

## 📝 Summary

✅ **T062-T065 Complete** (4/9 tasks for US3)

**What Works**:
- File metadata persistence (MongoDB)
- S3/MinIO storage integration
- Presigned URL generation (upload + download)
- Client-side upload flow (no backend proxy)
- REST API for file management

**Benefits**:
- **Scalability**: Client uploads directly to S3 (no backend bottleneck)
- **Performance**: Presigned URLs reduce server load
- **Security**: Time-limited URLs (15min upload, 1h download)
- **Storage**: MinIO S3-compatible (easy migration to AWS S3)

**Progress**: **44% Complete** (4/9 tasks for User Story 3) 🎉
