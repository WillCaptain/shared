#!/usr/bin/env node
/**
 * Generate aipp-tokens.css and themes/*.css from aipp-themes.json.
 *
 * Usage (from repo root or shared/theme):
 *   node shared/theme/generate-aipp-css.mjs
 *   node shared/theme/generate-aipp-css.mjs --copy-to ones/once/src/css
 *   node shared/theme/generate-aipp-css.mjs --check
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');
const jsonPath = path.join(__dirname, 'aipp-themes.json');
const atmosphereJsonPath = path.join(__dirname, 'aipp-atmosphere.json');
const backgroundsJsonPath = path.join(__dirname, 'aipp-backgrounds.json');
const bgAnimationsJsonPath = path.join(__dirname, 'aipp-bg-animations.json');
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

const PRESET_META_KEYS = new Set([
  'language', 'darkMode', 'standard', 'label', 'description',
  'atmosphere', 'fx', 'background', 'bgAnimation', 'icon',
]);

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

const COPY_FILES = [
  'aipp-tokens.css',
  'aipp-primitives.css',
  'aipp-sys-widgets.css',
  'aipp-atmosphere.css',
  'aipp-backgrounds.css',
  'aipp-shell.css',
  'theme-presets.json',
  'atmosphere-presets.json',
  'background-presets.json',
  'bg-animation-presets.json',
];

/** Deployment copies for hosts that do not Maven-copy from shared (e.g. once). */
const DEFAULT_COPY_TARGETS = [
  path.join(root, '../ones/once/src/css'),
  path.join(root, '../ones/ones-shell/src/css'),
];

function mergePreset(base, preset) {
  return { ...base, ...preset };
}

function resolveTokens(data, presetName) {
  const base = { ...data.tokens };
  const preset = data.presets[presetName] || {};
  const merged = mergePreset(base, preset);
  for (const key of PRESET_META_KEYS) delete merged[key];
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

function presetSelectors(presetName) {
  if (presetName === 'light') {
    return ['[data-aipp-theme="light"]', '[data-aipp-palette="light"]'];
  }
  return [`[data-aipp-palette="${presetName}"]`];
}

function buildPresetCss(data, presetName) {
  const tokens = resolveTokens(data, presetName);
  const selectors = presetSelectors(presetName).join(',\n');
  return `${HEADER}\n${selectors} {\n${tokensToCssVars(tokens).join('\n')}\n}\n`;
}

function listThemeFiles(themesDir) {
  if (!fs.existsSync(themesDir)) return [];
  return fs.readdirSync(themesDir).filter((f) => f.endsWith('.css')).sort();
}

function buildPresetsCatalog(data) {
  const presetNames = Object.keys(data.presets || {}).sort();
  const presets = presetNames.map((id) => {
    const raw = data.presets[id] || {};
    const tokens = resolveTokens(data, id);
    return {
      id,
      standard: raw.standard === true,
      darkMode: raw.darkMode !== false,
      label: raw.label || { en: id, zh: id },
      description: raw.description || null,
      atmosphere: raw.atmosphere || 'none',
      fx: raw.fx || { glow: 'off', motion: 'full' },
      background: raw.background || { kind: 'none', id: '' },
      bgAnimation: raw.bgAnimation || 'none',
      icon: raw.icon || { id: 'once', src: 'img/once-icon.png' },
      preview: {
        bg: tokens.bg,
        surface: tokens.surface,
        surface2: tokens.surface2,
        text: tokens.text,
        textDim: tokens.textDim,
        border: tokens.border,
        accent: tokens.accent,
      },
    };
  });
  presets.sort((a, b) => {
    if (a.standard !== b.standard) return a.standard ? -1 : 1;
    if (a.standard && b.standard) {
      if (a.id === 'dark') return -1;
      if (b.id === 'dark') return 1;
      if (a.id === 'light') return -1;
      if (b.id === 'light') return 1;
    }
    return a.id.localeCompare(b.id);
  });
  return { version: data.version || 2, presets };
}

function copyCssTo(dest) {
  fs.mkdirSync(path.join(dest, 'themes'), { recursive: true });
  for (const file of COPY_FILES) {
    const src = path.join(outCssDir, file);
    if (fs.existsSync(src)) fs.copyFileSync(src, path.join(dest, file));
  }
  for (const file of listThemeFiles(outThemesDir)) {
    fs.copyFileSync(path.join(outThemesDir, file), path.join(dest, 'themes', file));
  }
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
      if (!fs.readFileSync(src).equals(fs.readFileSync(dst))) {
        console.error(`DRIFT: ${dst} — run: node shared/theme/generate-aipp-css.mjs`);
        ok = false;
      }
    }
    for (const file of listThemeFiles(outThemesDir)) {
      const src = path.join(outThemesDir, file);
      const dst = path.join(dest, 'themes', file);
      if (!fs.existsSync(dst) || !fs.readFileSync(src).equals(fs.readFileSync(dst))) {
        console.error(`DRIFT: ${dst} — run: node shared/theme/generate-aipp-css.mjs`);
        ok = false;
      }
    }
  }
  return ok;
}

