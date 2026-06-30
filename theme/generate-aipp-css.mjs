#!/usr/bin/env node
/**
 * Generate aipp-tokens.css and themes/light.css from aipp-themes.json.
 *
 * Usage (from repo root or shared/theme):
 *   node shared/theme/generate-aipp-css.mjs
 *   node shared/theme/generate-aipp-css.mjs --copy-to ones/world-one/src/main/resources/static/css
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');
const jsonPath = path.join(__dirname, 'aipp-themes.json');
const outCssDir = path.join(root, 'css');
const outThemesDir = path.join(outCssDir, 'themes');

const TOKEN_TO_VAR = {
  bg: '--aipp-bg',
  surface: '--aipp-surface',
  surface2: '--aipp-surface2',
  surface3: '--aipp-surface3',
  text: '--aipp-text',
  textDim: '--aipp-text-dim',
  textMuted: '--aipp-text-muted',
  border: '--aipp-border',
  border2: '--aipp-border2',
  accent: '--aipp-accent',
  accentHover: '--aipp-accent-hover',
  accentGlow: '--aipp-accent-glow',
  active: '--aipp-active',
  danger: '--aipp-danger',
  success: '--aipp-success',
  warning: '--aipp-warning',
  info: '--aipp-info',
  font: '--aipp-font',
  fontMono: '--aipp-font-mono',
  fontSize: '--aipp-font-size',
  fontSizeSm: '--aipp-font-size-sm',
  fontSizeLg: '--aipp-font-size-lg',
  radius: '--aipp-radius',
  radiusSm: '--aipp-radius-sm',
  radiusLg: '--aipp-radius-lg',
  radiusPill: '--aipp-radius-pill',
};

const HOST_COMPAT = {
  '--aipp-bg': '--bg',
  '--aipp-surface': '--surface',
  '--aipp-surface2': '--surface2',
  '--aipp-surface3': '--surface3',
  '--aipp-text': '--text',
  '--aipp-text-dim': '--text-dim',
  '--aipp-text-muted': '--text-muted',
  '--aipp-border': '--border',
  '--aipp-border2': '--border2',
  '--aipp-accent': '--accent',
  '--aipp-accent-hover': '--accent-h',
  '--aipp-accent-glow': '--accent-glow',
  '--aipp-active': '--active',
  '--aipp-danger': '--danger',
  '--aipp-success': '--success',
  '--aipp-radius': '--radius',
};

const HEADER = `/* GENERATED — do not edit. Source: shared/theme/aipp-themes.json
 * Regenerate: node shared/theme/generate-aipp-css.mjs
 */\n`;

const SYS_WIDGETS_HEADER = `/* SHARED — edit only shared/css/aipp-sys-widgets.css
 * ones-shell copy: node shared/theme/generate-aipp-css.mjs
 * world-one: Maven copy-shared-css from shared/css (see world-one/pom.xml)
 */\n`;

const PRIMITIVES_HEADER = `/* SHARED — hand-maintained primitives. Sync via generate-aipp-css.mjs
 * Source: shared/css/aipp-primitives.css
 */\n`;

const COPY_FILES = ['aipp-tokens.css', 'aipp-primitives.css', 'aipp-sys-widgets.css'];

/** Deployment copies for hosts that do not Maven-copy from shared (e.g. ones-shell). */
const DEFAULT_COPY_TARGETS = [
  path.join(root, '../ones/ones-shell/src/css'),
];

function mergePreset(base, preset) {
  return { ...base, ...preset };
}

function resolveTokens(data, presetName) {
  const base = { ...data.tokens };
  const preset = data.presets[presetName] || {};
  const merged = mergePreset(base, preset);
  delete merged.language;
  delete merged.darkMode;
  return merged;
}

function formatValue(key, value) {
  if (key.startsWith('font') && key !== 'fontSize' && key !== 'fontSizeSm' && key !== 'fontSizeLg') {
    return String(value);
  }
  if (key.startsWith('radius') || key.startsWith('fontSize')) {
    return `${value}px`;
  }
  return String(value);
}

function tokensToCssVars(tokens) {
  const lines = [];
  for (const [key, cssVar] of Object.entries(TOKEN_TO_VAR)) {
    if (tokens[key] == null) continue;
    lines.push(`  ${cssVar}: ${formatValue(key, tokens[key])};`);
  }
  return lines;
}

function buildRootBlock(tokens, hostLayout, { includeCompat = false, includeLayout = true } = {}) {
  const lines = [':root {'];
  lines.push(...tokensToCssVars(tokens));
  if (includeCompat) {
    lines.push('');
    lines.push('  /* Host compat aliases (migration — prefer --aipp-* in new code) */');
    for (const [aippVar, hostVar] of Object.entries(HOST_COMPAT)) {
      lines.push(`  ${hostVar}: var(${aippVar});`);
    }
  }
  if (includeLayout && hostLayout) {
    lines.push('');
    lines.push('  /* Host layout (not theme tokens) */');
    if (hostLayout.funcbarW) lines.push(`  --funcbar-w: ${hostLayout.funcbarW};`);
    if (hostLayout.panelW) lines.push(`  --panel-w: ${hostLayout.panelW};`);
    if (hostLayout.chatPanelW) lines.push(`  --chat-panel-w: ${hostLayout.chatPanelW};`);
  }
  lines.push('}');
  return lines.join('\n');
}

function copyCssTo(dest) {
  fs.mkdirSync(path.join(dest, 'themes'), { recursive: true });
  for (const file of COPY_FILES) {
    const src = path.join(outCssDir, file);
    if (fs.existsSync(src)) fs.copyFileSync(src, path.join(dest, file));
  }
  fs.copyFileSync(path.join(outThemesDir, 'light.css'), path.join(dest, 'themes', 'light.css'));
}

function checkCopiesInSync(targets) {
  let ok = true;
  for (const dest of targets) {
    for (const file of COPY_FILES) {
      const src = path.join(outCssDir, file);
      const dst = path.join(dest, file);
      if (!fs.existsSync(dst)) {
        console.error(`MISSING copy: ${dst}`);
        ok = false;
        continue;
      }
      const a = fs.readFileSync(src);
      const b = fs.readFileSync(dst);
      if (!a.equals(b)) {
        console.error(`DRIFT: ${dst} differs from ${src} — run: node shared/theme/generate-aipp-css.mjs`);
        ok = false;
      }
    }
    const lightSrc = path.join(outThemesDir, 'light.css');
    const lightDst = path.join(dest, 'themes', 'light.css');
    if (!fs.existsSync(lightDst) || !fs.readFileSync(lightSrc).equals(fs.readFileSync(lightDst))) {
      console.error(`DRIFT: ${lightDst} — run: node shared/theme/generate-aipp-css.mjs`);
      ok = false;
    }
  }
  return ok;
}

function main() {
  const data = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
  const darkTokens = resolveTokens(data, 'dark');
  const lightTokens = resolveTokens(data, 'light');

  fs.mkdirSync(outThemesDir, { recursive: true });

  const tokensCss = `${HEADER}\n${buildRootBlock(darkTokens, data.hostLayout)}\n`;
  const lightCss = `${HEADER}\n[data-aipp-theme="light"] {\n${tokensToCssVars(lightTokens).join('\n')}\n}\n`;

  fs.writeFileSync(path.join(outCssDir, 'aipp-tokens.css'), tokensCss);

  // Ensure hand-maintained shared files have sync headers (content unchanged).
  for (const [file, hdr] of [
    ['aipp-primitives.css', PRIMITIVES_HEADER],
    ['aipp-sys-widgets.css', SYS_WIDGETS_HEADER],
  ]) {
    const p = path.join(outCssDir, file);
    if (fs.existsSync(p)) {
      let body = fs.readFileSync(p, 'utf8');
      if (!body.startsWith('/* SHARED') && !body.startsWith('/* GENERATED')) {
        fs.writeFileSync(p, hdr + body);
      }
    }
  }

  fs.writeFileSync(path.join(outThemesDir, 'light.css'), lightCss);

  const copyTargets = [];
  for (let i = 0; i < process.argv.length; i++) {
    if (process.argv[i] === '--copy-to' && process.argv[i + 1]) {
      copyTargets.push(path.resolve(process.argv[i + 1]));
    }
  }
  const targets = copyTargets.length > 0 ? copyTargets : DEFAULT_COPY_TARGETS;

  if (process.argv.includes('--check')) {
    const ok = checkCopiesInSync(targets);
    if (!ok) process.exit(1);
    console.log('All CSS copies in sync with shared/css/');
    return;
  }

  for (const dest of targets) {
    if (!fs.existsSync(path.dirname(dest))) continue;
    copyCssTo(dest);
    console.log(`Copied CSS to ${dest}`);
  }

  console.log('Generated shared/css/aipp-tokens.css and shared/css/themes/light.css');
}

main();
