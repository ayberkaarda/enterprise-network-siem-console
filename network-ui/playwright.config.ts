import path from 'node:path';
import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end smoke config for the SIEM console.
 *
 * Both the backend (H2 profile, no external services) and the frontend dev
 * server are started by Playwright itself via `webServer` so the suite is a
 * single command locally. The backend needs JDK 26 specifically, which is
 * not what this machine's default JAVA_HOME points at, so it is overridden
 * only for that one process.
 */
const backendDir = path.resolve(__dirname, '../demo');
const javaHome = 'C:\\Program Files\\Eclipse Adoptium\\jdk-26.0.2.101-hotspot';

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      // Absolute path on purpose: this shell sets NoDefaultCurrentDirectoryInExePath,
      // which disables cmd.exe's fallback search of the working directory for a
      // bare relative filename (measured 2026-09-14 — "mvnw.cmd" alone fails to
      // resolve even with the right cwd).
      command: `"${path.join(backendDir, 'mvnw.cmd')}" spring-boot:run -Dspring-boot.run.profiles=h2`,
      cwd: backendDir,
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: !process.env['CI'],
      timeout: 120_000,
      stdout: 'pipe',
      stderr: 'pipe',
      env: {
        ...process.env,
        JAVA_HOME: javaHome,
        PATH: `${javaHome}\\bin;${process.env['PATH'] ?? ''}`,
      },
    },
    {
      command: 'npm start',
      cwd: __dirname,
      url: 'http://localhost:4200',
      reuseExistingServer: !process.env['CI'],
      timeout: 120_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
});