function main() {
  const data = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
  const presetNames = Object.keys(data.presets || {}).sort();
  const darkTokens = resolveTokens(data, 'dark');

  fs.mkdirSync(outThemesDir, { recursive: true });

  const tokensCss = `${HEADER}\n${buildRootBlock(darkTokens, data.hostLayout)}\n`;
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

  const bundleParts = [];
  for (const presetName of presetNames) {
    if (presetName === 'dark') continue;
    const css = buildPresetCss(data, presetName);
    fs.writeFileSync(path.join(outThemesDir, `${presetName}.css`), css);
    bundleParts.push(css.trim());
  }
  fs.writeFileSync(path.join(outThemesDir, 'bundle.css'), `${bundleParts.join('\n\n')}\n`);

  fs.writeFileSync(
    path.join(outCssDir, 'theme-presets.json'),
    `${JSON.stringify(buildPresetsCatalog(data), null, 2)}\n`,
  );

  if (fs.existsSync(atmosphereJsonPath)) {
    const atmosphereData = JSON.parse(fs.readFileSync(atmosphereJsonPath, 'utf8'));
    fs.writeFileSync(
      path.join(outCssDir, 'atmosphere-presets.json'),
      `${JSON.stringify(atmosphereData, null, 2)}\n`,
    );
  }

  if (fs.existsSync(backgroundsJsonPath)) {
    const backgroundData = JSON.parse(fs.readFileSync(backgroundsJsonPath, 'utf8'));
    fs.writeFileSync(
      path.join(outCssDir, 'background-presets.json'),
      `${JSON.stringify(backgroundData, null, 2)}\n`,
    );
  }

  if (fs.existsSync(bgAnimationsJsonPath)) {
    const bgAnimationData = JSON.parse(fs.readFileSync(bgAnimationsJsonPath, 'utf8'));
    fs.writeFileSync(
      path.join(outCssDir, 'bg-animation-presets.json'),
      `${JSON.stringify(bgAnimationData, null, 2)}\n`,
    );
  }

  const copyTargets = [];
  for (let i = 0; i < process.argv.length; i++) {
    if (process.argv[i] === '--copy-to' && process.argv[i + 1]) {
      copyTargets.push(path.resolve(process.argv[i + 1]));
    }
  }
  const targets = copyTargets.length > 0 ? copyTargets : DEFAULT_COPY_TARGETS;

  if (process.argv.includes('--check')) {
    const existingTargets = targets.filter((dest) => fs.existsSync(path.dirname(dest)));
    if (existingTargets.length === 0) {
      console.log('No copy targets on disk — generated shared/css only');
      return;
    }
    const ok = checkCopiesInSync(existingTargets);
    if (!ok) process.exit(1);
    console.log('All CSS copies in sync with shared/css/');
    return;
  }

  for (const dest of targets) {
    if (!fs.existsSync(path.dirname(dest))) continue;
    copyCssTo(dest);
    console.log(`Copied CSS to ${dest}`);
  }

  console.log(`Generated shared/css/aipp-tokens.css and ${presetNames.length - 1} palette overlays (themes/bundle.css)`);
}

main();
