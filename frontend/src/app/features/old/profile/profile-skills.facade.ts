import { Injectable, inject } from '@angular/core';
import { SkillsApiService } from '../../../core/api/skills.api';
import { ProfileSkill, SkillTaxonomy } from '../../../core/models/skill-taxonomy.model';
import type { ProfileComponent } from './profile.component';

type SectionSkillTarget = 'exp' | 'project' | 'edu';

@Injectable({ providedIn: 'root' })
export class ProfileSkillsFacade {
  private skillsApi = inject(SkillsApiService);

  loadCategories(vm: ProfileComponent): void {
    this.skillsApi.getCategories().subscribe(cats => vm.availableCategories = cats);
  }

  saveEditSkill(vm: ProfileComponent, original: ProfileSkill): void {
    const updated: ProfileSkill = {
      ...original,
      proficiencyLevel: vm.editingSkill.proficiencyLevel ?? original.proficiencyLevel,
      yearsExperience: vm.editingSkill.yearsExperience,
    };

    this.skillsApi.updateProfileSkill(original.id!, updated).subscribe({
      next: () => {
        vm.editingSkillId = null;
        this.reloadProfileSkills(vm);
      }
    });
  }

  addProfileSkill(vm: ProfileComponent): void {
    vm.skillSaveError = '';
    if (!vm.newSkill.skillName?.trim()) {
      vm.skillSaveError = 'Skill name is required.';
      return;
    }

    const save = (taxonomyId?: string) => {
      const payload: ProfileSkill = {
        skillName: vm.newSkill.skillName!.trim(),
        taxonomyId: taxonomyId ?? vm.newSkill.taxonomyId,
        proficiencyLevel: vm.newSkill.proficiencyLevel ?? 'INTERMEDIATE',
        yearsExperience: vm.newSkill.yearsExperience ?? undefined,
        usedInProduction: vm.newSkill.usedInProduction ?? false,
        displayOrder: vm.profileSkills.length,
      };
      this.skillsApi.addProfileSkill(payload).subscribe({
        next: () => {
          this.reloadProfileSkills(vm);
          vm.newSkill = { skillName: '', proficiencyLevel: 'INTERMEDIATE', yearsExperience: undefined, usedInProduction: false };
          vm.taxonomySuggestions = [];
        },
        error: () => { vm.skillSaveError = 'Failed to save skill. Please try again.'; }
      });
    };

    if (!vm.newSkill.taxonomyId) {
      this.skillsApi.createTaxonomySkill(vm.newSkill.skillName!.trim()).subscribe({
        next: taxonomy => save(taxonomy.id),
        error: () => save()
      });
      return;
    }

    save();
  }

  deleteProfileSkill(vm: ProfileComponent, id: string): void {
    this.skillsApi.deleteProfileSkill(id).subscribe(() => this.reloadProfileSkills(vm));
  }

  createSkillForSection(vm: ProfileComponent): void {
    if (!vm.pendingNewSkill) return;
    const { name, section } = vm.pendingNewSkill;
    this.skillsApi.createTaxonomySkill(name, vm.pendingNewSkillCategory || undefined).subscribe({
      next: skill => {
        const list = this.sectionSkillsFor(vm, section);
        if (!list.find(s => s.id === skill.id)) list.push(skill);
        vm.pendingNewSkill = null;
        vm.pendingNewSkillCategory = '';
      }
    });
  }

  private reloadProfileSkills(vm: ProfileComponent): void {
    this.skillsApi.getProfileSkills().subscribe(d => vm.profileSkills = d);
  }

  private sectionSkillsFor(vm: ProfileComponent, section: SectionSkillTarget): SkillTaxonomy[] {
    if (section === 'exp') return vm.expEditSkills;
    if (section === 'project') return vm.projectEditSkills;
    return vm.educationEditSkills;
  }
}
