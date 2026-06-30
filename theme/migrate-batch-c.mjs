#!/usr/bin/env node
/** Batch C: world-list, decision-list, decision-chain-summary */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outCss = path.resolve(__dirname, '../css/aipp-sys-widgets.css');
const widgetsDir = path.resolve(__dirname, '../../entitir/world-entitir/src/main/resources/static/widgets');

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
  [/\bvar\(--accent(?!-)/g, 'var(--aipp-accent'],
  [/\bvar\(--danger/g, 'var(--aipp-danger'],
  [/\bvar\(--success/g, 'var(--aipp-success'],
  [/\bvar\(--active/g, 'var(--aipp-active'],
  [/\bvar\(--bg/g, 'var(--aipp-bg'],
];

const HEX_TO_TOKEN = [
  ['#d0d8f0', 'var(--aipp-text)'],
  ['#13151f', 'var(--aipp-surface)'],
  ['#272b3e', 'var(--aipp-border)'],
  ['#7c6ff7', 'var(--aipp-accent)'],
  ['#9a90ff', 'var(--aipp-accent-hover)'],
  ['#6b7a9e', 'var(--aipp-text-dim)'],
  ['#3d4460', 'var(--aipp-text-muted)'],
  ['#8b9cb8', 'var(--aipp-text-dim)'],
  ['#ffb74d', 'var(--aipp-warning)'],
  ['#ffcc66', 'var(--aipp-warning)'],
  ['#d8def8', 'var(--aipp-text)'],
  ['#f0a500', 'var(--aipp-warning)'],
  ['#ffb8c2', 'var(--aipp-danger)'],
];

function convertCss(css) {
  let out = css;
  for (const [re, rep] of HOST_VAR_MAP) out = out.replace(re, rep);
  for (const [hex, token] of HEX_TO_TOKEN) out = out.split(hex).join(token);
  out = out.replace(/border-color:#7c6ff7/g, 'border-color:var(--aipp-accent)');
  out = out.replace(/rgba\(124,111,247,\.06\)/g, 'color-mix(in srgb, var(--aipp-accent) 6%, transparent)');
  out = out.replace(/rgba\(124,111,247,\.10\)/g, 'color-mix(in srgb, var(--aipp-accent) 10%, transparent)');
  out = out.replace(/rgba\(124,111,247,\.04\)/g, 'color-mix(in srgb, var(--aipp-accent) 4%, transparent)');
  out = out.replace(/rgba\(124,111,247,\.15\)/g, 'color-mix(in srgb, var(--aipp-accent) 15%, transparent)');
  out = out.replace(/rgba\(124,111,247,\.25\)/g, 'color-mix(in srgb, var(--aipp-accent) 25%, transparent)');
  out = out.replace(/rgba\(124,111,247,\.12\)/g, 'color-mix(in srgb, var(--aipp-accent) 12%, transparent)');
  out = out.replace(/rgba\(107,122,158,\.2\)/g, 'color-mix(in srgb, var(--aipp-text-dim) 20%, transparent)');
  out = out.replace(/rgba\(255,152,0,\.15\)/g, 'color-mix(in srgb, var(--aipp-warning) 15%, transparent)');
  out = out.replace(/rgba\(79,191,143,\.15\)/g, 'color-mix(in srgb, var(--aipp-success) 15%, transparent)');
  out = out.replace(/rgba\(79,191,143,\.18\)/g, 'color-mix(in srgb, var(--aipp-success) 18%, transparent)');
  out = out.replace(/rgba\(255,79,106,\.16\)/g, 'color-mix(in srgb, var(--aipp-danger) 16%, transparent)');
  out = out.replace(/rgba\(255,79,106,\.08\)/g, 'color-mix(in srgb, var(--aipp-danger) 8%, transparent)');
  out = out.replace(/rgba\(255,79,106,\.1\)/g, 'color-mix(in srgb, var(--aipp-danger) 10%, transparent)');
  out = out.replace(/rgba\(255,193,7,\.08\)/g, 'color-mix(in srgb, var(--aipp-warning) 8%, transparent)');
  out = out.replace(/rgba\(255,193,7,\.25\)/g, 'color-mix(in srgb, var(--aipp-warning) 25%, transparent)');
  out = out.replace(/rgba\(240,165,0,\.08\)/g, 'color-mix(in srgb, var(--aipp-warning) 8%, transparent)');
  out = out.replace(/rgba\(240,165,0,\.12\)/g, 'color-mix(in srgb, var(--aipp-warning) 12%, transparent)');
  return out;
}

function stripWidgetCss(src) {
  return src
    .replace(/const STYLE_ID = '[^']+';\n\n?/g, '')
    .replace(/const CSS = `\n[\s\S]*?`;\n\n?/g, '')
    .replace(/function ensureStyles\(\) \{[\s\S]*?\}\n\n?/g, '')
    .replace(/  ensureStyles\(\);\n/g, '');
}

function extractCss(src) {
  const m = src.match(/const CSS = `\n([\s\S]*?)`;/);
  return m ? m[1] : null;
}

const JOBS = [
  { file: 'world-list/world-list.js', from: 'wl-', to: 'aipp-world-' },
  { file: 'decision-list/decision-list.js', from: 'dl-', to: 'aipp-decision-' },
  { file: 'decision-chain-summary/decision-chain-summary.js', from: 'dcs-', to: 'aipp-chain-' },
];

let append = '\n/* ── Batch C: entitir list widgets ── */\n';

for (const { file, from, to } of JOBS) {
  const full = path.join(widgetsDir, file);
  let src = fs.readFileSync(full, 'utf8');
  const cssRaw = extractCss(src);
  if (!cssRaw) {
    console.warn('No CSS in', file);
    continue;
  }
  let css = convertCss(cssRaw);
  css = css.replaceAll(`.${from}`, `.${to}`);
  append += `\n/* ${file} */\n${css}\n`;

  src = stripWidgetCss(src);
  src = src.split(from).join(to);
  fs.writeFileSync(full, src);
  console.log('Migrated', file);
}

fs.appendFileSync(outCss, append);
console.log('Appended Batch C to', outCss);
