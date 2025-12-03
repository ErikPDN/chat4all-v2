# Secrets Management Strategy

**Document Version**: 1.0  
**Last Updated**: December 3, 2025  
**Status**: Active (Docker Compose deployment)

---

## Overview

This document outlines the secrets management strategy for Chat4All v2, covering current practices for Docker Compose deployments and future migration paths for Kubernetes or HashiCorp Vault.

**Current Deployment**: Docker Compose (development and production)  
**Future Considerations**: Kubernetes Secrets, HashiCorp Vault, AWS Secrets Manager

---

## Current Approach: Docker Compose + Environment Variables

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    .env file (gitignored)               │
│  ┌──────────────────────────────────────────────────┐  │
│  │  POSTGRES_PASSWORD=secure_password_here          │  │
│  │  MONGO_PASSWORD=another_secure_password          │  │
│  │  REDIS_PASSWORD=redis_password                   │  │
│  │  MINIO_ROOT_PASSWORD=minio_password              │  │
│  │  WHATSAPP_API_TOKEN=whatsapp_token               │  │
│  │  TELEGRAM_BOT_TOKEN=telegram_token               │  │
│  │  INSTAGRAM_ACCESS_TOKEN=instagram_token          │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│              docker-compose.yml                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  services:                                        │  │
│  │    postgres:                                      │  │
│  │      environment:                                 │  │
│  │        POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}   │  │
│  │                                                   │  │
│  │    message-service:                              │  │
│  │      environment:                                 │  │
│  │        SPRING_DATASOURCE_PASSWORD: ${...}        │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│              Running Containers                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Environment variables injected at runtime       │  │
│  │  (never stored in images)                        │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Secret Categories

#### 1. Infrastructure Credentials

**PostgreSQL**:
```bash
POSTGRES_DB=chat4all
POSTGRES_USER=chat4all
POSTGRES_PASSWORD=<strong-password>
```

**MongoDB**:
```bash
MONGO_INITDB_ROOT_USERNAME=chat4all
MONGO_INITDB_ROOT_PASSWORD=<strong-password>
MONGO_INITDB_DATABASE=chat4all
```

**Redis**:
```bash
REDIS_PASSWORD=<strong-password>
```

**MinIO (S3)**:
```bash
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=<strong-password>
```

#### 2. External Platform Credentials

**WhatsApp Connector**:
```bash
WHATSAPP_API_TOKEN=<whatsapp-business-api-token>
WHATSAPP_PHONE_NUMBER_ID=<phone-number-id>
WHATSAPP_BUSINESS_ACCOUNT_ID=<business-account-id>
WHATSAPP_WEBHOOK_VERIFY_TOKEN=<webhook-verification-token>
```

**Telegram Connector**:
```bash
TELEGRAM_BOT_TOKEN=<bot-token-from-botfather>
TELEGRAM_WEBHOOK_SECRET=<webhook-secret>
```

**Instagram Connector**:
```bash
INSTAGRAM_ACCESS_TOKEN=<long-lived-access-token>
INSTAGRAM_APP_SECRET=<app-secret>
INSTAGRAM_VERIFY_TOKEN=<webhook-verification-token>
```

#### 3. Observability & Security

**Monitoring**:
```bash
GRAFANA_ADMIN_PASSWORD=<admin-password>
PROMETHEUS_BASIC_AUTH_PASSWORD=<optional-basic-auth>
```

**OAuth2/JWT**:
```bash
OAUTH2_CLIENT_SECRET=<keycloak-client-secret>
JWT_SIGNING_KEY=<strong-signing-key>
```

---

## Implementation Guide

### Step 1: Create .env File Template

Create `.env.example` (committed to Git):

```bash
# Infrastructure Secrets
POSTGRES_PASSWORD=change_me_in_production
MONGO_INITDB_ROOT_PASSWORD=change_me_in_production
REDIS_PASSWORD=change_me_in_production
MINIO_ROOT_PASSWORD=change_me_in_production

# WhatsApp Connector
WHATSAPP_API_TOKEN=your_whatsapp_token_here
WHATSAPP_PHONE_NUMBER_ID=your_phone_number_id
WHATSAPP_BUSINESS_ACCOUNT_ID=your_business_account_id
WHATSAPP_WEBHOOK_VERIFY_TOKEN=your_verify_token

# Telegram Connector
TELEGRAM_BOT_TOKEN=your_bot_token_here
TELEGRAM_WEBHOOK_SECRET=your_webhook_secret

# Instagram Connector
INSTAGRAM_ACCESS_TOKEN=your_access_token_here
INSTAGRAM_APP_SECRET=your_app_secret
INSTAGRAM_VERIFY_TOKEN=your_verify_token

# Grafana
GRAFANA_ADMIN_PASSWORD=change_me_in_production

# OAuth2
OAUTH2_CLIENT_SECRET=your_client_secret_here
JWT_SIGNING_KEY=generate_strong_key_here
```

