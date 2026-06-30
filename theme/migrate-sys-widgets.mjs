#!/usr/bin/env node
/**
 * One-time helper: extract sys widget CSS → shared/css/aipp-sys-widgets.css
 * and strip STYLE blocks from widget JS (class renames applied).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const sysDir = path.resolve(__dirname, '../../ones/world-one/src/main/resources/static/widgets/system');
const outCss = path.resolve(__dirname, '../css/aipp-sys-widgets.css');

const HOST_VAR_MAP = [
  [/\bvar\(--text-dim/g, 'var(--aipp-text-dim'],
  [/\bvar\(--text-muted/g, 'var(--aipp-text-muted'],
  [/\bvar\(--text(?!-)/g, 'var(--aipp-text'],
  [/\bvar\(--surface3/g, 'var(--aipp-surface3'],
  [/\bvar\(--surface2/g, 'var(--aipp-surface2'],
  [/\bvar\(--surface(?!2|3)/g, 'var(--aipp-surface'],
  [/\bvar\(--border2/g, 'var(--aipp-border2'],
  [/\bvar\(--border(?!2)/g, 'var(--aipp-border'],
  [/\bvar\(--accent-h/g, 'var(--aipp-accent-hover'],
  [/\bvar\(--accent-glow/g, 'var(--aipp-accent-glow'],
  [/\bvar\(--accent(?!-)/g, 'var(--aipp-accent'],
  [/\bvar\(--danger/g, 'var(--aipp-danger'],
  [/\bvar\(--success/g, 'var(--aipp-success'],
  [/\bvar\(--active/g, 'var(--aipp-active'],
  [/\bvar\(--radius/g, 'var(--aipp-radius'],
  [/\bvar\(--bg/g, 'var(--aipp-bg'],
];

function convertVars(css) {
  let out = css;
  for (const [re, rep] of HOST_VAR_MAP) out = out.replace(re, rep);
  return out;
}

const EXTRACT = [
  { file: 'capability-browser.js', prefix: 'cap-', newPrefix: 'aipp-cap-' },
  { file: 'capability-tree.js', prefix: 'ct-', newPrefix: 'aipp-tree-' },
  { file: 'configuration.js', prefix: 'sys-cfg-', newPrefix: 'aipp-cfg-' },
  { file: 'parameter-missing.js', prefix: 'spm-', newPrefix: 'aipp-param-' },
];

let combined = `/* Shared Host system widget layouts — no per-widget injected CSS.
 * Uses var(--aipp-*) only. Hand-maintained; extract source was sys widget JS.
 */\n\n`;

for (const { file, prefix, newPrefix } of EXTRACT) {
  const src = fs.readFileSync(path.join(sysDir, file), 'utf8');
  const m = src.match(/const CSS = `\n([\s\S]*?)`;/);
  if (!m) {
    console.warn('No CSS in', file);
    continue;
  }
  let css = m[1];
  css = convertVars(css);
  css = css.replaceAll(`.${prefix}`, `.${newPrefix}`);
  combined += `/* ── ${file} ── */\n${css}\n`;
}

fs.writeFileSync(outCss, combined);
console.log('Wrote', outCss);

for (const { file, prefix, newPrefix } of EXTRACT) {
  let src = fs.readFileSync(path.join(sysDir, file), 'utf8');
  src = src.replace(/const STYLE_ID = '[^']+';\n\nconst CSS = `\n[\s\S]*?`;\n\n/, '');
  src = src.replace(/function ensureStyles\(\) \{[\s\S]*?\}\n\n/, '');
  src = src.replace(/  ensureStyles\(\);\n/g, '');
  src = src.replaceAll(prefix, newPrefix);
  fs.writeFileSync(path.join(sysDir, file), src);
  console.log('Migrated', file);
}
