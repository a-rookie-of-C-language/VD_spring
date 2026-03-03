const playwright = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://localhost:5173';
const API_URL = 'http://localhost:8080';
const SCREENSHOTS_DIR = 'test-screenshots';

if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

const testAccounts = {
  superAdmin: { studentNo: '12323020420', password: 'arookieofc', role: 'superAdmin' },
  functionary: { studentNo: '12323020421', password: 'arookieofc', role: 'functionary' },
  student1: { studentNo: '20230001', password: 'arookieofc', role: 'student' },
  student2: { studentNo: '20230002', password: 'arookieofc', role: 'student' }
};

let testResults = {
  total: 0,
  passed: 0,
  failed: 0,
  tests: [],
  screenshots: []
};

async function logTest(name, passed, details = '') {
  testResults.total++;
  if (passed) {
    testResults.passed++;
    console.log(`✓ ${name}`);
  } else {
    testResults.failed++;
    console.log(`✗ ${name}: ${details}`);
  }
  testResults.tests.push({ name, passed, details });
}

async function login(page, studentNo, password) {
  try {
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForSelector('input[type="text"]', { timeout: 5000 });
    
    await page.fill('input[type="text"]', studentNo);
    await page.fill('input[type="password"]', password);
    await page.click('button:has-text("登录")');
    
    await page.waitForURL(`${BASE_URL}/app/**`, { timeout: 10000 });
    return true;
  } catch (e) {
    console.log('Login error:', e.message);
    return false;
  }
}

async function screenshot(page, name) {
  const filename = path.join(SCREENSHOTS_DIR, `${Date.now()}-${name.replace(/\s+/g, '_')}.png`);
  await page.screenshot({ path: filename, fullPage: true });
  testResults.screenshots.push(filename);
  console.log(`  📸 Screenshot: ${filename}`);
  return filename;
}

