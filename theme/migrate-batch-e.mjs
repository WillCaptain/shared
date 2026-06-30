#!/usr/bin/env node
/** Batch E: decision-reactor-console + note-one-wiki → shared/css/aipp-sys-widgets.css */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outCss = path.resolve(__dirname, '../css/aipp-sys-widgets.css');

const HOST_VAR_MAP = [
  [/\bvar\(--text-dim/g, 'var(--aipp-text-dim'],
  [/\bvar\(--text-muted/g, 'var(--aipp-text-muted'],
  [/\bvar\(--text(?!-)/g, 'var(--aipp-text'],
  [/\bvar\(--surface2/g, 'var(--aipp-surface2'],
  [/\bvar\(--surface3/g, 'var(--aipp-surface3'],
  [/\bvar\(--surface(?!2|3)/g, 'var(--aipp-surface'],
  [/\bvar\(--border2/g, 'var(--aipp-border2'],
  [/\bvar\(--border(?!2)/g, 'var(--aipp-border'],
  [/\bvar\(--accent-h/g, 'var(--aipp-accent-hover'],
  [/\bvar\(--accent-glow/g, 'var(--aipp-accent-glow'],
  [/\bvar\(--accent(?!-)/g, 'var(--aipp-accent'],
  [/\bvar\(--danger/g, 'var(--aipp-danger'],
  [/\bvar\(--success/g, 'var(--aipp-success'],
  [/\bvar\(--warning/g, 'var(--aipp-warning'],
  [/\bvar\(--info/g, 'var(--aipp-info'],
  [/\bvar\(--active/g, 'var(--aipp-active'],
  [/\bvar\(--radius/g, 'var(--aipp-radius'],
  [/\bvar\(--bg/g, 'var(--aipp-bg'],
];

const DRC_VAR_MAP = [
  [/\bvar\(--drc-text-muted/g, 'var(--aipp-text-dim'],
  [/\bvar\(--drc-text-dim/g, 'var(--aipp-text'],
  [/\bvar\(--drc-text/g, 'var(--aipp-text'],
];

const VSCODE_VAR_MAP = [
  [/\bvar\(--vscode-foreground[^)]*\)/g, 'var(--aipp-text)'],
  [/\bvar\(--vscode-editor-background[^)]*\)/g, 'var(--aipp-bg)'],
  [/\bvar\(--vscode-panel-border[^)]*\)/g, 'var(--aipp-border)'],
  [/\bvar\(--vscode-focusBorder[^)]*\)/g, 'var(--aipp-accent)'],
  [/\bvar\(--vscode-badge-background[^)]*\)/g, 'var(--aipp-surface2)'],
  [/\bvar\(--vscode-badge-foreground[^)]*\)/g, 'var(--aipp-text-dim)'],
  [/\bvar\(--vscode-descriptionForeground[^)]*\)/g, 'var(--aipp-text-dim)'],
];

const HEX_TO_TOKEN = [
  ['#e8ecf8', 'var(--aipp-text)'],
  ['#b4bdd8', 'var(--aipp-text-dim)'],
  ['#c8d0e8', 'var(--aipp-text)'],
  ['#d8d2ff', 'var(--aipp-accent-hover)'],
  ['#b8afff', 'var(--aipp-accent-hover)'],
  ['#d0d8f0', 'var(--aipp-text)'],
  ['#13151f', 'var(--aipp-surface)'],
  ['#13172b', 'var(--aipp-surface2)'],
  ['#191c29', 'var(--aipp-surface2)'],
  ['#272b3e', 'var(--aipp-border)'],
  ['#7c6ff7', 'var(--aipp-accent)'],
  ['#ef4444', 'var(--aipp-danger)'],
  ['#ff8a80', 'var(--aipp-danger)'],
  ['#86efac', 'var(--aipp-success)'],
  ['#fcd34d', 'var(--aipp-warning)'],
  ['#e2e8f0', 'var(--aipp-text)'],
  ['#3aa675', 'var(--aipp-success)'],
  ['#8a7cff', 'var(--aipp-accent)'],
  ['#e8b86d', 'var(--aipp-warning)'],
  ['#0a84ff', 'var(--aipp-accent)'],
  ['#1e1e1e', 'var(--aipp-bg)'],
  ['#333', 'var(--aipp-border)'],
  ['#444', 'var(--aipp-border)'],
  ['#ddd', 'var(--aipp-text)'],
  ['#ccc', 'var(--aipp-text-dim)'],
  ['#aaa', 'var(--aipp-text-dim)'],
];

const RGBA_REPLACEMENTS = [
  [/rgba\(255,255,255,\.04\)/g, 'color-mix(in srgb, var(--aipp-text) 4%, transparent)'],
  [/rgba\(255,255,255,\.03\)/g, 'color-mix(in srgb, var(--aipp-text) 3%, transparent)'],
  [/rgba\(255,255,255,\.06\)/g, 'color-mix(in srgb, var(--aipp-text) 6%, transparent)'],
  [/rgba\(255,255,255,\.08\)/g, 'color-mix(in srgb, var(--aipp-text) 8%, transparent)'],
  [/rgba\(255,255,255,\.12\)/g, 'color-mix(in srgb, var(--aipp-text) 12%, transparent)'],
  [/rgba\(255,255,255,\.15\)/g, 'color-mix(in srgb, var(--aipp-text) 15%, transparent)'],
  [/rgba\(255,255,255,\.18\)/g, 'color-mix(in srgb, var(--aipp-text) 18%, transparent)'],
  [/rgba\(124,111,247,\.08\)/g, 'color-mix(in srgb, var(--aipp-accent) 8%, transparent)'],
  [/rgba\(124,111,247,\.14\)/g, 'color-mix(in srgb, var(--aipp-accent) 14%, transparent)'],
  [/rgba\(124,111,247,\.18\)/g, 'color-mix(in srgb, var(--aipp-accent) 18%, transparent)'],
  [/rgba\(124,111,247,\.22\)/g, 'color-mix(in srgb, var(--aipp-accent) 22%, transparent)'],
  [/rgba\(124,111,247,\.35\)/g, 'color-mix(in srgb, var(--aipp-accent) 35%, transparent)'],
  [/rgba\(239,68,68,\.2\)/g, 'color-mix(in srgb, var(--aipp-danger) 20%, transparent)'],
  [/rgba\(34,197,94,\.12\)/g, 'color-mix(in srgb, var(--aipp-success) 12%, transparent)'],
  [/rgba\(34,197,94,\.35\)/g, 'color-mix(in srgb, var(--aipp-success) 35%, transparent)'],
  [/rgba\(251,191,36,\.12\)/g, 'color-mix(in srgb, var(--aipp-warning) 12%, transparent)'],
  [/rgba\(251,191,36,\.35\)/g, 'color-mix(in srgb, var(--aipp-warning) 35%, transparent)'],
  [/rgba\(10,132,255,\.12\)/g, 'color-mix(in srgb, var(--aipp-accent) 12%, transparent)'],
  [/rgba\(10,132,255,\.15\)/g, 'color-mix(in srgb, var(--aipp-accent) 15%, transparent)'],
  [/rgba\(10,132,255,\.2\)/g, 'color-mix(in srgb, var(--aipp-accent) 20%, transparent)'],
  [/rgba\(232,184,109,\.2\)/g, 'color-mix(in srgb, var(--aipp-warning) 20%, transparent)'],
  [/rgba\(232,184,109,\.35\)/g, 'color-mix(in srgb, var(--aipp-warning) 35%, transparent)'],
  [/rgba\(0,0,0,\.55\)/g, 'color-mix(in srgb, var(--aipp-bg) 55%, transparent)'],
];

function convertCss(css, { vscode = false, drc = false } = {}) {
  let out = css;
  if (drc) {
    out = out.replace(
      /\.drc-root\{[\s\S]*?color:var\(--drc-text\);\s*\}/,
      `.aipp-drc-root{
  width:100%;height:100%;min-height:0;display:flex;flex-direction:row;
  font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;font-size:13px;
  color:var(--aipp-text);
}`,
    );
  }
  for (const [re, rep] of HOST_VAR_MAP) out = out.replace(re, rep);
  if (drc) {
    for (const [re, rep] of DRC_VAR_MAP) out = out.replace(re, rep);
  }
  if (vscode) {
    for (const [re, rep] of VSCODE_VAR_MAP) out = out.replace(re, rep);
  }
  for (const [hex, token] of HEX_TO_TOKEN) out = out.split(hex).join(token);
  for (const [re, rep] of RGBA_REPLACEMENTS) out = out.replace(re, rep);
  return out;
}

function convertVars(text) {
  let out = text;
  for (const [re, rep] of HOST_VAR_MAP) out = out.replace(re, rep);
  for (const [re, rep] of DRC_VAR_MAP) out = out.replace(re, rep);
  for (const [re, rep] of VSCODE_VAR_MAP) out = out.replace(re, rep);
  return out;
}

function stripWidgetCss(src) {
  return src
    .replace(/const STYLE_ID = '[^']+';\n/, '')
    .replace(/const CSS = `\n[\s\S]*?`;\n\n?/, '')
    .replace(/function ensureStyles\(\) \{[\s\S]*?\}\n\n?/, '')
    .replace(/  ensureStyles\(\);\n/g, '');
}

function extractCss(src) {
  const m = src.match(/const CSS = `\n([\s\S]*?)`;/);
  return m ? m[1] : null;
}

const JOBS = [
  {
    file: path.resolve(
      __dirname,
      '../../ones/decision-reactor/src/main/resources/static/widgets/decision-reactor-console/decision-reactor-console.js',
    ),
    cssFrom: 'drc-',
    cssTo: 'aipp-drc-',
    jsFrom: 'drc-',
    jsTo: 'aipp-drc-',
    convertOpts: { drc: true },
  },
  {
    file: path.resolve(
      __dirname,
      '../../ones/note-one/src/main/resources/static/widgets/note-one-wiki/note-one-wiki.js',
    ),
    cssFrom: 'n1-',
    cssTo: 'aipp-wiki-',
    jsFrom: 'n1-',
    jsTo: 'aipp-wiki-',
    convertOpts: { vscode: true },
  },
];

let append = '\n/* ── Batch E: decision-reactor-console + note-one-wiki ── */\n';

for (const job of JOBS) {
  let src = fs.readFileSync(job.file, 'utf8');
  const cssRaw = extractCss(src);
  if (!cssRaw) {
    console.warn('No CSS in', job.file);
    continue;
  }
  let css = convertCss(cssRaw, job.convertOpts);
  css = css.replaceAll(`.${job.cssFrom}`, `.${job.cssTo}`);
  append += `\n/* ${path.basename(path.dirname(job.file))}/${path.basename(job.file)} */\n${css}\n`;

  src = stripWidgetCss(src);
  src = src.split(job.jsFrom).join(job.jsTo);
  src = convertVars(src);
  fs.writeFileSync(job.file, src);
  console.log('Migrated', job.file);
}

fs.appendFileSync(outCss, append);
console.log('Appended Batch E to', outCss);
