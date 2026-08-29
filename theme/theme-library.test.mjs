import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { compatibilityThemes, loadThemeLibrary } from './theme-library.mjs';

test('all built-in themes are discovered from self-contained directories', () => {
  const library = loadThemeLibrary();
  const ids = library.themes.map((theme) => theme.id);
  assert.deepEqual(ids, [
    'arc-grid', 'catppuccin-mocha', 'crimson-sage', 'dark', 'gilded-confluence',
    'hatsune-miku', 'light', 'neon-circuit', 'rose-pine-dawn', 'sakura-pop',
  ]);
  for (const theme of library.themes) {
    assert.equal(theme.manifest.id, theme.id);
    assert.ok(fs.existsSync(path.join(theme.directory, 'theme.css')));
    assert.ok(fs.existsSync(path.join(theme.directory, theme.manifest.animation.program)));
    assert.ok(fs.existsSync(path.join(theme.directory, theme.manifest.animation.fallback)));
    if (theme.manifest.resources?.background) {
      const runtimeBackground = path.join(theme.directory, theme.manifest.resources.background);
      assert.match(theme.manifest.resources.background, /\/background\.jpg$/);
      assert.ok(fs.existsSync(path.join(theme.directory, 'resources/background-original.png')));
      assert.ok(fs.statSync(runtimeBackground).size <= 500 * 1024,
        `${theme.id} runtime background must not exceed 500 KiB`);
      assert.ok(fs.existsSync(path.join(theme.directory, theme.manifest.resources.preview)));
      assert.ok(fs.existsSync(path.join(theme.directory, theme.manifest.animation.preview_asset)));
      assert.ok(fs.statSync(path.join(theme.directory, theme.manifest.resources.preview)).size < 300_000);
      assert.ok(fs.statSync(path.join(theme.directory, theme.manifest.animation.preview_asset)).size < 300_000);
    }
  }
  assert.deepEqual(
    JSON.parse(fs.readFileSync(new URL('./aipp-themes.json', import.meta.url), 'utf8')),
    compatibilityThemes(library),
  );
});

test('theme catalog is ordered light, dark, then featured', () => {
  const ids = JSON.parse(fs.readFileSync(
    new URL('../css/theme-presets.json', import.meta.url), 'utf8')).presets.map((item) => item.id);
  assert.deepEqual(ids, [
    'light', 'rose-pine-dawn', 'sakura-pop',
    'dark', 'catppuccin-mocha', 'arc-grid',
    'hatsune-miku', 'neon-circuit', 'gilded-confluence', 'crimson-sage',
  ]);
});

test('advanced catalogs expose every theme background and animation', () => {
  const library = loadThemeLibrary();
  const backgrounds = JSON.parse(fs.readFileSync(
    new URL('../css/background-presets.json', import.meta.url), 'utf8')).backgrounds;
  const animations = JSON.parse(fs.readFileSync(
    new URL('../css/bg-animation-presets.json', import.meta.url), 'utf8')).animations;
  const backgroundsById = new Map(backgrounds.map((item) => [item.id, item]));
  const animationsById = new Map(animations.map((item) => [item.id, item]));

  for (const theme of library.themes) {
    if (theme.manifest.resources?.background) {
      assert.equal(
        backgroundsById.get(theme.id)?.runtime?.asset,
        `css/themes/${theme.id}/${theme.manifest.resources.background}`,
        `${theme.id} background must be selectable from Advanced`,
      );
      assert.equal(
        backgroundsById.get(theme.id)?.runtime?.preview_asset,
        `css/themes/${theme.id}/resources/preview.png`,
      );
    }
    const animation = theme.manifest.animation;
    if (animation?.id && animation.id !== 'none') {
      const catalogEntry = animationsById.get(animation.id);
      assert.ok(catalogEntry?.program, `${animation.id} program must be selectable from Advanced`);
      assert.ok(catalogEntry?.fallback, `${animation.id} fallback must be selectable from Advanced`);
      assert.equal(typeof catalogEntry?.preview, 'object',
        `${animation.id} color preview metadata must survive alongside its poster`);
      assert.equal(catalogEntry?.previewAsset,
        `css/themes/${theme.id}/animation/preview.png`);
    }
  }
});

test('a new directory is discovered without editing a central registry', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ones-theme-library-'));
  fs.writeFileSync(path.join(root, 'theme-base.json'), JSON.stringify({
    version: 2, tokens: {}, hostLayout: {},
  }));
  for (const id of ['dark', 'light', 'new-theme']) {
    const directory = path.join(root, 'themes', id);
    fs.mkdirSync(path.join(directory, 'animation'), { recursive: true });
    if (id === 'new-theme') {
      fs.mkdirSync(path.join(directory, 'resources'), { recursive: true });
      fs.writeFileSync(path.join(directory, 'resources/heroes-overlay.png'), 'fixture');
    }
    fs.writeFileSync(path.join(directory, 'theme.json'), JSON.stringify({
      schema_version: 1,
      id,
      package_id: `ones.standard.${id}`,
      preset: { standard: true },
      resources: id === 'new-theme' ? { heroes: 'resources/heroes-overlay.png' } : {},
      animation: {
        id: 'none', program: 'animation/program.json', fallback: 'animation/fallback.json',
      },
    }));
    fs.writeFileSync(path.join(directory, 'theme.css'),
      `html[data-aipp-palette="${id}"] { --example: 1; }\n`);
    fs.writeFileSync(path.join(directory, 'animation/program.json'), '{}\n');
    fs.writeFileSync(path.join(directory, 'animation/fallback.json'), '{}\n');
  }
  assert.deepEqual(loadThemeLibrary(root).themes.map((theme) => theme.id),
    ['dark', 'light', 'new-theme']);
});

test('theme CSS cannot escape its local resource directory', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ones-theme-library-'));
  fs.writeFileSync(path.join(root, 'theme-base.json'), JSON.stringify({
    version: 2, tokens: {}, hostLayout: {},
  }));
  for (const id of ['dark', 'light']) {
    const directory = path.join(root, 'themes', id);
    fs.mkdirSync(path.join(directory, 'animation'), { recursive: true });
    fs.writeFileSync(path.join(directory, 'theme.json'), JSON.stringify({
      schema_version: 1, id, package_id: `ones.standard.${id}`,
      preset: { standard: true }, resources: {},
      animation: { id: 'none', program: 'animation/program.json', fallback: 'animation/fallback.json' },
    }));
    fs.writeFileSync(path.join(directory, 'theme.css'), id === 'dark'
      ? 'html[data-aipp-palette="dark"]{background:url("https://evil.invalid/x")}\n'
      : 'html[data-aipp-palette="light"]{}\n');
    fs.writeFileSync(path.join(directory, 'animation/program.json'), '{}\n');
    fs.writeFileSync(path.join(directory, 'animation/fallback.json'), '{}\n');
  }
  assert.throws(() => loadThemeLibrary(root), /URLs must stay/);
});
