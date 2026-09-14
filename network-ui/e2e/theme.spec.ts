import { expect, test } from '@playwright/test';

/**
 * Theme persistence path: login -> Settings -> pick daylight -> the attribute
 * that drives the token overrides is set -> a reload keeps the choice -> back
 * to gunmetal so the suite leaves no state behind for a later run.
 */

const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'changeme-on-first-login';

test('choosing a theme in Settings persists across a reload', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Kullanıcı adı').fill(ADMIN_USERNAME);
  await page.getByLabel('Parola').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'OTURUM AÇ' }).click();
  await expect(page).toHaveURL('http://localhost:4200/');

  await page.locator('a.nav-item[href="#view-settings"]').click();

  const daylightCard = page.locator('.theme-card', { hasText: 'Gündüz' });
  await expect(daylightCard).toBeVisible();
  await daylightCard.click();

  await expect(page.locator('html')).toHaveAttribute('data-theme', 'daylight');
  await expect(daylightCard).toHaveAttribute('aria-checked', 'true');

  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'daylight');

  await page.locator('a.nav-item[href="#view-settings"]').click();
  const gunmetalCard = page.locator('.theme-card', { hasText: 'Gunmetal' });
  await gunmetalCard.click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'gunmetal');
});
