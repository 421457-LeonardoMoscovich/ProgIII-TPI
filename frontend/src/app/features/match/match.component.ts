import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { MatchService } from '../../core/services/match.service';
import { AuthService } from '../../core/services/auth.service';
import { FieldPokemon, FilteredGameStateDto } from '../../core/models/match.model';
import {
  GameActionDto,
  attack,
  attachEnergy,
  evolve,
  pass,
  playBasic,
  playTrainer,
  retreat,
} from '../../core/models/game-action.model';
import { GameEventDto } from '../../core/models/game-event.model';
import { MatchConnectionState, MatchSocketService } from '../../core/services/match-socket.service';
import { ChatComponent } from './chat/chat.component';

type HandCard = FilteredGameStateDto['myHand'][number];
type DropTarget = 'active' | 'bench';
type BoardPokemon = FieldPokemon & {
  zone: 'my-active' | 'my-bench' | 'opponent-active' | 'opponent-bench';
};
type PendingAttack = {
  index: number;
  name: string;
  cost: string[];
  damage: string;
  text: string;
  affordable: boolean;
};
type ToastKind = 'info' | 'success' | 'warning' | 'error';
type Toast = { id: number; message: string; kind: ToastKind };

@Component({
  selector: 'app-match',
  standalone: true,
  imports: [CommonModule, DragDropModule, ChatComponent],
  templateUrl: './match.component.html',
  styleUrl: './match.component.scss',
})
export class MatchComponent implements OnInit {
  private matchService = inject(MatchService);
  private socketService = inject(MatchSocketService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private toastId = 0;

  matchId = signal<number>(0);
  state = signal<FilteredGameStateDto | null>(null);
  loading = signal(true);
  error = signal('');
  connectionState = signal<MatchConnectionState>('disconnected');
  actionLog = signal<string[]>([]);
  selectedCard = signal<HandCard | null>(null);
  selectedPokemon = signal<BoardPokemon | null>(null);
  pendingAttack = signal<PendingAttack | null>(null);
  toasts = signal<Toast[]>([]);
  logCollapsed = signal(false);
  lastEventSequence = signal(0);

  turnLabel = computed(() => (this.canAct() ? 'Tu turno' : 'Turno del rival'));
  matchIdStr = computed(() => String(this.matchId()));
  selectedEnergyTarget = computed(() => {
    const pokemon = this.selectedPokemon();
    if (pokemon?.zone === 'my-active' || pokemon?.zone === 'my-bench') {
      return pokemon;
    }
    return this.state()?.myActive
      ? this.asBoardPokemon(this.state()!.myActive!, 'my-active')
      : null;
  });

  get currentUsername() {
    return this.authService.currentUser();
  }

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.matchId.set(id);

    this.socketService.connectionState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((state) => {
        const previous = this.connectionState();
        this.connectionState.set(state);
        if (previous !== state) {
          this.handleConnectionToast(previous, state);
        }
      });

    this.socketService.events$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((event) => {
      this.lastEventSequence.set(Math.max(this.lastEventSequence(), event.sequence));
      const message = this.describeEvent(event);
      this.appendLog(message);
      this.pushToast(message, this.eventToastKind(event));
      this.handleEventAnimation(event);
      this.refreshState(id);
    });

    this.socketService.acks$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((ack) => {
      if (!ack.ok) {
        this.error.set(ack.reason ?? 'La accion fue rechazada');
        this.pushToast(ack.reason ?? 'Accion rechazada por el servidor', 'error');
        return;
      }
      this.error.set('');
      if (ack.sequence !== null) {
        this.lastEventSequence.set(Math.max(this.lastEventSequence(), ack.sequence));
      }
      this.pushToast('Accion confirmada', 'success');
      this.refreshState(id);
    });

    this.destroyRef.onDestroy(() => this.socketService.disconnect());
    this.loadInitialState(id);
  }

  hpPercent(pokemon: FieldPokemon): number {
    if (pokemon.maxHp === 0) return 0;
    return Math.max(0, Math.min(100, Math.round((pokemon.hp / pokemon.maxHp) * 100)));
  }

  hpClass(pokemon: FieldPokemon): string {
    const pct = this.hpPercent(pokemon);
    if (pct > 50) return 'hp-high';
    if (pct > 25) return 'hp-mid';
    return 'hp-low';
  }

  energySummary(pokemon: FieldPokemon | null): string {
    if (!pokemon) return '0 energias';
    const entries = Object.entries(pokemon.energies ?? {}).filter(([, count]) => count > 0);
    if (entries.length === 0) return '0 energias';
    return entries.map(([type, count]) => `${type} x${count}`).join(' | ');
  }

  energyTotal(pokemon: FieldPokemon | null): number {
    return Object.values(pokemon?.energies ?? {}).reduce((total, count) => total + count, 0);
  }

  damageCounters(pokemon: FieldPokemon | null): number {
    return Math.floor((pokemon?.damage ?? 0) / 10);
  }

  toolSummary(pokemon: FieldPokemon | null): string {
    const tools = pokemon?.tools ?? [];
    return tools.length > 0 ? tools.join(' | ') : 'Sin herramientas';
  }

