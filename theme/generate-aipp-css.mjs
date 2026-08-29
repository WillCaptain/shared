#!/usr/bin/env node
/**
 * Scan self-contained themes/<id>/ directories and generate compatibility
 * catalogs plus deployable css/themes/<id>/ trees.
 *
 * Usage (from repo root or shared/theme):
 *   node shared/theme/generate-aipp-css.mjs
 *   node shared/theme/generate-aipp-css.mjs --copy-to ones/once/src/css
 *   node shared/theme/generate-aipp-css.mjs --check
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { compatibilityThemes, loadThemeLibrary } from './theme-library.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');
const jsonPath = path.join(__dirname, 'aipp-themes.json');
const atmosphereJsonPath = path.join(__dirname, 'aipp-atmosphere.json');
const backgroundsJsonPath = path.join(__dirname, 'aipp-backgrounds.json');
const bgAnimationsJsonPath = path.join(__dirname, 'aipp-bg-animations.json');
const backgroundBasePath = path.join(__dirname, 'background-base.json');
const bgAnimationBasePath = path.join(__dirname, 'bg-animation-base.json');
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
  'atmosphere', 'fx', 'chrome', 'background', 'bgAnimation', 'icon', 'presentation',
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

const HEADER = `/* GENERATED — do not edit. Source: shared/theme/themes/<id>/
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

function buildPresetCss(data, presetName, themeCss = '') {
  const tokens = resolveTokens(data, presetName);
  const selectors = presetSelectors(presetName).join(',\n');
  const local = String(themeCss || '').trim();
  return `${HEADER}\n${selectors} {\n${tokensToCssVars(tokens).join('\n')}\n}\n${local ? `\n${local}\n` : ''}`;
}

function normalizeChrome(raw) {
  if (!raw || raw.mode !== 'luminous-lines') return { mode: 'none' };
  const safeColor = (value, fallback) => {
    const color = String(value || '').trim();
    return /^(?:#[0-9a-f]{6}|rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}(?:\s*,\s*(?:0(?:\.\d+)?|1(?:\.0+)?))?\s*\))$/i.test(color)
      ? color
      : fallback;
  };
  return {
    mode: 'luminous-lines',
    line: safeColor(raw.line, '#58a6ff'),
    lineAlt: safeColor(raw.lineAlt, '#d29922'),
    glow: safeColor(raw.glow, 'rgba(88,166,255,0.22)'),
    glowAlt: safeColor(raw.glowAlt, 'rgba(210,153,34,0.18)'),
  };
}

function listThemeFiles(themesDir) {
  if (!fs.existsSync(themesDir)) return [];
  const files = [];
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const item = path.join(directory, entry.name);
      if (entry.isDirectory()) visit(item);
      else if (entry.isFile()) files.push(path.relative(themesDir, item));
    }
  };
  visit(themesDir);
  return files.sort();
}

function buildPresetsCatalog(data, library) {
  const presetNames = Object.keys(data.presets || {}).sort();
  const presets = presetNames.map((id) => {
    const raw = data.presets[id] || {};
    const tokens = resolveTokens(data, id);
    const source = library.themes.find((theme) => theme.id === id);
    const previewAsset = source?.manifest.resources?.preview
      ? `css/themes/${id}/resources/preview.png` : null;
    return {
      id,
      standard: raw.standard === true,
      darkMode: raw.darkMode !== false,
      label: raw.label || { en: id, zh: id },
      description: raw.description || null,
      atmosphere: raw.atmosphere || 'none',
      fx: raw.fx || { glow: 'off', motion: 'full' },
      chrome: normalizeChrome(raw.chrome),
      background: raw.background || { kind: 'none', id: '' },
      bgAnimation: raw.bgAnimation || 'none',
      icon: raw.icon || { id: 'once', src: 'img/once-icon.png' },
      presentation: raw.presentation || {
        group: raw.darkMode === false ? 'light' : 'dark',
        order: 100,
      },
      previewAsset,
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
    const groupOrder = { light: 0, dark: 1, featured: 2 };
    const leftGroup = groupOrder[a.presentation?.group] ?? 9;
    const rightGroup = groupOrder[b.presentation?.group] ?? 9;
    if (leftGroup !== rightGroup) return leftGroup - rightGroup;
    const leftOrder = Number(a.presentation?.order) || 100;
    const rightOrder = Number(b.presentation?.order) || 100;
    if (leftOrder !== rightOrder) return leftOrder - rightOrder;
    return a.id.localeCompare(b.id);
  });
  return { version: data.version || 2, presets };
}

function copyCssTo(dest) {
  fs.mkdirSync(path.join(dest, 'themes'), { recursive: true });
  const sourceThemeDirs = new Set(fs.readdirSync(outThemesDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory()).map((entry) => entry.name));
  for (const entry of fs.readdirSync(path.join(dest, 'themes'), { withFileTypes: true })) {
    if (entry.isDirectory() && !sourceThemeDirs.has(entry.name)) {
      fs.rmSync(path.join(dest, 'themes', entry.name), { recursive: true, force: true });
    }
  }
  for (const file of COPY_FILES) {
    const src = path.join(outCssDir, file);
    if (fs.existsSync(src)) fs.copyFileSync(src, path.join(dest, file));
  }
  for (const file of listThemeFiles(outThemesDir)) {
    const target = path.join(dest, 'themes', file);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(path.join(outThemesDir, file), target);
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
  const library = loadThemeLibrary();
  const data = compatibilityThemes(library);
  fs.writeFileSync(jsonPath, `${JSON.stringify(data, null, 2)}\n`);
  const presetNames = Object.keys(data.presets || {}).sort();
  const darkTokens = resolveTokens(data, 'dark');

  fs.mkdirSync(outThemesDir, { recursive: true });
  const themeIds = new Set(library.themes.map((theme) => theme.id));
  for (const entry of fs.readdirSync(outThemesDir, { withFileTypes: true })) {
    if (entry.isDirectory() && !themeIds.has(entry.name)) {
      fs.rmSync(path.join(outThemesDir, entry.name), { recursive: true, force: true });
    }
  }

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
    const source = library.themes.find((theme) => theme.id === presetName);
    const css = buildPresetCss(data, presetName, source?.css);
    const themeOut = path.join(outThemesDir, presetName);
    fs.mkdirSync(themeOut, { recursive: true });
    fs.writeFileSync(path.join(themeOut, 'theme.css'), css);
    if (source) {
      for (const asset of Object.values(source.manifest.resources || {})) {
        if (!asset) continue;
        const target = path.join(themeOut, asset);
        fs.mkdirSync(path.dirname(target), { recursive: true });
        fs.copyFileSync(path.join(source.directory, asset), target);
      }
      if (source.manifest.animation?.preview_asset) {
        const asset = source.manifest.animation.preview_asset;
        const target = path.join(themeOut, asset);
        fs.mkdirSync(path.dirname(target), { recursive: true });
        fs.copyFileSync(path.join(source.directory, asset), target);
      }
    }
    if (presetName !== 'dark') bundleParts.push(buildPresetCss(data, presetName).trim());
  }
  fs.writeFileSync(path.join(outThemesDir, 'bundle.css'), `${bundleParts.join('\n\n')}\n`);

  fs.writeFileSync(
    path.join(outCssDir, 'theme-presets.json'),
    `${JSON.stringify(buildPresetsCatalog(data, library), null, 2)}\n`,
  );

  if (fs.existsSync(atmosphereJsonPath)) {
    const atmosphereData = JSON.parse(fs.readFileSync(atmosphereJsonPath, 'utf8'));
    fs.writeFileSync(
      path.join(outCssDir, 'atmosphere-presets.json'),
      `${JSON.stringify(atmosphereData, null, 2)}\n`,
    );
  }

  if (fs.existsSync(backgroundsJsonPath)) {
    const backgroundData = JSON.parse(fs.readFileSync(backgroundBasePath, 'utf8'));
    for (const theme of library.themes) {
      const background = theme.manifest.background;
      if (!background || !theme.manifest.resources?.background) continue;
      backgroundData.backgrounds.push({
        id: theme.id,
        label: background.label || theme.manifest.preset.label,
        description: background.description || theme.manifest.preset.description,
        builtin: true,
        previewClass: '',
        runtime: {
          asset: `css/themes/${theme.id}/${theme.manifest.resources.background}`,
          preview_asset: theme.manifest.resources?.preview
            ? `css/themes/${theme.id}/resources/preview.png` : null,
          opacity: background.opacity,
          overlay: background.overlay,
          focal_x: background.focal_x,
          focal_y: background.focal_y,
        },
        package: {
          opacity: background.opacity,
          overlay: background.overlay,
          focal_x: background.focal_x,
          focal_y: background.focal_y,
        },
      });
    }
    fs.writeFileSync(backgroundsJsonPath, `${JSON.stringify(backgroundData, null, 2)}\n`);
    fs.writeFileSync(
      path.join(outCssDir, 'background-presets.json'),
      `${JSON.stringify(backgroundData, null, 2)}\n`,
    );
  }

  if (fs.existsSync(bgAnimationsJsonPath)) {
    const bgAnimationData = JSON.parse(fs.readFileSync(bgAnimationBasePath, 'utf8'));
    for (const theme of library.themes) {
      const animation = theme.manifest.animation;
      if (!animation || animation.id === 'none') continue;
      bgAnimationData.animations.push({
        id: animation.id,
        label: animation.label,
        description: animation.description,
        builtin: true,
        opacity: Number.isFinite(Number(animation.opacity))
          ? Math.max(0, Math.min(1, Number(animation.opacity))) : 0.78,
        previewClass: '',
        previewAsset: animation.preview_asset
          ? `css/themes/${theme.id}/animation/preview.png` : null,
        preview: animation.preview || {
          from: theme.manifest.preset.bg,
          to: theme.manifest.preset.surface2,
          left: theme.manifest.preset.accentGlow || theme.manifest.preset.accent,
          right: theme.manifest.preset.info || theme.manifest.preset.warning,
          focus_y: 0.5,
        },
        program: JSON.parse(fs.readFileSync(path.join(theme.directory, animation.program), 'utf8')),
        fallback: JSON.parse(fs.readFileSync(path.join(theme.directory, animation.fallback), 'utf8')),
      });
    }
    fs.writeFileSync(bgAnimationsJsonPath, `${JSON.stringify(bgAnimationData, null, 2)}\n`);
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
