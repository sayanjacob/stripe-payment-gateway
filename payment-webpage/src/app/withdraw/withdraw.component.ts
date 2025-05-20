import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import axios from 'axios';

@Component({
  selector: 'app-withdraw',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './withdraw.component.html',
  styleUrl: './withdraw.component.css'
})
export class WithdrawComponent implements OnInit {
  amount: number = 0;
  userId: number = 28;
  message: string = '';

  constructor() { }
  async ngOnInit() {
    this.userId = Number(sessionStorage.getItem('userId'));
    // Initialization logic can go here
  }

  async withdrawFunds(){
    try {
      const response = await axios.post(`http://localhost:8080/api/transactions/withdraw?userId=${this.userId}&amount=${this.amount*100}`);
      this.message = 'Withdraw Initiated!';
      console.log(response.data);
      alert('Withdraw Initiated! Check your account balance.');

    } catch (error) {
      this.message = 'Deposit failed. Please try again.';
      console.error(error);
    }
  }
}
