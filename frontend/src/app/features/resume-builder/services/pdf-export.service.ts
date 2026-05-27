import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class PdfExportService {
  async download(el: HTMLElement, name: string, docSize: 'A4' | 'Letter' = 'A4'): Promise<void> {
    const html2canvas = (await import('html2canvas')).default;
    const jsPDF = (await import('jspdf')).jsPDF;

    const canvas = await html2canvas(el, {
      scale: 2,
      useCORS: true,
      allowTaint: true,
      backgroundColor: '#ffffff',
    });

    const imgData = canvas.toDataURL('image/png');
    const [w, h] = docSize === 'A4' ? [210, 297] : [215.9, 279.4];

    const pdf = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: docSize === 'A4' ? 'a4' : 'letter',
    });

    const imgW = w;
    const imgH = (canvas.height * imgW) / canvas.width;
    let yPos = 0;

    while (yPos < imgH) {
      if (yPos > 0) pdf.addPage();
      pdf.addImage(imgData, 'PNG', 0, -yPos, imgW, imgH);
      yPos += h;
    }

    pdf.save(`${name || 'resume'}.pdf`);
  }

  async print(el: HTMLElement): Promise<void> {
    const html2canvas = (await import('html2canvas')).default;
    const canvas = await html2canvas(el, { scale: 2, useCORS: true, backgroundColor: '#ffffff' });
    const dataUrl = canvas.toDataURL('image/png');

    const iframe = document.createElement('iframe');
    iframe.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:0;height:0';
    document.body.appendChild(iframe);
    iframe.contentDocument!.write(`<img src="${dataUrl}" style="width:100%">`);
    iframe.contentDocument!.close();
    iframe.contentWindow!.focus();
    iframe.contentWindow!.print();
    setTimeout(() => document.body.removeChild(iframe), 1000);
  }
}
