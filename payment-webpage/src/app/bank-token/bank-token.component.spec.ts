import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BankTokenComponent } from './bank-token.component';

describe('BankTokenComponent', () => {
  let component: BankTokenComponent;
  let fixture: ComponentFixture<BankTokenComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BankTokenComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(BankTokenComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
