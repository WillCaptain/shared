#!/usr/bin/env node
/** Batch B: memory-manager + world-detail → shared/css/aipp-sys-widgets.css */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
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
  {
    file: path.resolve(__dirname, '../../entitir/world-entitir/src/main/resources/static/widgets/world-detail/world-detail.js'),
    cssPrefix: 'wd-',
    newPrefix: 'aipp-detail-',
    jsReplacements: [['wd-', 'aipp-detail-']],
  },
  {
    file: path.resolve(__dirname, '../../ones/memory-one/src/main/resources/static/widgets/memory-manager/memory-manager.js'),
    cssPrefix: 'mm-root',
    cssPrefixAlt: 'mem-',
    newRoot: 'aipp-mem-root',
    newPrefix: 'aipp-mem-',
    jsReplacements: [
      ['mm-root', 'aipp-mem-root'],
      ['.mem-', '.aipp-mem-'],
      ["class=\"mem-", "class=\"aipp-mem-"],
      ["class='mem-", "class='aipp-mem-"],
      ["'mem-", "'aipp-mem-"],
      [' mem-', ' aipp-mem-'],
    ],
  },
];

let append = '\n/* ── Batch B: AIPP widgets (world-detail, memory-manager) ── */\n';

for (const job of JOBS) {
  let src = fs.readFileSync(job.file, 'utf8');
  const cssRaw = extractCss(src);
  if (!cssRaw) {
    console.warn('No CSS in', job.file);
    continue;
  }
  let css = convertVars(cssRaw);
  if (job.cssPrefix && job.newPrefix) {
    css = css.replaceAll(`.${job.cssPrefix}`, `.${job.newPrefix}`);
  }
  if (job.cssPrefixAlt && job.newPrefix) {
    css = css.replaceAll(`.${job.cssPrefixAlt}`, `.${job.newPrefix}`);
  }
  if (job.newRoot) {
    css = css.replaceAll(`.mm-root`, `.${job.newRoot}`);
  }
  append += `\n/* ${path.basename(job.file)} */\n${css}\n`;

  src = stripWidgetCss(src);
  for (const [from, to] of job.jsReplacements) {
    src = src.split(from).join(to);
  }
  // memory-manager: SVG / inline host vars
  src = convertVars(src);
  fs.writeFileSync(job.file, src);
  console.log('Migrated', job.file);
}

fs.appendFileSync(outCss, append);
console.log('Appended Batch B CSS to', outCss);
