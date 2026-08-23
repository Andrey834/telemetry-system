import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DeviceMap } from './device-map';

describe('DeviceMap', () => {
  let component: DeviceMap;
  let fixture: ComponentFixture<DeviceMap>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeviceMap],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(DeviceMap);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
