#!/usr/bin/env bash
# End-to-end smoke test for a running Personal Finance Manager instance.
# This is the project's own regression script, not the assignment's financial_manager_tests.sh.
#
# Usage: bash scripts/api_smoke_test.sh [BASE_URL]
#   BASE_URL defaults to http://localhost:8080/api
set -uo pipefail

BASE_URL="${1:-http://localhost:8080/api}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

PASS=0
FAIL=0
RUN_ID="$(date +%s)$RANDOM"
TODAY="$(date -u +%Y-%m-%d)"
NEXT_YEAR="$(( $(date -u +%Y) + 1 ))-12-31"
FUTURE_DAY="$(( $(date -u +%Y) + 1 ))-01-01"
YEAR="$(date -u +%Y)"
MONTH="$(date -u +%-m)"

# request <cookie-jar> <method> <path> [json-body]  -> sets STATUS and BODY
request() {
    local jar="$1" method="$2" path="$3" body="${4:-}"
    local args=(-s -o "$WORK_DIR/body" -w '%{http_code}' -X "$method" -b "$jar" -c "$jar" "$BASE_URL$path")
    if [[ -n "$body" ]]; then
        args+=(-H 'Content-Type: application/json' -d "$body")
    fi
    STATUS="$(curl "${args[@]}")"
    BODY="$(cat "$WORK_DIR/body")"
}

expect() {
    local description="$1" expected="$2"
    if [[ "$STATUS" == "$expected" ]]; then
        PASS=$((PASS + 1)); printf '  PASS  %-60s %s\n' "$description" "$STATUS"
    else
        FAIL=$((FAIL + 1)); printf '  FAIL  %-60s expected %s got %s: %s\n' "$description" "$expected" "$STATUS" "$BODY"
    fi
}

expect_body() {
    local description="$1" needle="$2"
    if [[ "$BODY" == *"$needle"* ]]; then
        PASS=$((PASS + 1)); printf '  PASS  %-60s\n' "$description"
    else
        FAIL=$((FAIL + 1)); printf '  FAIL  %-60s body lacks %s: %s\n' "$description" "$needle" "$BODY"
    fi
}

json_field() { sed -n "s/.*\"$1\":\([0-9]*\).*/\1/p" <<<"$BODY" | head -1; }

A="$WORK_DIR/a.txt"; B="$WORK_DIR/b.txt"; ANON="$WORK_DIR/anon.txt"
USER_A="alice.$RUN_ID@example.com"; USER_B="bob.$RUN_ID@example.com"

echo "== Authentication"
request "$A" POST /auth/register "{\"username\":\"$USER_A\",\"password\":\"password123\",\"fullName\":\"Alice Doe\",\"phoneNumber\":\"+1234567890\"}"
expect "register user A" 201
expect_body "register response has userId" '"userId"'
request "$A" POST /auth/register "{\"username\":\"$(tr "[:lower:]" "[:upper:]" <<<"$USER_A")\",\"password\":\"password123\",\"fullName\":\"Alice Doe\",\"phoneNumber\":\"+1234567890\"}"
expect "duplicate registration (case-insensitive)" 409
request "$A" POST /auth/register '{"username":"not-an-email","password":"password123","fullName":"X","phoneNumber":"+1234567890"}'
expect "invalid email" 400
request "$A" POST /auth/register "{\"username\":\"weak.$RUN_ID@example.com\",\"password\":\"short\",\"fullName\":\"X\",\"phoneNumber\":\"+1234567890\"}"
expect "weak password" 400
request "$B" POST /auth/register "{\"username\":\"$USER_B\",\"password\":\"password123\",\"fullName\":\"Bob Doe\",\"phoneNumber\":\"+0987654321\"}"
expect "register user B" 201
request "$ANON" GET /transactions
expect "unauthenticated access" 401
request "$A" POST /auth/login "{\"username\":\"$USER_A\",\"password\":\"wrong-password1\"}"
expect "login with wrong password" 401
request "$A" POST /auth/login "{\"username\":\"nobody.$RUN_ID@example.com\",\"password\":\"password123\"}"
expect "login with unknown user" 401
request "$A" POST /auth/login "{\"username\":\"$USER_A\",\"password\":\"password123\"}"
expect "login user A" 200
request "$B" POST /auth/login "{\"username\":\"$USER_B\",\"password\":\"password123\"}"
expect "login user B" 200

echo "== Categories"
request "$A" GET /categories
expect "list categories" 200
expect_body "default Salary present" '"name":"Salary","type":"INCOME","isCustom":false'
request "$A" POST /categories '{"name":"Freelance","type":"INCOME"}'
expect "create custom category" 201
request "$A" POST /categories '{"name":"freelance","type":"EXPENSE"}'
expect "duplicate custom category" 409
request "$A" POST /categories '{"name":"Food","type":"EXPENSE"}'
expect "custom category shadowing default" 409
request "$A" POST /categories '{"name":"Bad","type":"SAVINGS"}'
expect "invalid category type" 400
request "$B" POST /categories '{"name":"Freelance","type":"INCOME"}'
expect "same custom name for another user" 201

