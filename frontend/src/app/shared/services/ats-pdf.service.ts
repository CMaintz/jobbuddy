import { Injectable } from '@angular/core';
import type { jsPDF } from 'jspdf';
import { ResumeData, ResumeSettings, ResumeSkill } from '../../features/resume-builder/models/resume-builder.models';
import { StructuredDocument } from '../../core/models/structured-document.model';

/**
 * Text-based PDF generation (jsPDF text API — no canvas rasterization).
 * ATS systems parse PDF text; screenshot-style PDFs are invisible to them.
 * Single-column classic layout: standard headings, selectable text throughout.
 */

export interface AtsItem {
  title: string;
  meta?: string;
  body?: string;
  bullets?: string[];
}

export interface AtsSection {
  heading: string;
  /** Structured entries (experience, education, projects). */
  items?: AtsItem[];
  /** Simple line content (skills, languages) — joined text also works. */
  lines?: string[];
}

export interface AtsResumeModel {
  name: string;
  headline?: string;
  contactLine?: string;
  linksLine?: string;
  summary?: string;
  sections: AtsSection[];
}

const PAGE_W = 210;
const PAGE_H = 297;
const MARGIN = 18;
const CONTENT_W = PAGE_W - MARGIN * 2;

@Injectable({ providedIn: 'root' })
export class AtsPdfService {

  async downloadResume(model: AtsResumeModel, filename: string): Promise<void> {
    const doc = await this.newDoc();
    let y = MARGIN;

    y = this.header(doc, model, y);

    if (model.summary?.trim()) {
      y = this.sectionHeading(doc, 'Profile', y);
      y = this.paragraph(doc, model.summary.trim(), y, 9.5);
      y += 3;
    }

    for (const section of model.sections) {
      const hasContent = (section.items?.length ?? 0) > 0 || (section.lines?.length ?? 0) > 0;
      if (!hasContent) continue;
      y = this.sectionHeading(doc, section.heading, y);
      for (const line of section.lines ?? []) {
        y = this.paragraph(doc, line, y, 9.5);
      }
      for (const item of section.items ?? []) {
        y = this.ensureRoom(doc, y, 14);
        doc.setFont('helvetica', 'bold').setFontSize(10.5).setTextColor(20);
        y = this.wrapped(doc, item.title, y, 4.6);
        if (item.meta) {
          doc.setFont('helvetica', 'normal').setFontSize(9).setTextColor(110);
          y = this.wrapped(doc, item.meta, y, 4);
        }
        if (item.body) {
          y += 0.5;
          y = this.paragraph(doc, item.body, y, 9.5);
        }
        for (const bullet of item.bullets ?? []) {
          y = this.paragraph(doc, `•  ${bullet}`, y, 9.5, 4);
        }
        y += 2.5;
      }
      y += 1.5;
    }

    doc.save(`${filename}.pdf`);
  }

  async downloadLetter(opts: {
    name?: string; headline?: string; contactLine?: string;
    paragraphs: string[]; filename: string;
  }): Promise<void> {
    const doc = await this.newDoc();
    let y = MARGIN;

    if (opts.name) {
      y = this.header(doc, { name: opts.name, headline: opts.headline, contactLine: opts.contactLine, sections: [] }, y);
    }
    for (const para of opts.paragraphs) {
      y = this.paragraph(doc, para, y, 10.5, 0, 5.2);
      y += 3.5;
    }
    doc.save(`${opts.filename}.pdf`);
  }

  // ── Layout primitives ─────────────────────────────────────────

  private async newDoc() {
    const { jsPDF } = await import('jspdf');
    return new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  }

  private header(doc: jsPDF, model: AtsResumeModel, y: number): number {
    doc.setFont('helvetica', 'bold').setFontSize(17).setTextColor(15);
    doc.text(model.name || 'Curriculum Vitae', MARGIN, y + 5);
    y += 8;
    doc.setFont('helvetica', 'normal').setFontSize(10.5).setTextColor(70);
    if (model.headline) {
      doc.text(model.headline, MARGIN, y + 3);
      y += 5;
    }
    doc.setFontSize(9).setTextColor(110);
    if (model.contactLine) {
      doc.text(model.contactLine, MARGIN, y + 3);
      y += 4.5;
    }
    if (model.linksLine) {
      doc.text(model.linksLine, MARGIN, y + 3);
      y += 4.5;
    }
    y += 2;
    doc.setDrawColor(60).setLineWidth(0.4);
    doc.line(MARGIN, y, PAGE_W - MARGIN, y);
    return y + 5;
  }

