#!/usr/bin/env node
/** Batch D: entity-graph — extract ensureStyles() CSS to shared/css/aipp-sys-widgets.css */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outCss = path.resolve(__dirname, '../css/aipp-sys-widgets.css');
const widgetFile = path.resolve(
  __dirname,
  '../../entitir/world-entitir/src/main/resources/static/widgets/entity-graph/entity-graph.js',
);

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
  [/\bvar\(--active/g, 'var(--aipp-active'],
  [/\bvar\(--radius/g, 'var(--aipp-radius'],
  [/\bvar\(--bg/g, 'var(--aipp-bg'],
];

const HEX_TO_TOKEN = [
  ['#d0d8f0', 'var(--aipp-text)'],
  ['#13151f', 'var(--aipp-surface)'],
  ['#191c29', 'var(--aipp-surface2)'],
  ['#0d0f18', 'var(--aipp-surface3)'],
  ['#272b3e', 'var(--aipp-border)'],
  ['#7c6ff7', 'var(--aipp-accent)'],
  ['#9a90ff', 'var(--aipp-accent-hover)'],
  ['#6b7a9e', 'var(--aipp-text-dim)'],
  ['#3d4460', 'var(--aipp-text-muted)'],
  ['#ff4f6a', 'var(--aipp-danger)'],
  ['#4fbf8f', 'var(--aipp-success)'],
  ['#b9a8ff', 'var(--aipp-accent-hover)'],
  ['#8ec5ff', 'var(--aipp-info)'],
  ['#5eead4', 'var(--aipp-success)'],
  ['#fbbf24', 'var(--aipp-warning)'],
  ['#ff9fb4', 'var(--aipp-danger)'],
];

const RGBA_REPLACEMENTS = [
  [/rgba\(124,111,247,\.06\)/g, 'color-mix(in srgb, var(--aipp-accent) 6%, transparent)'],
  [/rgba\(124,111,247,\.10\)/g, 'color-mix(in srgb, var(--aipp-accent) 10%, transparent)'],
  [/rgba\(124,111,247,\.04\)/g, 'color-mix(in srgb, var(--aipp-accent) 4%, transparent)'],
  [/rgba\(124,111,247,\.12\)/g, 'color-mix(in srgb, var(--aipp-accent) 12%, transparent)'],
  [/rgba\(124,111,247,\.14\)/g, 'color-mix(in srgb, var(--aipp-accent) 14%, transparent)'],
  [/rgba\(124,111,247,\.15\)/g, 'color-mix(in srgb, var(--aipp-accent) 15%, transparent)'],
  [/rgba\(124,111,247,\.25\)/g, 'color-mix(in srgb, var(--aipp-accent) 25%, transparent)'],
  [/rgba\(124,111,247,\.35\)/g, 'color-mix(in srgb, var(--aipp-accent) 35%, transparent)'],
  [/rgba\(79,191,143,\.12\)/g, 'color-mix(in srgb, var(--aipp-success) 12%, transparent)'],
  [/rgba\(79,191,143,\.15\)/g, 'color-mix(in srgb, var(--aipp-success) 15%, transparent)'],
  [/rgba\(255,79,106,\.08\)/g, 'color-mix(in srgb, var(--aipp-danger) 8%, transparent)'],
  [/rgba\(255,79,106,\.12\)/g, 'color-mix(in srgb, var(--aipp-danger) 12%, transparent)'],
  [/rgba\(255,79,106,\.16\)/g, 'color-mix(in srgb, var(--aipp-danger) 16%, transparent)'],
  [/rgba\(245,158,11,\.15\)/g, 'color-mix(in srgb, var(--aipp-warning) 15%, transparent)'],
  [/rgba\(245,158,11,\.25\)/g, 'color-mix(in srgb, var(--aipp-warning) 25%, transparent)'],
  [/rgba\(16,185,129,\.15\)/g, 'color-mix(in srgb, var(--aipp-success) 15%, transparent)'],
  [/rgba\(16,185,129,\.25\)/g, 'color-mix(in srgb, var(--aipp-success) 25%, transparent)'],
  [/rgba\(78,154,241,\.12\)/g, 'color-mix(in srgb, var(--aipp-info) 12%, transparent)'],
  [/rgba\(45,212,191,\.12\)/g, 'color-mix(in srgb, var(--aipp-success) 12%, transparent)'],
  [/rgba\(251,191,36,\.14\)/g, 'color-mix(in srgb, var(--aipp-warning) 14%, transparent)'],
];

function convertCss(css) {
  let out = css;
  for (const [re, rep] of HOST_VAR_MAP) out = out.replace(re, rep);
  for (const [hex, token] of HEX_TO_TOKEN) out = out.split(hex).join(token);
  for (const [re, rep] of RGBA_REPLACEMENTS) out = out.replace(re, rep);
  out = out.replace(/\.eg-root/g, '.aipp-graph-root');
  out = out.replace(/\.eg-preview-shell/g, '.aipp-graph-preview-shell');
  return out;
}

function convertVars(text) {
  let out = text;
  for (const [re, rep] of HOST_VAR_MAP) out = out.replace(re, rep);
  return out;
}

function extractEnsureStylesCss(src) {
  const m = src.match(
    /function ensureStyles\(\) \{[\s\S]*?s\.textContent = `\n([\s\S]*?)`;\n  document\.head\.appendChild\(s\);\n\}/,
  );
  return m ? m[1] : null;
}

function stripEnsureStyles(src) {
  return src
    .replace(/const STYLE_ID = '[^']+';\n/, '')
    .replace(/\/\/ Wrapper styles only[\s\S]*?function ensureStyles\(\) \{[\s\S]*?\}\n\n/, '')
    .replace(/  ensureStyles\(\);\n/g, '');
}

let src = fs.readFileSync(widgetFile, 'utf8');
const cssRaw = extractEnsureStylesCss(src);
if (!cssRaw) {
  console.error('Could not extract ensureStyles CSS from entity-graph.js');
  process.exit(1);
}

let css = convertCss(cssRaw);
const append = `\n/* ── Batch D: entity-graph ── */\n\n/* entity-graph/entity-graph.js */\n${css}\n`;
fs.appendFileSync(outCss, append);

src = stripEnsureStyles(src);
src = src.split('eg-root').join('aipp-graph-root');
src = src.split('eg-preview-shell').join('aipp-graph-preview-shell');
src = convertVars(src);

fs.writeFileSync(widgetFile, src);
console.log('Migrated entity-graph.js');
console.log('Appended Batch D to', outCss);