echo "== Transactions"
request "$A" POST /transactions "{\"amount\":5000.00,\"date\":\"$TODAY\",\"category\":\"Salary\",\"description\":\"Salary\"}"
expect "create income transaction" 201
expect_body "type derived from category" '"type":"INCOME"'
TX_ID="$(json_field id)"
request "$A" POST /transactions "{\"amount\":1200.50,\"date\":\"$TODAY\",\"category\":\"Rent\"}"
expect "create expense transaction" 201
request "$A" POST /transactions "{\"amount\":300,\"date\":\"$TODAY\",\"category\":\"Freelance\"}"
expect "create transaction with custom category" 201
request "$A" POST /transactions "{\"amount\":0,\"date\":\"$TODAY\",\"category\":\"Food\"}"
expect "zero amount" 400
request "$A" POST /transactions "{\"amount\":-5,\"date\":\"$TODAY\",\"category\":\"Food\"}"
expect "negative amount" 400
request "$A" POST /transactions "{\"amount\":5,\"date\":\"$FUTURE_DAY\",\"category\":\"Food\"}"
expect "future date" 400
request "$A" POST /transactions '{"amount":5,"date":"2024-13-45","category":"Food"}'
expect "malformed date" 400
request "$A" POST /transactions "{\"amount\":5,\"date\":\"$TODAY\",\"category\":\"Nope\"}"
expect "unknown category" 400
request "$A" POST /transactions "{\"amount\":5,\"date\":\"$TODAY\",\"category\":\"Food\",\"type\":\"INCOME\"}"
expect "type conflicting with category" 400
request "$A" POST /transactions '{"amount":'
expect "malformed JSON" 400
request "$A" GET /transactions
expect "list transactions" 200
expect_body "wrapped in transactions" '"transactions":['
request "$A" GET "/transactions?type=EXPENSE"
expect "filter by type" 200
request "$A" GET "/transactions?startDate=2024-02-01&endDate=2024-01-01"
expect "startDate after endDate" 400
request "$A" GET "/transactions?type=BOGUS"
expect "invalid type filter" 400
request "$A" PUT "/transactions/$TX_ID" '{"amount":6000.00,"description":"Updated"}'
expect "update transaction" 200
expect_body "amount updated" '"amount":6000.00'
request "$A" PUT "/transactions/$TX_ID" '{"date":"2020-01-01"}'
expect "update with date field (date is read-only)" 200
expect_body "date unchanged" "\"date\":\"$TODAY\""
request "$B" PUT "/transactions/$TX_ID" '{"amount":1}'
expect "user B cannot update A's transaction" 404
request "$B" DELETE "/transactions/$TX_ID"
expect "user B cannot delete A's transaction" 404
request "$A" PUT "/transactions/999999999" '{"amount":1}'
expect "update missing transaction" 404

echo "== Category deletion"
request "$A" DELETE /categories/Freelance
expect "delete referenced category" 400
request "$A" DELETE /categories/Salary
expect "delete default category" 403
request "$A" DELETE /categories/DoesNotExist
expect "delete missing category" 404
request "$B" DELETE /categories/Freelance
expect "user B deletes own unreferenced category" 200

echo "== Goals"
request "$A" POST /goals "{\"goalName\":\"Emergency Fund\",\"targetAmount\":5000.00,\"targetDate\":\"$NEXT_YEAR\",\"startDate\":\"$TODAY\"}"
expect "create goal" 201
expect_body "progress computed" '"currentProgress":5099.50'
GOAL_ID="$(json_field id)"
request "$A" POST /goals "{\"goalName\":\"Past\",\"targetAmount\":100,\"targetDate\":\"2020-01-01\"}"
expect "goal with past target date" 400
request "$A" GET /goals
expect "list goals" 200
request "$A" GET "/goals/$GOAL_ID"
expect "get goal" 200
request "$B" GET "/goals/$GOAL_ID"
expect "user B cannot read A's goal" 403
request "$A" PUT "/goals/$GOAL_ID" "{\"targetAmount\":10000.00,\"targetDate\":\"$NEXT_YEAR\"}"
expect "update goal" 200
expect_body "percentage recomputed" '"progressPercentage":51.0,'
request "$A" GET /goals/abc
expect "non-numeric goal id" 400
request "$A" GET /goals/999999999
expect "missing goal" 404

echo "== Reports"
request "$A" GET "/reports/monthly/$YEAR/$MONTH"
expect "monthly report" 200
expect_body "net savings" '"netSavings":5099.50'
request "$A" GET "/reports/yearly/$YEAR"
expect "yearly report" 200
request "$A" GET "/reports/monthly/$YEAR/13"
expect "invalid month" 400
request "$B" GET "/reports/yearly/$YEAR"
expect "user B report excludes A's data" 200
expect_body "user B has zero net savings" '"netSavings":0}'

echo "== Deletion effects"
request "$A" DELETE "/transactions/$TX_ID"
expect "delete transaction" 200
request "$A" DELETE "/transactions/$TX_ID"
expect "repeat delete" 404
request "$A" GET "/reports/monthly/$YEAR/$MONTH"
expect_body "report reflects deletion" '"netSavings":-900.50'
request "$A" DELETE "/goals/$GOAL_ID"
expect "delete goal" 200

echo "== Logout"
request "$A" POST /auth/logout
expect "logout" 200
request "$A" GET /transactions
expect "session invalid after logout" 401
request "$A" POST /auth/logout
expect "logout without session" 401

echo
echo "Passed: $PASS  Failed: $FAIL"
[[ "$FAIL" -eq 0 ]]
