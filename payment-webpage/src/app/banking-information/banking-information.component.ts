/**
 * Component for handling user banking information and linking a bank account using Stripe Financial Connections.
 *
 * This component manages a multi-step form for collecting and verifying banking details,
 * integrates with Stripe to securely link a user's bank account, and communicates with a backend API
 * for customer and payment method management.
 *
 * @remarks
 * - Uses Stripe.js for Financial Connections and US bank account setup.
 * - Handles customer creation and retrieval via backend endpoints.
 * - Provides UI feedback for loading and error states.
 *
 * @example
 * <app-banking-information></app-banking-information>
 *
 * @property {FormGroup} bankForm - The reactive form group for bank information.
 * @property {string} bankName - The name of the bank, resolved from the routing number.
 * @property {number | null} userId - The current user's ID, retrieved from session storage.
 * @property {string} customerId - The Stripe customer ID associated with the user.
 * @property {boolean} isLoading - Indicates if an async operation is in progress.
 * @property {string} errorMessage - Stores error messages for display.
 * @property {Stripe | null} stripe - The Stripe.js instance.
 * @property {number} currentStep - The current step in the multi-step form (1 or 2).
 * @property {boolean} submitting - Indicates if the component is in a submitting state.
 * @property {boolean} userConsent - Indicates if the user has given consent to link their bank account.
 *
 * @method ngOnInit Initializes Stripe, retrieves user/customer info, and prepares the component.
 * @method ngAfterViewInit Sets up the bank account link button after the view is initialized.
 * @method nextStep Advances to the next step in the form and triggers bank linking if appropriate.
 * @method prevStep Returns to the previous step in the form.
 * @method getConnectAccountId Retrieves the Stripe Connect account and customer ID for the user.
 * @method createCustomerId Creates a new Stripe customer for the user if one does not exist.
 * @method setupLinkBankAccountButton Attaches the click event handler to the bank account link button.
 * @method ngOnDestroy Cleans up event listeners when the component is destroyed.
 * @method handleLinkBankAccount Handles the full flow of linking a bank account via Stripe Financial Connections.
 * @method checkRoutingNumber Validates the routing number and fetches the bank name.
 *
 * @viewChild linkBankAccountButton Reference to the bank account link button in the template.
 */
