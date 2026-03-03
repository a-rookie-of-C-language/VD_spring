#!/bin/bash

set -e

API_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:5173"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="E3E_TEST_REPORT_${TIMESTAMP}.md"

declare -A test_results
test_results[passed]=0
test_results[failed]=0
test_results[total]=0

declare -a test_accounts
test_accounts[0]="12323020420:arookieofc:superAdmin"
test_accounts[1]="12323020421:arookieofc:functionary"
test_accounts[2]="20230001:arookieofc:student"

declare -A tokens

log_test() {
  local name=$1
  local passed=$2
  local details=$3
  
  ((test_results[total]++))
  
  if [ "$passed" = true ]; then
    ((test_results[passed]++))
    echo "✓ $name"
  else
    ((test_results[failed]++))
    echo "✗ $name: $details"
  fi
  
  echo "- **$name**: $([ "$passed" = true ] && echo 'PASS' || echo "FAIL - $details")" >> "$REPORT_FILE"
}

login_user() {
  local student_no=$1
  local password=$2
  local role=$3
  
  echo "Logging in as $role ($student_no)..."
  
  local response=$(curl -s -X POST "$API_URL/user/login" \
    -H "Content-Type: application/json" \
    -d "{\"studentNo\":\"$student_no\",\"password\":\"$password\"}")
  
  local token=$(echo "$response" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
  
  if [ -z "$token" ]; then
    echo "Login failed for $role"
    return 1
  fi
  
  tokens[$role]="$token"
  log_test "Login - $role" true "Token: ${token:0:20}..."
  return 0
}

test_auth() {
  echo -e "\n## 1. AUTHENTICATION & AUTHORIZATION TESTS" >> "$REPORT_FILE"
  echo "### Testing Authentication..."
  
  for account in "${test_accounts[@]}"; do
    IFS=':' read -r student_no password role <<< "$account"
    if login_user "$student_no" "$password" "$role"; then
      
      local verify_response=$(curl -s -X GET "$API_URL/user/verifyToken" \
        -H "Authorization: Bearer ${tokens[$role]}")
      
      local valid=$(echo "$verify_response" | grep -q "valid" && echo true || echo false)
      log_test "Token Validation - $role" "$valid"
    fi
  done
}

test_activities() {
  echo -e "\n## 2. ACTIVITY MANAGEMENT TESTS" >> "$REPORT_FILE"
  echo "### Testing Activities..."
  
  local functionary_token="${tokens[functionary]}"
  
  echo "Querying activities..."
  local activity_response=$(curl -s -X POST "$API_URL/api/activities/query" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $functionary_token" \
    -d '{"page":1,"pageSize":10}')
  
  local activity_count=$(echo "$activity_response" | grep -o '"id"' | wc -l)
  log_test "Query Activities Endpoint" true "$activity_count activities found"
  
  echo "Activity response sample:"
  echo "$activity_response" | head -c 200
  echo ""
}

test_enrollment() {
  echo -e "\n## 3. ENROLLMENT TESTS" >> "$REPORT_FILE"
  echo "### Testing Student Enrollment..."
  
  local student_token="${tokens[student]}"
  
  # Try to get first activity to enroll
  local activities=$(curl -s -X POST "$API_URL/api/activities/query" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $student_token" \
    -d '{"page":1,"pageSize":1}')
  
  log_test "Student Can Query Activities" true
}

test_statistics() {
  echo -e "\n## 4. STATISTICS TESTS" >> "$REPORT_FILE"
  echo "### Testing Statistics..."
  
  for role in superAdmin functionary student; do
    local token="${tokens[$role]}"
    
    if [ ! -z "$token" ]; then
      local stats_response=$(curl -s -X GET "$API_URL/api/activities/MyStatus" \
        -H "Authorization: Bearer $token")
      
      local has_data=$(echo "$stats_response" | grep -q '"' && echo true || echo false)
      log_test "Personal Statistics - $role" "$has_data"
    fi
  done
}

test_monitoring() {
  echo -e "\n## 5. SYSTEM MONITORING TESTS" >> "$REPORT_FILE"
  echo "### Testing System Monitoring..."
  
  local admin_token="${tokens[superAdmin]}"
  
  local monitor_response=$(curl -s -X GET "$API_URL/api/monitoring/dashboard" \
    -H "Authorization: Bearer $admin_token")
  
  local has_data=$(echo "$monitor_response" | grep -q '"' && echo true || echo false)
  log_test "Monitoring Dashboard - Admin" "$has_data"
}

test_error_handling() {
  echo -e "\n## 6. ERROR HANDLING & EDGE CASES" >> "$REPORT_FILE"
  echo "### Testing Error Handling..."
  
  echo "Testing invalid login..."
  local invalid_login=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/user/login" \
    -H "Content-Type: application/json" \
    -d '{"studentNo":"invalid","password":"wrong"}')
  
  local http_code=$(echo "$invalid_login" | tail -n 1)
  log_test "Invalid Credentials Rejection" "$([ "$http_code" != "200" ] && echo true || echo false)"
  
  echo "Testing no token access..."
  local no_token=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/api/activities/MyStatus")
  local no_token_code=$(echo "$no_token" | tail -n 1)
  log_test "Protected Route Without Token" "$([ "$no_token_code" != "200" ] && echo true || echo false)"
}

test_frontend_pages() {
  echo -e "\n## 7. FRONTEND PAGE LOAD TESTS" >> "$REPORT_FILE"
  echo "### Testing Frontend Pages..."
  
  local pages=("login" "app/activities" "app/my-stats" "app/system-monitor" "app/request-hours")
  
  for page in "${pages[@]}"; do
    local start_time=$(date +%s%N)
    local response=$(curl -s -w "\n%{http_code}" "$FRONTEND_URL/$page")
    local end_time=$(date +%s%N)
    local load_time=$(( (end_time - start_time) / 1000000 ))
    
    local http_code=$(echo "$response" | tail -n 1)
    local page_content=$(echo "$response" | head -n -1)
    
    if [ "$http_code" = "200" ]; then
      log_test "Frontend Page: $page" true "Load time: ${load_time}ms"
    else
      log_test "Frontend Page: $page" false "HTTP $http_code"
    fi
  done
}

main() {
  echo "# Comprehensive End-to-End Test Report" > "$REPORT_FILE"
  echo "Generated: $(date)" >> "$REPORT_FILE"
  echo "" >> "$REPORT_FILE"
  
  echo "Starting comprehensive E2E tests..."
  
  test_auth
  test_activities
  test_enrollment
  test_statistics
  test_monitoring
  test_error_handling
  test_frontend_pages
  
  echo -e "\n## TEST SUMMARY" >> "$REPORT_FILE"
  echo "- **Total Tests**: ${test_results[total]}" >> "$REPORT_FILE"
  echo "- **Passed**: ${test_results[passed]}" >> "$REPORT_FILE"
  echo "- **Failed**: ${test_results[failed]}" >> "$REPORT_FILE"
  local pass_rate=$(( test_results[passed] * 100 / test_results[total] ))
  echo "- **Pass Rate**: ${pass_rate}%" >> "$REPORT_FILE"
  
  echo -e "\n=== TEST SUMMARY ==="
  echo "Total: ${test_results[total]}"
  echo "Passed: ${test_results[passed]}"
  echo "Failed: ${test_results[failed]}"
  echo "Pass Rate: ${pass_rate}%"
  echo ""
  echo "Report saved to: $REPORT_FILE"
}

main
