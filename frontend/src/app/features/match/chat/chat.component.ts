import { Component, DestroyRef, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ChatMessageDto, MatchSocketService } from '../../../core/services/match-socket.service';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.component.html',
})
export class ChatComponent implements OnInit {
  @Input() matchId!: string;
  @Input() username!: string;

  private socketService = inject(MatchSocketService);
  private destroyRef = inject(DestroyRef);

  messages = signal<ChatMessageDto[]>([]);
  inputText = '';

  ngOnInit(): void {
    this.socketService.chatMessages$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((msg) => {
      this.messages.update((list) => {
        const updated = [...list, msg];
        return updated.length > 100 ? updated.slice(-100) : updated;
      });
    });
  }

  send(): void {
    const text = this.inputText.trim();
    if (!text) return;
    this.socketService.sendChatMessage(this.matchId, text);
    this.inputText = '';
  }
}
