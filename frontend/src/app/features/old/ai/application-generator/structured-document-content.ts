import { DocumentTheme, StructuredDocument } from '../../../core/models/structured-document.model';

export function applyRenderOptions(
  document: StructuredDocument,
  showProfileImage: boolean,
  theme: DocumentTheme
): StructuredDocument {
  return {
    ...document,
    options: {
      ...(document.options ?? { showProfileImage: false }),
      showProfileImage,
      theme
    }
  };
}

export function cvPlainText(document: StructuredDocument): string {
  const lines: string[] = [];
  for (const section of document.sections ?? []) {
    lines.push(section.heading);
    if (section.body) lines.push(section.body);
    for (const item of section.items ?? []) {
      if (item.title) lines.push(item.title);
      if (item.subtitle) lines.push(item.subtitle);
      if (item.dateRange) lines.push(item.dateRange);
      if (item.description) lines.push(item.description);
      for (const bullet of item.bullets ?? []) lines.push(`- ${bullet}`);
      if ((item.technologies ?? []).length > 0) lines.push((item.technologies ?? []).join(', '));
    }
    lines.push('');
  }
  return lines.join('\n').trim();
}

export function documentText(document: StructuredDocument): string {
  return document.documentType === 'CV' ? cvPlainText(document) : document.bodyContent ?? '';
}

export function editorHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>');
}
