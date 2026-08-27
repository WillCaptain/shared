#!/usr/bin/env node

/**
 * Generates source trees for every `standard: true` preset in aipp-themes.json.
 * All built-in themes share the same .ones-theme layout; richer assets (background,
 * icon, animation IR) are optional overlays kept under packages/ones.standard.<id>/.
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
const PACKAGES_DIR = path.join(ROOT, 'packages');
const CATALOG_PATH = path.join(ROOT, 'standard-theme-packages.json');
const COMPILE = process.argv.includes('--compile');

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

function buildShell(preset, packageDir) {
  const hasBackground = hasAssetDir(packageDir, 'background');
  const hasIcon = hasAssetDir(packageDir, 'icon');
  const atmosphere = preset.atmosphere ?? 'none';
  const fx = preset.fx ?? { glow: 'off', motion: 'full' };
  return {
    schema_version: 1,
    dark_mode: preset.darkMode ?? true,
    atmosphere,
    fx: {
      glow: fx.glow ?? 'off',
      motion: fx.motion ?? 'full',
    },
    background: hasBackground
      ? { kind: 'asset', opacity: 0.55, overlay: 0.30, focal_x: 0.50, focal_y: 0.50 }
      : { kind: 'none' },
    icon: hasIcon ? { kind: 'asset' } : { kind: 'host_default' },
  };
}

function defaultLicense() {
  return `Theme package code and generated theme configuration:
Copyright (c) 2026 Ones contributors.

This standard Ones theme package contains declarative colors and shell metadata
only. It is distributed as part of the Ones Host standard theme set.
`;
}

function generatePackage(presetId, preset, globalTokens) {
  const packageId = `ones.standard.${presetId}`;
  const packageDir = path.join(PACKAGES_DIR, packageId);
  fs.mkdirSync(packageDir, { recursive: true });
  fs.mkdirSync(path.join(packageDir, 'theme'), { recursive: true });
  fs.mkdirSync(path.join(packageDir, 'animation'), { recursive: true });

  const programPath = path.join(packageDir, 'animation/program.json');
  const fallbackPath = path.join(packageDir, 'animation/fallback.json');
  if (!fs.existsSync(programPath)) writeJson(programPath, emptyAnimation());
  if (!fs.existsSync(fallbackPath)) writeJson(fallbackPath, emptyAnimation());

  const shell = buildShell(preset, packageDir);
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
  const entries = Object.entries(themes.presets ?? {})
    .filter(([, preset]) => preset.standard === true)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));

  if (entries.length === 0) {
    throw new Error('No standard presets found in aipp-themes.json');
  }

  const packages = entries.map(([presetId, preset]) =>
    generatePackage(presetId, preset, themes.tokens));

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
