import { expect, request, test } from '@playwright/test';

/**
 * End-to-end smoke path: login -> register a device -> acknowledge an incident.
 *
 * The incident is not produced by waiting on the correlation engine (slow and
 * non-deterministic in a smoke test); it is opened directly through the API
 * with a freshly issued token, exactly the way an external caller with a
 * session would. The console then has to surface that same incident and let
 * an authorised operator move it through its first lifecycle transition.
 */

const API_BASE_URL = 'http://localhost:8080';
const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'changeme-on-first-login';

test('login, register a device, acknowledge an incident', async ({ page }) => {
  const runId = Date.now();

  // ---------------------------------------------------------------- setup
  // Obtain a token and open an OPEN incident out-of-band, independent of the
  // UI under test.
  const api = await request.newContext({ baseURL: API_BASE_URL });
  const loginResponse = await api.post('/api/v1/auth/login', {
    data: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD },
  });
  expect(loginResponse.ok(), await loginResponse.text()).toBeTruthy();
  const { accessToken } = (await loginResponse.json()) as { accessToken: string };

  const incidentTitle = `E2E Smoke Incident ${runId}`;
  const createIncidentResponse = await api.post('/api/v1/incidents', {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      title: incidentTitle,
      description: 'Opened by the Playwright smoke test to exercise the acknowledge transition.',
      severity: 'HIGH',
    },
  });
  expect(createIncidentResponse.ok(), await createIncidentResponse.text()).toBeTruthy();
  const createdIncident = (await createIncidentResponse.json()) as { status: string };
  expect(createdIncident.status).toBe('OPEN');
  await api.dispose();

  // ------------------------------------------------------------- 1. login
  await page.goto('/login');
  await page.getByLabel('Kullanıcı adı').fill(ADMIN_USERNAME);
  await page.getByLabel('Parola').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'OTURUM AÇ' }).click();

  await expect(page).toHaveURL('http://localhost:4200/');
  await expect(page.locator('.session-user')).toHaveText(ADMIN_USERNAME);

  // ------------------------------------------------------- 2. add a device
  const deviceName = `E2E-Device-${runId}`;
  const deviceIp = '10.42.7.99';

  await page.locator('a.nav-item[href="#view-devices"]').click();
  await page.getByLabel('Cihaz adı').fill(deviceName);
  await page.getByLabel('IP adresi').fill(deviceIp);
  await page.getByLabel('Cihaz tipi').selectOption('SERVER');
  await page.getByRole('button', { name: 'Sisteme Kaydet' }).click();

  const deviceRow = page.locator('tbody tr', { hasText: deviceName });
  await expect(deviceRow).toBeVisible();
  await expect(deviceRow).toContainText(deviceIp);

  // ------------------------------------------- 3. acknowledge the incident
  await page.locator('a.nav-item[href="#view-incidents"]').click();

  const incidentCard = page.locator('.card', { hasText: incidentTitle });
  await expect(incidentCard).toBeVisible();
  await expect(incidentCard.getByText('AÇIK', { exact: true })).toBeVisible();

  await incidentCard.getByRole('button', { name: 'Onayla (Ack)' }).click();

  await expect(incidentCard.getByText('ONAYLANDI', { exact: true })).toBeVisible();
});
