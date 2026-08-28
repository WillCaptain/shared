#!/usr/bin/env node

/**
 * Generates source trees for every `standard: true` preset in aipp-themes.json.
 * All built-in themes share the same .ones-theme layout. Trusted background/icon
 * defaults are resolved from shared/theme/assets; richer reviewed overlays may be
 * retained under packages/ones.standard.<id>/.
 *
 * Usage:
 *   node shared/theme/generate-standard-theme-packages.mjs [--compile]
 */

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const THEMES_JSON = path.join(ROOT, 'aipp-themes.json');
const BACKGROUNDS_JSON = path.join(ROOT, 'aipp-backgrounds.json');
const ASSETS_DIR = path.join(ROOT, 'assets');
const PACKAGES_DIR = path.join(ROOT, 'packages');
const CATALOG_PATH = path.join(ROOT, 'standard-theme-packages.json');
const COMPILE = process.argv.includes('--compile');
// The shipped Miku archive is an approved immutable artifact. Its richer source
// remains available for inspection, but routine standard-theme regeneration must
// not replace the reviewed binary.
const PRESERVED_COMPILED_PRESETS = new Set(['hatsune-miku']);

const TOKEN_KEYS = [
  'bg', 'surface', 'surface2', 'surface3', 'text', 'textDim', 'textMuted',
  'border', 'border2', 'accent', 'accentHover', 'accentGlow', 'active',
  'danger', 'success', 'warning', 'info',
];
const TYPO_KEYS = [
  'font', 'fontMono', 'fontSize', 'fontSizeSm', 'fontSizeLg',
  'radius', 'radiusSm', 'radiusLg', 'radiusPill',
];

function canonicalValue(value) {
  if (Array.isArray(value)) return value.map(canonicalValue);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value).sort().map((key) => [key, canonicalValue(value[key])]),
    );
  }
  return value;
}

function writeJson(relativePath, value) {
  if (fs.existsSync(relativePath)) {
    const existing = JSON.parse(fs.readFileSync(relativePath, 'utf8'));
    if (JSON.stringify(canonicalValue(existing)) === JSON.stringify(canonicalValue(value))) return;
  }
  const text = `${JSON.stringify(canonicalValue(value))}\n`;
  fs.writeFileSync(relativePath, text, 'utf8');
}

function mapFont(value, fallback) {
  if (!value || typeof value !== 'string') return fallback;
  const lower = value.toLowerCase();
  if (lower.includes('mono') || lower.includes('jetbrains') || lower.includes('fira code')) {
    return 'system-mono';
  }
  return 'system-sans';
}

function buildTokens(globalTokens, preset) {
  const tokens = { schema_version: 1 };
  for (const key of TOKEN_KEYS) {
    tokens[key] = preset[key] ?? globalTokens[key];
  }
  for (const key of TYPO_KEYS) {
    if (key === 'font') tokens.font = mapFont(preset.font ?? globalTokens.font, 'system-sans');
    else if (key === 'fontMono') {
      tokens.fontMono = mapFont(preset.fontMono ?? globalTokens.fontMono, 'system-mono');
    } else {
      tokens[key] = preset[key] ?? globalTokens[key];
    }
  }
  return tokens;
}

function hasAssetDir(packageDir, subdir) {
  const dir = path.join(packageDir, subdir);
  if (!fs.existsSync(dir)) return false;
  return fs.readdirSync(dir).some((name) => fs.statSync(path.join(dir, name)).isFile());
}

function copyTrustedAsset(sourcePath, destinationPath) {
  if (!fs.existsSync(sourcePath)) return false;
  fs.mkdirSync(path.dirname(destinationPath), { recursive: true });
  const source = fs.readFileSync(sourcePath);
  if (fs.existsSync(destinationPath) && fs.readFileSync(destinationPath).equals(source)) return true;
  fs.copyFileSync(sourcePath, destinationPath);
  return true;
}

function ensurePackageAssets(presetId, preset, packageDir) {
  const backgroundId = preset.background?.kind === 'preset' ? preset.background.id : null;
  if (backgroundId && backgroundId !== 'none') {
    const copied = copyTrustedAsset(
      path.join(ASSETS_DIR, 'backgrounds', `${backgroundId}.png`),
      path.join(packageDir, 'background/background.png'),
    );
    if (!copied && !hasAssetDir(packageDir, 'background')) {
      throw new Error(`Standard preset ${presetId} is missing trusted background asset: ${backgroundId}`);
    }
  }

  const iconId = preset.icon?.id;
  if (iconId) {
    const copied = copyTrustedAsset(
      path.join(ASSETS_DIR, 'icons', `${iconId}.png`),
      path.join(packageDir, 'icon/icon.png'),
    );
    if (!copied && !hasAssetDir(packageDir, 'icon')) {
      throw new Error(`Standard preset ${presetId} is missing trusted icon asset: ${iconId}`);
    }
  }
}

