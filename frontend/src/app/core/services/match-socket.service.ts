import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GameActionDto } from '../models/game-action.model';
import { GameEventDto } from '../models/game-event.model';

export interface AckDto {
  ok: boolean;
  reason: string | null;
  sequence: number | null;
}

export interface ChatMessageDto {
  sender: string;
  content: string;
  timestamp: number;
}

export type MatchConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

@Injectable({ providedIn: 'root' })
export class MatchSocketService implements OnDestroy {
  private client: Client | null = null;
  private readonly eventsSubject = new Subject<GameEventDto>();
  private readonly ackSubject = new Subject<AckDto>();
  private readonly chatSubject = new Subject<ChatMessageDto>();
  private readonly connectionStateSubject = new BehaviorSubject<MatchConnectionState>('disconnected');
  private readonly subscriptions: StompSubscription[] = [];

  readonly events$: Observable<GameEventDto> = this.eventsSubject.asObservable();
  readonly acks$: Observable<AckDto> = this.ackSubject.asObservable();
  readonly chatMessages$: Observable<ChatMessageDto> = this.chatSubject.asObservable();
  readonly connectionState$ = this.connectionStateSubject.asObservable();

  connect(matchId: string, token: string, userId: number): void {
    if (this.client?.active) {
      this.disconnect();
    }

    this.connectionStateSubject.next('connecting');
    this.client = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl),
      reconnectDelay: 3_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      connectHeaders: { Authorization: `Bearer ${token}` },
      debug: () => undefined,
      onConnect: () => {
        this.connectionStateSubject.next('connected');
        this.subscribe(matchId, userId);
      },
      onDisconnect: () => this.connectionStateSubject.next('disconnected'),
      onStompError: (frame) => console.error(
        '[match-ws] broker error',
        JSON.stringify({ headers: frame.headers, body: frame.body }),
      ),
      onWebSocketClose: () => {
        if (this.client?.active) {
          this.connectionStateSubject.next('reconnecting');
          return;
        }
        this.connectionStateSubject.next('disconnected');
      },
    });

    this.client.activate();
  }

  sendAction(matchId: string, action: GameActionDto): void {
    this.client?.publish({
      destination: `/app/match/${matchId}/action`,
      body: JSON.stringify(action),
    });
  }

  sendChatMessage(matchId: string, content: string): void {
    this.client?.publish({
      destination: `/app/match/${matchId}/chat`,
      body: JSON.stringify({ content }),
    });
  }

  disconnect(): void {
    while (this.subscriptions.length > 0) {
      this.subscriptions.pop()?.unsubscribe();
    }
    this.client?.deactivate();
    this.client = null;
    this.connectionStateSubject.next('disconnected');
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.eventsSubject.complete();
    this.ackSubject.complete();
    this.chatSubject.complete();
    this.connectionStateSubject.complete();
  }

  private subscribe(matchId: string, userId: number): void {
    if (!this.client) {
      return;
    }

    while (this.subscriptions.length > 0) {
      this.subscriptions.pop()?.unsubscribe();
    }

    this.subscriptions.push(
      this.client.subscribe(`/topic/match/${matchId}/${userId}`, (message: IMessage) => {
        this.eventsSubject.next(JSON.parse(message.body) as GameEventDto);
      }),
      this.client.subscribe('/user/queue/ack', (message: IMessage) => {
        this.ackSubject.next(JSON.parse(message.body) as AckDto);
      }),
      this.client.subscribe(`/topic/match/${matchId}/chat`, (message: IMessage) => {
        this.chatSubject.next(JSON.parse(message.body) as ChatMessageDto);
      }),
    );
  }
}
