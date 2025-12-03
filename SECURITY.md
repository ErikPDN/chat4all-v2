# Security Policy

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please report them via email to: **security@chat4all.com**

You should receive a response within 48 hours. If for some reason you do not, please follow up via email to ensure we received your original message.

Please include the following information:

- Type of vulnerability (e.g., SQL injection, XSS, authentication bypass)
- Full paths of affected source file(s)
- Location of the affected source code (tag/branch/commit or direct URL)
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the vulnerability

## Security Update Policy

### Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |
| < 1.0   | :x:                |

### Security Scanning

We use multiple automated tools to detect vulnerabilities:

- **OWASP Dependency Check**: Scans Maven dependencies weekly
- **Snyk**: Continuous monitoring for known vulnerabilities
- **Trivy**: Container image scanning for CVEs
- **Gitleaks**: Secret detection in source code
- **Dependabot**: Automated dependency updates

### Vulnerability Severity Levels

#### Critical (CVSS 9.0-10.0)
- **Response Time**: < 24 hours
- **Fix Timeline**: < 72 hours
- **Patch Release**: Emergency release

#### High (CVSS 7.0-8.9)
- **Response Time**: < 48 hours
- **Fix Timeline**: < 1 week
- **Patch Release**: Next scheduled release or hotfix

#### Medium (CVSS 4.0-6.9)
- **Response Time**: < 1 week
- **Fix Timeline**: < 2 weeks
- **Patch Release**: Next scheduled release

#### Low (CVSS 0.1-3.9)
- **Response Time**: < 2 weeks
- **Fix Timeline**: Next major/minor release
- **Patch Release**: Bundled with feature releases

## Security Best Practices

### Dependencies

1. **Keep dependencies up-to-date**
   - Dependabot creates PRs automatically
   - Review and merge security updates promptly
   - Test thoroughly before deploying to production

2. **Review transitive dependencies**
   - Use `mvn dependency:tree` to audit dependencies
   - Exclude vulnerable transitive dependencies when possible
   - Document exceptions in `.github/security/dependency-check-suppressions.xml`

3. **Monitor for CVEs**
   - GitHub Security alerts enabled
   - Weekly automated scans via GitHub Actions
   - Subscribe to security mailing lists for critical libraries

### Secrets Management

1. **Never commit secrets to Git**
   - Use environment variables for sensitive data
   - Leverage Kubernetes Secrets or HashiCorp Vault
   - Configure `.gitignore` to exclude credential files

2. **Rotate credentials regularly**
   - Database passwords: Quarterly
   - API keys: Every 6 months
   - Service accounts: Annually

3. **Use strong encryption**
   - TLS 1.3 for all network communication
   - AES-256 for data at rest
   - BCrypt/Argon2 for password hashing

### Container Security

1. **Use minimal base images**
   - Prefer `eclipse-temurin:21-jre` over full JDK images
   - Use Alpine or distroless when possible
   - Remove unnecessary packages

2. **Scan images before deployment**
   - Trivy scans run automatically in CI/CD
   - Block deployments with CRITICAL vulnerabilities
   - Address HIGH vulnerabilities within 1 week

3. **Run containers as non-root**
   - Use `USER` directive in Dockerfiles
   - Drop unnecessary capabilities
   - Use read-only filesystems when possible

### API Security

1. **Authentication & Authorization**
   - OAuth2/JWT for all authenticated endpoints
   - Scope-based authorization (messages:read, messages:write, etc.)
   - Rate limiting per user and globally

2. **Input Validation**
   - Validate all user inputs
   - Use `@Valid` and `@Validated` annotations
   - Sanitize data before persistence

3. **Output Encoding**
   - Prevent XSS in API responses
   - Use Content-Security-Policy headers
   - Escape user-generated content

### Database Security

1. **PostgreSQL**
   - Use parameterized queries (JPA prevents SQL injection)
   - Encrypt connections (SSL/TLS)
   - Principle of least privilege for database users

2. **MongoDB**
   - Enable authentication (SCRAM-SHA-256)
   - Use role-based access control
   - Encrypt connections and data at rest

3. **Redis**
   - Require authentication (requirepass)
   - Use separate databases per service
   - Disable dangerous commands (FLUSHALL, KEYS)

### Kafka Security

1. **Authentication**
   - Enable SASL/SCRAM for client authentication
   - Use SSL/TLS for encryption in transit

2. **Authorization**
   - Topic-level ACLs per service
   - Principle of least privilege

3. **Data Protection**
   - Encrypt sensitive message content
   - Configure retention policies
   - Monitor consumer lag for anomalies

## Incident Response

### Detection
1. Monitor security alerts in GitHub Security tab
2. Review CI/CD security scan failures
3. Analyze application logs for suspicious activity
4. Track metrics for unusual patterns (error spikes, latency)

### Response
1. **Assess severity** using CVSS scoring
2. **Contain** the vulnerability (disable affected endpoints if needed)
3. **Investigate** root cause and blast radius
4. **Remediate** by patching or mitigating
5. **Verify** fix through testing
6. **Deploy** to production
7. **Document** in post-mortem

### Communication
- **Internal**: Slack #security-incidents channel
- **External**: Email affected users if data breach
- **Public**: Security advisory on GitHub if widely impactful

## Compliance

### Data Protection
- **GDPR**: User data deletion within 30 days
- **LGPD**: Brazilian data protection compliance
- **Audit logs**: 7-year retention per regulations

### Security Standards
- **OWASP Top 10**: Regular assessment
- **CIS Benchmarks**: Docker and Kubernetes hardening
- **ISO 27001**: Information security management (roadmap)

## Contact

- **Security Team**: security@chat4all.com
- **Response Time**: < 48 hours
- **PGP Key**: Available at https://chat4all.com/.well-known/pgp-key.txt

---

Last Updated: 2025-12-03
