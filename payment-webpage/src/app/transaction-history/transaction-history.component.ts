import { Component } from '@angular/core';
import axios from 'axios';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-transaction-history',
  standalone: true,
  imports: [CommonModule],  // Import MatTableModule here
  templateUrl: './transaction-history.component.html',
  styleUrls: ['./transaction-history.component.css']
})
export class TransactionHistoryComponent {
  transactions: any[] = []; // Initialize as an empty array
  userId: number = 0;

  constructor() {}

  async ngOnInit() {
    this.userId = Number(sessionStorage.getItem('userId'));
    const response = await axios.get(`http://localhost:8080/api/transactions/trans-history?userId=${this.userId}`);
    this.transactions = response.data; // Assign the response data to the transactions array
  }


}
