/**
 * The letter text as the HTML the PDF renderer reads. Edited letters already are HTML; generated
 * ones are plain text, where a blank line separates paragraphs and a single newline breaks the
 * line, as the letter paper shows them.
 */
export function letterBodyHtml(content: string): string {
  if (content.includes('<')) return content;
  return content.split(/\n{2,}/)
    .map(p => p.trim())
    .filter(Boolean)
    .map(p => `<p>${escapeHtml(p).replace(/\n/g, '<br>')}</p>`)
    .join('');
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
