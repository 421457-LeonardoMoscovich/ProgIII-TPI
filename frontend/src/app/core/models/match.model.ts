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

// Used by GET /api/matches (lobby list)
export interface MatchSummary {
  id: number;
  status: MatchStatus;
  player1Username: string;
  player2Username: string | null;
  createdAt: string;
  turnNumber: number;
}

// A single Pokémon on the field (filtered DTO from server)
export interface FieldPokemon {
  id: string;
  name: string;
  hp: number;
  maxHp: number;
  damage: number;
  retreatCost: number;
  energies: Record<string, number>;
  tools: string[];
  statusCondition: string | null;
  attacks: Array<{ name: string; cost: string[]; damage: string; text: string }>;
}

// Filtered state DTO from GET /api/matches/{id}/state
export interface FilteredGameStateDto {
  matchId: number;
  myPlayerId: number;
  turnNumber: number;
  currentPlayerId: number;
  phase: 'DRAW' | 'MAIN' | 'ATTACK' | 'BETWEEN_TURNS';
  myPrizesLeft: number;
  opponentPrizesLeft: number;
  myHandCount: number;
  opponentHandCount: number;
  myDeckCount: number;
  opponentDeckCount: number;
  myDiscardCount: number;
  opponentDiscardCount: number;
  energyAttachedThisTurn: boolean;
  retreatedThisTurn: boolean;
  attackDoneThisTurn: boolean;
  supporterPlayedThisTurn: boolean;
  myActive: FieldPokemon | null;
  opponentActive: FieldPokemon | null;
  myBench: FieldPokemon[];
  opponentBench: FieldPokemon[];
  myHand: Array<{ id: string; name: string; type: string; supertype: string }>;
  winner: string | null;
}

export interface MatchReconnectDto {
  snapshot: FilteredGameStateDto;
  recentEvents: Array<{
    type: string;
    payload: Record<string, unknown>;
    sequence: number;
  }>;
  currentSequence: number;
}
