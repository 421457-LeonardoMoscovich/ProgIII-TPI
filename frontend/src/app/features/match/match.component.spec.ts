import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { FilteredGameStateDto } from '../../core/models/match.model';
import { MatchService } from '../../core/services/match.service';
import { AuthService } from '../../core/services/auth.service';
import { MatchConnectionState, MatchSocketService } from '../../core/services/match-socket.service';
import { MatchComponent } from './match.component';

const state: FilteredGameStateDto = {
  matchId: 7,
  myPlayerId: 1,
  turnNumber: 3,
  currentPlayerId: 1,
  phase: 'MAIN',
  myPrizesLeft: 6,
  opponentPrizesLeft: 6,
  myHandCount: 2,
  opponentHandCount: 4,
  myDeckCount: 28,
  opponentDeckCount: 30,
  myDiscardCount: 3,
  opponentDiscardCount: 2,
  energyAttachedThisTurn: false,
  retreatedThisTurn: false,
  attackDoneThisTurn: false,
  supporterPlayedThisTurn: false,
  myActive: {
    id: 'active-1',
    name: 'Pikachu',
    hp: 50,
    maxHp: 60,
    damage: 10,
    energies: { Lightning: 1 },
    tools: [],
    retreatCost: 1,
    statusCondition: null,
    attacks: [{ name: 'Impactrueno', cost: ['Lightning'], damage: '30', text: '' }],
  },
  opponentActive: {
    id: 'opp-1',
    name: 'Squirtle',
    hp: 60,
    maxHp: 60,
    damage: 0,
    energies: {},
    tools: [],
    retreatCost: 1,
    statusCondition: null,
    attacks: [],
  },
  myBench: [],
  opponentBench: [],
  myHand: [
    { id: 'energy-1', name: 'Energia Rayo', type: 'Lightning', supertype: 'Energy' },
    { id: 'trainer-1', name: 'Pocion', type: 'Item', supertype: 'Trainer' },
  ],
  winner: null,
};

describe('MatchComponent', () => {
  let fixture: ComponentFixture<MatchComponent>;
  let socket: {
    connectionState$: BehaviorSubject<MatchConnectionState>;
    events$: Subject<unknown>;
    acks$: Subject<unknown>;
    connect: ReturnType<typeof vi.fn>;
    disconnect: ReturnType<typeof vi.fn>;
    sendAction: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    socket = {
      connectionState$: new BehaviorSubject<MatchConnectionState>('connected'),
      events$: new Subject<unknown>(),
      acks$: new Subject<unknown>(),
      connect: vi.fn(),
      disconnect: vi.fn(),
      sendAction: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [MatchComponent],
      providers: [
        { provide: MatchService, useValue: { getState: vi.fn(() => of(state)) } },
        { provide: MatchSocketService, useValue: socket },
        { provide: AuthService, useValue: { currentUser: signal('ash'), getToken: vi.fn(() => 'token') } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '7' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MatchComponent);
    fixture.detectChanges();
  });

  it('sends selected energy to the selected own Pokemon', () => {
    const component = fixture.componentInstance;

    component.selectCard(state.myHand[0]);
    component.selectPokemon(state.myActive!, 'my-active');
    component.playSelectedCard();

    expect(socket.sendAction).toHaveBeenCalledWith('7', {
      type: 'ATTACH_ENERGY',
      payload: { energyCardId: 'energy-1', targetInPlayId: 'active-1' },
    });
  });

  it('plays a basic Pokemon into the active slot when dropped and there is no active Pokemon', () => {
    const component = fixture.componentInstance;

    component.state.set({
      ...state,
      myActive: null,
      myHand: [{ id: 'basic-1', name: 'Bulbasaur', type: 'Basic', supertype: 'Pokémon' }],
    });

    component.dropHandCard(component.state()!.myHand[0], 'active');

    expect(socket.sendAction).toHaveBeenCalledWith('7', {
      type: 'PLAY_BASIC',
      payload: { cardId: 'basic-1', toBench: false },
    });
  });

  it('attaches dropped energy to the provided target Pokemon', () => {
    const component = fixture.componentInstance;

    component.dropHandCard(state.myHand[0], 'active', state.myActive!);

    expect(socket.sendAction).toHaveBeenCalledWith('7', {
      type: 'ATTACH_ENERGY',
      payload: { energyCardId: 'energy-1', targetInPlayId: 'active-1' },
    });
  });

  it('requires attack confirmation before sending attack action', () => {
    const component = fixture.componentInstance;

    component.openAttackConfirm(0);
    expect(socket.sendAction).not.toHaveBeenCalled();

    component.confirmAttack();
    expect(socket.sendAction).toHaveBeenCalledWith('7', {
      type: 'ATTACK',
      payload: { attackIndex: 0 },
    });
  });
});
