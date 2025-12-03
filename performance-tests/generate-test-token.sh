#!/bin/bash
#
# Generate test JWT tokens for performance testing
# This creates simple JWT tokens for local testing without Keycloak
#
# Usage: ./generate-test-token.sh [username]
#

USERNAME="${1:-perf-test-user}"
ISSUER="http://localhost:8090/realms/chat4all"
EXPIRES_IN=3600  # 1 hour

# Simple JWT generator using jq and base64
# Note: This creates unsigned tokens for LOCAL TESTING ONLY
# Production should use proper Keycloak tokens

# JWT Header
HEADER=$(echo -n '{"alg":"none","typ":"JWT"}' | base64 -w 0 | tr '+/' '-_' | tr -d '=')

# JWT Payload
NOW=$(date +%s)
EXP=$((NOW + EXPIRES_IN))

PAYLOAD=$(cat <<EOF | jq -c . | base64 -w 0 | tr '+/' '-_' | tr -d '='
{
  "sub": "${USERNAME}",
  "iss": "${ISSUER}",
  "aud": "chat4all-api",
  "exp": ${EXP},
  "iat": ${NOW},
  "jti": "$(uuidgen)",
  "preferred_username": "${USERNAME}",
  "email": "${USERNAME}@test.local",
  "realm_access": {
    "roles": ["user", "agent"]
  }
}
EOF
)

# Create unsigned JWT (header.payload.)
# Note: The trailing dot with no signature works for testing with permitAll() endpoints
TOKEN="${HEADER}.${PAYLOAD}."

echo "$TOKEN"
