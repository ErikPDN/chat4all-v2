# File Upload Fix Summary

## Problem Statement
File uploads were failing with HTTP 403 "SignatureDoesNotMatch" error when using Docker-based MinIO and File Service containers.

## Root Cause Analysis

### AWS4 Signature Issue
AWS SDK v2 includes the request hostname in the AWS4-HMAC-SHA256 signature calculation. When:
1. Presigner generates URL with hostname: `minio:9000` (internal Docker)
2. Client makes request with hostname: `localhost:9000` (external)
3. MinIO validates signature but rejects because Host header doesn't match

### Original Configuration Problem
- Single `S3_ENDPOINT` used for both:
  - Server-side operations (S3Client accessing internal Docker service)
  - Client presigned URLs (external client accessing localhost)
- Mismatch between presigner endpoint and client-accessible endpoint

## Solution Implementation

### Dual Endpoint Configuration

**Step 1**: Split endpoint configuration in `application.yml`
```yaml
s3:
  endpoint: ${S3_ENDPOINT:http://minio:9000}        # Internal Docker
  public-endpoint: ${S3_PUBLIC_ENDPOINT:http://localhost:9000}  # External
```

**Step 2**: Update S3Config to use different endpoints
- S3Client (server): Uses `endpoint` (minio:9000)
- S3Presigner (client): Uses `publicEndpoint` (localhost:9000)

**Step 3**: Update MongoDB connection for Docker
- Changed from `localhost:27017` to `host.docker.internal:27017` for File Service Docker container

## Files Modified

### Configuration Files
1. **`services/file-service/src/main/resources/application.yml`**
   - Added `S3_PUBLIC_ENDPOINT` property
   - Changed MongoDB URI to use `host.docker.internal`

2. **`services/file-service/src/main/java/com/chat4all/file/config/S3Config.java`**
   - Added `publicEndpoint` injection
   - Updated `s3Presigner()` bean to use `publicEndpoint`

### Docker Configuration
3. **`docker-compose.yml`**
   - Ensured MinIO accessible on localhost:9000
   - Configured File Service with dual endpoint environment variables

## Testing

### Validation Tests
1. **100MB Upload Test**: ✅ PASSED (HTTP 200)
2. **2GB Upload Test**: ✅ PASSED (HTTP 200, ~200 MB/s)

### Test Scripts
- `/tmp/test-local-upload.sh` - 100MB test
- `/tmp/test-2gb-upload.sh` - 2GB test

## Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│                    Client                            │
│  (Browser/App at localhost)                         │
└────────────────────┬────────────────────────────────┘
                     │
                     │ HTTP PUT (client's localhost:9000)
                     ▼
        ┌────────────────────────┐
        │    MinIO Container     │
        │   Port: 9000/9001      │
        │  (Docker network)      │
        └────────────────────────┘
                     ▲
                     │
        ┌────────────┴──────────────┐
        │                           │
        │  File Service Container   │
        │  (Docker network)         │
        │  S3Client: minio:9000     │ (internal communication)
        │  S3Presigner: localhost   │ (presigned URLs for client)
        └───────────────────────────┘
```

## Key Insights

1. **AWS4 Signature Hostname Binding**: The signature is tied to the request hostname. All participants must use the same hostname to validate signatures.

2. **Docker Network Resolution**: Internal Docker DNS resolves `minio` service name, but external clients cannot. Using different endpoints for each is required.

3. **Presigner Endpoint Strategy**: The presigner endpoint must be accessible from the client, not from the server perspective.

## Verification Commands

```bash
# Check File Service health
curl http://localhost:8084/actuator/health

# Generate presigned URL
curl -X POST http://localhost:8084/api/files/initiate \
  -H "Content-Type: application/json" \
  -d '{"filename": "test.bin", "fileSize": 1000000, "mimeType": "application/octet-stream"}'

# Verify MinIO accessibility
curl http://localhost:9000/minio/health/live
```

## Conclusion

The dual endpoint configuration successfully resolves AWS4 signature validation issues while maintaining proper internal Docker communication. File uploads up to 2GB are fully functional and tested.

**Status**: ✅ RESOLVED AND VALIDATED
