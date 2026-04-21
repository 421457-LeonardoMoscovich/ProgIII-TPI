import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Card } from '../../../core/models/card.model';

@Component({
  selector: 'app-card-preview',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './card-preview.component.html',
})
export class CardPreviewComponent {
  card = input.required<Card>();
  quantity = input<number>(0);
  maxQuantity = input<number>(4);
}
