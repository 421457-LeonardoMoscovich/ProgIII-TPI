export type GameEventType =
  | 'MatchStarted'
  | 'CardDrawn'
  | 'PokemonPlayed'
  | 'PokemonEvolved'
  | 'EnergyAttached'
  | 'TrainerPlayed'
  | 'PokemonRetreated'
  | 'AttackDeclared'
  | 'DamageDealt'
  | 'StatusApplied'
  | 'PokemonKnockedOut'
  | 'PrizeTaken'
  | 'TurnEnded'
  | 'MatchFinished';

export interface GameEventDto {
  type: GameEventType;
  payload: Record<string, unknown>;
  sequence: number;
}

export interface MatchStartedPayload {
  matchId: string;
  player1Id: number;
  player2Id: number;
  firstPlayerId: number;
}

export interface CardDrawnPayload {
  matchId: string;
  userId: number;
  cardId: string | null;
}

export interface PokemonPlayedPayload {
  matchId: string;
  userId: number;
  cardId: string;
  toBench: boolean;
}

export interface EnergyAttachedPayload {
  matchId: string;
  userId: number;
  energyCardId: string;
  targetCardId: string;
}

export interface AttackDeclaredPayload {
  matchId: string;
  attackerId: number;
  attackName: string;
}

export interface DamageDealtPayload {
  matchId: string;
  attackerCardId: string;
  defenderCardId: string;
  amount: number;
}

export interface StatusAppliedPayload {
  matchId: string;
  targetCardId: string;
  status: string;
}

export interface PokemonKnockedOutPayload {
  matchId: string;
  ownerUserId: number;
  cardId: string;
}

export interface PrizeTakenPayload {
  matchId: string;
  userId: number;
  prizesRemaining: number;
}

export interface TurnEndedPayload {
  matchId: string;
  userId: number;
  globalTurn: number;
}

export interface MatchFinishedPayload {
  matchId: string;
  winnerUserId: number;
  reason: string;
}
