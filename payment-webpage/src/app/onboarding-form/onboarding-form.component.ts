import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import axios from 'axios';
import { Router } from '@angular/router';
import { loadStripe, Stripe } from '@stripe/stripe-js';

/**
 * Component for handling user onboarding and bank account linking with Stripe.
 *
 * This component manages a multi-step onboarding form, including user information collection,
 * Stripe Connect account creation, and bank account linking for payouts.
 *
 * @remarks
 * - Uses Angular Reactive Forms for form management and validation.
 * - Integrates with Stripe.js for secure bank account tokenization.
 * - Communicates with a backend server for Stripe account creation and onboarding.
 *
 * @example
 * <app-onboarding-form></app-onboarding-form>
 *
 * @property {FormGroup} userForm - Form group for collecting user information.
 * @property {FormGroup} bankForm - Form group for collecting bank account details.
 * @property {boolean} submitting - Indicates if a form submission is in progress.
 * @property {string} bankName - Name of the bank resolved from the routing number.
 * @property {boolean} isLoading - Indicates if a loading operation is in progress.
 * @property {string} errorMessage - Stores error messages for display.
 * @property {string} accountId - Stripe Connect account ID.
 * @property {number} userId - User identifier (should be dynamically set in production).
 * @property {number} currentStep - Current step in the onboarding process.
 * @property {Stripe | null} stripe - Stripe.js instance.
 *
 * @method ngOnInit Initializes the forms and sets up value change subscriptions.
 * @method ngOnDestroy Cleanup logic (optional).
 * @method createConnectAccount Creates a Stripe Connect account for the user.
 * @method submitForm Handles submission of the onboarding form.
 * @method submitBankDetails Handles submission of the bank account form and tokenization.
 * @method nextStep Advances to the next onboarding step.
 * @method prevStep Returns to the previous onboarding step.
 * @method checkRoutingNumber Validates the routing number and fetches the bank name.
 * @getter progressPercent Returns the progress percentage based on the current step.
 */
@Component({
  selector: 'app-onboarding-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './onboarding-form.component.html',
  styleUrls: ['./onboarding-form.component.css']
})
export class OnboardingFormComponent implements OnInit, OnDestroy {
  userForm!: FormGroup;
  bankForm!: FormGroup;
  submitting = false;
  bankName: string = '';
  isLoading: boolean = false;
  errorMessage: string = '';
  accountId: string = '';
  userId: number = 33; // Replace with dynamic logic if needed
  currentStep = 1;
  stripe: Stripe | null = null;


  constructor(private fb: FormBuilder, private router: Router) { }

  ngOnInit(): void {

    this.userForm = this.fb.group({
      ip: ["66.249.70.162", Validators.required],
      tosConsent: [false, Validators.requiredTrue],

      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: ['+10000000000', Validators.required],
      addressLine1: ['123 Main St', Validators.required],
      addressLine2: ['Wilson Avenue', Validators.required],

      city: ['San Francisco', Validators.required],
      state: ['CA', Validators.required],
      postalCode: ['94111', Validators.required],
      country: ['US', Validators.required],
      ssnLast4: ['0000', [Validators.required, Validators.pattern(/^\d{4}$/)]],
      dob: ['', [Validators.required, Validators.pattern(/^\d{4}-\d{2}-\d{2}$/)]],
      accountId: ['', Validators.required],
    });

    // Automatically create connect account when user accepts terms
    this.userForm.get('tosConsent')?.valueChanges.subscribe((consentGiven: boolean) => {
      if (consentGiven && !this.userForm.get('accountId')?.value) {
        this.createConnectAccount();
      }
    });

    sessionStorage.setItem("userId", String(this.userId));

    this.bankForm = this.fb.group({
      accountHolderName: ['', Validators.required],
      routingNumber: ['', [Validators.required, Validators.pattern('^[0-9]{9}$')]],
      accountNumber: ['', Validators.required],
      accountType: ['checking', Validators.required]
    });

    this.bankForm.get('routingNumber')?.valueChanges.subscribe(async (value) => {
      if (value?.length === 9) {
        await this.checkRoutingNumber();
      }
    });
  }

  // Function to create Stripe Connect account
  async createConnectAccount() {
    this.submitting = true;
    try {
      const response = await axios.post(`http://localhost:8080/stripe/create?userId=${this.userId}`);
      this.userForm.patchValue({ accountId: response.data.accountId });
      this.accountId = response.data.accountId;
      this.submitting = false;

    } catch (error) {
      console.error('Error creating connect account:', error);
    }
  }

  // Form submission handler
  async submitForm() {
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      return;
    }

    this.submitting = true;
    try {
      // Submitting the form to the backend for onboarding
      const response = await axios.post('http://localhost:8080/stripe/onboarding', this.userForm.value);
      console.log('Success:', response.data);

      // Once the form is successfully submitted, proceed to step 5
      this.currentStep = 5;  // Move to the next step (Add Bank Details)
    } catch (error) {
      console.error('Error submitting form:', error);
      this.errorMessage = 'Something went wrong. Please try again later.';
    } finally {
      this.submitting = false;
    }
  }

  async submitBankDetails() {
    if (this.bankForm.invalid) {
      this.bankForm.markAllAsTouched();
      return;
    }

    this.submitting = true;

    try {
      this.stripe = await loadStripe('replace with pk test');

      if (!this.stripe || !this.accountId) {
        this.errorMessage = 'Stripe or Account ID not initialized';
        return;
      }

      const { accountHolderName, routingNumber, accountNumber, accountType } = this.bankForm.value;

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
      alert("Payoutbank added");
      this.router.navigate(['/add-bank-info']); // Navigate to the next page with the account ID
    } catch (err) {
      console.error('Unexpected error:', err);
      this.errorMessage = 'Unexpected error occurred';
    } finally {
      this.submitting = false;
    }
  }

  // Method to calculate progress percentage
  get progressPercent(): number {
    return (this.currentStep / 5) * 100;
  }

  // Go to the next step
  nextStep(): void {
    if (this.currentStep < 5) this.currentStep++;
  }

  // Go to the previous step
  prevStep(): void {
    if (this.currentStep > 1) this.currentStep--;
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

  ngOnDestroy() {
    // Optional: add cleanup logic here if needed
  }
}