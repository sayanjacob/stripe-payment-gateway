import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms'; // Import necessary modules
import axios from 'axios';

@Component({
  selector: 'app-deposit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './deposit.component.html',
  styleUrls: ['./deposit.component.css']
})
export class DepositComponent implements OnInit {
  depositForm: FormGroup;
  userId: number | null = null;
  message: string = '';
  data: any = null;
  balance: number = 0;
  showBalance: boolean = false;
  isLoading: boolean = false;
  loadFailed: boolean = false;

  constructor(private fb: FormBuilder) {
    this.depositForm = this.fb.group({
      amount: [0, [Validators.required, Validators.min(1), Validators.pattern('^[0-9]*$')]]
    });
  }

  ngOnInit(): void {
    const storedUserId = sessionStorage.getItem('userId');
    if (storedUserId) {
      this.userId = Number(storedUserId);
      this.getBalance();
    } else {
      this.message = 'User ID not found. Please log in again.';
      this.loadFailed = true;
    }

    this.depositForm.get('amount')?.valueChanges.subscribe(value => {
      if (this.depositForm.get('amount')?.invalid) {
        this.message = 'Please enter a valid amount.';
      } else if (value > this.balance) {
        this.message = 'Deposit amount exceeds available balance.';
        this.depositForm.get('amount')?.setErrors({ exceedBalance: true });
      } else {
        this.message = '';
        this.depositForm.get('amount')?.setErrors(null);
      }
    });
  }

  async getBalance() {
    if (!this.userId) {
      this.message = 'User ID not found. Please log in again.';
      this.loadFailed = true;
      return;
    }

    this.isLoading = true;
    try {
      const response = await axios.get(`http://localhost:8080/api/transactions/data?userId=${this.userId}`);
      this.data = response.data;
      this.balance = response.data.balance / 100;
    } catch (error) {
      this.handleError(error);
    } finally {
      this.isLoading = false;
    }
  }

  toggleBalance() {
    this.showBalance = !this.showBalance;
  }

  async depositFunds() {
    if (!this.userId || this.depositForm.invalid) {
      this.message = 'Please enter a valid amount or log in again.';
      return;
    }

    const amount = this.depositForm.get('amount')?.value*100;
    this.isLoading = true;
    try {
      await axios.post(`http://localhost:8080/api/transactions/deposit?userId=${this.userId}&amount=${amount}`);
      this.message = 'Deposit initiated successfully!';
      await this.getBalance();
    } catch (error) {
      this.handleError(error);
    } finally {
      this.isLoading = false;
    }
  }

  private handleError(error: any) {
    this.loadFailed = true;
    if (error.response && error.response.status === 404) {
      this.message = 'Inactive Account! Please contact support.';
    } else {
      this.message = 'An error occurred. Please try again later.';
    }
  }
  
}
