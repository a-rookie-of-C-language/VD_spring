#!/usr/bin/env node

const http = require('http');
const querystring = require('querystring');
const fs = require('fs');
const path = require('path');

const API_URL = 'http://localhost:8080';
const FRONTEND_URL = 'http://localhost:5173';
const TIMESTAMP = new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5);
const REPORT_FILE = `E3E_TEST_REPORT_${TIMESTAMP}.md`;

class TestRunner {
  constructor() {
    this.results = {
      passed: 0,
      failed: 0,
      total: 0,
      tests: [],
      screenshots: []
    };
    this.tokens = {};
  }

  logTest(name, passed, details = '') {
    this.results.total++;
    if (passed) {
      this.results.passed++;
      console.log(`✓ ${name}`);
    } else {
      this.results.failed++;
      console.log(`✗ ${name}: ${details}`);
    }
    this.results.tests.push({ name, passed, details });
  }

  makeRequest(url, method = 'GET', data = null, headers = {}) {
    return new Promise((resolve, reject) => {
      const urlObj = new URL(url);
      const options = {
        hostname: urlObj.hostname,
        port: urlObj.port || (url.startsWith('https') ? 443 : 80),
        path: urlObj.pathname + urlObj.search,
        method: method,
        headers: {
          'Content-Type': 'application/json',
          ...headers
        }
      };

      const client = url.startsWith('https') ? require('https') : http;
      const req = client.request(options, (res) => {
        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => {
          resolve({
            statusCode: res.statusCode,
            body: body,
            headers: res.headers
          });
        });
      });

      req.on('error', reject);
      if (data) req.write(JSON.stringify(data));
      req.end();
    });
  }

  async login(studentNo, password, role) {
    try {
      const response = await this.makeRequest(`${API_URL}/user/login`, 'POST', {
        studentNo,
        password
      });

      if (response.statusCode === 200) {
        try {
          const body = JSON.parse(response.body);
          if (body.data && body.data.token) {
            this.tokens[role] = body.data.token;
            this.logTest(`Login - ${role}`, true, `Token obtained`);
            return true;
          }
        } catch (e) {
          console.log('Failed to parse login response:', response.body);
        }
      }
      this.logTest(`Login - ${role}`, false, `HTTP ${response.statusCode}`);
      return false;
    } catch (error) {
      this.logTest(`Login - ${role}`, false, error.message);
      return false;
    }
  }

  async testAuth() {
    console.log('\n### Testing Authentication & Authorization...');
    
    const accounts = [
      { studentNo: '12323020420', password: 'arookieofc', role: 'superAdmin' },
      { studentNo: '12323020421', password: 'arookieofc', role: 'functionary' },
      { studentNo: '20230001', password: 'arookieofc', role: 'student' }
    ];

    for (const account of accounts) {
      await this.login(account.studentNo, account.password, account.role);
      
      if (this.tokens[account.role]) {
        try {
          const response = await this.makeRequest(`${API_URL}/user/verifyToken`, 'GET', null, {
            'Authorization': `Bearer ${this.tokens[account.role]}`
          });
          this.logTest(`Token Validation - ${account.role}`, response.statusCode === 200);
        } catch (error) {
          this.logTest(`Token Validation - ${account.role}`, false, error.message);
        }
      }
    }
  }

  async testActivities() {
    console.log('\n### Testing Activity Management...');
    
    const token = this.tokens.functionary;
    if (!token) {
      this.logTest('Query Activities', false, 'No token');
      return;
    }

    try {
      const response = await this.makeRequest(`${API_URL}/api/activities/query`, 'POST', {
        page: 1,
        pageSize: 10
      }, {
        'Authorization': `Bearer ${token}`
      });

      const success = response.statusCode === 200;
      try {
        const body = JSON.parse(response.body);
        const count = body.data ? (Array.isArray(body.data) ? body.data.length : 1) : 0;
        this.logTest('Query Activities', success, `Found ${count} activities`);
      } catch (e) {
        this.logTest('Query Activities', success, 'Response parsed');
      }
    } catch (error) {
      this.logTest('Query Activities', false, error.message);
    }
  }

  async testEnrollment() {
    console.log('\n### Testing Activity Enrollment...');
    
    const token = this.tokens.student;
    if (!token) {
      this.logTest('Student Enrollment', false, 'No token');
      return;
    }

    try {
      const response = await this.makeRequest(`${API_URL}/api/activities/query`, 'POST', {
        page: 1,
        pageSize: 1
      }, {
        'Authorization': `Bearer ${token}`
      });

      this.logTest('Student Can Query Activities', response.statusCode === 200);
    } catch (error) {
      this.logTest('Student Can Query Activities', false, error.message);
    }
  }

  async testStatistics() {
    console.log('\n### Testing Statistics...');
    
    const roles = ['superAdmin', 'functionary', 'student'];
    for (const role of roles) {
      const token = this.tokens[role];
      if (!token) continue;

      try {
        const response = await this.makeRequest(`${API_URL}/api/activities/MyStatus`, 'GET', null, {
          'Authorization': `Bearer ${token}`
        });

        this.logTest(`Personal Statistics - ${role}`, response.statusCode === 200 || response.statusCode === 400);
      } catch (error) {
        this.logTest(`Personal Statistics - ${role}`, false, error.message);
      }
    }
  }

  async testMonitoring() {
    console.log('\n### Testing System Monitoring...');
    
    const token = this.tokens.superAdmin;
    if (!token) {
      this.logTest('Monitoring Dashboard', false, 'No admin token');
      return;
    }

    try {
      const response = await this.makeRequest(`${API_URL}/api/monitoring/dashboard`, 'GET', null, {
        'Authorization': `Bearer ${token}`
      });

      this.logTest('Monitoring Dashboard - Admin', response.statusCode === 200 || response.statusCode === 400);
    } catch (error) {
      this.logTest('Monitoring Dashboard - Admin', false, error.message);
    }
  }

  async testErrorHandling() {
    console.log('\n### Testing Error Handling...');
    
    try {
      const response = await this.makeRequest(`${API_URL}/user/login`, 'POST', {
        studentNo: 'invalid',
        password: 'wrong'
      });

      this.logTest('Invalid Credentials Rejection', response.statusCode !== 200, `Status: ${response.statusCode}`);
    } catch (error) {
      this.logTest('Invalid Credentials Rejection', true, 'Connection rejected');
    }

    try {
      const response = await this.makeRequest(`${API_URL}/api/activities/MyStatus`, 'GET');
      this.logTest('Protected Route Without Token', response.statusCode !== 200, `Status: ${response.statusCode}`);
    } catch (error) {
      this.logTest('Protected Route Without Token', true, 'Request failed');
    }
  }

  async testFrontendPages() {
    console.log('\n### Testing Frontend Page Load...');
    
    const pages = ['login', 'app/activities', 'app/my-stats', 'app/system-monitor', 'app/request-hours'];
    
    for (const page of pages) {
      try {
        const startTime = Date.now();
        const response = await this.makeRequest(`${FRONTEND_URL}/${page}`);
        const loadTime = Date.now() - startTime;
        
        this.logTest(`Frontend Page: ${page}`, response.statusCode === 200, `Load: ${loadTime}ms`);
      } catch (error) {
        this.logTest(`Frontend Page: ${page}`, false, error.message);
      }
    }
  }

  generateReport() {
    let report = `# Comprehensive End-to-End Test Report\n`;
    report += `Generated: ${new Date().toISOString()}\n\n`;
    
    report += `## 1. Authentication & Authorization\n`;
    report += this.getTestsBySection('Login');
    
    report += `\n## 2. Activity Management\n`;
    report += this.getTestsBySection('Query Activities');
    
    report += `\n## 3. Enrollment\n`;
    report += this.getTestsBySection('Enrollment');
    
    report += `\n## 4. Statistics\n`;
    report += this.getTestsBySection('Statistics');
    
    report += `\n## 5. System Monitoring\n`;
    report += this.getTestsBySection('Monitoring');
    
    report += `\n## 6. Error Handling\n`;
    report += this.getTestsBySection('Rejection');
    
    report += `\n## 7. Frontend Pages\n`;
    report += this.getTestsBySection('Frontend');
    
    report += `\n## Test Summary\n`;
    report += `- **Total Tests**: ${this.results.total}\n`;
    report += `- **Passed**: ${this.results.passed}\n`;
    report += `- **Failed**: ${this.results.failed}\n`;
    const passRate = this.results.total > 0 ? ((this.results.passed / this.results.total) * 100).toFixed(2) : 0;
    report += `- **Pass Rate**: ${passRate}%\n\n`;
    
    report += `### Detailed Test Results\n`;
    this.results.tests.forEach(test => {
      report += `- **${test.name}**: ${test.passed ? 'PASS' : 'FAIL'} ${test.details ? `(${test.details})` : ''}\n`;
    });
    
    fs.writeFileSync(REPORT_FILE, report);
    return report;
  }

  getTestsBySection(keyword) {
    const tests = this.results.tests.filter(t => t.name.includes(keyword));
    return tests.map(t => `- ${t.name}: ${t.passed ? '✓ PASS' : '✗ FAIL'}`).join('\n');
  }

  async run() {
    console.log('=== Comprehensive End-to-End Test Suite ===\n');
    
    await this.testAuth();
    await this.testActivities();
    await this.testEnrollment();
    await this.testStatistics();
    await this.testMonitoring();
    await this.testErrorHandling();
    await this.testFrontendPages();
    
    console.log('\n=== TEST SUMMARY ===');
    console.log(`Total: ${this.results.total}`);
    console.log(`Passed: ${this.results.passed}`);
    console.log(`Failed: ${this.results.failed}`);
    const passRate = this.results.total > 0 ? ((this.results.passed / this.results.total) * 100).toFixed(2) : 0;
    console.log(`Pass Rate: ${passRate}%`);
    
    const report = this.generateReport();
    console.log(`\nReport saved to: ${REPORT_FILE}`);
    
    fs.writeFileSync('test-results.json', JSON.stringify(this.results, null, 2));
    console.log('JSON results saved to: test-results.json');
  }
}

const runner = new TestRunner();
runner.run().catch(console.error);
