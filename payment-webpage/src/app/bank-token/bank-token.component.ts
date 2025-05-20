import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { loadStripe, Stripe } from '@stripe/stripe-js';
import axios from 'axios';

@Component({
  selector: 'app-bank-token',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, CommonModule],
  templateUrl: './bank-token.component.html',
  styleUrl: './bank-token.component.css'
})
export class BankTokenComponent implements OnInit {
  bankForm!: FormGroup;
  userId: number = 16; // Replace with dynamic logic if needed
  bankName: string = '';
  isLoading: boolean = false;
  errorMessage: string = '';
  stripe: Stripe | null = null;
  accountId: string = '';

  constructor(private fb: FormBuilder,private router: Router) { }

  async ngOnInit() {
    this.stripe = await loadStripe('replace pk_test');

    try {
      this.userId=Number(sessionStorage.getItem("userId"));
      const response = await axios.get(`http://localhost:8080/stripe/getStripeUserDetails?userId=${this.userId}`);
      this.accountId = response.data.connect_id;
    } catch (error) {
      this.errorMessage = 'Failed to retrieve account ID';
      console.error(error);
    }

    this.bankForm = this.fb.group({
      accountHolderName: ['', Validators.required],
      routingNumber: ['', [Validators.required, Validators.pattern('^[0-9]{9}$')]],
      accountNumber: ['', Validators.required],
      accountType: ['checking', Validators.required]
    });

    this.bankForm.get('routingNumber')?.valueChanges.subscribe(async (value) => {
      if (value.length === 9) {
        await this.checkRoutingNumber();
      }
    });
  }

  async submitBankDetails() {
    if (!this.stripe || !this.accountId) {
      this.errorMessage = 'Stripe or Account ID not initialized';
      return;
    }

    const { accountHolderName, routingNumber, accountNumber, accountType } = this.bankForm.value;

    try {
      const result = await this.stripe.createToken('bank_account', {
        country: 'US',
        currency: 'usd',
        routing_number: routingNumber,
        account_number: accountNumber,
        account_holder_name: accountHolderName,
        account_holder_type: 'individual'
      });

      if (result.error) {
        this.errorMessage = result.error.message || 'Token creation failed';
        return;
      }

      // Send the token to the server
      const token = result.token?.id;
      const response = await axios.post('http://localhost:8080/stripe/addPayoutAccount', {
        accountId: this.accountId,
        token: token
      });

      console.log('Bank added successfully:', response.data);
      alert("Payoutbank added")
      this.router.navigate(['/add-bank-info']); // Navigate to the next page with the account ID

    } catch (err) {
      console.error('Unexpected error:', err);
      this.errorMessage = 'Unexpected error occurred';
    }
  }

  async checkRoutingNumber() {
    const rn = this.bankForm.get('routingNumber')?.value;
    this.isLoading = true;
    this.errorMessage = '';
    this.bankName = '';

    try {
      const response = await axios.get(`https://www.routingnumbers.info/api/data.json?rn=${rn}`);
      if (response.data.code === 200 && response.data.customer_name) {
        this.bankName = response.data.customer_name;
      } else {
        this.errorMessage = 'Invalid routing number';
      }
    } catch (error) {
      this.errorMessage = 'Error checking routing number';
      console.error('Error:', error);
    } finally {
      this.isLoading = false;
    }
  }
}
