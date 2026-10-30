import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { McvCustomSectionsSectionComponent } from './mcv-custom-sections-section.component';
import { CustomSection } from '../../../core/models/profile-section.model';

/**
 * The builder and the preview only notice an edit when they are handed a new list. Items are two
 * levels deep here, so an in-place write is easy to slip back in and would go unnoticed.
 */
describe('McvCustomSectionsSectionComponent', () => {
  it('replaces the list when an item is edited, leaving the old one untouched', () => {
    TestBed.configureTestingModule({ imports: [McvCustomSectionsSectionComponent, TranslateModule.forRoot()] });
    const fixture = TestBed.createComponent(McvCustomSectionsSectionComponent);
    const original: CustomSection[] = [
      { heading: 'Volunteering', items: [{ text: 'Coach' }, { text: 'Mentor' }] },
    ];
    fixture.componentRef.setInput('list', original);
    let emitted: CustomSection[] | undefined;
    fixture.componentInstance.list.subscribe(list => emitted = list);
    fixture.detectChanges();

    const inputs = fixture.nativeElement.querySelectorAll('input') as NodeListOf<HTMLInputElement>;
    const secondItem = inputs[2];
    secondItem.value = 'Trainer';
    secondItem.dispatchEvent(new Event('input'));

    expect(emitted?.[0].items.map(i => i.text)).toEqual(['Coach', 'Trainer']);
    expect(original[0].items[1].text).toBe('Mentor');
  });
});