  cardKind(card: HandCard | null): string {
    if (!card) return '';
    return this.cardSupertype(card);
  }

  isMyTurn(): boolean {
    const s = this.state();
    return s !== null && s.currentPlayerId === s.myPlayerId;
  }

  canAct(): boolean {
    const s = this.state();
    return this.isMyTurn() && !s?.winner && this.connectionState() === 'connected';
  }

  canReconnect(): boolean {
    return !this.loading() && this.connectionState() !== 'connected';
  }

  selectCard(card: HandCard): void {
    this.selectedCard.set(card);
    this.pushToast(`${card.name} seleccionada`, 'info');
  }

  selectPokemon(pokemon: FieldPokemon, zone: BoardPokemon['zone']): void {
    this.selectedPokemon.set(this.asBoardPokemon(pokemon, zone));
  }

  clearSelection(): void {
    this.selectedCard.set(null);
    this.selectedPokemon.set(null);
  }

  playSelectedCard(): void {
    const card = this.selectedCard();
    if (!card || !this.canAct()) return;

    if (this.cardSupertype(card) === 'pokemon') {
      const hasActive = this.state()?.myActive !== null;
      this.sendActionWithToast(
        playBasic(card.id, hasActive),
        hasActive ? 'Pokemon enviado a banca' : 'Pokemon enviado al activo',
      );
      return;
    }

    if (this.cardSupertype(card) === 'energy') {
      const target = this.selectedEnergyTarget();
      if (!target) {
        this.pushToast('Selecciona un Pokemon propio para unir energia', 'warning');
        return;
      }
      this.sendActionWithToast(attachEnergy(card.id, target.id), `Energia unida a ${target.name}`);
      return;
    }

    if (this.cardSupertype(card) === 'trainer') {
      this.sendActionWithToast(playTrainer(card.id), 'Entrenador jugado');
    }
  }

  dropHandCard(card: HandCard, target: DropTarget, targetPokemon?: FieldPokemon): void {
    if (!this.canAct()) return;
    if (this.cardSupertype(card) === 'pokemon') {
      this.sendActionWithToast(
        playBasic(card.id, target === 'bench'),
        target === 'bench' ? 'Pokemon enviado a banca' : 'Pokemon enviado al activo',
      );
      return;
    }
    if (this.cardSupertype(card) === 'energy' && targetPokemon) {
      this.sendActionWithToast(
        attachEnergy(card.id, targetPokemon.id),
        `Energia unida a ${targetPokemon.name}`,
      );
    }
  }

  openAttackConfirm(index: number): void {
    const active = this.state()?.myActive;
    const selected = active?.attacks[index];
    if (this.canAct() && selected) {
      this.pendingAttack.set({
        index,
        name: selected.name,
        cost: selected.cost ?? [],
        damage: selected.damage,
        text: selected.text,
        affordable: this.canPayAttackCost(active, selected.cost ?? []),
      });
    }
  }

  confirmAttack(): void {
    const selected = this.pendingAttack();
    if (!selected || !this.canAct()) return;
    this.sendActionWithToast(attack(selected.index), `Ataque declarado: ${selected.name}`);
    this.pendingAttack.set(null);
  }

  evolveSelected(): void {
    const card = this.selectedCard();
    const target = this.selectedPokemon();
    if (!card || !target || !this.canAct()) return;
    if (
      this.cardSupertype(card) !== 'pokemon' ||
      (target.zone !== 'my-active' && target.zone !== 'my-bench')
    ) {
      this.pushToast('Selecciona una evolucion y un Pokemon propio objetivo', 'warning');
      return;
    }
    this.sendActionWithToast(evolve(card.id, target.id), `Evolucionando a ${target.name}`);
  }

  retreatActive(): void {
    const active = this.state()?.myActive;
    if (!active || !this.canAct()) return;
    if ((this.state()?.myBench.length ?? 0) === 0) {
      this.pushToast('No hay Pokemon en banca para promover', 'warning');
      return;
    }
    if (this.energyTotal(active) < active.retreatCost) {
      this.pushToast(`Faltan energias para retirar: costo ${active.retreatCost}`, 'warning');
      return;
    }
    const discarded = Array.from({ length: active.retreatCost }, (_, index) => `energy-${index}`);
    this.sendActionWithToast(retreat(discarded), 'Pokemon activo retirado');
  }

  cancelAttack(): void {
    this.pendingAttack.set(null);
  }

  passTurn(): void {
    if (this.canAct()) {
      this.sendActionWithToast(pass(), 'Turno finalizado');
    }
  }

