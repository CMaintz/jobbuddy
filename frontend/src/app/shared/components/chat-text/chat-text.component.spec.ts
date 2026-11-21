import { TestBed } from '@angular/core/testing';
import { ChatTextComponent } from './chat-text.component';

function render(text: string): HTMLElement {
  TestBed.configureTestingModule({ imports: [ChatTextComponent] });
  const fixture = TestBed.createComponent(ChatTextComponent);
  fixture.componentRef.setInput('text', text);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('ChatTextComponent', () => {
  it('renders bold, italic and inline code inside paragraphs', () => {
    const el = render('Det er **vigtigt** og *lidt* `kode`.\n\nNyt afsnit.');

    expect(el.querySelectorAll('p').length).toBe(2);
    expect(el.querySelector('strong')?.textContent).toBe('vigtigt');
    expect(el.querySelector('em')?.textContent).toBe('lidt');
    expect(el.querySelector('code')?.textContent).toBe('kode');
  });

  it('renders bullet and numbered lists', () => {
    const el = render('Fordele:\n- Aarhus\n- **Intern** kommunikation\n\n1. Arla\n2. Vestas');

    expect(Array.from(el.querySelectorAll('ul > li')).map(li => li.textContent?.trim()))
      .toEqual(['Aarhus', 'Intern kommunikation']);
    expect(Array.from(el.querySelectorAll('ol > li')).map(li => li.textContent?.trim()))
      .toEqual(['Arla', 'Vestas']);
    expect(el.querySelector('ul strong')?.textContent).toBe('Intern');
  });

  it('shows links, images and HTML as plain text, so nothing loads or links away', () => {
    const hostile = 'Se ![x](https://evil.example/p.png) og [klik](https://evil.example/login)'
      + ' <img src="https://evil.example/q"> <a href="https://evil.example">her</a>'
      + ' <script>alert(1)</script>';
    const el = render(hostile);

    expect(el.querySelector('img, a, script, iframe')).toBeNull();
    expect(el.textContent).toContain('![x](https://evil.example/p.png)');
    expect(el.textContent).toContain('[klik](https://evil.example/login)');
    expect(el.textContent).toContain('<img src="https://evil.example/q">');
  });

  it('shows a heading as an ordinary paragraph', () => {
    const el = render('## Opslag\nTre gode bud.');

    expect(el.querySelector('h1, h2, h3, h4, h5, h6')).toBeNull();
    expect(el.querySelector('p')?.textContent).toBe('Opslag\nTre gode bud.');
  });
});
