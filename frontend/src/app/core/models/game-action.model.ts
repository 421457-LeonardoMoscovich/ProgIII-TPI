export type GameActionType =
  | 'PLAY_BASIC'
  | 'ATTACH_ENERGY'
  | 'ATTACK'
  | 'PASS'
  | 'PLAY_TRAINER'
  | 'EVOLVE'
  | 'RETREAT';

export interface GameActionDto {
  type: GameActionType;
  payload: Record<string, unknown>;
}

export function playBasic(cardId: string, toBench: boolean): GameActionDto {
  return { type: 'PLAY_BASIC', payload: { cardId, toBench } };
}

export function attachEnergy(energyCardId: string, targetInPlayId: string): GameActionDto {
  return { type: 'ATTACH_ENERGY', payload: { energyCardId, targetInPlayId } };
}

export function attack(attackIndex: number): GameActionDto {
  return { type: 'ATTACK', payload: { attackIndex } };
}

export function pass(): GameActionDto {
  return { type: 'PASS', payload: {} };
}

export function playTrainer(cardId: string): GameActionDto {
  return { type: 'PLAY_TRAINER', payload: { cardId } };
}

export function evolve(evolutionCardId: string, targetInPlayId: string): GameActionDto {
  return { type: 'EVOLVE', payload: { evolutionCardId, targetInPlayId } };
}

export function retreat(discardedEnergyIds: string[]): GameActionDto {
  return { type: 'RETREAT', payload: { discardedEnergyIds } };
}
