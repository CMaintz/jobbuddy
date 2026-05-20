import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import * as profileActions from './profile.actions';
import type { ProfileComponent } from './profile.component';

@Injectable({ providedIn: 'root' })
export class ProfileOverviewFacade {
  private http = inject(HttpClient);

  load(vm: ProfileComponent): void {
    profileActions.loadProfile(vm, this.http);
  }

  uploadPhoto(vm: ProfileComponent, file: File): void {
    profileActions.uploadPhoto(vm, this.http, file);
  }

  importLinkedInPdf(vm: ProfileComponent, file: File): void {
    profileActions.importLinkedInPdf(vm, this.http, file);
  }

  importCvPdf(vm: ProfileComponent, file: File): void {
    profileActions.importCvPdf(vm, this.http, file);
  }

  applyCvPdfPreview(vm: ProfileComponent): void {
    profileActions.applyCvPdfPreview(vm);
  }

  applyLinkedInPreview(vm: ProfileComponent): void {
    profileActions.applyLinkedInPreview(vm);
  }

  save(vm: ProfileComponent): void {
    profileActions.saveProfile(vm, this.http);
  }
}