  private sectionHeading(doc: jsPDF, text: string, y: number): number {
    y = this.ensureRoom(doc, y, 16);
    doc.setFont('helvetica', 'bold').setFontSize(10.5).setTextColor(30);
    doc.text(text.toUpperCase(), MARGIN, y + 3.5);
    doc.setDrawColor(190).setLineWidth(0.25);
    doc.line(MARGIN, y + 5, PAGE_W - MARGIN, y + 5);
    return y + 8.5;
  }

  private paragraph(doc: jsPDF, text: string, y: number, size: number, indent = 0, lineH = 4.4): number {
    doc.setFont('helvetica', 'normal').setFontSize(size).setTextColor(40);
    const lines: string[] = doc.splitTextToSize(text, CONTENT_W - indent);
    for (const line of lines) {
      y = this.ensureRoom(doc, y, lineH + 1);
      doc.text(line, MARGIN + indent, y + lineH - 1);
      y += lineH;
    }
    return y;
  }

  private wrapped(doc: jsPDF, text: string, y: number, lineH: number): number {
    const lines: string[] = doc.splitTextToSize(text, CONTENT_W);
    for (const line of lines) {
      y = this.ensureRoom(doc, y, lineH + 1);
      doc.text(line, MARGIN, y + lineH - 1);
      y += lineH;
    }
    return y;
  }

  private ensureRoom(doc: jsPDF, y: number, needed: number): number {
    if (y + needed > PAGE_H - MARGIN) {
      doc.addPage();
      return MARGIN;
    }
    return y;
  }
}

// ── Mappers ─────────────────────────────────────────────────────

/** CV Builder draft → ATS model. Pass the display (possibly anonymised) personal info. */
export function resumeDataToAts(
  data: ResumeData, pi = data.personalInfo, settings?: ResumeSettings,
): AtsResumeModel {
  const contact = [pi.email, pi.phone, pi.location].filter(Boolean).join('  ·  ');
  const links = [pi.linkedin, pi.github, pi.website, ...data.socials.map(s => s.url)]
    .filter(Boolean).join('  ·  ');
  // Build each section tagged with its layout id, then order/filter by the builder's column
  // config so the ATS export matches the layout the user arranged (was hard-coded before).
  const built: { id: string; section: AtsSection }[] = [];

  if (data.strengths.length) {
    built.push({ id: 'strengths', section: { heading: 'Strengths', lines: [data.strengths.map(s => s.title).filter(Boolean).join('  ·  ')] } });
  }
  if (data.experience.length) {
    built.push({ id: 'experience', section: {
      heading: 'Experience',
      items: data.experience.map(e => ({
        title: [e.title, e.company].filter(Boolean).join(' — '),
        meta: [`${e.startDate || ''} – ${e.current ? 'Present' : e.endDate || ''}`.trim(), e.location]
          .filter(v => v && v !== '–').join('  ·  '),
        body: stripHtml(e.description),
        bullets: [],
      })),
    } });
  }
  const skillLines = groupedSkillLines(data.skills);
  if (skillLines.length) built.push({ id: 'skills', section: { heading: 'Skills', lines: skillLines } });
  if (data.projects.length) {
    built.push({ id: 'projects', section: {
      heading: 'Projects',
      items: data.projects.map(p => ({
        title: p.name,
        meta: [p.date, p.link].filter(Boolean).join('  ·  '),
        body: stripHtml(p.description),
      })),
    } });
  }
  if (data.education.length) {
    built.push({ id: 'education', section: {
      heading: 'Education',
      items: data.education.map(e => ({
        title: [e.degree, e.school].filter(Boolean).join(' — '),
        meta: `${e.startDate || ''} – ${e.current ? 'Present' : e.endDate || ''}`.trim(),
      })),
    } });
  }
  if (data.certifications.length) {
    built.push({ id: 'certifications', section: {
      heading: 'Certifications',
      lines: data.certifications.map(c => [c.name, c.issuer, c.date].filter(Boolean).join(' — ')),
    } });
  }
  if (data.languages.length) {
    built.push({ id: 'languages', section: {
      heading: 'Languages',
      lines: [data.languages.map(l => `${l.name} (${l.proficiency})`).join('  ·  ')],
    } });
  }
  data.customSections?.forEach((custom, idx) => {
    const lines = [
      ...(custom.body?.trim() ? [stripHtml(custom.body)!] : []),
      ...(custom.items ?? []).map(i => `•  ${i.text}`),
    ];
    if (lines.length) built.push({ id: `custom-${idx}`, section: { heading: custom.heading, lines } });
  });

  const sections = orderAtsSections(built, settings);

  return {
    name: pi.fullName || 'Curriculum Vitae',
    headline: pi.title || undefined,
    contactLine: contact || undefined,
    linksLine: links || undefined,
    summary: stripHtml(pi.summary),
    sections,
  };
}

