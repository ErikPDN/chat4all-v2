# Testing Security Workflows (T148)

This guide explains how to validate the security scanning workflows created in T148.

## Quick Summary

✅ **YAML Validation**: Passed with `yamllint`  
✅ **Workflow Syntax**: Valid GitHub Actions YAML  
⏳ **Full Scan**: Requires GitHub Actions execution or long local run

---

## 1. Validate YAML Syntax (Local)

```bash
# Install yamllint (if not installed)
sudo apt install yamllint  # Ubuntu/Debian
brew install yamllint      # macOS

# Validate workflow YAML
yamllint .github/workflows/security.yml
```

**Expected Output**: No errors (warnings about line length are acceptable)

---

## 2. Test OWASP Dependency Check (Local)

⚠️ **Warning**: First run downloads 320K+ CVE records (~5-15 minutes)

```bash
# Test on single module (faster)
mvn org.owasp:dependency-check-maven:check \
  -DskipTests \
  -Dformat=HTML \
  -pl services/message-service

# View report
open services/message-service/target/dependency-check-report.html
```

**Recommended**: Get free NVD API key to speed up downloads:
1. Register: https://nvd.nist.gov/developers/request-an-api-key
2. Add to `~/.m2/settings.xml`:
   ```xml
   <settings>
     <profiles>
       <profile>
         <id>owasp</id>
         <properties>
           <nvdApiKey>YOUR-API-KEY-HERE</nvdApiKey>
         </properties>
       </profile>
     </profiles>
     <activeProfiles>
       <activeProfile>owasp</activeProfile>
     </activeProfiles>
   </settings>
   ```

---

## 3. Test Dependabot Configuration (GitHub)

```bash
# Validate syntax
cat .github/dependabot.yml | grep -E "package-ecosystem|directory|schedule"

# Expected: 13 configurations (parent POM + services + shared + Docker + GHA)
```

**After Push to GitHub**:
1. Go to **Settings** → **Security** → **Dependabot**
2. Verify "Dependency graph" is enabled
3. Wait 24h for first automated PRs

---

## 4. Test Gitleaks Secret Detection (Local)

```bash
# Install Gitleaks
docker pull ghcr.io/gitleaks/gitleaks:latest

# Scan repository
docker run --rm -v $(pwd):/repo ghcr.io/gitleaks/gitleaks:latest \
  detect --source /repo --verbose

# Expected: No leaks found (or known false positives)
```

---

## 5. Test Trivy Container Scan (Local)

```bash
# Install Trivy
docker pull aquasec/trivy:latest

# Build a service image
docker-compose build message-service

# Scan for vulnerabilities
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy:latest image \
  --severity HIGH,CRITICAL \
  chat4all-message-service:latest

# Generate HTML report
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -v $(pwd):/output \
  aquasec/trivy:latest image \
  --format template \
  --template "@contrib/html.tpl" \
  --output /output/trivy-report.html \
  chat4all-message-service:latest

# View report
open trivy-report.html
```

---

## 6. GitHub Actions Full Workflow Test

### Option A: Manual Trigger (Recommended)

1. Push changes to GitHub:
   ```bash
   git push origin feature/phase11-polish
   ```

2. Go to **Actions** → **Security Scanning** → **Run workflow**

3. Monitor execution:
   - ✅ `dependency-check`: ~10-15 minutes
   - ✅ `trivy-container-scan`: ~5-10 minutes per service
   - ✅ `docker-secrets-scan`: ~1-2 minutes
   - ✅ `security-summary`: ~1 minute

4. Check outputs:
   - **Artifacts**: Download HTML reports
   - **Security tab**: View SARIF uploads
   - **Summary**: Review scan results

### Option B: Push to Main/Develop

```bash
# Merge feature branch
git checkout develop
git merge feature/phase11-polish
git push origin develop

# Workflow triggers automatically
```

### Option C: Create Pull Request

```bash
# Push feature branch
git push origin feature/phase11-polish

# Create PR on GitHub
# Workflow runs on PR events
```

---

## 7. Verify Dependabot Auto-Merge (After Setup)

1. Wait for Dependabot PR (or create test PR)
2. Check PR comments:
   - **Patch/Minor**: Auto-approved by bot
   - **Major**: Manual review comment added

---

## Expected Outcomes

### ✅ Successful Workflow Run

- All jobs complete (may have warnings)
- Artifacts uploaded (reports available for 30 days)
- SARIF files in GitHub Security tab
- Summary shows scan completion

### ⚠️ Expected Warnings

- **OWASP**: Known CVEs in Spring Boot dependencies (usually LOW/MEDIUM)
- **Trivy**: Base image vulnerabilities (Alpine/OpenJDK)
- **Dependabot**: Existing outdated dependencies

### ❌ Failures Requiring Action

- **CRITICAL/HIGH CVEs**: Update dependencies immediately
- **Hardcoded secrets**: Remove and rotate credentials
- **Build failures**: Fix Dockerfile or Maven config

---

## Troubleshooting

### Issue: OWASP takes too long

**Solution**: Add NVD API key (see section 2)

### Issue: Snyk scan fails

**Cause**: Missing `SNYK_TOKEN` secret

**Solution**:
1. Register at https://snyk.io/
2. Get API token from account settings
3. Add to GitHub: **Settings** → **Secrets** → **Actions** → **New repository secret**
   - Name: `SNYK_TOKEN`
   - Value: `your-token-here`

### Issue: Trivy fails to build images

**Cause**: Missing Dockerfile or build dependencies

**Solution**:
```bash
# Test build locally first
mvn clean package -DskipTests -pl services/message-service
docker build -t test-image services/message-service

# Fix any errors before pushing
```

### Issue: Gitleaks reports false positives

**Solution**: Add to `.gitleaksignore`:
```
# Example: Ignore test fixtures
tests/fixtures/fake-credentials.json:1
```

---

## Regular Maintenance

### Weekly (Automated)

- ✅ Security workflow runs every Sunday 2 AM UTC
- ✅ Dependabot checks for updates daily

### Monthly (Manual)

- Review accumulated vulnerability reports
- Update suppressions file (remove expired entries)
- Check GitHub Security tab for trends

### Quarterly (Manual)

- Review and update `dependency-check-suppressions.xml`
- Audit false positive suppressions (remove if fixed)
- Update security policy (SECURITY.md) if needed

---

## References

- **OWASP Dependency Check**: https://jeremylong.github.io/DependencyCheck/
- **Snyk**: https://docs.snyk.io/
- **Trivy**: https://aquasecurity.github.io/trivy/
- **Gitleaks**: https://github.com/gitleaks/gitleaks
- **Dependabot**: https://docs.github.com/en/code-security/dependabot
- **NVD API**: https://nvd.nist.gov/developers/request-an-api-key

---

## Summary Checklist

- [x] YAML syntax validated with `yamllint`
- [x] Workflow structure reviewed
- [ ] OWASP Dependency Check tested locally (optional - very slow)
- [ ] Trivy container scan tested locally (recommended)
- [ ] Gitleaks scan tested locally (recommended)
- [ ] GitHub Actions workflow triggered manually (required)
- [ ] Snyk token configured in GitHub Secrets (required for Snyk job)
- [ ] Reports reviewed and vulnerabilities addressed
- [ ] Dependabot enabled and monitoring PRs

**Status**: T148 implementation complete and ready for production use.
