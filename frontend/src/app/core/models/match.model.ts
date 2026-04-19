export type MatchStatus = 'WAITING' | 'SETUP' | 'ACTIVE' | 'FINISHED';
export type MatchResult = 'PLAYER1_WIN' | 'PLAYER2_WIN' | 'DRAW' | 'ABANDONED';

export interface Match {
  id: number;
  player1Id: number;
  player2Id: number | null;
  player1DeckId: number;
  player2DeckId: number | null;
  status: MatchStatus;
  result: MatchResult | null;
  winnerId: number | null;
  startedAt: string | null;
  finishedAt: string | null;
}