### Step 2: Create Actual .env File

```bash
# Copy template
cp .env.example .env

# Edit with real secrets (NEVER commit this file)
nano .env
```

### Step 3: Ensure .gitignore Includes .env

```gitignore
# Secrets and environment files
.env
.env.local
.env.production
*.env
!.env.example

# Docker sensitive files
docker-compose.override.yml
```

### Step 4: Update docker-compose.yml

```yaml
version: '3.9'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-chat4all}
      POSTGRES_USER: ${POSTGRES_USER:-chat4all}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}  # From .env
    # ... rest of config

  message-service:
    image: chat4all-message-service:latest
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/chat4all
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-chat4all}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_MONGODB_URI: mongodb://${MONGO_INITDB_ROOT_USERNAME}:${MONGO_INITDB_ROOT_PASSWORD}@mongodb:27017/chat4all
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
    # ... rest of config

  whatsapp-connector:
    image: chat4all-whatsapp-connector:latest
    environment:
      WHATSAPP_API_TOKEN: ${WHATSAPP_API_TOKEN}
      WHATSAPP_PHONE_NUMBER_ID: ${WHATSAPP_PHONE_NUMBER_ID}
      WHATSAPP_WEBHOOK_VERIFY_TOKEN: ${WHATSAPP_WEBHOOK_VERIFY_TOKEN}
    # ... rest of config
```

---

## Security Best Practices

### 1. Secret Generation

**Strong Password Requirements**:
- Minimum 32 characters
- Mix of uppercase, lowercase, numbers, symbols
- Use password manager or generation tools

```bash
# Generate strong passwords
openssl rand -base64 32

# Generate JWT signing key (256-bit)
openssl rand -hex 32

# Generate webhook verification tokens
uuidgen | tr '[:upper:]' '[:lower:]'
```

### 2. Access Control

**File Permissions**:
```bash
# Restrict .env file access
chmod 600 .env

# Only owner (root or deploy user) can read
ls -la .env
# -rw------- 1 user user 2048 Dec  3 10:00 .env
```

**Server Access**:
- Store `.env` only on production servers
- Use SSH keys for server access (no passwords)
- Implement bastion host for production access
- Enable audit logging for secret access

### 3. Never Hardcode Secrets

❌ **WRONG**:
```yaml
# docker-compose.yml
environment:
  POSTGRES_PASSWORD: "my_password_123"  # NEVER DO THIS
```

✅ **CORRECT**:
```yaml
# docker-compose.yml
environment:
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}  # From .env file
```

❌ **WRONG**:
```java
// application.yml
spring:
  datasource:
    password: "hardcoded_password"  // NEVER DO THIS
```

✅ **CORRECT**:
```java
// application.yml
spring:
  datasource:
    password: ${SPRING_DATASOURCE_PASSWORD}  # From environment
```

### 4. Development vs Production

**Development (.env.dev)**:
```bash
POSTGRES_PASSWORD=dev_password_only_for_local
MONGO_INITDB_ROOT_PASSWORD=dev_password_only_for_local
```

**Production (.env.production)**:
```bash
POSTGRES_PASSWORD=<generate-strong-production-password>
MONGO_INITDB_ROOT_PASSWORD=<generate-strong-production-password>
```

**Load appropriate file**:
```bash
# Development
docker-compose --env-file .env.dev up

# Production
docker-compose --env-file .env.production up -d
```

---

## Secret Rotation Policy

### Rotation Schedule

| Secret Type | Rotation Frequency | Priority |
|-------------|-------------------|----------|
| Database passwords | Quarterly (90 days) | High |
| API tokens (WhatsApp, Telegram, Instagram) | Every 6 months | High |
| Redis password | Quarterly (90 days) | Medium |
| MinIO credentials | Annually | Medium |
| Webhook verification tokens | Every 6 months | High |
| JWT signing keys | Annually (with overlap) | Critical |
| Grafana admin password | Quarterly (90 days) | Medium |

