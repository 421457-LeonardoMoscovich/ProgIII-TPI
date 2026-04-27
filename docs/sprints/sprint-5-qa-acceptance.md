# Sprint 5 QA Acceptance Checklist

Scope: two-player realtime match flow, hidden opponent hand, and reconnect behavior.

## Automated Coverage

- Backend: `MatchControllerIntegrationTest` verifies create/join starts a session and each player receives a filtered state view.
- Backend: the filtered state response exposes `myHand` only, never `opponentHand`; opponent hand is represented by `opponentHandCount`.
- Backend: `GameEventPublisherTest` verifies private per-player topics and masks `CardDrawn.cardId` for the opponent.
- Frontend: `MatchSocketService` unit tests verify JWT connect headers, private topic subscription, ACK handling, action publish destination, reconnect cleanup, heartbeat, and reconnecting state on active socket close.

## Manual Two-Browser Acceptance

1. Start backend and frontend with the documented dev commands.
2. Browser A: log in as player 1, create or select a valid deck, create a match from the lobby.
3. Browser B: log in as player 2, create or select a valid deck, join the waiting match.
4. Confirm both browsers navigate to the same match and show `WS connected`.
5. Confirm Browser A only shows player 1 hand cards and Browser B only shows player 2 hand cards.
6. Confirm opponent UI shows only hand count, prizes count, deck count, active Pokemon, and bench cards.
7. Browser A: play a basic Pokemon or pass turn; confirm Browser B receives the update without page refresh.
8. Browser B: submit an invalid out-of-turn action if possible; confirm the UI shows a rejection and state remains consistent.
9. Close or hard-refresh Browser B during the match, then reopen the match URL.
10. Confirm Browser B reloads the latest REST snapshot, reconnects WebSocket, and continues receiving later actions.
11. Repeat one action after reconnect and confirm Browser A and Browser B converge to the same turn number, phase, active/bench state, and hand counts.

## Known Sprint 5 QA Boundary

- No Cypress or Playwright dependency was added for Sprint 5. Current acceptance coverage is a mix of backend integration tests, frontend service unit tests, and this manual two-browser checklist.
- Reconnect acceptance should cover both paths currently present in the codebase: page reload/manual reconnect through the REST snapshot and transient socket loss through STOMP auto-reconnect. Backend reconnect snapshot plus masked recent events are covered separately by controller/publisher tests.
