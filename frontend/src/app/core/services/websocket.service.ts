import { Injectable, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Spike-grade STOMP client (Sprint 1 / SPIKE-01).
 * Will be hardened and wrapped in a typed game-events service during Sprint 5.
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client | null = null;
  readonly connected = signal(false);

  connect(): void {
    if (this.client?.active) return;
    this.client = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl),
      reconnectDelay: 2_000,
      debug: (msg) => console.debug('[stomp]', msg),
      onConnect: () => this.connected.set(true),
      onDisconnect: () => this.connected.set(false),
      onStompError: (frame) => console.error('[stomp] broker error', frame),
    });
    this.client.activate();
  }

  disconnect(): void {
    this.client?.deactivate();
    this.connected.set(false);
  }

  subscribe<T>(destination: string): Observable<T> {
    const subject = new Subject<T>();
    let sub: StompSubscription | undefined;
    const tryBind = () => {
      if (this.client?.connected) {
        sub = this.client.subscribe(destination, (msg: IMessage) => {
          subject.next(JSON.parse(msg.body) as T);
        });
      } else {
        setTimeout(tryBind, 200);
      }
    };
    tryBind();
    return new Observable<T>((obs) => {
      const s = subject.subscribe(obs);
      return () => {
        sub?.unsubscribe();
        s.unsubscribe();
      };
    });
  }

  publish(destination: string, body: unknown): void {
    this.client?.publish({ destination, body: JSON.stringify(body) });
  }
}