function readAnimationCapabilities(programPath) {
  if (!fs.existsSync(programPath)) {
    return { pointer: false, local_time: false, reduced_motion: true };
  }
  const program = JSON.parse(fs.readFileSync(programPath, 'utf8'));
  let pointer = false;
  let localTime = false;
  for (const layer of program.layers ?? []) {
    for (const node of layer.nodes ?? []) {
      if (node.type === 'pointer_field') pointer = true;
      if (node.type === 'local_time_curve') localTime = true;
    }
  }
  return { pointer, local_time: localTime, reduced_motion: true };
}

function emptyAnimation() {
  return {
    schema_version: 1,
    fps: 60,
    max_particles: 0,
    layers: [],
  };
}

function particleAnimation({ id, color, count, fallback = false }) {
  return {
    schema_version: 1,
    fps: fallback ? 24 : 60,
    max_particles: count,
    layers: [{
      id,
      blend: 'source-over',
      opacity: fallback ? 0.28 : 0.52,
      nodes: [{
        id: `${id}_particles`,
        type: 'particle_emitter',
        params: {
          count,
          shape: 'petal',
          color,
          size_min: 2,
          size_max: fallback ? 6 : 8,
          speed_min: fallback ? 2 : 4,
          speed_max: fallback ? 12 : 28,
          lifetime_min: 5,
          lifetime_max: 18,
          direction: 1.57,
          spread: fallback ? 0.4 : 0.9,
        },
      }],
    }],
  };
}

function scanLinesAnimation(fallback = false) {
  return {
    schema_version: 1,
    fps: fallback ? 24 : 60,
    max_particles: 0,
    layers: [{
      id: fallback ? 'gentle_scan' : 'cyber_scan',
      blend: 'source-over',
      opacity: fallback ? 0.22 : 0.48,
      nodes: [{
        id: fallback ? 'gentle_lines' : 'scan_lines',
        type: 'scan_lines',
        params: {
          spacing: fallback ? 10 : 6,
          speed: fallback ? 6 : 45,
          width: fallback ? 0.6 : 1.2,
          color: fallback ? 'rgba(0,229,255,0.08)' : 'rgba(0,229,255,0.18)',
          glitch: fallback ? 0 : 0.12,
        },
      }],
    }],
  };
}

function auroraAnimation(fallback = false) {
  return {
    schema_version: 1,
    fps: fallback ? 24 : 60,
    max_particles: 0,
    layers: [{
      id: fallback ? 'gentle_aurora' : 'aurora_ribbons',
      blend: 'lighter',
      opacity: fallback ? 0.2 : 0.42,
      nodes: [{
        id: fallback ? 'gentle_aurora_gradient' : 'aurora_gradient',
        type: 'gradient',
        params: {
          kind: 'linear',
          colors: ['rgba(137,180,250,0)', 'rgba(203,166,247,0.42)', 'rgba(166,227,161,0)'],
          stops: [0, 0.5, 1],
          x0: 0,
          y0: 0,
          x1: 1,
          y1: 1,
          radius: 1,
        },
      }],
    }],
  };
}

function standardAnimation(animationId, fallback = false) {
  switch (animationId) {
    case 'sakura-fall':
      return particleAnimation({
        id: fallback ? 'gentle_sakura' : 'sakura_fall',
        color: 'rgba(226,111,158,0.72)',
        count: fallback ? 30 : 72,
        fallback,
      });
    case 'rose-petal-drift':
      return particleAnimation({
        id: fallback ? 'gentle_rose' : 'rose_petal_drift',
        color: 'rgba(180,122,153,0.68)',
        count: fallback ? 32 : 76,
        fallback,
      });
    case 'scan-lines':
      return scanLinesAnimation(fallback);
    case 'aurora-drift':
      return auroraAnimation(fallback);
    default:
      return emptyAnimation();
  }
}

function buildShell(preset, packageDir, backgrounds) {
  const hasBackground = hasAssetDir(packageDir, 'background');
  const hasIcon = hasAssetDir(packageDir, 'icon');
  const atmosphere = preset.atmosphere ?? 'none';
  const fx = preset.fx ?? { glow: 'off', motion: 'full' };
  const backgroundDefaults = backgrounds.get(preset.background?.id)?.package ?? {};
  const generated = {
    schema_version: 1,
    dark_mode: preset.darkMode ?? true,
    atmosphere,
    fx: {
      glow: fx.glow ?? 'off',
      motion: fx.motion ?? 'full',
    },
    background: hasBackground
      ? {
          kind: 'asset',
          opacity: backgroundDefaults.opacity ?? 0.55,
          overlay: backgroundDefaults.overlay ?? 0.30,
          focal_x: backgroundDefaults.focal_x ?? 0.50,
          focal_y: backgroundDefaults.focal_y ?? 0.50,
        }
      : { kind: 'none' },
    icon: hasIcon ? { kind: 'asset' } : { kind: 'host_default' },
  };
  const existingPath = path.join(packageDir, 'theme/shell.json');
  if (!hasBackground || !fs.existsSync(existingPath)) return generated;
  const existing = JSON.parse(fs.readFileSync(existingPath, 'utf8'));
  return {
    ...generated,
    background: existing.background?.kind === 'asset'
      ? existing.background
      : generated.background,
  };
}

