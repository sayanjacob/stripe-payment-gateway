import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'payment-success',
    loadComponent: () => import('./payment-success/payment-success.component')
      .then(m => m.PaymentSuccessComponent) // Lazy loading standalone component
  },
  {
    path: 'add-bank',
    loadComponent: () => import('./bank-token/bank-token.component')
      .then(m => m.BankTokenComponent) // Lazy loading Stripe component
  },
  {
    path: 'history',
    loadComponent: () => import('./transaction-history/transaction-history.component')
      .then(m => m.TransactionHistoryComponent) // Lazy loading Stripe component
  },
  {
    path: '',
    loadComponent: () => import('./onboarding-form/onboarding-form.component')
      .then(m => m.OnboardingFormComponent) // Lazy loading Stripe component
  },
  {
    path: 'deposit',
    loadComponent: () => import('./deposit/deposit.component')
      .then(m => m.DepositComponent) // Lazy loading Stripe component

  }, {
    path: 'withdraw',
    loadComponent: () => import('./withdraw/withdraw.component')
      .then(m => m.WithdrawComponent) // Lazy loading Stripe component

  },
  {
    path: 'add-bank-info',
    loadComponent: () => import('./banking-information/banking-information.component')
      .then(m => m.BankingInformationComponent) // Lazy loading Stripe component
  },
  {
    path: 'refresh/:connectedAccountId', // Fixed incorrect path syntax
    loadComponent: () => import('./refresh/refresh.component')
      .then(m => m.RefreshComponent) // Lazy loading Stripe component
  }, {
    path: 'return/:connectedAccountId', // Fixed incorrect path syntax
    loadComponent: () => import('./return/return.component')
      .then(m => m.ReturnComponent) // Lazy loading Stripe component
  }
];
