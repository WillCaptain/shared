/** Stable browser-side Host interface registry. Domain implementations live in AIPPs. */
(function registerHostInterfaces(global) {
  'use strict';

  const PROVIDERS = Object.freeze({
    'shared.theme.apply/v1': Object.freeze({
      appId: 'theme-one',
      moduleUrl: 'api/proxy/app/theme-one/theme-interface/theme-interface.js',
      bootstrapTool: 'theme_current',
      probeIntervalMs: 30000,
    }),
  });
  const FALLBACK_KEY_PREFIX = 'ones.host-interface.fallback.v1:';
  const loaded = new Map();
  const implementations = new Map();
  const probes = new Map();
  const timers = new Map();
  const activeEffects = new Map();

  function assert(condition, message) {
    if (!condition) throw new Error(message);
  }

  function emit(type, state, reason) {
    global.dispatchEvent(new global.CustomEvent('aipp-host-interface-change', {
      detail: Object.freeze({ type, state, reason: reason || null }),
    }));
  }

  async function implementation(type) {
    const provider = PROVIDERS[type];
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
    if (identity && activeEffects.get(effect.type) === identity) return effect.payload;
    const api = await implementation(effect.type);
    const result = await api.apply(effect.payload);
    if (identity) activeEffects.set(effect.type, identity);
    emit(effect.type, 'active');
    return result;
  }

  function effectIdentity(effect) {
    const payload = effect?.payload;
    if (!payload?.package_id || !payload?.version || !payload?.instance_id) return null;
    return payload.package_id + '@' + payload.version + '#' + payload.instance_id;
  }

  async function fallback(type, reason) {
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
        emit(type, 'fallback', reason);
        return result;
      } catch (error) {
        console.warn('[HostInterfaces] cached fallback failed for ' + type, error);
      }
    }
    await api?.unload?.();
    activeEffects.delete(type);
    emit(type, 'neutral', reason);
    return null;
  }

  async function probe(type, provider) {
    if (probes.has(type)) return probes.get(type);
    const pending = (async () => {
      const controller = new AbortController();
      const timeout = global.setTimeout?.(() => controller.abort(), 3000);
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
        return await dispatch(body.host_effect);
      } catch (error) {
        console.warn('[HostInterfaces] provider unavailable for ' + type, error);
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
    const tasks = Object.entries(PROVIDERS).map(([type, provider]) => {
      if (options.schedule !== false && !timers.has(type)) {
        timers.set(type, global.setInterval(
          () => { probe(type, provider).catch((error) => {
            console.warn('[HostInterfaces] scheduled probe failed for ' + type, error);
          }); }, provider.probeIntervalMs));
      }
      return probe(type, provider);
    });
    return Promise.allSettled(tasks);
  }

  function stop() {
    for (const timer of timers.values()) global.clearInterval(timer);
    timers.clear();
  }

  global.AippHostInterfaces = Object.freeze({ dispatch, bootstrap, fallback, stop });
}(window));