function defaultLicense() {
  return `Theme package code and generated theme configuration:
Copyright (c) 2026 Ones contributors.

This standard Ones theme package contains declarative colors and shell metadata
only. It is distributed as part of the Ones Host standard theme set.
`;
}

function generatePackage(presetId, preset, globalTokens, backgrounds) {
  const packageId = `ones.standard.${presetId}`;
  const packageDir = path.join(PACKAGES_DIR, packageId);
  fs.mkdirSync(packageDir, { recursive: true });
  fs.mkdirSync(path.join(packageDir, 'theme'), { recursive: true });
  fs.mkdirSync(path.join(packageDir, 'animation'), { recursive: true });
  ensurePackageAssets(presetId, preset, packageDir);

  const programPath = path.join(packageDir, 'animation/program.json');
  const fallbackPath = path.join(packageDir, 'animation/fallback.json');
  if (!fs.existsSync(programPath)) {
    writeJson(programPath, standardAnimation(preset.bgAnimation, false));
  }
  if (!fs.existsSync(fallbackPath)) {
    writeJson(fallbackPath, standardAnimation(preset.bgAnimation, true));
  }

  const shell = buildShell(preset, packageDir, backgrounds);
  const capabilities = readAnimationCapabilities(programPath);
  const hasBackground = shell.background.kind === 'asset';
  const hasIcon = shell.icon.kind === 'asset';

  writeJson(path.join(packageDir, 'theme/tokens.json'), buildTokens(globalTokens, preset));
  writeJson(path.join(packageDir, 'theme/shell.json'), shell);

  const licensePath = path.join(packageDir, 'LICENSE.txt');
  if (!fs.existsSync(licensePath)) fs.writeFileSync(licensePath, defaultLicense(), 'utf8');

  const manifest = {
    schema_version: 1,
    package_id: packageId,
    version: '1.0.0',
    name: preset.label,
    description: preset.description ?? {
      en: `Standard Ones theme: ${preset.label.en}.`,
      zh: `Ones 标准主题：${preset.label.zh ?? preset.label.en}。`,
    },
    publisher: { id: 'ones', display_name: 'Ones' },
    min_host_version: '1.0.0',
    components: {
      tokens: 'theme/tokens.json',
      shell: 'theme/shell.json',
      background: hasBackground ? 'background/background.png' : null,
      animation: 'animation/program.json',
      animation_fallback: 'animation/fallback.json',
      icon: hasIcon ? 'icon/icon.png' : null,
    },
    capabilities,
    license: 'LICENSE.txt',
    integrity: 'integrity.json',
  };
  writeJson(path.join(packageDir, 'manifest.json'), manifest);

  return {
    preset_id: presetId,
    package_id: packageId,
    version: manifest.version,
    source_dir: path.relative(ROOT, packageDir),
    standard: true,
  };
}

function main() {
  const themes = JSON.parse(fs.readFileSync(THEMES_JSON, 'utf8'));
  const backgroundCatalog = JSON.parse(fs.readFileSync(BACKGROUNDS_JSON, 'utf8'));
  const backgrounds = new Map(
    (backgroundCatalog.backgrounds ?? []).map((background) => [background.id, background]),
  );
  const entries = Object.entries(themes.presets ?? {})
    .filter(([, preset]) => preset.standard === true)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));

  if (entries.length === 0) {
    throw new Error('No standard presets found in aipp-themes.json');
  }

  const packages = entries.map(([presetId, preset]) =>
    generatePackage(presetId, preset, themes.tokens, backgrounds));

  const catalog = {
    schema_version: 1,
    generated_at: new Date().toISOString(),
    source: 'shared/theme/aipp-themes.json',
    packages,
  };
  writeJson(CATALOG_PATH, catalog);
  console.log(`Generated ${packages.length} standard theme package source trees.`);

  if (COMPILE) {
    const compiler = path.join(ROOT, 'compile-theme-package.mjs');
    for (const entry of packages) {
      const sourceDir = path.join(ROOT, entry.source_dir);
      const compiledPath = path.join(
        PACKAGES_DIR,
        `${entry.package_id}-${entry.version}.ones-theme`,
      );
      if (PRESERVED_COMPILED_PRESETS.has(entry.preset_id) && fs.existsSync(compiledPath)) {
        console.log(`Preserved reviewed package: ${entry.package_id}`);
        continue;
      }
      const result = spawnSync(process.execPath, [compiler, sourceDir], { encoding: 'utf8' });
      if (result.status !== 0) {
        console.error(result.stderr || result.stdout);
        throw new Error(`Failed to compile ${entry.package_id}`);
      }
      console.log((result.stdout || '').trim());
    }
  }
}

main();