import {
  Component,
  OnInit,
  ElementRef,
  ViewChild,
  OnDestroy,
  AfterViewInit
} from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { loadStripe, Stripe } from '@stripe/stripe-js';
import axios from 'axios';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-banking-information',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './banking-information.component.html',
  styleUrls: ['./banking-information.component.css']
})
export class BankingInformationComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('linkBankAccountButton') linkBankAccountButton?: ElementRef<HTMLButtonElement>;

  bankForm!: FormGroup;
  bankName: string = '';
  userId: number | null = null;
  customerId: string = '';
  isLoading: boolean = false;
  errorMessage: string = '';
  stripe: Stripe | null = null;
  currentStep = 1;
  submitting = false;
  userConsent = false;

  private buttonClickListener?: () => void;

  constructor(private fb: FormBuilder, private route: ActivatedRoute) { }

  async ngOnInit() {
    // Initialize Stripe with your publishable key
    this.stripe = await loadStripe('replace with pk test');

    this.userId = Number(sessionStorage.getItem('userId'));
    this.submitting = true;

    // Get the customer's Stripe account info
    await this.getConnectAccountId();
  }

  ngAfterViewInit() {
    // Set up the bank account link button after view is fully initialized
    setTimeout(() => this.setupLinkBankAccountButton(), 100);
  }

  get progressPercent(): number {
    return (this.currentStep / 2) * 100;
  }

  // Go to the next step
  nextStep(): void {
    if (this.currentStep < 2) {
      this.currentStep++;
      
      // When moving to step 2, ensure the button is set up
      if (this.currentStep === 2) {
        setTimeout(() => {
          this.setupLinkBankAccountButton();
          // Automatically trigger the bank connection flow when reaching step 2
          if (this.userConsent && this.linkBankAccountButton?.nativeElement) {
            this.handleLinkBankAccount();
          }
        }, 200);
      }
    }
  }

  // Go to the previous step
  prevStep(): void {
    if (this.currentStep > 1) this.currentStep--;
  }

  async getConnectAccountId() {
    try {
      const response = await axios.get(`http://localhost:8080/stripe/getStripeUserDetails?userId=${this.userId}`);
      console.log('User Stripe details:', response.data);
      
      if (response.data.customer_id === null) {
        console.log('No customer found for userId:', this.userId);
        await this.createCustomerId(response.data.connect_id);
      } else {
        this.customerId = response.data.customer_id;
        console.log("customerId", this.customerId);
      }
      this.submitting = false;
    } catch (error) {
      console.error('Error getting Stripe account details:', error);
      this.errorMessage = 'Failed to retrieve account information. Please try again.';
      this.submitting = false;
    }
  }

  async createCustomerId(accountId: string) {
    try {
      const response = await axios.post(`http://localhost:8080/stripe/createCustomer?userId=${this.userId}&connectAccountId=${accountId}`);
      console.log('Created customer:', response.data);
      this.customerId = response.data.customer_id;
    } catch (error) {
      console.error('Error creating customer:', error);
      this.errorMessage = 'Failed to create customer account. Please try again.';
    }
  }

  setupLinkBankAccountButton() {
    const button = this.linkBankAccountButton?.nativeElement;
    if (button && this.stripe) {
      // Remove any existing listeners first to prevent duplicates
      if (this.buttonClickListener) {
        button.removeEventListener('click', this.buttonClickListener);
      }
      
      this.buttonClickListener = this.handleLinkBankAccount.bind(this);
      button.addEventListener('click', this.buttonClickListener);
      console.log('Bank account button listener set up');
    } else {
      console.warn('Button element or Stripe not available');
    }
  }

  ngOnDestroy(): void {
    const button = this.linkBankAccountButton?.nativeElement;
    if (button && this.buttonClickListener) {
      button.removeEventListener('click', this.buttonClickListener);
    }
  }

  async handleLinkBankAccount() {
    if (!this.stripe) {
      console.error('Stripe not initialized');
      this.errorMessage = 'Payment service not initialized. Please refresh the page.';
      return;
    }

    if (!this.customerId) {
      console.error('Customer ID not available');
      this.errorMessage = 'Customer account not found. Please try again.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    try {
      console.log('Starting bank account connection process...');
      
      // 1. Create a Financial Connections Session
      const response = await axios.post('http://localhost:8080/stripe/addPaymentMethod', {}, {
        params: {
          customerId: this.customerId,
        }
      });

      if (response.status !== 200) {
        throw new Error('Failed to create financial connections session');
      }

      const clientSecret = response.data.clientSecret;
      console.log("Client secret received:", clientSecret);

      // 2. Collect bank account details using Stripe's UI
      const result = await this.stripe.collectFinancialConnectionsAccounts({
        clientSecret: clientSecret,
      });

      if (result.error) {
        throw new Error(result.error.message || 'Error collecting bank account information');
      } 
      
      if (result.financialConnectionsSession.accounts.length === 0) {
        throw new Error('No accounts were linked');
      }
      
      console.log('Successfully linked accounts:', result.financialConnectionsSession.accounts);
      const accountId = result.financialConnectionsSession.accounts[0].id;

      // 3. Attach the payment method to the customer
      const attachResponse = await axios.post("http://localhost:8080/stripe/attachPaymentMethodToCustomer", {
        customerId: this.customerId,
        financialConnectionsAccountId: accountId,
      });

      if (attachResponse.status !== 200) {
        throw new Error('Failed to attach payment method');
      }

      // 4. Confirm the bank account setup
      const setupResult = await this.stripe.confirmUsBankAccountSetup(
        attachResponse.data.setupIntentClientSecret,
        {
          payment_method: attachResponse.data.paymentMethod,
        }
      );

      if (setupResult.error) {
        throw new Error(setupResult.error.message || 'Error confirming bank account setup');
      }

      console.log('Bank account setup successful:', setupResult.setupIntent.status);
      alert('Bank account linked successfully!');
      
      // You might want to redirect or show a success state here
      
    } catch (error: any) {
      console.error('Error linking bank account:', error);
      this.errorMessage = error.message || 'Failed to link bank account. Please try again.';
    } finally {
      this.isLoading = false;
    }
  }

  async checkRoutingNumber() {
    const rn = this.bankForm.get('routingNumber')?.value;
    if (!rn) return;

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
      console.error('Error checking routing number:', error);
    } finally {
      this.isLoading = false;
    }
  }
}