/** StructuredDocument (master/tailored CV render model) → ATS model. */
export function structuredDocToAts(doc: StructuredDocument): AtsResumeModel {
  const id = doc.identity ?? {};
  const contact = [id.email, id.phone, id.location].filter(Boolean).join('  ·  ');
  const links = [id.linkedinUrl, id.githubUrl, id.websiteUrl].filter(Boolean).join('  ·  ');
  let summary: string | undefined;
  const sections: AtsSection[] = [];

  for (const section of doc.sections ?? []) {
    if (section.type === 'profile' && section.body) {
      summary = section.body;
      continue;
    }
    if (section.type === 'skills' && section.items?.length) {
      const lines = groupedSkillLines(
        section.items.map(i => ({ id: '', name: i.title ?? '', category: i.category })));
      if (lines.length) sections.push({ heading: section.heading, lines });
      continue;
    }
    if (section.items?.length) {
      sections.push({
        heading: section.heading,
        items: section.items.map(item => ({
          title: [item.title, item.subtitle].filter(Boolean).join(' — '),
          meta: [item.dateRange, item.location].filter(Boolean).join('  ·  '),
          body: item.description,
          bullets: item.bullets ?? [],
        })),
      });
    } else if (section.body) {
      sections.push({ heading: section.heading, lines: [section.body] });
    }
  }

  return {
    name: id.name || 'Curriculum Vitae',
    headline: id.headline || undefined,
    contactLine: contact || undefined,
    linksLine: links || undefined,
    summary,
    sections,
  };
}

/**
 * Orders and filters ATS sections to match the resume-builder's column layout: sections follow
 * the combined leftColumn+rightColumn order and hidden sections are dropped. Sections not present
 * in the config (e.g. custom sections) keep their build order at the end. Without settings, the
 * build order is used unchanged.
 */
function orderAtsSections(
  built: { id: string; section: AtsSection }[], settings?: ResumeSettings,
): AtsSection[] {
  if (!settings) return built.map(b => b.section);
  const config = [...(settings.leftColumn ?? []), ...(settings.rightColumn ?? [])];
  const rank = new Map(config.map((c, i) => [c.id, i]));
  const hidden = new Set(config.filter(c => !c.visible).map(c => c.id));
  return built
    .filter(b => !hidden.has(b.id))
    .map((b, i) => ({ b, i }))
    .sort((a, z) => (rank.get(a.b.id) ?? 900 + a.i) - (rank.get(z.b.id) ?? 900 + z.i))
    .map(({ b }) => b.section);
}

/**
 * Renders skills as one line per category (e.g. "Languages:  Java  ·  Kotlin"), categorised groups
 * first and uncategorised skills on a trailing line. A single uncategorised group collapses to the
 * previous flat single-line behaviour.
 */
function groupedSkillLines(skills: ResumeSkill[]): string[] {
  const groups = new Map<string, string[]>();
  const order: string[] = [];
  for (const s of skills) {
    if (!s.name) continue;
    const key = s.category?.trim() || '';
    if (!groups.has(key)) { groups.set(key, []); order.push(key); }
    groups.get(key)!.push(s.name);
  }
  order.sort((a, b) => (a === '' ? 1 : 0) - (b === '' ? 1 : 0));
  return order.map(k => (k ? `${k}:  ` : '') + groups.get(k)!.join('  ·  '));
}

function stripHtml(value?: string): string | undefined {
  if (!value) return undefined;
  if (!value.includes('<')) return value;
  const div = document.createElement('div');
  div.innerHTML = value.replace(/<\/(p|li|ul|ol)>/g, '</$1>\n');
  return (div.textContent ?? '').replace(/\n{3,}/g, '\n\n').trim() || undefined;
}