### Rotation Procedure

#### 1. Database Passwords (Zero-Downtime)

```bash
# Step 1: Add new password to .env (keep old one)
POSTGRES_PASSWORD_NEW=<new-password>

# Step 2: Update PostgreSQL to accept both passwords
docker exec -it chat4all-postgres psql -U chat4all -c \
  "ALTER USER chat4all WITH PASSWORD '${POSTGRES_PASSWORD_NEW}';"

# Step 3: Update .env
sed -i 's/POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=<new-password>/' .env

# Step 4: Restart services one by one
docker-compose up -d --no-deps --force-recreate message-service
docker-compose up -d --no-deps --force-recreate user-service
# ... repeat for all services

# Step 5: Verify all services connected successfully
docker-compose ps
docker-compose logs message-service | grep "Started MessageServiceApplication"
```

#### 2. API Tokens (Connector Rotation)

```bash
# Step 1: Generate new token in platform (WhatsApp Business API, Telegram BotFather)

# Step 2: Update .env with new token
WHATSAPP_API_TOKEN=<new-token>

# Step 3: Restart connector service
docker-compose up -d --no-deps --force-recreate whatsapp-connector

# Step 4: Verify connectivity
docker-compose logs whatsapp-connector | grep "Connected to WhatsApp API"

# Step 5: Revoke old token in platform
```

#### 3. JWT Signing Key (With Overlap Period)

```bash
# Step 1: Generate new key
NEW_JWT_KEY=$(openssl rand -hex 32)

# Step 2: Add as secondary key (support both for 7 days)
JWT_SIGNING_KEY=${CURRENT_KEY}
JWT_SIGNING_KEY_SECONDARY=${NEW_JWT_KEY}

# Step 3: Restart services
docker-compose restart api-gateway message-service user-service

# Step 4: After 7 days, promote secondary to primary
JWT_SIGNING_KEY=${NEW_JWT_KEY}
# Remove JWT_SIGNING_KEY_SECONDARY

# Step 5: Restart again
docker-compose restart api-gateway message-service user-service
```

---

## Backup and Recovery

### Backup Secrets Securely

**Option 1: Encrypted Backup**:
```bash
# Encrypt .env file with GPG
gpg --symmetric --cipher-algo AES256 .env
# Output: .env.gpg

# Upload to secure storage (S3 with encryption)
aws s3 cp .env.gpg s3://chat4all-secrets-backup/production/

# Decrypt when needed
gpg --decrypt .env.gpg > .env
```

**Option 2: Password Manager**:
- Store all secrets in KeePassXC, 1Password, or Bitwarden
- Enable 2FA for password manager access
- Share team vault for production secrets (read-only for most users)

### Disaster Recovery

**Recovery Steps**:
1. Access secure backup (encrypted .env.gpg or password manager)
2. Decrypt and restore .env file on new server
3. Set correct permissions: `chmod 600 .env`
4. Start services: `docker-compose up -d`
5. Verify connectivity and functionality
6. **Rotate all secrets** (assume compromise if server was lost)

---

## Incident Response

### If Secrets Are Compromised

**Immediate Actions (within 1 hour)**:
1. **Revoke** compromised credentials immediately
2. **Rotate** all affected secrets
3. **Audit** access logs for unauthorized usage
4. **Notify** team and stakeholders

**Short-term Actions (within 24 hours)**:
1. Investigate root cause (how secrets were exposed)
2. Review and update access controls
3. Scan for malicious activity using compromised credentials
4. Document incident in security log

**Long-term Actions (within 1 week)**:
1. Implement additional safeguards (MFA, IP whitelisting)
2. Conduct security training for team
3. Consider migration to Vault or Secrets Manager
4. Update this document with lessons learned

### Detection Methods

**Monitor for**:
- Unusual API usage patterns (WhatsApp, Telegram, Instagram)
- Failed authentication attempts in logs
- Database connections from unknown IPs
- Unexpected service restarts or configuration changes

**Alerts**:
```bash
# Example: Monitor failed Redis authentication
docker-compose logs redis | grep "WRONGPASS"

# Example: Monitor PostgreSQL failed logins
docker-compose logs postgres | grep "FATAL: password authentication failed"
```

---

## Future Migration Paths

### Option 1: HashiCorp Vault (Recommended for Self-Hosted)

