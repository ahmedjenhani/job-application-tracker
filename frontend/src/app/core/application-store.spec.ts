import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApplicationStore } from './application-store';
import { APP_CONFIG } from './config';
import { JobApplication } from './models';

function app(id: number, status: JobApplication['status']): JobApplication {
  return { id, company: `Company ${id}`, role: 'Dev', status, skills: [], createdAt: '', updatedAt: '' };
}

describe('ApplicationStore', () => {
  let store: ApplicationStore;
  let backend: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: APP_CONFIG, useValue: { apiUrl: 'http://api.test' } },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    store = TestBed.inject(ApplicationStore);
    backend = TestBed.inject(HttpTestingController);

    const loading = store.load();
    backend.expectOne('http://api.test/api/applications').flush([app(1, 'APPLIED'), app(2, 'WISHLIST')]);
    await loading;
  });

  it('loads the applications', () => {
    expect(store.items().map((a) => a.id)).toEqual([1, 2]);
    expect(store.loaded()).toBe(true);
  });

  it('moves a card immediately (optimistic) and keeps the server answer', async () => {
    const change = store.changeStatus(1, 'INTERVIEW');

    expect(store.items().find((a) => a.id === 1)?.status).toBe('INTERVIEW'); // before the server replied

    const req = backend.expectOne('http://api.test/api/applications/1/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'INTERVIEW' });
    req.flush({ ...app(1, 'INTERVIEW'), updatedAt: 'now' });
    await change;

    expect(store.items().find((a) => a.id === 1)?.updatedAt).toBe('now');
  });

  it('puts the card back when the server refuses the change', async () => {
    const change = store.changeStatus(1, 'OFFER');
    expect(store.items().find((a) => a.id === 1)?.status).toBe('OFFER');

    backend.expectOne('http://api.test/api/applications/1/status').flush(null, { status: 500, statusText: 'Error' });

    await expect(change).rejects.toBeTruthy();
    expect(store.items().find((a) => a.id === 1)?.status).toBe('APPLIED');
  });

  it('does not call the server when the status did not change', async () => {
    await store.changeStatus(1, 'APPLIED');
    backend.expectNone('http://api.test/api/applications/1/status');
  });

  it('adds created applications to the top and removes deleted ones', async () => {
    const creating = store.create({ company: 'New', role: 'Dev', status: 'APPLIED', skills: [] });
    backend.expectOne((r) => r.method === 'POST').flush(app(3, 'APPLIED'));
    await creating;
    expect(store.items()[0].id).toBe(3);

    const removing = store.remove(3);
    backend.expectOne('http://api.test/api/applications/3').flush(null);
    await removing;
    expect(store.items().some((a) => a.id === 3)).toBe(false);
  });
});
