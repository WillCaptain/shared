#!/usr/bin/env node

/**
 * Generates source trees for every self-contained shared/theme/themes/<id>/
 * directory. All built-in themes share the same .ones-theme layout and keep
 * their trusted CSS, raster assets, and animation documents beside theme.json.
 *
 * Usage:
 *   node shared/theme/generate-standard-theme-packages.mjs [--compile]
 */

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { compatibilityThemes, loadThemeLibrary } from './theme-library.mjs';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const BACKGROUNDS_JSON = path.join(ROOT, 'aipp-backgrounds.json');
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

function ensurePackageAssets(sourceTheme, packageDir) {
  const { id: presetId, directory, manifest } = sourceTheme;
  if (manifest.resources?.background) {
    const copied = copyTrustedAsset(
      path.join(directory, manifest.resources.background),
      path.join(packageDir, 'background/background.png'),
    );
    if (!copied && !hasAssetDir(packageDir, 'background')) {
      throw new Error(`Standard preset ${presetId} is missing trusted background asset: ${backgroundId}`);
    }
  } else {
    fs.rmSync(path.join(packageDir, 'background/background.png'), { force: true });
  }

  if (manifest.resources?.icon) {
    const copied = copyTrustedAsset(
      path.join(directory, manifest.resources.icon),
      path.join(packageDir, 'icon/icon.png'),
    );
    if (!copied && !hasAssetDir(packageDir, 'icon')) {
        throw new Error(`Standard preset ${presetId} is missing its trusted icon asset`);
    }
  } else {
    fs.rmSync(path.join(packageDir, 'icon/icon.png'), { force: true });
  }

  if (fs.existsSync(path.join(directory, 'effects.css'))) {
    copyTrustedAsset(path.join(directory, 'effects.css'), path.join(packageDir, 'theme/effects.css'));
  } else {
    fs.rmSync(path.join(packageDir, 'theme/effects.css'), { force: true });
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

function buildShell(preset, packageDir, backgrounds, sourceTheme) {
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
    ...(sourceTheme.manifest.package_shell?.chrome
      ? { chrome: sourceTheme.manifest.package_shell.chrome }
      : {}),
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
  return generated;
}

function defaultLicense() {
  return `Theme package code and generated theme configuration:
Copyright (c) 2026 Ones contributors.

This standard Ones theme package contains declarative colors and shell metadata
only. It is distributed as part of the Ones Host standard theme set.
`;
}

function generatePackage(sourceTheme, globalTokens, backgrounds) {
  const { id: presetId, manifest: sourceManifest } = sourceTheme;
  const preset = sourceManifest.preset;
  const packageId = `ones.standard.${presetId}`;
  const packageDir = path.join(PACKAGES_DIR, packageId);
  fs.mkdirSync(packageDir, { recursive: true });
  fs.mkdirSync(path.join(packageDir, 'theme'), { recursive: true });
  fs.mkdirSync(path.join(packageDir, 'animation'), { recursive: true });
  ensurePackageAssets(sourceTheme, packageDir);

  const programPath = path.join(packageDir, 'animation/program.json');
  const fallbackPath = path.join(packageDir, 'animation/fallback.json');
  copyTrustedAsset(path.join(sourceTheme.directory, sourceManifest.animation.program), programPath);
  copyTrustedAsset(path.join(sourceTheme.directory, sourceManifest.animation.fallback), fallbackPath);

  const shell = buildShell(preset, packageDir, backgrounds, sourceTheme);
  const capabilities = readAnimationCapabilities(programPath);
  const hasBackground = shell.background.kind === 'asset';
  const hasIcon = shell.icon.kind === 'asset';
  const hasEffects = fs.existsSync(path.join(packageDir, 'theme/effects.css'));

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
      ...(hasEffects ? { effects: 'theme/effects.css' } : {}),
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
  const library = loadThemeLibrary();
  const themes = compatibilityThemes(library);
  const backgroundCatalog = JSON.parse(fs.readFileSync(BACKGROUNDS_JSON, 'utf8'));
  const backgrounds = new Map(
    (backgroundCatalog.backgrounds ?? []).map((background) => [background.id, background]),
  );
  const entries = library.themes;

  if (entries.length === 0) {
    throw new Error('No standard theme directories found under shared/theme/themes');
  }

  const packages = entries.map((theme) => {
    if (theme.manifest.preserve_compiled === true) {
      return {
        preset_id: theme.id,
        package_id: theme.manifest.package_id,
        version: theme.manifest.version,
        source_dir: path.relative(ROOT, path.join(PACKAGES_DIR, theme.manifest.package_id)),
        standard: true,
      };
    }
    return generatePackage(theme, themes.tokens, backgrounds);
  });

  const catalog = {
    schema_version: 1,
    generated_at: new Date().toISOString(),
    source: 'shared/theme/themes/*/theme.json',
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
      const sourceTheme = library.themes.find((theme) => theme.id === entry.preset_id);
      if (sourceTheme?.manifest.preserve_compiled === true && fs.existsSync(compiledPath)) {
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
