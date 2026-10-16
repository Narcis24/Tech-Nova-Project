import { Component } from '@angular/core';
import { OrderFormComponent } from '../../components/order-form/order-form.component';

@Component({
  selector: 'app-order-ticket',
  imports: [OrderFormComponent],
  templateUrl: './order-ticket.component.html',
  styleUrl: './order-ticket.component.css',
})
export class OrderTicketComponent {}
