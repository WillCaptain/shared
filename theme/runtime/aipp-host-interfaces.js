/** Stable browser-side Host interface registry. Domain implementations live in AIPPs. */
(function registerHostInterfaces(global) {
  'use strict';

  const DIRECTORY_URL = 'api/host/extensions';
  const DIRECTORY_REFRESH_MS = 5000;
  const PROBE_TIMEOUT_MS = 10000;
  const PROBE_FAILURE_THRESHOLD = 3;
  const PROVIDER_CACHE_KEY = 'ones.host-interface.providers.v1';
  const FALLBACK_KEY_PREFIX = 'ones.host-interface.fallback.v1:';
  const providers = new Map();
  const loaded = new Map();
  const implementations = new Map();
  const probes = new Map();
  const timers = new Map();
  const activeEffects = new Map();
  const probeFailures = new Map();
  const fallbackActive = new Set();
  let directory = Object.freeze({
    schema_version: 1, banner_icons: [], banner_tabs: [], interface_providers: [], conflicts: [],
  });
  let discovery = null;
  let directoryTimer = null;

  function assert(condition, message) {
    if (!condition) throw new Error(message);
  }

  function emit(type, state, reason) {
    global.dispatchEvent(new global.CustomEvent('aipp-host-interface-change', {
      detail: Object.freeze({ type, state, reason: reason || null }),
    }));
  }

  function safeProvider(input) {
    assert(input && typeof input.type === 'string', 'Host interface provider type is required');
    assert(/^shared\.[a-z0-9.-]+\/v[1-9]\d*$/.test(input.type),
      'invalid Host interface provider type');
    const appId = String(input.app_id || '');
    assert(/^[a-z][a-z0-9-]{0,127}$/.test(appId), 'invalid Host interface provider app id');
    const moduleMatch = typeof input.module_url === 'string'
      ? input.module_url.match(/^api\/proxy\/app\/([a-z][a-z0-9-]{0,127})\/(?:[a-zA-Z0-9._-]+\/)*[a-zA-Z0-9._-]+\.js$/)
      : null;
    assert(moduleMatch && moduleMatch[1] === appId, 'unsafe Host interface module URL');
    assert(typeof input.bootstrap_tool === 'string'
        && /^[a-z][a-z0-9_]{0,127}$/.test(input.bootstrap_tool),
    'invalid Host interface bootstrap tool');
    const interval = Number(input.probe_interval_ms);
    assert(Number.isInteger(interval) && interval >= 5000 && interval <= 300000,
      'invalid Host interface probe interval');
    return Object.freeze({
      type: input.type,
      appId,
      moduleUrl: input.module_url,
      bootstrapTool: input.bootstrap_tool,
      probeIntervalMs: interval,
      online: input.online !== false,
    });
  }

  function installProviders(items, persist) {
    const next = new Map();
    for (const item of Array.isArray(items) ? items : []) {
      const provider = safeProvider(item);
      assert(!next.has(provider.type), 'duplicate Host interface provider: ' + provider.type);
      next.set(provider.type, provider);
    }
    for (const [type, existing] of providers) {
      const replacement = next.get(type);
      if (!replacement
          || existing.appId !== replacement.appId
          || existing.moduleUrl !== replacement.moduleUrl
          || existing.bootstrapTool !== replacement.bootstrapTool
          || existing.probeIntervalMs !== replacement.probeIntervalMs) {
        const api = implementations.get(type);
        Promise.resolve(api?.unload?.()).catch((error) => {
          console.warn('[HostInterfaces] provider unload failed for ' + type, error);
        });
        loaded.delete(type);
        implementations.delete(type);
        activeEffects.delete(type);
        probeFailures.delete(type);
        fallbackActive.delete(type);
        const timer = timers.get(type);
        if (timer != null) global.clearInterval(timer);
        timers.delete(type);
      }
    }
    providers.clear();
    next.forEach((value, key) => providers.set(key, value));
    if (persist) {
      try {
        global.localStorage?.setItem(PROVIDER_CACHE_KEY, JSON.stringify(items));
      } catch (error) {
        console.warn('[HostInterfaces] provider directory was not persisted', error);
      }
    }
  }

  function restoreProviders() {
    try {
      const encoded = global.localStorage?.getItem(PROVIDER_CACHE_KEY);
      if (encoded) installProviders(JSON.parse(encoded), false);
    } catch (error) {
      console.warn('[HostInterfaces] ignored invalid cached provider directory', error);
    }
  }

  async function discover() {
    if (discovery) return discovery;
    const pending = (async () => {
      const response = await global.fetch(DIRECTORY_URL, { method: 'GET' });
      if (!response.ok) throw new Error('Host extension directory returned HTTP ' + response.status);
      const body = await response.json();
      assert(body?.schema_version === 1, 'unsupported Host extension directory schema');
      assert(Array.isArray(body.banner_icons) && Array.isArray(body.banner_tabs)
          && Array.isArray(body.interface_providers), 'invalid Host extension directory');
      // Keep the last owner-published provider directory across a Host restart. This
      // preserves the owner-provided fallback without embedding a default in the Host.
      if (body.interface_providers.length > 0 || providers.size === 0) {
        installProviders(body.interface_providers, body.interface_providers.length > 0);
      }
      directory = Object.freeze({
        schema_version: 1,
        banner_icons: Object.freeze([...body.banner_icons]),
        banner_tabs: Object.freeze([...body.banner_tabs]),
        interface_providers: Object.freeze([...body.interface_providers]),
        conflicts: Object.freeze([...(body.conflicts || [])]),
      });
      global.dispatchEvent(new global.CustomEvent('aipp-host-extensions-change', {
        detail: directory,
      }));
      return directory;
    })().finally(() => {
      if (discovery === pending) discovery = null;
    });
    discovery = pending;
    return pending;
  }

  async function implementation(type) {
    let provider = providers.get(type);
    if (!provider) {
      await discover();
      provider = providers.get(type);
    }
    assert(provider, 'unsupported shared Host interface: ' + type);
    if (!loaded.has(type)) {
      const moduleUrl = new URL(provider.moduleUrl, document.baseURI).href;
      const loader = global.AippHostInterfaceLoader?.importModule;
      const pending = Promise.resolve()
        .then(() => loader ? loader(moduleUrl) : import(moduleUrl))
        .then((module) => {
          const api = module.default || module;
          assert(typeof api.apply === 'function' && typeof api.unload === 'function'
              && typeof api.prepareFallback === 'function'
              && typeof api.applyFallback === 'function',
          'invalid shared Host interface implementation: ' + type);
          implementations.set(type, api);
          return api;
        })
        .catch((error) => {
          loaded.delete(type);
          implementations.delete(type);
          throw error;
        });
      loaded.set(type, pending);
    }
    return loaded.get(type);
  }

  function readFallback(type) {
    try {
      const encoded = global.localStorage?.getItem(FALLBACK_KEY_PREFIX + type);
      if (!encoded) return null;
      const effect = JSON.parse(encoded);
      return effect?.type === type ? effect : null;
    } catch (error) {
      console.warn('[HostInterfaces] ignored invalid fallback for ' + type, error);
      return null;
    }
  }

  async function rememberFallback(type, effect) {
    assert(effect?.type === type, 'fallback Host effect type mismatch');
    const api = await implementation(type);
    await api.prepareFallback(effect.payload);
    try {
      global.localStorage?.setItem(FALLBACK_KEY_PREFIX + type, JSON.stringify(effect));
    } catch (error) {
      console.warn('[HostInterfaces] fallback metadata was not persisted for ' + type, error);
    }
  }

  async function dispatch(effect) {
    assert(effect && typeof effect.type === 'string', 'host effect type is required');
    const identity = effectIdentity(effect);
    const api = await implementation(effect.type);
    if (identity && activeEffects.get(effect.type) === identity) {
      const stillActive = typeof api.isActive !== 'function' || await api.isActive(effect.payload);
      if (stillActive) return effect.payload;
      activeEffects.delete(effect.type);
    }
    const result = await api.apply(effect.payload);
    if (identity) activeEffects.set(effect.type, identity);
    probeFailures.delete(effect.type);
    fallbackActive.delete(effect.type);
    emit(effect.type, 'active');
    return result;
  }

  function effectIdentity(effect) {
    const payload = effect?.payload;
    if (!payload?.package_id || !payload?.version || !payload?.instance_id) return null;
    return payload.package_id + '@' + payload.version + '#' + payload.instance_id;
  }

  async function fallback(type, reason) {
    if (fallbackActive.has(type)) return null;
    const effect = readFallback(type);
    let api = implementations.get(type);
    if (!api && effect) {
      try {
        api = await implementation(type);
      } catch (error) {
        console.warn('[HostInterfaces] fallback implementation unavailable for ' + type, error);
      }
    }
    if (api && effect) {
      try {
        const result = await api.applyFallback(effect.payload);
        const identity = effectIdentity(effect);
        if (identity) activeEffects.set(type, identity);
        fallbackActive.add(type);
        emit(type, 'fallback', reason);
        return result;
      } catch (error) {
        console.warn('[HostInterfaces] cached fallback failed for ' + type, error);
      }
    }
    await api?.unload?.();
    activeEffects.delete(type);
    fallbackActive.delete(type);
    emit(type, 'neutral', reason);
    return null;
  }

  async function probe(type, provider) {
    if (probes.has(type)) return probes.get(type);
    const pending = (async () => {
      const controller = new AbortController();
      const timeout = global.setTimeout?.(() => controller.abort(), PROBE_TIMEOUT_MS);
      try {
        const response = await global.fetch('api/proxy/tools/' + provider.bootstrapTool, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: '{}',
          signal: controller.signal,
        });
        if (!response.ok) throw new Error('provider returned HTTP ' + response.status);
        const body = await response.json();
        assert(body?.host_effect?.type === type, 'provider returned no current Host effect');
        assert(body?.fallback_effect?.type === type, 'provider returned no fallback Host effect');
        await rememberFallback(type, body.fallback_effect);
        probeFailures.delete(type);
        return await dispatch(body.host_effect);
      } catch (error) {
        console.warn('[HostInterfaces] provider unavailable for ' + type, error);
        const failures = (probeFailures.get(type) || 0) + 1;
        probeFailures.set(type, failures);
        // A valid projection remains usable during a transient owner/client scheduling stall.
        // Cold start still uses the owner's cached fallback immediately; an active theme only
        // falls back after repeated failures confirm that its owner is actually unavailable.
        if (activeEffects.has(type) && !fallbackActive.has(type)
            && failures < PROBE_FAILURE_THRESHOLD) return null;
        return fallback(type, error?.message || 'provider unavailable');
      } finally {
        if (timeout != null) global.clearTimeout?.(timeout);
        probes.delete(type);
      }
    })();
    probes.set(type, pending);
    return pending;
  }

  async function bootstrap(options = {}) {
    try {
      if (options.discovered !== true) await discover();
    } catch (error) {
      console.warn('[HostInterfaces] extension discovery deferred', error);
    }
    const tasks = [...providers.entries()].map(([type, provider]) => {
      const alreadyScheduled = timers.has(type);
      if (options.schedule !== false && !alreadyScheduled) {
        timers.set(type, global.setInterval(
          () => { probe(type, provider).catch((error) => {
            console.warn('[HostInterfaces] scheduled probe failed for ' + type, error);
          }); }, provider.probeIntervalMs));
      }
      // The five-second directory refresh discovers owners and banner contributions. Existing
      // interface providers keep their own (usually much slower) probe cadence; otherwise every
      // directory refresh also performs a redundant bootstrap tool call.
      if (options.discovered === true && alreadyScheduled) return Promise.resolve(null);
      return probe(type, provider);
    });
    if (options.schedule !== false && directoryTimer == null) {
      directoryTimer = global.setInterval(() => {
        discover().then(() => bootstrap({ schedule: true, discovered: true })).catch((error) => {
          console.warn('[HostInterfaces] extension refresh failed', error);
        });
      }, DIRECTORY_REFRESH_MS);
    }
    return Promise.allSettled(tasks);
  }

  function stop() {
    for (const timer of timers.values()) global.clearInterval(timer);
    timers.clear();
    probeFailures.clear();
    if (directoryTimer != null) global.clearInterval(directoryTimer);
    directoryTimer = null;
  }

  restoreProviders();
  global.AippHostInterfaces = Object.freeze({
    dispatch, bootstrap, fallback, stop, discover, implementation,
    extensions: () => directory,
  });
}(window));
