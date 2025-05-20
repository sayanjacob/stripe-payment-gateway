import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BankingInformationComponent } from './banking-information.component';

describe('BankingInformationComponent', () => {
  let component: BankingInformationComponent;
  let fixture: ComponentFixture<BankingInformationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BankingInformationComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(BankingInformationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
