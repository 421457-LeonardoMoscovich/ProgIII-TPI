import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: {
    timeout: 15_000,
  },
  use: {
    baseURL: 'http://localhost:4300',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  webServer: [
    {
      command: '.\\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=test" "-Dspring-boot.run.useTestClasspath=true"',
      cwd: '../backend',
      url: 'http://localhost:8081/actuator/health',
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: 'npm start -- --host localhost --port 4300',
      cwd: '.',
      url: 'http://localhost:4300',
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
});
