import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const SOURCE = fs.readFileSync(
  new URL('./aipp-host-interfaces.js', import.meta.url), 'utf8');
const TYPE = 'shared.theme.apply/v1';
const PROVIDER = Object.freeze({
  type: TYPE,
  app_id: 'theme-one',
  module_url: 'api/proxy/app/theme-one/theme-interface/theme-interface.js',
  bootstrap_tool: 'theme_current',
  probe_interval_ms: 30000,
  online: true,
});

function directory(providers = [PROVIDER]) {
  return {
    schema_version: 1,
    banner_icons: [],
    banner_tabs: [],
    interface_providers: providers,
    conflicts: [],
  };
}

function effect(packageId) {
  return {
    type: TYPE,
    payload: {
      package_id: packageId,
      version: '1.0.0',
      instance_id: packageId === 'ones.standard.dark'
        ? '222222222222222222222222' : '111111111111111111111111',
    },
  };
}

function harness(api, initialToolFetch, initialDirectory = directory(), initialStorage = []) {
  const storage = new Map(initialStorage);
  const events = [];
  let toolFetch = initialToolFetch;
  let extensionDirectory = initialDirectory;
  const window = {
    AippHostInterfaceLoader: { importModule: async () => ({ default: api }) },
    localStorage: {
      getItem: (key) => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, String(value)),
    },
    fetch: (url, ...args) => String(url) === 'api/host/extensions'
      ? Promise.resolve(extensionDirectory instanceof Error
        ? { ok: false, status: 503, json: async () => ({}) }
        : { ok: true, status: 200, json: async () => extensionDirectory })
      : toolFetch(url, ...args),
    dispatchEvent: (event) => events.push({ type: event.type, detail: event.detail }),
    CustomEvent: class { constructor(type, init) { this.type = type; this.detail = init.detail; } },
    setTimeout: () => 1,
    clearTimeout() {},
    setInterval: () => 2,
    clearInterval() {},
  };
  vm.runInNewContext(SOURCE, {
    window,
    document: { baseURI: 'http://host.invalid/' },
    URL,
    AbortController,
    console: { ...console, warn() {} },
  });
  return {
    window, storage, events,
    setToolFetch: (next) => { toolFetch = next; },
    setDirectory: (next) => { extensionDirectory = next; },
  };
}

test('bootstrap caches the owner fallback and applies it when the provider goes down', async () => {
  const calls = [];
  const api = {
    apply: async (payload) => calls.push(['apply', payload.package_id]),
    unload: async () => calls.push(['unload']),
    prepareFallback: async (payload) => calls.push(['prepare', payload.package_id]),
    applyFallback: async (payload) => calls.push(['fallback', payload.package_id]),
  };
  const current = effect('user.alice.blue');
  const fallback = effect('ones.standard.dark');
  const h = harness(api, async () => ({
    ok: true, status: 200, json: async () => ({
      ok: true, host_effect: current, fallback_effect: fallback,
    }),
  }));

  await h.window.AippHostInterfaces.bootstrap({ schedule: false });
  assert.deepEqual(calls, [
    ['prepare', 'ones.standard.dark'],
    ['apply', 'user.alice.blue'],
  ]);
  assert.equal(h.events.filter((event) => event.type === 'aipp-host-interface-change').at(-1)
    .detail.state, 'active');
  assert.match(h.storage.get('ones.host-interface.fallback.v1:' + TYPE), /ones\.standard\.dark/);

  await h.window.AippHostInterfaces.bootstrap({ schedule: false });
  assert.equal(calls.filter(([name]) => name === 'apply').length, 1);

  h.setToolFetch(async () => ({ ok: false, status: 503 }));
  await h.window.AippHostInterfaces.bootstrap({ schedule: false });
  assert.deepEqual(calls.at(-1), ['fallback', 'ones.standard.dark']);
  assert.equal(h.events.filter((event) => event.type === 'aipp-host-interface-change').at(-1)
    .detail.state, 'fallback');
});

test('failed dynamic imports are evicted so a recovered provider can attach', async () => {
  let attempts = 0;
  const api = {
    apply: async (payload) => payload.package_id,
    unload: async () => {},
    prepareFallback: async () => {},
    applyFallback: async () => {},
  };
  const h = harness(api, async () => ({ ok: false, status: 503 }));
  h.window.AippHostInterfaceLoader.importModule = async () => {
    attempts += 1;
    if (attempts === 1) throw new Error('offline');
    return { default: api };
  };

  await assert.rejects(h.window.AippHostInterfaces.dispatch(effect('user.alice.blue')), /offline/);
  assert.equal(await h.window.AippHostInterfaces.dispatch(effect('user.alice.blue')),
    'user.alice.blue');
  assert.equal(attempts, 2);
});

test('a cold start without an owner-provided cache remains a neutral shell', async () => {
  const api = {
    apply: async () => {}, unload: async () => {},
    prepareFallback: async () => {}, applyFallback: async () => {},
  };
  const h = harness(api, async () => ({ ok: false, status: 503 }), new Error('offline'));

  await h.window.AippHostInterfaces.bootstrap({ schedule: false });

  assert.equal(h.events.length, 0);
  assert.equal(h.storage.size, 0);
});

test('uses an owner-published cached provider and fallback while Theme One is unavailable', async () => {
  const calls = [];
  const api = {
    apply: async () => {}, unload: async () => {}, prepareFallback: async () => {},
    applyFallback: async (payload) => calls.push(payload.package_id),
  };
  const fallback = effect('ones.standard.dark');
  const h = harness(
    api,
    async () => ({ ok: false, status: 503 }),
    new Error('host directory unavailable'),
    [
      ['ones.host-interface.providers.v1', JSON.stringify([PROVIDER])],
      ['ones.host-interface.fallback.v1:' + TYPE, JSON.stringify(fallback)],
    ],
  );

  await h.window.AippHostInterfaces.bootstrap({ schedule: false });

  assert.deepEqual(calls, ['ones.standard.dark']);
  assert.equal(h.events.filter((event) => event.type === 'aipp-host-interface-change').at(-1)
    .detail.state, 'fallback');
});

test('discovers shell contributions and provider modules from the generic Host directory', async () => {
  const api = {
    apply: async () => {}, unload: async () => {},
    prepareFallback: async () => {}, applyFallback: async () => {},
  };
  const icon = {
    operation: 'register_banner_icon', id: 'theme-library',
    label: { en: 'Themes' }, icon: 'app',
    action: { kind: 'tool', tool: 'theme_manager_open' }, order: 100,
    app_id: 'theme-one', app_icon: '<svg></svg>', app_color: '#39C5BB', online: true,
  };
  const h = harness(api, async () => ({ ok: false, status: 503 }), directory([PROVIDER]));
  h.setDirectory({ ...directory([PROVIDER]), banner_icons: [icon] });

  await h.window.AippHostInterfaces.discover();

  const event = h.events.find((item) => item.type === 'aipp-host-extensions-change');
  assert.equal(event.detail.banner_icons[0].action.tool, 'theme_manager_open');
  assert.equal(h.window.AippHostInterfaces.extensions().interface_providers[0].app_id, 'theme-one');
});
