import { test, expect, request, Browser, BrowserContext, Page } from '@playwright/test';

type AuthResponse = { token: string; username: string };
type CardDto = { id: string; supertype: string; subtypes: string[] };
type DeckDto = { id: number; name: string; isValid: boolean };
type MatchDto = { id: number };

const API_BASE = 'http://localhost:8081/';

test.describe.configure({ mode: 'serial' });

async function apiRegister(username: string, email: string, password: string): Promise<AuthResponse> {
  const api = await request.newContext({ baseURL: API_BASE });
  const response = await api.post('api/auth/register', {
    data: { username, email, password },
  });
  if (!response.ok()) {
    throw new Error(`register failed: ${response.status()} ${await response.text()}`);
  }
  return await response.json();
}

async function apiCards(token: string): Promise<CardDto[]> {
  const api = await request.newContext({
    baseURL: API_BASE,
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
  const response = await api.get('api/cards?set=xy1');
  expect(response.ok()).toBeTruthy();
  return await response.json();
}

async function apiCreateValidDeck(token: string, name: string, basicPokemonId: string, basicEnergyId: string): Promise<DeckDto> {
  const api = await request.newContext({
    baseURL: API_BASE,
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });

  const createResponse = await api.post(`api/decks?name=${encodeURIComponent(name)}`);
  expect(createResponse.ok()).toBeTruthy();
  const deck = await createResponse.json() as DeckDto;

  const updateResponse = await api.put(`api/decks/${deck.id}/cards`, {
    data: {
      [basicPokemonId]: 4,
      [basicEnergyId]: 56,
    },
  });
  expect(updateResponse.ok()).toBeTruthy();

  const validateResponse = await api.post(`api/decks/${deck.id}/validate`);
  expect(validateResponse.ok()).toBeTruthy();
  const validation = await validateResponse.json() as { valid: boolean };
  expect(validation.valid).toBeTruthy();

  return deck;
}

async function apiCreateMatch(token: string, deckId: number): Promise<MatchDto> {
  const api = await request.newContext({
    baseURL: API_BASE,
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
  const response = await api.post('api/matches', {
    data: { deckId },
  });
  expect(response.ok()).toBeTruthy();
  return await response.json();
}

async function apiJoinMatch(token: string, matchId: number, deckId: number): Promise<void> {
  const api = await request.newContext({
    baseURL: API_BASE,
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
  const response = await api.post(`api/matches/${matchId}/join`, {
    data: { deckId },
  });
  expect(response.ok()).toBeTruthy();
}

async function sessionContext(browser: Browser): Promise<BrowserContext> {
  return await browser.newContext();
}

async function login(page: Page, baseURL: string, username: string, password: string): Promise<void> {
  await page.goto(`${baseURL}/login`);
  await page.getByTestId('login-username').fill(username);
  await page.locator('input[type="password"]').fill(password);
  await expect(page.getByTestId('login-submit')).toBeEnabled();
  await Promise.all([
    page.waitForURL(url => url.pathname.endsWith('/decks')),
    page.getByTestId('login-submit').click(),
  ]);
  await page.waitForLoadState('networkidle');
}

async function openMatch(page: Page, baseURL: string, matchId: number): Promise<void> {
  await page.goto(`${baseURL}/match/${matchId}`);
  await expect(page.getByTestId('match-board')).toBeVisible();
  await expect(page.getByTestId('connection-pill')).toContainText('connected');
}

async function canAct(page: Page): Promise<boolean> {
  return await page.getByTestId('pass-turn-btn').isEnabled();
}

async function playBasicToActive(page: Page): Promise<void> {
  const basicPokemonCard = page.locator('[data-testid="hand-card"][data-card-supertype*="Pok"]').first();
  await expect(basicPokemonCard).toBeVisible();
  await basicPokemonCard.click();
  await page.getByRole('button', { name: 'Usar carta' }).click();
  await expect(page.getByTestId('my-active-card')).toBeVisible();
}

async function attachEnergyToActive(page: Page): Promise<void> {
  const energyCard = page.locator('[data-testid="hand-card"][data-card-supertype="Energy"]').first();
  await expect(energyCard).toBeVisible();
  await energyCard.click();
  await page.getByRole('button', { name: 'Usar carta' }).click();
}

test('sincroniza acciones realtime y recupera estado despues de reconectar', async ({ browser, baseURL }) => {
  const suffix = Date.now();
  const password = 'pass1234!';
  const player1 = await apiRegister(`qa_p1_${suffix}`, `qa_p1_${suffix}@test.com`, password);
  const player2 = await apiRegister(`qa_p2_${suffix}`, `qa_p2_${suffix}@test.com`, password);

  const cards = await apiCards(player1.token);
  const basicPokemonId = cards.find(card => card.supertype.includes('Pok') && card.subtypes.includes('Basic'))?.id;
  const basicEnergyId = cards.find(card => card.supertype === 'Energy' && card.subtypes.includes('Basic'))?.id;

  expect(basicPokemonId).toBeTruthy();
  expect(basicEnergyId).toBeTruthy();

  const deck1 = await apiCreateValidDeck(player1.token, `QA Deck 1 ${suffix}`, basicPokemonId!, basicEnergyId!);
  const deck2 = await apiCreateValidDeck(player2.token, `QA Deck 2 ${suffix}`, basicPokemonId!, basicEnergyId!);
  const match = await apiCreateMatch(player1.token, deck1.id);
  await apiJoinMatch(player2.token, match.id, deck2.id);

  const player1Context = await sessionContext(browser);
  const player2Context = await sessionContext(browser);
  const page1 = await player1Context.newPage();
  const page2 = await player2Context.newPage();

  await login(page1, baseURL!, player1.username, password);
  await login(page2, baseURL!, player2.username, password);
  await openMatch(page1, baseURL!, match.id);
  await openMatch(page2, baseURL!, match.id);

  const page1Acts = await canAct(page1);
  const actorPage = page1Acts ? page1 : page2;
  const watcherPage = page1Acts ? page2 : page1;
  const watcherContext = page1Acts ? player2Context : player1Context;

  await playBasicToActive(actorPage);
  await expect(watcherPage.getByTestId('opponent-active-card')).toBeVisible();

  await attachEnergyToActive(actorPage);
  await expect(actorPage.getByTestId('action-log-line').last()).toContainText('Energia unida');
  await expect(watcherPage.getByTestId('action-log-line').last()).toContainText('Energia unida');

  await watcherPage.close();
  await actorPage.getByTestId('pass-turn-btn').click();

  const reopenedWatcherPage = await watcherContext.newPage();
  await openMatch(reopenedWatcherPage, baseURL!, match.id);
  await expect(reopenedWatcherPage.getByTestId('turn-pill')).toContainText('Tu turno');
  await expect(reopenedWatcherPage.getByTestId('phase-badge')).toContainText('Turno');

  await player1Context.close();
  await player2Context.close();
});