**Architecture**:
```
┌──────────────────────────────────────────┐
│         HashiCorp Vault Server           │
│  ┌────────────────────────────────────┐  │
│  │  secrets/chat4all/postgres         │  │
│  │  secrets/chat4all/mongodb          │  │
│  │  secrets/chat4all/whatsapp         │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
                  ↓ API
┌──────────────────────────────────────────┐
│       Docker Containers with Vault       │
│       Agent Injector Sidecars            │
└──────────────────────────────────────────┘
```

**Benefits**:
- Centralized secret management
- Automatic secret rotation
- Audit logging of all secret access
- Fine-grained access control (AppRole, policies)
- Encryption at rest and in transit

**Migration Steps**:
1. Deploy Vault server (HA mode with 3 nodes)
2. Initialize and unseal Vault
3. Create secret policies for each service
4. Migrate secrets from .env to Vault
5. Update docker-compose.yml to use Vault agent
6. Test and validate
7. Decommission .env files

### Option 2: Kubernetes Secrets (When Migrating to K8s)

**Architecture**:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-credentials
type: Opaque
data:
  password: <base64-encoded-password>
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: message-service
spec:
  template:
    spec:
      containers:
      - name: message-service
        env:
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-credentials
              key: password
```

**Benefits**:
- Native Kubernetes integration
- RBAC for secret access
- Can integrate with external secret stores (Vault, AWS Secrets Manager)
- Automatic mounting as files or environment variables

**Migration Steps**:
1. Convert docker-compose.yml to Kubernetes manifests
2. Create Secret objects from .env values
3. Update Deployment specs to reference Secrets
4. Deploy to Kubernetes cluster
5. Validate secret injection
6. Enable secret encryption at rest (EncryptionConfiguration)

### Option 3: AWS Secrets Manager / Azure Key Vault (Cloud-Native)

**Benefits**:
- Fully managed service
- Automatic rotation support
- Integration with IAM/RBAC
- High availability and durability
- Audit trails via CloudTrail/Azure Monitor

**Migration Steps**:
1. Create secrets in AWS Secrets Manager
2. Grant IAM permissions to ECS tasks / EC2 instances
3. Update application to fetch secrets from AWS SDK
4. Remove .env files
5. Monitor secret access in CloudTrail

---

## Compliance and Audit

### PCI-DSS Requirements (if handling payment data)

- ✅ Secrets encrypted at rest (.env file permissions)
- ✅ Secrets encrypted in transit (TLS for API calls)
- ✅ Access logs enabled (Docker logs)
- ⚠️ Automatic rotation (manual process currently)
- ⚠️ Multi-factor authentication (future: Vault)

### GDPR/LGPD Requirements

- ✅ No customer data in secrets
- ✅ Access control (file permissions)
- ✅ Incident response procedure documented
- ✅ Audit logs (7-year retention in PostgreSQL)

### Audit Checklist

Monthly review:
- [ ] Verify .env file permissions (600)
- [ ] Check for secrets in Git history (`git log -p | grep -i password`)
- [ ] Review access logs for anomalies
- [ ] Confirm rotation schedule adherence
- [ ] Test disaster recovery procedure
- [ ] Update `.env.example` if new secrets added

---

## References

- [Docker Compose Environment Variables](https://docs.docker.com/compose/environment-variables/)
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [AWS Secrets Manager](https://aws.amazon.com/secrets-manager/)
- [Azure Key Vault](https://azure.microsoft.com/en-us/services/key-vault/)

---

## Appendix: Quick Reference

### Generate Secrets

```bash
# PostgreSQL password (32 chars)
openssl rand -base64 32

# JWT signing key (256-bit hex)
openssl rand -hex 32

# UUID v4 (for webhook tokens)
uuidgen | tr '[:upper:]' '[:lower:]'

# API token (64 chars alphanumeric)
tr -dc A-Za-z0-9 </dev/urandom | head -c 64; echo
```

### Check for Exposed Secrets

```bash
# Scan Git history
git log -p | grep -iE "(password|secret|token|key).*="

# Scan current codebase
grep -r -iE "(password|secret|token|key).*=" --include="*.yml" --include="*.yaml" --include="*.java" .

# Check .env is gitignored
git check-ignore -v .env
```

### Validate Secret Strength

```bash
# Check password entropy (aim for >128 bits)
echo -n "your_password_here" | openssl sha256
```

---

**Document Owner**: Security Team  
**Review Cycle**: Quarterly  
**Next Review**: March 3, 2026
