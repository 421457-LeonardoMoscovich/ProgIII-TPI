export interface Weakness {
  type: string;
  value: string;
}

export interface Resistance {
  type: string;
  value: string;
}

export interface Attack {
  name: string;
  cost: string[];
  convertedEnergyCost: number;
  damage: string;
  text: string;
}

export type Supertype = 'Pokémon' | 'Trainer' | 'Energy';

export interface Card {
  id: string;
  setCode: string;
  number: string;
  name: string;
  supertype: Supertype;
  subtypes: string[];
  hp: number | null;
  types: string[];
  retreatCost: string[];
  weaknesses: Weakness[] | null;
  resistances: Resistance[] | null;
  attacks: Attack[] | null;
  evolvesFrom: string | null;
  imageSmall: string | null;
  imageLarge: string | null;
}
