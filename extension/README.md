# Jobbuddy Job Capture (browser extension)

Captures the job posting you're viewing — LinkedIn, a company career page, any job board —
and opens Jobbuddy's apply screen with the title, company, location, and description prefilled.
From there the normal flow takes over: one click creates the job + application and generates
the document you picked.

## How it works

1. The popup injects a scraper into the current tab. It prefers **schema.org `JobPosting`
   JSON-LD** (present on most job boards and ATS pages: LinkedIn, Indeed, Greenhouse, Lever,
   Jobindex, …). If none is found it falls back to your text selection or the page's main content.
2. The capture is parked in `chrome.storage` and a Jobbuddy tab opens.
3. A content script on the Jobbuddy origin moves the capture into the app's `localStorage`
   (`jb-captured-job`); the apply screen consumes it and shows a "Captured from …" banner.

No credentials live in the extension — job creation happens through your logged-in app session.

## Install (developer mode)

1. Open `chrome://extensions` (or `edge://extensions`).
2. Enable **Developer mode**.
3. **Load unpacked** → select this `extension/` folder.

## Configuration

The popup has a **Jobbuddy URL** field (default `http://localhost:4200`). If you host the app
elsewhere, set the URL there **and** add the origin to `content_scripts[0].matches` in
`manifest.json` (then reload the extension) so the handoff bridge runs on that origin.

## Tips

- If a page has no structured data and grabs too much text, select the job description
  first — a selection longer than ~200 characters wins over the page heuristic.
- Captures expire after 10 minutes if not consumed by the apply screen.
