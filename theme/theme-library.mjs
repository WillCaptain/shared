import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const ID = /^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$/;
const SAFE_PATH = /^(?:resources\/[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?\.(?:png|jpe?g|webp|avif)|animation\/(?:program|fallback)\.json|animation\/preview\.png|effects\.css)$/;

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function requireValue(condition, message) {
  if (!condition) throw new Error(message);
}

export function loadThemeLibrary(root = ROOT) {
  const base = readJson(path.join(root, 'theme-base.json'));
  const themesRoot = path.join(root, 'themes');
  const definitions = fs.readdirSync(themesRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => {
      const id = entry.name;
      requireValue(ID.test(id), `Invalid theme directory id: ${id}`);
      const directory = path.join(themesRoot, id);
      const manifestPath = path.join(directory, 'theme.json');
      const cssPath = path.join(directory, 'theme.css');
      requireValue(fs.existsSync(manifestPath), `Missing ${id}/theme.json`);
      requireValue(fs.existsSync(cssPath), `Missing ${id}/theme.css`);
      const manifest = readJson(manifestPath);
      requireValue(manifest.schema_version === 1, `Unsupported theme schema: ${id}`);
      requireValue(manifest.id === id, `Theme id must match its directory: ${id}`);
      requireValue(manifest.package_id === `ones.standard.${id}`,
        `Built-in package id must match its theme id: ${id}`);
      requireValue(manifest.preset && manifest.preset.standard === true,
        `Built-in theme must be standard: ${id}`);
      for (const value of Object.values(manifest.resources || {})) {
        if (value == null) continue;
        requireValue(SAFE_PATH.test(value), `Unsafe theme resource path in ${id}: ${value}`);
        requireValue(fs.existsSync(path.join(directory, value)), `Missing ${id}/${value}`);
      }
      for (const key of ['program', 'fallback']) {
        const value = manifest.animation?.[key];
        requireValue(SAFE_PATH.test(String(value || '')), `Unsafe ${key} path in ${id}`);
        requireValue(fs.existsSync(path.join(directory, value)), `Missing ${id}/${value}`);
      }
      if (manifest.animation?.preview_asset) {
        const value = manifest.animation.preview_asset;
        requireValue(SAFE_PATH.test(value), `Unsafe animation preview path in ${id}`);
        requireValue(fs.existsSync(path.join(directory, value)), `Missing ${id}/${value}`);
      }
      const css = fs.readFileSync(cssPath, 'utf8');
      const themeSelector = `[data-aipp-palette="${id}"]`;
      const backgroundSelector = `[data-aipp-background="${id}"]`;
      requireValue(!/[<>\\]/.test(css) && !/@\s*import\b/i.test(css),
        `Unsafe CSS construct in ${id}/theme.css`);
      requireValue(!/url\s*\((?!["']?\.\/resources\/)/i.test(css),
        `Theme CSS URLs must stay under ${id}/resources`);
      requireValue(css.includes(themeSelector) || css.includes(backgroundSelector)
        || css.trim().endsWith(`for ${id}. Loaded after shared standard CSS. */`),
      `Theme CSS must be scoped to ${id}`);
      return { id, directory, manifest, css };
    })
    .sort((left, right) => left.id.localeCompare(right.id));
  requireValue(definitions.some((theme) => theme.id === 'dark'), 'Missing dark theme');
  requireValue(definitions.some((theme) => theme.id === 'light'), 'Missing light theme');
  return { base, themes: definitions };
}

export function compatibilityThemes(library) {
  return {
    version: library.base.version,
    tokens: library.base.tokens,
    hostLayout: library.base.hostLayout,
    presets: Object.fromEntries(library.themes.map((theme) => [theme.id, theme.manifest.preset])),
  };
}
