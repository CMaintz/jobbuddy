import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivityStreakService } from './activity-streak.service';

describe('ActivityStreakService', () => {
  let service: ActivityStreakService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withXhr()), provideHttpClientTesting()],
    });
    service = TestBed.inject(ActivityStreakService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts at zero', () => {
    expect(service.days()).toBe(0);
  });

  it("asks for the streak in the browser's time zone", () => {
    service.refresh();

    const req = http.expectOne(r => r.url === '/api/v1/analytics/streak');
    expect(req.request.params.get('zone')).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
    req.flush({ days: 5, lastWeek: [0, 0, 1, 1, 1, 2, 1] });

    expect(service.days()).toBe(5);
    expect(service.lastWeek()).toEqual([0, 0, 1, 1, 1, 2, 1]);
  });

  it('falls back to zero when the request fails', () => {
    service.days.set(3);
    service.lastWeek.set([1, 1, 1, 0, 0, 0, 0]);
    service.refresh();

    http.expectOne(r => r.url === '/api/v1/analytics/streak')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(service.days()).toBe(0);
    expect(service.lastWeek()).toEqual([0, 0, 0, 0, 0, 0, 0]);
  });

  it('drops a stale response when refreshed again', () => {
    service.refresh();
    service.refresh();

    const [stale, fresh] = http.match(r => r.url === '/api/v1/analytics/streak');
    expect(stale.cancelled).toBeTrue();
    fresh.flush({ days: 2, lastWeek: [0, 0, 0, 0, 0, 1, 1] });

    expect(service.days()).toBe(2);
  });

  it('clears on sign-out', () => {
    service.refresh();
    const req = http.expectOne(r => r.url === '/api/v1/analytics/streak');
    service.clear();

    expect(req.cancelled).toBeTrue();
    expect(service.days()).toBe(0);
  });
});
