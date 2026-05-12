# XL Logic Website

The static project homepage lives in this folder.

## Pages

- index.html: landing page and positioning
- features.html: feature set and architecture overview
- python.html: Python onboarding and code examples
- builder.html: no-code builder and logic blocks
- blocks.html: block families and current status
- docs.html: links back to the existing repository documents

## View locally

Open directly:

- load docs/site/index.html in a browser

Or start a small local server:

```powershell
Set-Location "D:\projekt\XLLogic"
py -m http.server 4173 -d docs/site
```

Then open http://localhost:4173 in the browser.

## Note

The website is meant to be the branding and onboarding layer. The deeper technical details should continue to live in README.md and the markdown files under docs/.