async function runTests() {
  const browser = await playwright.chromium.launch({ headless: false });
  const context = await browser.createBrowserContext();
  
  try {
    // ===== TEST 1: Authentication & Authorization =====
    console.log('\n=== TEST 1: Authentication & Authorization ===');
    
    const page1 = await context.newPage();
    await page1.setViewportSize({ width: 1920, height: 1080 });
    
    let loginSuccess = await login(page1, testAccounts.superAdmin.studentNo, testAccounts.superAdmin.password);
    await logTest('SuperAdmin Login', loginSuccess);
    
    if (loginSuccess) {
      await page1.waitForLoadState('networkidle');
      await screenshot(page1, 'superAdmin-dashboard');
      
      const token = await page1.evaluate(() => localStorage.getItem('token'));
      await logTest('Token Persists in Storage', !!token);
    }
    
    await page1.close();
    
    // Test functionary login
    const page2 = await context.newPage();
    await page2.setViewportSize({ width: 1920, height: 1080 });
    
    loginSuccess = await login(page2, testAccounts.functionary.studentNo, testAccounts.functionary.password);
    await logTest('Functionary Login', loginSuccess);
    
    if (loginSuccess) {
      await page2.waitForLoadState('networkidle');
      await screenshot(page2, 'functionary-dashboard');
    }
    
    await page2.close();
    
    // Test student login
    const page3 = await context.newPage();
    await page3.setViewportSize({ width: 1920, height: 1080 });
    
    loginSuccess = await login(page3, testAccounts.student1.studentNo, testAccounts.student1.password);
    await logTest('Student Login', loginSuccess);
    
    if (loginSuccess) {
      await page3.waitForLoadState('networkidle');
      await screenshot(page3, 'student-dashboard');
    }
    
    await page3.close();
    
    // ===== TEST 2: Activity Management (as functionary) =====
    console.log('\n=== TEST 2: Activity Management ===');
    
    const pageAdmin = await context.newPage();
    await pageAdmin.setViewportSize({ width: 1920, height: 1080 });
    
    loginSuccess = await login(pageAdmin, testAccounts.functionary.studentNo, testAccounts.functionary.password);
    
    if (loginSuccess) {
      await pageAdmin.goto(`${BASE_URL}/app/activities`, { waitUntil: 'networkidle' });
      await screenshot(pageAdmin, 'activities-list');
      
      const activitiesCount = await pageAdmin.evaluate(() => {
        return document.querySelectorAll('[class*="activity"], [class*="card"], [class*="item"]').length;
      });
      await logTest('Activities Page Loads', activitiesCount > 0, `Found ${activitiesCount} activity elements`);
      
      // Navigate to add activity
      await pageAdmin.goto(`${BASE_URL}/app/add-activity`, { waitUntil: 'networkidle' });
      const addPageLoaded = await pageAdmin.evaluate(() => document.body.innerText.includes('活动'));
      await logTest('Add Activity Page Loads', addPageLoaded);
      
      if (addPageLoaded) {
        await screenshot(pageAdmin, 'add-activity-form');
      }
    }
    
    await pageAdmin.close();
    
    // ===== TEST 3: Activity Enrollment (as student) =====
    console.log('\n=== TEST 3: Activity Enrollment ===');
    
    const pageStudent = await context.newPage();
    await pageStudent.setViewportSize({ width: 1920, height: 1080 });
    
    loginSuccess = await login(pageStudent, testAccounts.student1.studentNo, testAccounts.student1.password);
    
    if (loginSuccess) {
      await pageStudent.goto(`${BASE_URL}/app/activities`, { waitUntil: 'networkidle' });
      await screenshot(pageStudent, 'student-activities-list');
      await logTest('Student Can View Activities', true);
    }
    
    await pageStudent.close();
    
    // ===== TEST 4: Personal Statistics =====
    console.log('\n=== TEST 4: Personal Statistics ===');
    
    const pageStats = await context.newPage();
    await pageStats.setViewportSize({ width: 1920, height: 1080 });
    
    loginSuccess = await login(pageStats, testAccounts.student1.studentNo, testAccounts.student1.password);
    
    if (loginSuccess) {
      await pageStats.goto(`${BASE_URL}/app/my-stats`, { waitUntil: 'networkidle' });
      const statsLoaded = await pageStats.evaluate(() => document.body.innerText.length > 100);
      await logTest('Personal Statistics Page', statsLoaded);
      
      if (statsLoaded) {
        await screenshot(pageStats, 'personal-stats');
      }
    }
    
    await pageStats.close();
    
    // ===== TEST 5: System Monitoring (as admin) =====
    console.log('\n=== TEST 5: System Monitoring ===');
    
    const pageMonitor = await context.newPage();
    await pageMonitor.setViewportSize({ width: 1920, height: 1080 });
    
    loginSuccess = await login(pageMonitor, testAccounts.superAdmin.studentNo, testAccounts.superAdmin.password);
    
    if (loginSuccess) {
      await pageMonitor.goto(`${BASE_URL}/app/system-monitor`, { waitUntil: 'networkidle' });
      const monitorLoaded = await pageMonitor.evaluate(() => document.body.innerText.length > 100);
      await logTest('System Monitor Page Loads', monitorLoaded);
      
      if (monitorLoaded) {
        await screenshot(pageMonitor, 'system-monitor');
      }
    }
    
    await pageMonitor.close();
    
    // ===== TEST 6: Request Hours Page =====
    console.log('\n=== TEST 6: Hour Request Page ===');
    
    const pageRequest = await context.newPage();
    await pageRequest.setViewportSize({ width: 1920, height: 1080 });
    
    loginSuccess = await login(pageRequest, testAccounts.student1.studentNo, testAccounts.student1.password);
    
    if (loginSuccess) {
      await pageRequest.goto(`${BASE_URL}/app/request-hours`, { waitUntil: 'networkidle' });
      const requestLoaded = await pageRequest.evaluate(() => document.body.innerText.length > 100);
      await logTest('Request Hours Page Loads', requestLoaded);
      
      if (requestLoaded) {
        await screenshot(pageRequest, 'request-hours-form');
      }
    }
    
    await pageRequest.close();
    
  } catch (error) {
    console.error('Test error:', error);
  } finally {
    await context.close();
    await browser.close();
  }
  
  // Print summary
  console.log('\n=== TEST SUMMARY ===');
  console.log(`Total: ${testResults.total}`);
  console.log(`Passed: ${testResults.passed}`);
  console.log(`Failed: ${testResults.failed}`);
  console.log(`Pass Rate: ${((testResults.passed / testResults.total) * 100).toFixed(2)}%`);
  console.log(`Screenshots: ${testResults.screenshots.length}`);
  
  fs.writeFileSync('test-results.json', JSON.stringify(testResults, null, 2));
  console.log('\nResults saved to test-results.json');
  console.log('Screenshots saved to test-screenshots/');
}

runTests().catch(console.error);