  reconnect(): void {
    const state = this.state();
    if (!state) return;
    this.connectSocket(state);
    this.matchService
      .reconnect(this.matchId(), this.lastEventSequence())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reconnectState) => {
          this.state.set(reconnectState.snapshot);
          this.lastEventSequence.set(
            Math.max(this.lastEventSequence(), reconnectState.currentSequence),
          );
          reconnectState.recentEvents.forEach((event) => {
            this.lastEventSequence.set(Math.max(this.lastEventSequence(), event.sequence));
            const message = this.describeEvent(event as GameEventDto);
            this.appendLog(message);
          });
        },
        error: () => this.refreshState(this.matchId()),
      });
    this.pushToast('Reconectando a la partida...', 'info');
  }

  toggleLog(): void {
    this.logCollapsed.update((value) => !value);
  }

  dismissToast(id: number): void {
    this.toasts.update((items) => items.filter((item) => item.id !== id));
  }

  goLobby() {
    this.router.navigate(['/lobby']);
  }

  private handleEventAnimation(event: GameEventDto): void {
    switch (event.type) {
      case 'DamageDealt':
        this.triggerAnimation('pokemon-opponent-active', 'damage-flash', 600);
        break;
      case 'PokemonEvolved':
        this.triggerAnimation('pokemon-mine-active', 'evolving', 500);
        break;
      case 'PokemonKnockedOut':
        this.triggerAnimation('pokemon-opponent-active', 'knocking-out', 750);
        break;
    }
  }

  private triggerAnimation(elementId: string, cssClass: string, durationMs = 700): void {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.classList.add(cssClass);
    window.setTimeout(() => el.classList.remove(cssClass), durationMs);
  }

  private loadInitialState(matchId: number): void {
    this.matchService
      .getState(matchId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (state) => {
          this.state.set(state);
          this.loading.set(false);
          this.connectSocket(state);
        },
        error: () => {
          this.error.set('Error al cargar estado de partida');
          this.loading.set(false);
        },
      });
  }

  private refreshState(matchId: number): void {
    this.matchService
      .getState(matchId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (state) => this.state.set(state),
        error: () => this.error.set('Error al actualizar estado de partida'),
      });
  }

  private connectSocket(state: FilteredGameStateDto): void {
    const token = this.authService.getToken();
    if (!token) {
      this.error.set('Sesion expirada. Volve a iniciar sesion.');
      return;
    }
    this.socketService.connect(String(state.matchId), token, state.myPlayerId);
  }

  private sendActionWithToast(actionDto: GameActionDto, optimisticMessage: string): void {
    this.socketService.sendAction(String(this.matchId()), actionDto);
    this.selectedCard.set(null);
    this.pushToast(optimisticMessage, 'info');
  }

  private canPayAttackCost(pokemon: FieldPokemon, cost: string[]): boolean {
    return this.energyTotal(pokemon) >= cost.length;
  }

  private asBoardPokemon(pokemon: FieldPokemon, zone: BoardPokemon['zone']): BoardPokemon {
    return { ...pokemon, zone };
  }

  private cardSupertype(card: HandCard): 'pokemon' | 'energy' | 'trainer' | 'other' {
    const normalized = card.supertype
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
    if (normalized.startsWith('pok')) return 'pokemon';
    if (normalized === 'energy') return 'energy';
    if (normalized === 'trainer') return 'trainer';
    return 'other';
  }

  private describeEvent(event: GameEventDto): string {
    switch (event.type) {
      case 'MatchStarted':
        return 'Partida iniciada';
      case 'CardDrawn':
        return 'Carta robada';
      case 'PokemonPlayed':
        return 'Pokemon jugado';
      case 'PokemonEvolved':
        return 'Pokemon evolucionado';
      case 'EnergyAttached':
        return 'Energia unida';
      case 'TrainerPlayed':
        return 'Entrenador jugado';
      case 'PokemonRetreated':
        return 'Pokemon retirado';
      case 'AttackDeclared':
        return `Ataque: ${String(event.payload['attackName'] ?? '')}`;
      case 'DamageDealt':
        return `Dano infligido: ${String(event.payload['amount'] ?? '')}`;
      case 'StatusApplied':
        return `Estado aplicado: ${String(event.payload['status'] ?? '')}`;
      case 'PokemonKnockedOut':
        return 'Pokemon fuera de combate';
      case 'PrizeTaken':
        return 'Carta de premio tomada';
      case 'TurnEnded':
        return 'Turno finalizado';
      case 'MatchFinished':
        return 'Partida finalizada';
      default:
        return event.type;
    }
  }

  private eventToastKind(event: GameEventDto): ToastKind {
    if (event.type === 'MatchFinished') return 'success';
    if (event.type === 'PokemonKnockedOut' || event.type === 'DamageDealt') return 'warning';
    return 'info';
  }

  private handleConnectionToast(previous: MatchConnectionState, next: MatchConnectionState): void {
    if (next === 'connected') {
      this.pushToast(
        previous === 'disconnected' ? 'Conexion WebSocket establecida' : 'Reconectado a la partida',
        'success',
      );
    }
    if (next === 'reconnecting') {
      this.pushToast('Conexion perdida. Reintentando...', 'warning');
    }
  }

  private pushToast(message: string, kind: ToastKind): void {
    const toast = { id: ++this.toastId, message, kind };
    this.toasts.update((items) => [...items.slice(-3), toast]);
    window.setTimeout(() => this.dismissToast(toast.id), 4_000);
  }

  private appendLog(message: string): void {
    this.actionLog.update((items) => [...items.slice(-29), message]);
  }
}
