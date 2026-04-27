import { Client } from '@stomp/stompjs';
import { MatchSocketService } from './match-socket.service';

vi.mock('@stomp/stompjs', () => {
  class MockClient {
    static instances: MockClient[] = [];

    active = false;
    readonly subscriptions: Array<{
      destination: string;
      callback: (message: { body: string }) => void;
      unsubscribe: ReturnType<typeof vi.fn>;
    }> = [];
    readonly publish = vi.fn();

    constructor(readonly config: Record<string, unknown>) {
      MockClient.instances.push(this);
    }

    activate(): void {
      this.active = true;
    }

    deactivate(): void {
      this.active = false;
      (this.config['onDisconnect'] as (() => void) | undefined)?.();
    }

    subscribe(destination: string, callback: (message: { body: string }) => void) {
      const subscription = { destination, callback, unsubscribe: vi.fn() };
      this.subscriptions.push(subscription);
      return subscription;
    }
  }

  return { Client: MockClient };
});

vi.mock('sockjs-client', () => ({ default: vi.fn(() => ({})) }));

type MockClientInstance = {
  active: boolean;
  config: {
    connectHeaders: Record<string, string>;
    heartbeatIncoming: number;
    heartbeatOutgoing: number;
    reconnectDelay: number;
    onConnect: () => void;
    onWebSocketClose: () => void;
  };
  publish: ReturnType<typeof vi.fn>;
  subscriptions: Array<{
    destination: string;
    callback: (message: { body: string }) => void;
    unsubscribe: ReturnType<typeof vi.fn>;
  }>;
};

function clientInstances(): MockClientInstance[] {
  return (Client as unknown as { instances: MockClientInstance[] }).instances;
}

describe('MatchSocketService', () => {
  beforeEach(() => {
    clientInstances().length = 0;
    vi.clearAllMocks();
  });

  it('connects with JWT headers and subscribes to private match and ACK queues', () => {
    const service = new MatchSocketService();
    const states: string[] = [];
    service.connectionState$.subscribe(state => states.push(state));

    service.connect('match-7', 'jwt-token', 42);
    const client = clientInstances()[0];
    client.config.onConnect();

    expect(client.config.connectHeaders).toEqual({ Authorization: 'Bearer jwt-token' });
    expect(client.config.reconnectDelay).toBe(3000);
    expect(client.config.heartbeatIncoming).toBe(10000);
    expect(client.config.heartbeatOutgoing).toBe(10000);
    expect(client.subscriptions.map(sub => sub.destination)).toEqual([
      '/topic/match/match-7/42',
      '/user/queue/ack',
    ]);
    expect(states).toEqual(['disconnected', 'connecting', 'connected']);

    service.ngOnDestroy();
  });

  it('emits match events, ACKs, and publishes actions to the match endpoint', () => {
    const service = new MatchSocketService();
    const events: unknown[] = [];
    const acks: unknown[] = [];
    service.events$.subscribe(event => events.push(event));
    service.acks$.subscribe(ack => acks.push(ack));

    service.connect('9', 'jwt-token', 5);
    const client = clientInstances()[0];
    client.config.onConnect();

    client.subscriptions[0].callback({
      body: JSON.stringify({ type: 'TurnEnded', payload: { userId: 5 }, sequence: 10 }),
    });
    client.subscriptions[1].callback({
      body: JSON.stringify({ ok: false, reason: 'not your turn' }),
    });
    service.sendAction('9', { type: 'PASS', payload: {} });

    expect(events).toEqual([{ type: 'TurnEnded', payload: { userId: 5 }, sequence: 10 }]);
    expect(acks).toEqual([{ ok: false, reason: 'not your turn' }]);
    expect(client.publish).toHaveBeenCalledWith({
      destination: '/app/match/9/action',
      body: JSON.stringify({ type: 'PASS', payload: {} }),
    });

    service.ngOnDestroy();
  });

  it('cleans old subscriptions and client when reconnecting to another private topic', () => {
    const service = new MatchSocketService();

    service.connect('1', 'first-token', 11);
    const firstClient = clientInstances()[0];
    firstClient.config.onConnect();

    service.connect('2', 'second-token', 22);
    const secondClient = clientInstances()[1];
    secondClient.config.onConnect();

    expect(firstClient.active).toBe(false);
    expect(firstClient.subscriptions.every(sub => sub.unsubscribe.mock.calls.length === 1)).toBe(true);
    expect(secondClient.config.connectHeaders).toEqual({ Authorization: 'Bearer second-token' });
    expect(secondClient.subscriptions.map(sub => sub.destination)).toEqual([
      '/topic/match/2/22',
      '/user/queue/ack',
    ]);

    service.ngOnDestroy();
  });

  it('marks the socket reconnecting when an active websocket closes', () => {
    const service = new MatchSocketService();
    const states: string[] = [];
    service.connectionState$.subscribe(state => states.push(state));

    service.connect('3', 'jwt-token', 33);
    const client = clientInstances()[0];
    client.config.onConnect();
    client.config.onWebSocketClose();

    expect(states).toEqual(['disconnected', 'connecting', 'connected', 'reconnecting']);

    service.ngOnDestroy();
  });
});
