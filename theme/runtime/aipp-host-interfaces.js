/** Stable browser-side Host interface registry. Domain implementations live in AIPPs. */
(function registerHostInterfaces(global) {
  'use strict';

  const PROVIDERS = Object.freeze({
    'shared.theme.apply/v1': Object.freeze({
      appId: 'theme-one',
      moduleUrl: '/api/proxy/app/theme-one/theme-interface/theme-interface.js',
      bootstrapTool: 'theme_current',
    }),
  });
  const loaded = new Map();

  function assert(condition, message) {
    if (!condition) throw new Error(message);
  }

  async function implementation(type) {
    const provider = PROVIDERS[type];
    assert(provider, 'unsupported shared Host interface: ' + type);
    if (!loaded.has(type)) {
      loaded.set(type, import(provider.moduleUrl).then((module) => {
        const api = module.default || module;
        assert(typeof api.apply === 'function' && typeof api.unload === 'function',
          'invalid shared Host interface implementation: ' + type);
        return api;
      }));
    }
    return loaded.get(type);
  }

  async function dispatch(effect) {
    assert(effect && typeof effect.type === 'string', 'host effect type is required');
    const api = await implementation(effect.type);
    const result = await api.apply(effect.payload);
    global.dispatchEvent(new CustomEvent('aipp-host-interface-change', {
      detail: Object.freeze({ type: effect.type }),
    }));
    return result;
  }

  async function bootstrap() {
    for (const [type, provider] of Object.entries(PROVIDERS)) {
      try {
        const response = await fetch('/api/proxy/tools/' + provider.bootstrapTool, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: '{}',
        });
        if (!response.ok) continue;
        const body = await response.json();
        if (body?.host_effect?.type === type) await dispatch(body.host_effect);
      } catch (error) {
        console.warn('[HostInterfaces] bootstrap deferred for ' + type, error);
      }
    }
  }

  global.AippHostInterfaces = Object.freeze({ dispatch, bootstrap });
}(window));
