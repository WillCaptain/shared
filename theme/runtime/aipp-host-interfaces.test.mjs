import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const SOURCE = fs.readFileSync(
  new URL('./aipp-host-interfaces.js', import.meta.url), 'utf8');
const TYPE = 'shared.theme.apply/v1';

function effect(packageId) {
  return { type: TYPE, payload: { package_id: packageId } };
}

function harness(api, initialFetch) {
  const storage = new Map();
  const events = [];
  let fetchImpl = initialFetch;
  const window = {
    AippHostInterfaceLoader: { importModule: async () => ({ default: api }) },
    localStorage: {
      getItem: (key) => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, String(value)),
    },
    fetch: (...args) => fetchImpl(...args),
    dispatchEvent: (event) => events.push(event.detail),
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
    console,
  });
  return { window, storage, events, setFetch: (next) => { fetchImpl = next; } };
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
  assert.equal(h.events.at(-1).state, 'active');
  assert.match([...h.storage.values()][0], /ones\.standard\.dark/);

  h.setFetch(async () => ({ ok: false, status: 503 }));
  await h.window.AippHostInterfaces.bootstrap({ schedule: false });
  assert.deepEqual(calls.at(-1), ['fallback', 'ones.standard.dark']);
  assert.equal(h.events.at(-1).state, 'fallback');
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
  const h = harness(api, async () => ({ ok: false, status: 503 }));

  await h.window.AippHostInterfaces.bootstrap({ schedule: false });

  assert.equal(h.events.at(-1).state, 'neutral');
  assert.equal(h.storage.size, 0);
});
