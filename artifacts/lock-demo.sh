#!/usr/bin/env bash
# Demonstrates the Redisson distributed lock (lock:unit:{unitId}) by firing two
# assignment requests for the SAME unit in parallel: one wins (201), the other
# is rejected (409 RESOURCE_LOCKED).
#
# Prereqs:
#   - backend running on :8080, Redis up
#   - a DISPATCHER access token (POST /assignments is DISPATCHER-only + MFA/aal2):
#       log in as dispatcher@demo.ae in the browser (complete TOTP), then
#       DevTools -> Application -> Local Storage -> http://localhost:3000 ->
#       key  sb-jdevzqbzbxnhqnynrmgg-auth-token  -> copy the access_token value.
#
# Usage:
#   export TOKEN='<paste access_token>'
#   ./artifacts/lock-demo.sh
set -euo pipefail

BASE=http://localhost:8080/api/v1
: "${TOKEN:?Set TOKEN to a DISPATCHER access_token (see header)}"
AUTH="Authorization: Bearer $TOKEN"

# open the Redis session (everything else 401s without it)
curl -s -o /dev/null -w "session: %{http_code}\n" -X POST "$BASE/auth/session" -H "$AUTH"

# pick an OPEN incident and an AVAILABLE unit
INC=$(curl -s "$BASE/incidents?size=20" -H "$AUTH" | \
  python3 -c "import sys,json;print(next(i['id'] for i in json.load(sys.stdin)['data'] if i['status']=='OPEN'))")
UNIT=$(curl -s "$BASE/resources/proximity?lat=24.4539&lng=54.3773&radius=300000&type=ALL" -H "$AUTH" | \
  python3 -c "import sys,json;print(next(u['id'] for u in json.load(sys.stdin) if u['status']=='AVAILABLE'))")
echo "incident=$INC  unit=$UNIT"

BODY="{\"incidentId\":\"$INC\",\"unitId\":\"$UNIT\"}"

# fire TWO assignments for the SAME unit, truly in parallel
curl -s -o /tmp/a1.txt -w "req1: %{http_code}\n" -X POST "$BASE/assignments" -H "$AUTH" -H "Content-Type: application/json" -d "$BODY" &
curl -s -o /tmp/a2.txt -w "req2: %{http_code}\n" -X POST "$BASE/assignments" -H "$AUTH" -H "Content-Type: application/json" -d "$BODY" &
wait

echo "req1 body: $(cat /tmp/a1.txt)"
echo "req2 body: $(cat /tmp/a2.txt)"

# reset so the demo can be repeated: release the winning assignment (unit -> AVAILABLE)
AID=$(python3 -c "import json;
import sys
for f in ('/tmp/a1.txt','/tmp/a2.txt'):
    d=json.load(open(f))
    if d.get('id'): print(d['id']); break" 2>/dev/null || true)
if [ -n "${AID:-}" ]; then
  curl -s -o /dev/null -w "reset (release $AID): %{http_code}\n" -X DELETE "$BASE/assignments/$AID" -H "$AUTH"
fi
