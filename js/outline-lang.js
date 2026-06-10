/* ════════════════════════════════════════════════════════════════════════
 *  Outline Language SDK  —  Monaco language services
 *  ─────────────────────────────────────────────────────────────────────
 *  Unified API for any Monaco-based Outline code editor.
 *
 *  ── Global registrations (idempotent, language-wide) ──
 *   OutlineLang.registerLanguage()
 *   OutlineLang.registerCompletions(options)
 *   OutlineLang.registerHover(options?)
 *
 *  ── Type map (session-scoped symbol data) ──
 *   OutlineLang.setTypeMap(data)
 *   OutlineLang.loadTypeMap(url, force?)   async — fetch + setTypeMap
 *   OutlineLang.invalidateTypeMap()
 *   OutlineLang.lookupType(word)
 *
 *  ── Per-editor helpers ──
 *   OutlineLang.createDiagnostics(options)
 *
 *  ── Editor factory (recommended entry point) ──
 *   OutlineLang.createEditor(container, options) → Promise<EditorHandle>
 *     Creates a fully-configured Outline Monaco editor in one call.
 *     Handles Monaco loading, language/hover/completion registration,
 *     type-map loading, editor creation, and diagnostics setup.
 * ════════════════════════════════════════════════════════════════════════ */
'use strict';

window.OutlineLang = (function () {

  /** Debounce for validate + infer (ms). Override via {@link #setEditorDebounceMs}. */
  var _editorDebounceMs = 1000;

  function setEditorDebounceMs(ms) {
    var n = Number(ms);
    if (!isFinite(n)) return;
    _editorDebounceMs = Math.max(0, Math.min(30000, Math.round(n)));
  }

  function resolveDebounceMs(override) {
    if (override != null && isFinite(Number(override))) {
      return Math.max(0, Math.min(30000, Math.round(Number(override))));
    }
    return _editorDebounceMs;
  }

  /**
   * Debounced async task with generation guard (stale HTTP responses ignored).
   * @returns {Function} schedule — call to debounce; `.flush()` run now; `.cancel()` drop pending.
   */
  function createDebouncedTask(options) {
    var debounceMs = resolveDebounceMs(options.debounceMs);
    var execute = options.execute;
    var timer = null;
    var generation = 0;

    function cancelTimer() {
      if (timer) { clearTimeout(timer); timer = null; }
    }

    function schedule() {
      cancelTimer();
      timer = setTimeout(function() {
        timer = null;
        flush();
      }, debounceMs);
    }

    function flush() {
      cancelTimer();
      var gen = ++generation;
      function isStale() { return gen !== generation; }
      return Promise.resolve(execute(gen, isStale)).catch(function() {});
    }

    function cancel() {
      generation++;
      cancelTimer();
    }

    schedule.flush = flush;
    schedule.cancel = cancel;
    schedule.runNow = flush;
    return schedule;
  }

  /* ── 1. Language registration (idempotent) ──────────────────────────────── */

  var _registered = false;

  function registerLanguage() {
    if (_registered) return;
    _registered = true;

    monaco.languages.register({ id: 'outline' });

    monaco.languages.setMonarchTokensProvider('outline', {
      keywords: [
        'let','var','if','else','match','outline','module',
        'import','export','from','as','return','this','with',
        'fx','baseNode','sync','macro','true','false','unit','Unit',
      ],
      typeKeywords: ['String','Int','Long','Float','Double','Bool','Number','Unit'],
      tokenizer: {
        root: [
          [/\/\/.*/, 'comment'],
          [/\/\*[\s\S]*?\*\//, 'comment'],
          [/#"([^"\\]|\\.)*"/, 'string.literal-type'],
          [/"([^"\\]|\\.)*"/, 'string'],
          [/\b[A-Z]\w*\b/, 'type.identifier'],
          [/\b(let|var|if|else|match|outline|module|import|export|from|as|return|this|with|fx|baseNode|sync|macro|unit)\b/, 'keyword'],
          [/\b(true|false)\b/, 'constant.language'],
          [/\b(String|Int|Long|Float|Double|Bool|Number|Unit)\b/, 'type.primitive'],
          [/\b\d+[lL]?\b/, 'number'],
          [/\b\d+\.\d+[fFdD]?\b/, 'number.float'],
          [/->|=>|\.\.\./, 'operator.special'],
          [/[=!<>]=?|[+\-*\/%&|^~]/, 'operator'],
          [/[{}\[\](),.;:]/, 'delimiter'],
          [/\b[a-z_]\w*\b/, 'identifier'],
        ],
      },
    });

    monaco.languages.setLanguageConfiguration('outline', {
      brackets:         [['(',')'],['{','}'],['[',']']],
      autoClosingPairs: [{open:'(',close:')'},{open:'{',close:'}'},{open:'[',close:']'},{open:'"',close:'"'}],
      surroundingPairs: [{open:'(',close:')'},{open:'{',close:'}'},{open:'[',close:']'},{open:'"',close:'"'}],
      comments: { lineComment: '//', blockComment: ['/*','*/'] },
    });

    _defineThemes();
  }

  function _defineThemes() {
    monaco.editor.defineTheme('outline-dark', {
      base: 'vs-dark', inherit: true,
      rules: [
        {token:'keyword',            foreground:'4facfe', fontStyle:'bold'},
        {token:'type.identifier',    foreground:'c084fc', fontStyle:'bold'},
        {token:'type.primitive',     foreground:'67e8f9'},
        {token:'string',             foreground:'a3e635'},
        {token:'string.literal-type',foreground:'f9a825', fontStyle:'bold'},
        {token:'number',             foreground:'fbbf24'},
        {token:'number.float',       foreground:'fb923c'},
        {token:'comment',            foreground:'4a5a7a', fontStyle:'italic'},
        {token:'operator.special',   foreground:'4facfe', fontStyle:'bold'},
        {token:'operator',           foreground:'94a3b8'},
        {token:'constant.language',  foreground:'f472b6', fontStyle:'bold'},
        {token:'identifier',         foreground:'e2e8f0'},
        {token:'delimiter',          foreground:'64748b'},
      ],
      colors: {
        'editor.background'                :'#13172b',
        'editor.foreground'                :'#e2e8f0',
        'editor.lineHighlightBackground'   :'#1d2340',
        'editor.selectionBackground'       :'#3a4a7a',
        'editorLineNumber.foreground'      :'#2d3a5a',
        'editorLineNumber.activeForeground':'#5a7aaa',
        'editorCursor.foreground'          :'#4facfe',
        'editorError.foreground'           :'#f87171',
        'editorWarning.foreground'         :'#fbbf24',
      },
    });

    monaco.editor.defineTheme('outline-light', {
      base: 'vs', inherit: true,
      rules: [
        {token:'keyword',            foreground:'1d4ed8', fontStyle:'bold'},
        {token:'type.identifier',    foreground:'7c3aed', fontStyle:'bold'},
        {token:'type.primitive',     foreground:'0891b2'},
        {token:'string',             foreground:'15803d'},
        {token:'string.literal-type',foreground:'d97706', fontStyle:'bold'},
        {token:'number',             foreground:'b45309'},
        {token:'number.float',       foreground:'c2410c'},
        {token:'comment',            foreground:'9ca3af', fontStyle:'italic'},
        {token:'operator.special',   foreground:'1d4ed8', fontStyle:'bold'},
        {token:'constant.language',  foreground:'db2777', fontStyle:'bold'},
      ],
      colors: {
        'editor.background'             :'#f8fafc',
        'editor.lineHighlightBackground':'#f1f5f9',
        'editorError.foreground'        :'#dc2626',
        'editorWarning.foreground'      :'#d97706',
      },
    });
  }

  /* ── 2. Completion provider (stale-while-revalidate + optional schema-first) ─
   *
   * Registered exactly once per page; per-editor routing is handled via
   * {@link attachModelOptions} which stores a config blob on each model.
   * The provider prefers the per-model config when present, otherwise falls
   * back to the defaults passed to the first registerCompletions() call.
   */

  var _completionRegistered = false;
  var _globalCompletionDefaults = null;
  var _modelOptions = new WeakMap();

  /**
   * Attach per-editor config to a Monaco model so the global providers
   * (completions, hover, diagnostics) can route per-editor.
   * Usage: attachModelOptions(editor.getModel(), { completions, hoverOptions, ... })
   */
  function attachModelOptions(model, opts) {
    if (!model) return;
    var existing = _modelOptions.get(model) || {};
    _modelOptions.set(model, Object.assign(existing, opts || {}));
  }
  function getModelOptions(model) {
    return (model && _modelOptions.get(model)) || {};
  }

  function registerCompletions(options) {
    _globalCompletionDefaults = options || {};
    if (_completionRegistered) return;
    _completionRegistered = true;

    var defaults = _globalCompletionDefaults;

    var MAX_ENTRIES     = 64;
    var completionCache = new Map();
    var pendingFetch    = new Map();

    function cacheSet(key, items) {
      if (completionCache.size >= MAX_ENTRIES)
        completionCache.delete(completionCache.keys().next().value);
      completionCache.set(key, items);
    }

    function buildSuggestions(items, model, position) {
      var word  = model.getWordUntilPosition(position);
      var range = {
        startLineNumber: position.lineNumber, startColumn: word.startColumn,
        endLineNumber:   position.lineNumber, endColumn:   word.endColumn,
      };
      return {
        suggestions: (items || []).map(function(it) {
          return {
            label:           it.label,
            kind:            it.kind === 'property' ? monaco.languages.CompletionItemKind.Property
                           : it.kind === 'method'   ? monaco.languages.CompletionItemKind.Method
                           : it.kind === 'outline'  ? monaco.languages.CompletionItemKind.Class
                           : it.kind === 'keyword'  ? monaco.languages.CompletionItemKind.Keyword
                           : it.kind === 'builtin'  ? monaco.languages.CompletionItemKind.Function
                           : monaco.languages.CompletionItemKind.Variable,
            insertText:      it.kind === 'method' ? it.label + '($0)' : it.label,
            insertTextRules: it.kind === 'method'
              ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
            range:           range,
            detail:          it.documentation ? (it.detail || '') + '  \xb7  ' + it.documentation : (it.detail || ''),
            documentation:   it.documentation ? { value: '**' + it.label + '**\n\n' + it.documentation } : undefined,
            sortText:        it.sortText || it.label,
          };
        }),
      };
    }

    function mergeItems(primary, secondary) {
      var seen = Object.create(null);
      var out = [];
      function add(list) {
        (list || []).forEach(function(it) {
          if (!it || !it.label || seen[it.label]) return;
          seen[it.label] = true;
          out.push(it);
        });
      }
      add(primary);
      add(secondary);
      return out;
    }

    function extractChainBase(textBefore) {
      var lines = textBefore.split(/[\n;]/);
      for (var i = lines.length - 1; i >= 0; i--) {
        var line = lines[i].trim();
        if (!line || line.charAt(0) === '.') continue;
        var dotIdx = line.indexOf('.');
        if (dotIdx > 0) {
          var m = line.substring(0, dotIdx).trim().match(/([a-zA-Z_]\w*)$/);
          return m ? m[1] : null;
        }
        break;
      }
      return null;
    }

    function resolveFromSchema(textBefore, schema) {
      if (!schema) return null;
      var m1 = textBefore.match(/(\w+)\s*\(\s*\)\s*\.$/);
      if (m1) { var r1 = schema[m1[1]]; if (r1 && r1.length) return r1; }
      var m2 = textBefore.match(/(\w+)\s*\.$/);
      if (m2) { var r2 = schema[m2[1]]; if (r2 && r2.length) return r2; }
      var base = extractChainBase(textBefore);
      if (base) { var r3 = schema[base]; if (r3 && r3.length) return r3; }
      return null;
    }

    function startFetch(url, body, cacheKey) {
      if (pendingFetch.has(cacheKey)) return pendingFetch.get(cacheKey);
      var p = fetch(url, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
        .then(function(r) { return r.ok ? r.json() : Promise.reject(r.status); })
        .then(function(d) { if (d.items && d.items.length) cacheSet(cacheKey, d.items); return d.items || []; })
        .catch(function(e) { console.warn('[outline completions]', e); return null; })
        .finally(function() { pendingFetch.delete(cacheKey); });
      pendingFetch.set(cacheKey, p);
      return p;
    }

    // Union trigger chars across all potential callers. Monaco only reads
    // triggerCharacters once at provider-registration time; additional chars
    // (like letters) rely on editor option `quickSuggestions.other`.
    var triggerChars = (options && options.triggerChars) || ['.', ' '];

    monaco.languages.registerCompletionItemProvider('outline', {
      triggerCharacters: triggerChars,
      provideCompletionItems: async function(model, position) {
        var offset     = model.getOffsetAt(position);
        var code       = model.getValue();
        var textBefore = code.substring(0, offset);

        // Per-model options win over global defaults.
        var modelOpts = getModelOptions(model).completions || {};
        var urlResolver = modelOpts.url ? function() { return modelOpts.url; }
                       : (modelOpts.urlResolver)
                       || defaults.urlResolver
                       || function() { return '/api/completions'; };
        var getExtraBody     = modelOpts.getExtraBody     || defaults.getExtraBody     || null;
        var getSchemaMembers = modelOpts.getSchemaMembers || defaults.getSchemaMembers || null;
        var getLocalMembers  = modelOpts.getLocalMembers  || defaults.getLocalMembers  || null;
        var localItems       = getLocalMembers ? (getLocalMembers(textBefore, code, offset) || null) : null;

        if (getSchemaMembers) {
          var members = resolveFromSchema(textBefore, getSchemaMembers());
          if (members) return buildSuggestions(mergeItems(localItems, members), model, position);
        }

        var url      = urlResolver(model);
        var extra    = getExtraBody ? getExtraBody(code, offset) : {};
        var body     = Object.assign({ code: code, offset: offset }, extra);
        var cacheKey = url + '::' + textBefore + JSON.stringify(extra);

        var cached = completionCache.get(cacheKey);
        if (cached !== undefined) {
          startFetch(url, body, cacheKey);  // background revalidate
          return buildSuggestions(mergeItems(localItems, cached), model, position);
        }
        var items = await startFetch(url, body, cacheKey);
        return buildSuggestions(mergeItems(localItems, items || []), model, position);
      },
    });
  }

  /* ── 3. Internal type map + public lookup API ───────────────────────────── */

  var _PRIMITIVES = {
    'String':  '**`String`** — Primitive type · string',
    'Int':     '**`Int`** — Primitive type · 32-bit integer',
    'Long':    '**`Long`** — Primitive type · 64-bit integer',
    'Float':   '**`Float`** — Primitive type · 32-bit floating point',
    'Double':  '**`Double`** — Primitive type · 64-bit floating point',
    'Bool':    '**`Bool`** — Primitive type · boolean',
    'Date':    '**`Date`** — Primitive type · date',
    'Unit':    '**`Unit`** — Primitive type · unit',
    'Nothing': '**`Nothing`** — Primitive type · null',
  };

  var _sessionTypeMap = {};

  function setTypeMap(data) {
    _sessionTypeMap = data ? Object.assign({}, data) : {};
  }

  function lookupType(word) {
    if (!word) return null;
    return _PRIMITIVES[word] || _sessionTypeMap[word] || null;
  }

  /* ── 3a. Canonical symbol rendering (12th-style brief hover) ───────────────
   * One renderer used by every hover surface — static type maps, dynamic
   * /api/code-hover responses, and `setSymbols`. A new editor never needs to
   * hand-build hover markdown again.
   *
   *   renderSymbolMd({ name, kind, type, doc? }) →
   *     **name**  *kind*
   *     ```outline
   *     name : type
   *     ```
   *     (doc body if present)
   *
   * `kind` is rendered as a small italic label after the name (the 12th
   * playground style). Internal GCP placeholders (`Lazy{SymbolIdentifier)`,
   * trailing `)` typo, `?` for unknown) are normalised here so backends do
   * not have to remember to clean them up.
   */
  /**
   * Display type text for Monaco cards. JVM backends must use
   * MetaExtractor.formatType before serialising; this only strips inference
   * artifacts (Lazy{…}) so we do not duplicate format rules in JS.
   */
  function formatTypeLabel(t) {
    if (!t) return '?';
    var s = String(t).trim().replace(/Lazy\{[^{}]*[)}]/g, '?');
    return s || '?';
  }

  function _isSelfReferentialType(type, name) {
    if (!type || !name) return false;
    var t = String(type).trim();
    return t === name || t === '`' + name + '`';
  }

  function renderSymbolMd(sym) {
    if (!sym || !sym.name) return null;
    var name = String(sym.name);
    if (_isSelfReferentialType(sym.type, name)) return null;
    var kind = sym.kind ? String(sym.kind) : '';
    var type = formatTypeLabel(sym.type);
    var head = '**' + name + '**' + (kind ? '  *' + kind + '*' : '');
    var body = '\n```outline\n' + name + ' : ' + type + '\n```';
    var doc  = sym.doc ? '\n\n' + String(sym.doc) : '';
    return head + body + doc;
  }

  /**
   * Set the static type map from a structured symbol list (12th playground
   * style). Each entry: { name, kind, type, doc? }. The same canonical
   * renderer used for dynamic hover responses produces the markdown, so the
   * card looks identical regardless of source.
   */
  function setSymbols(symbols) {
    var map = {};
    (symbols || []).forEach(function(s) {
      if (!s || !s.name) return;
      var md = renderSymbolMd(s);
      if (md) map[s.name] = md;
    });
    setTypeMap(map);
  }

  /**
   * Local, context-aware trigger hints used when backend hover is temporarily unavailable.
   * Keep all UI-facing fallback strings centralized here.
   */
  function lookupTriggerLocalType(word, options) {
    if (!word) return null;
    var opts = options || {};
    var entityType = (opts.entityType || '').trim();
    var paramName = (opts.paramName || '').trim();
    var activatorType = (opts.activatorType || '').trim();

    if (entityType && paramName && word === paramName) {
      return '**' + word + '** : *`' + entityType + '`*';
    }
    if (activatorType === 'ontology') {
      if (word === 'event_type') return '**event_type** : *`String`*';
      if (entityType && word === 'event_entity') return '**event_entity** : *`' + entityType + '`*';
    }
    return null;
  }

  /* ── 3b. Type map loader ────────────────────────────────────────────────── */

  var _typeMapUrl    = null;
  var _typeMapLoaded = false;

  /**
   * Fetch session type data from a URL and call setTypeMap().
   * Idempotent within the same URL; pass force=true to re-fetch.
   */
  function loadTypeMap(url, force) {
    if (!url) return Promise.resolve();
    if (_typeMapLoaded && _typeMapUrl === url && !force) return Promise.resolve();
    _typeMapUrl    = url;
    _typeMapLoaded = true;
    return fetch(url)
      .then(function(r) { return r.ok ? r.json() : null; })
      .then(function(data) { if (data) setTypeMap(data); })
      .catch(function() {});
  }

  function invalidateTypeMap() {
    _typeMapLoaded = false;
  }

  /**
   * Unified member query API for any variable/property expression.
   *
   * @param {Object} options
   *   url            string   optional, default '/api/proxy/code-members'
   *   session_id     string   required
   *   code           string   required (full editor content)
   *   offset         number   required (cursor/hover offset)
   *   entity_type    string   optional
   *   include_builtin boolean optional, default true
   * @returns {Promise<{receiver:string, items:Array}>}
   */
  function getMembers(options) {
    var opts = options || {};
    var url = opts.url || '/api/proxy/code-members';
    if (!opts.session_id || typeof opts.code !== 'string') {
      return Promise.resolve({ receiver: '', items: [] });
    }
    var payload = {
      session_id: opts.session_id,
      code: opts.code,
      offset: typeof opts.offset === 'number' ? opts.offset : 0,
      entity_type: opts.entity_type || undefined,
      include_builtin: opts.include_builtin !== false,
    };
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
      .then(function(r) { return r.ok ? r.json() : { receiver: '', items: [] }; })
      .then(function(d) { return { receiver: d.receiver || '', items: d.items || [] }; })
      .catch(function() { return { receiver: '', items: [] }; });
  }

  /* ── 4. Hover provider (backed by internal type map + dynamic fallback) ── */

  var _hoverRegistered = false;
  var _hoverFallbackFn = null;
  // Per-model, per-word/scope last-good hover cache. When a hover request comes back
  // empty (typically because the user has a transient inference error
  // somewhere else in the buffer), we fall back to the previous successful
  // result for the same word in the same model — so users keep seeing type
  // info while they're mid-edit instead of an empty popup.
  var _hoverCache = new WeakMap(); // model -> Map<word+scope+version, content>
  function _hoverCacheGet(model, word) {
    var m = _hoverCache.get(model); if (!m) return null;
    return m.get(word) || null;
  }
  function _hoverCachePut(model, word, content) {
    var m = _hoverCache.get(model);
    if (!m) { m = new Map(); _hoverCache.set(model, m); }
    m.set(word, content);
  }
  function _hoverScopeId(model, extra) {
    extra = extra || {};
    if (extra.scope_id != null) return String(extra.scope_id);
    if (extra.scopeId  != null) return String(extra.scopeId);
    var parts = [
      extra.session_id || '',
      extra.entity_type || '',
      extra.inputType || '',
      extra.executor_raw_body ? 'executor' : '',
      extra.action_raw_body ? 'action' : '',
    ];
    if (Array.isArray(extra.upstreams)) {
      parts.push(extra.upstreams.map(function(s) {
        return [s && s.name, s && s.type].join(':');
      }).join('|'));
    }
    parts.push(model && model.uri ? String(model.uri) : '');
    return parts.join('#');
  }

  /**
   * Set a dynamic hover fallback function.
   * Called when the type map has no match. Signature:
   *   fn(word, code, offset) → Promise<string|null>
   * The returned string is treated as Markdown hover content.
   */
  function setHoverFallback(fn) {
    _hoverFallbackFn = fn || null;
  }

  var _globalHoverDefaults = null;

  function registerHover(options) {
    _globalHoverDefaults = options || {};
    if (_hoverRegistered) return;
    _hoverRegistered = true;

    monaco.languages.registerHoverProvider('outline', {
      provideHover: async function(model, position) {
        var defaults = _globalHoverDefaults || {};
        var modelOpts = getModelOptions(model).hoverOptions || {};
        var urlResolver = typeof modelOpts.hoverUrl === 'function'
                            ? modelOpts.hoverUrl
                       : modelOpts.hoverUrl ? function() { return modelOpts.hoverUrl; }
                       : typeof defaults.hoverUrl === 'function'
                            ? defaults.hoverUrl
                       : function() { return defaults.hoverUrl || null; };
        var getExtraBody = modelOpts.getExtraBody || defaults.getExtraBody || null;
        var wordInfo = model.getWordAtPosition(position);
        if (!wordInfo) return null;
        var offset = model.getOffsetAt(position);

        var hoverRange = {
          startLineNumber: position.lineNumber, startColumn: wordInfo.startColumn,
          endLineNumber:   position.lineNumber, endColumn:   wordInfo.endColumn,
        };
        try {
          var line = model.getLineContent(position.lineNumber) || '';
          var afterWord = line.substring(Math.max(0, wordInfo.endColumn - 1));
          if (/^\(\)/.test(afterWord)) hoverRange.endColumn += 2;
        } catch (_) {}
        // Always surface marker diagnostics at cursor position (red/yellow squiggles).
        // This keeps error hover reliable even when type/remote hover is unavailable.
        var markerContents = [];
        try {
          var markers = monaco.editor.getModelMarkers({ resource: model.uri }) || [];
          markerContents = markers
            .filter(function(m) {
              if (position.lineNumber < m.startLineNumber || position.lineNumber > m.endLineNumber) return false;
              if (position.lineNumber === m.startLineNumber && position.column < m.startColumn) return false;
              if (position.lineNumber === m.endLineNumber && position.column > m.endColumn) return false;
              return !!m.message;
            })
            .map(function(m) {
              var level = m.severity === monaco.MarkerSeverity.Warning ? 'Warning' : 'Error';
              return { value: '**' + level + '**: ' + m.message };
            });
        } catch (_) {}

        // If the host editor opted into preferDynamic (typically code editors
        // backed by a real type-inference endpoint), skip the static type map
        // so inferred types win over schema-derived labels (e.g. `Computer`
        // vs the schema's `Computer` entity-doc card).
        var preferDynamic = !!modelOpts.preferDynamic
          || (modelOpts.hoverOptions && modelOpts.hoverOptions.preferDynamic);
        var markdown = preferDynamic ? null : lookupType(wordInfo.word);
        if (markdown) {
          return {
            range: hoverRange,
            contents: [{ value: markdown, isTrusted: true }].concat(markerContents),
          };
        }

        // Dynamic fallback (set by the active editor via setHoverFallback)
        if (_hoverFallbackFn) {
          try {
            var code   = model.getValue();
            var result = await _hoverFallbackFn(wordInfo.word, code, offset);
            if (result) {
              return {
                range: hoverRange,
                contents: [{ value: result, isTrusted: true }].concat(markerContents),
              };
            }
          } catch (_) {}
        }

        // Static URL fallback (for schema-hover etc.)
        var url = urlResolver(model);
        if (!url) return markerContents.length ? { range: hoverRange, contents: markerContents } : null;

        var code   = model.getValue();
        var extra  = getExtraBody ? getExtraBody(code, offset) : {};
        var version = typeof model.getVersionId === 'function' ? model.getVersionId() : '';
        var hoverCacheKey = [wordInfo.word, _hoverScopeId(model, extra), version].join('@');
        var cachedHit = _hoverCacheGet(model, hoverCacheKey);
        if (cachedHit) {
          return {
            range: hoverRange,
            contents: [{ value: cachedHit, isTrusted: true }].concat(markerContents),
          };
        }
        var body   = Object.assign({ code: code, offset: offset }, extra);

        try {
          var resp = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
          });
          if (!resp.ok) return null;
          var data = await resp.json();
          // Preferred shape: structured symbol payload, rendered uniformly
          // by renderSymbolMd. Falls back to legacy `contents` markdown.
          var rendered = data && data.symbol ? renderSymbolMd(data.symbol) : null;
          var content  = rendered || (data && data.contents) || null;
          if (!content) {
            var cached = _hoverCacheGet(model, hoverCacheKey);
            if (cached) {
              return {
                range: hoverRange,
                contents: [{ value: cached, isTrusted: true }].concat(markerContents),
              };
            }
            return markerContents.length ? { range: hoverRange, contents: markerContents } : null;
          }
          _hoverCachePut(model, hoverCacheKey, content);
          return {
            range: hoverRange,
            contents: [{ value: content, isTrusted: true }].concat(markerContents),
          };
        } catch (_) {
          var cachedErr = _hoverCacheGet(model, hoverCacheKey);
          if (cachedErr) {
            return {
              range: hoverRange,
              contents: [{ value: cachedErr, isTrusted: true }].concat(markerContents),
            };
          }
          return markerContents.length ? { range: hoverRange, contents: markerContents } : null;
        }
      },
    });
  }

  /* ── 4b. Signature help provider (function call parameter card) ─────────── */

  var _signatureRegistered = false;
  var _globalSignatureDefaults = null;

  function registerSignatureHelp(options) {
    _globalSignatureDefaults = options || {};
    if (_signatureRegistered) return;
    _signatureRegistered = true;

    monaco.languages.registerSignatureHelpProvider('outline', {
      signatureHelpTriggerCharacters: ['(', ',', '{', '='],
      signatureHelpRetriggerCharacters: [',', '{', '='],
      provideSignatureHelp: async function(model, position) {
        var defaults = _globalSignatureDefaults || {};
        var modelOpts = getModelOptions(model).signatureHelp || {};
        var url = modelOpts.url || defaults.url || null;
        if (!url) return null;
        var getExtraBody = modelOpts.getExtraBody || defaults.getExtraBody || null;
        var offset = model.getOffsetAt(position);
        var code = model.getValue();
        var extra = getExtraBody ? getExtraBody(code, offset) : {};
        var body = Object.assign({ code: code, offset: offset }, extra);
        try {
          var resp = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
          });
          if (!resp.ok) return null;
          var data = await resp.json();
          if (!data || !data.signatures || !data.signatures.length) return null;
          return {
            value: {
              signatures: data.signatures,
              activeSignature: data.activeSignature || 0,
              activeParameter: data.activeParameter || 0,
            },
            dispose: function() {},
          };
        } catch (_) {
          return null;
        }
      },
    });
  }

  /* ── 5. Diagnostics (debounced validate + setModelMarkers) ──────────────── */

  function createDiagnostics(options) {
    var validateUrl    = options.validateUrl;
    var getRequestBody = options.getRequestBody  || null;
    var mapMarker      = options.mapMarker       || function(m) { return m; };
    var editorRef      = options.editor;
    var markerOwner    = options.markerOwner     || 'outline-lint';
    var onResult       = typeof options.onResult === 'function' ? options.onResult : null;

    return createDebouncedTask({
      debounceMs: options.debounceMs,
      execute: async function(gen, isStale) {
        var ed = editorRef;
        if (!ed) return;
        var model = ed.getModel();
        if (!model) return;
        var raw = ed.getValue();
        if (!raw.trim()) {
          if (!isStale()) monaco.editor.setModelMarkers(model, markerOwner, []);
          if (!isStale() && onResult) onResult({ status: 'ok', returnType: '' });
          return;
        }
        try {
          var body = getRequestBody ? getRequestBody(raw) : { code: raw };
          var resp = await fetch(validateUrl, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
          });
          if (!resp.ok || !editorRef || isStale()) return;
          var data = await resp.json();
          if (isStale()) return;
          if (onResult) onResult(data);
          var userLines = model.getLineCount();
          monaco.editor.setModelMarkers(model, markerOwner,
            (data.markers || [])
              .map(mapMarker)
              .filter(function(m) {
                return m && m.startLine >= 1 && m.startLine <= userLines;
              })
              .map(function(m) {
                return {
                  startLineNumber: m.startLine,
                  startColumn:     m.startColumn + 1,
                  endLineNumber:   Math.max(m.startLine, Math.min(userLines, m.endLine)),
                  endColumn:       m.endColumn + 1,
                  message:         m.message,
                  severity:        m.severity === 4
                    ? monaco.MarkerSeverity.Warning
                    : monaco.MarkerSeverity.Error,
                };
              }));
        } catch (_) {}
      },
    });
  }

  /* ── 5b. Return-type inference (debounced infer + optional status callback) */

  function createInfer(options) {
    var inferUrl       = options.inferUrl;
    var getRequestBody = options.getRequestBody || null;
    var editorRef      = options.editor;
    var onStatus       = typeof options.onStatus === 'function' ? options.onStatus : null;
    var mapResponse    = typeof options.mapResponse === 'function'
      ? options.mapResponse
      : function(data) {
          if (data && data.status === 'ok') {
            var ret = String(data.returnType || '').trim();
            return { kind: 'ok', text: ret ? ('valid · ' + ret) : 'valid' };
          }
          return { kind: 'err', text: (data && data.error) || 'invalid' };
        };

    function emit(status, raw) {
      if (!onStatus) return;
      onStatus(status || { kind: '', text: '' }, raw);
    }

    return createDebouncedTask({
      debounceMs: options.debounceMs,
      execute: async function(gen, isStale) {
        var ed = editorRef;
        if (!ed) return;
        var code = (ed.getValue() || '').trim();
        if (!code) {
          if (!isStale()) emit({ kind: '', text: '' }, null);
          return;
        }
        if (!isStale()) emit({ kind: '', text: 'inferring…' }, null);
        try {
          var body = getRequestBody ? getRequestBody(code) : { code: code };
          var resp = await fetch(inferUrl, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
          });
          if (isStale()) return;
          if (!resp.ok) { emit({ kind: 'err', text: 'infer failed' }, null); return; }
          var data = await resp.json();
          if (isStale()) return;
          emit(mapResponse(data), data);
        } catch (e) {
          if (!isStale()) emit({ kind: 'err', text: (e && e.message) || 'infer failed' }, null);
        }
      },
    });
  }

  /* ── 5c. Manifest helpers (host-agnostic editor context) ────────────────── */

  /**
   * Build a generic wrap function from static open/close envelope strings.
   * If the user code already contains `->`, returns it unchanged (full lambda).
   */
  function envelopeWrap(spec) {
    var open  = (spec && spec.open  != null) ? String(spec.open)  : '';
    var close = (spec && spec.close != null) ? String(spec.close) : '';
    return function(rawCode, rawOffset) {
      var raw = rawCode || '';
      if (raw.trim().indexOf('->') >= 0) return { code: raw, offset: rawOffset || 0 };
      return {
        code:   open + raw + close,
        offset: Math.max(0, (rawOffset || 0) + open.length),
      };
    };
  }

  /**
   * Normalize a host editor manifest to the canonical SDK shape.
   * Accepts legacy aliases: listener_signature, wrap_open, wrap_close.
   */
  function normalizeEditorManifest(raw) {
    if (!raw || typeof raw !== 'object') return {};
    var m = Object.assign({}, raw);
    if (m.listener_signature && !m.signature) m.signature = m.listener_signature;
    if (m.wrap_open != null || m.wrap_close != null) {
      m.wrap = m.wrap || {};
      if (m.wrap_open  != null && m.wrap.open  == null) m.wrap.open  = m.wrap_open;
      if (m.wrap_close != null && m.wrap.close == null) m.wrap.close = m.wrap_close;
    }
    delete m.wrap_open;
    delete m.wrap_close;
    delete m.listener_signature;
    if (!m.urls || typeof m.urls !== 'object') m.urls = {};
    return m;
  }

  /**
   * Fetch and normalize an editor manifest from a host URL.
   * @param {string} manifestUrl
   * @returns {Promise<Object>}
   */
  function loadEditorContext(manifestUrl) {
    if (!manifestUrl) return Promise.reject(new Error('manifestUrl is required'));
    return fetch(manifestUrl)
      .then(function(r) {
        return r.ok ? r.json() : Promise.reject(new Error('manifest fetch failed: ' + r.status));
      })
      .then(normalizeEditorManifest);
  }

  /**
   * Build a createEditor `inference` config from a normalized manifest context.
   *
   * Host manifests supply static preamble, wrap envelope, and endpoint URLs.
   * Widgets pass dynamic pieces (wrap fn, prelude provider, infer callbacks)
   * via `options` — no scenario branches inside the SDK.
   *
   * @param {Object} ctx       normalized manifest (from loadEditorContext)
   * @param {Object} [options]
   *   wrap                 function(code,offset) — overrides manifest envelope
   *   prelude              string | () => string — overrides manifest preamble
   *   urls                 object — merge/override manifest urls
   *   sessionId, entityType, onInferStatus, mapInferResponse, triggerChars,
   *   preferDynamicHover, inferDebounceMs, useValidateForInfer, getExtraBody(code,offset),
   *   getInferRequestBody(code) — optional infer-only body (defaults to baseBody)
   */
  function inferenceFromContext(ctx, options) {
    options = options || {};
    ctx = ctx || {};
    var urls = Object.assign({}, ctx.urls || {}, options.urls || {});

    var wrap = options.wrap;
    if (!wrap && ctx.wrap && (ctx.wrap.open != null || ctx.wrap.close != null)) {
      wrap = envelopeWrap(ctx.wrap);
    }
    if (!wrap) {
      wrap = function(code, offset) { return { code: code, offset: offset || 0 }; };
    }

    var inf = {
      prelude: options.prelude != null ? options.prelude : (ctx.preamble || ctx.prelude || ''),
      wrap:    wrap,
      urls:    urls,
    };
    if (options.sessionId  != null) inf.sessionId  = options.sessionId;
    if (options.entityType  != null) inf.entityType  = options.entityType;
    if (options.onInferStatus)        inf.onInferStatus     = options.onInferStatus;
    if (options.mapInferResponse)     inf.mapInferResponse  = options.mapInferResponse;
    if (options.triggerChars)         inf.triggerChars      = options.triggerChars;
    if (options.preferDynamicHover !== undefined) inf.preferDynamicHover = options.preferDynamicHover;
    if (options.inferDebounceMs != null) inf.inferDebounceMs = options.inferDebounceMs;
    if (options.useValidateForInfer != null) inf.useValidateForInfer = !!options.useValidateForInfer;
    if (options.editorDebounceMs != null) inf.editorDebounceMs = options.editorDebounceMs;
    if (options.getExtraBody)         inf.getExtraBody      = options.getExtraBody;
    if (options.getInferRequestBody)  inf.getInferRequestBody = options.getInferRequestBody;
    if (ctx.signature) inf.signature = ctx.signature;
    return inf;
  }

  /* ── 6. Editor factory (unified Monaco creation) ────────────────────────── */

  var _monacoLoaderReady = false;
  var MONACO_CDN = 'https://cdn.jsdelivr.net/npm/monaco-editor@0.46.0/min/vs';

  var _defaultEditorOptions = {
    language:            'outline',
    theme:               'outline-dark',
    minimap:             { enabled: false },
    fontSize:            13,
    fontFamily:          "'JetBrains Mono','Fira Code',monospace",
    lineNumbers:         'on',
    scrollBeyondLastLine: false,
    automaticLayout:     true,
    wordWrap:            'on',
    tabSize:             2,
    insertSpaces:        true,
    // Attach overflow widgets (hover, suggest) to document.body so they are
    // not clipped by ancestor elements with overflow:hidden (e.g. modals).
    fixedOverflowWidgets: true,
    // Trigger completion as the user types any identifier character — not
    // just dot — so bare-identifier prefix filtering (e.g. `e` → employee /
    // Employee / event_entity) works out of the box.
    quickSuggestions: { other: true, comments: false, strings: false },
    suggestOnTriggerCharacters:   true,
    acceptSuggestionOnCommitCharacter: false,
    autoClosingDelete:  'never',
  };

  /**
   * Create a fully-configured Outline Monaco editor.
   *
   * @param {HTMLElement} container   DOM element to host the editor
   * @param {Object}      options
   *   value            string        initial code (default '')
   *   readOnly         boolean       default false
   *   editorOptions    Object        extra Monaco editor options (merged last, overrides defaults)
   *   completions      Object|null   { url, getExtraBody(code,offset)=>obj, triggerChars }
   *   diagnostics      Object|null   { validateUrl, getRequestBody(code)=>obj, debounceMs, markerOwner, editorRef }
   *   typeMapUrl       string|null   GET URL for session type map
   *   hoverOptions     Object|null   passed to registerHover() (e.g. { hoverUrl })
   *
   * @returns {Promise<{ editor, scheduleDiagnostics }>}
   */
  /**
   * Expand a high-level `inference` config into the per-provider options
   * (`completions`, `signatureHelp`, `hoverOptions`, `diagnostics`,
   * `typeMapUrl`). Lets a host editor describe one wrap+endpoints config
   * instead of repeating the same body-builder four times.
   *
   * Shape:
   *   inference: {
   *     prelude,                         string | () => string   (optional)
   *                                      Extra Outline source in scope,
   *                                      prepended before the editor code.
   *                                      Generic — the SDK has no idea what
   *                                      the prelude is (world schema,
   *                                      stdlib, fixture, …). Empty default.
   *     wrap(code, offset),              (code, offset) => { code, offset }
   *                                      Per-editor host binding (e.g.
   *                                      action lambda). Identity by default.
   *     sessionId,                       string | () => string   (legacy)
   *     entityType,                      string | () => string   (legacy)
   *                                      Forwarded if set, omitted if not.
   *                                      New editors should rely on prelude
   *                                      + wrap and leave these unset.
   *     urls: {
   *       validate, infer, hover, completions, signature, members, typeMap
   *     },
     *     onInferStatus(status, rawData), optional — infer status + raw JSON response
   *     mapInferResponse(data),          optional override for infer response → status
   *     inferDebounceMs,                 per-editor override (default: {@link #setEditorDebounceMs})
   *     useValidateForInfer,             boolean, default false. When true,
   *                                      infer status/returnType is consumed
   *                                      from validate response; no /infer call.
   *     editorDebounceMs,                alias for inferDebounceMs (validate + infer share)
   *     triggerChars,                    default ['.', ' ']
   *     preferDynamicHover               default true — skip static typeMap
   *                                      for hover, route straight to the
   *                                      hover endpoint (so inferred types
   *                                      always win over schema labels).
   *   }
   *
   * Per-provider options the caller already supplied are preserved — this
   * only fills in defaults for anything that was left blank.
   */
  function _expandInferenceConfig(opts) {
    var inf = opts && opts.inference;
    if (!inf) return opts || {};
    var urls = inf.urls || {};
    var resolve = function(v) { return typeof v === 'function' ? v() : v; };
    var wrap = typeof inf.wrap === 'function'
      ? inf.wrap
      : function(code, offset) { return { code: code, offset: offset }; };

    // Composition order: prelude + wrap(editor_code).
    // - prelude:  extra Outline source in scope (e.g. world schema). String
    //             or () => string. Empty by default — generic editor (12th)
    //             needs no prelude; world-entitir-style hosts pass theirs.
    // - wrap:     per-editor host binding (e.g. action lambda).
    // The unified body shape is `{ code, offset }` — no domain-specific
    // fields. Legacy `sessionId`/`entityType` are forwarded only for hosts
    // that still need them server-side; new editors should leave them unset.
    function baseBody(code, offset) {
      var w = wrap(code, offset || 0) || { code: code, offset: offset || 0 };
      var preludeRaw = resolve(inf.prelude);
      var prelude    = (preludeRaw == null ? '' : String(preludeRaw));
      var combinedCode   = prelude + (w.code != null ? w.code : code);
      var combinedOffset = prelude.length + (typeof w.offset === 'number' ? w.offset : (offset || 0));
      // prelude_length lets the server cache the parsed+inferred preamble ASF
      // by prelude content hash and fork it per request, so a typing user
      // re-parses only the wrapped user code (~100 chars) instead of the
      // full world schema (~thousands of chars) on every keystroke.
      var body = { code: combinedCode, offset: combinedOffset, prelude_length: prelude.length };
      var sid = resolve(inf.sessionId);
      var et  = resolve(inf.entityType);
      if (sid) body.session_id  = sid;
      if (et)  body.entity_type = et;
      if (typeof inf.getExtraBody === 'function') {
        var extra = inf.getExtraBody(code, offset || 0) || {};
        Object.assign(body, extra);
      }
      return body;
    }

    // Newlines in wrap.open only. Stateless validate strips prelude server-side
    // (via prelude_length) and returns markers in post-prelude coordinates.
    function wrapOpenLineCount() {
      var w = wrap('', 0) || { code: '', offset: 0 };
      var openLen = typeof w.offset === 'number' ? w.offset : 0;
      var open = (w.code || '').substring(0, openLen);
      var n = 0;
      for (var i = 0; i < open.length; i++) if (open.charCodeAt(i) === 10) n++;
      return n;
    }

    var out = Object.assign({}, opts);
    var editorDebounce = resolveDebounceMs(
        inf.editorDebounceMs != null ? inf.editorDebounceMs : inf.inferDebounceMs);

    if (urls.completions && !out.completions) {
      out.completions = {
        url:           urls.completions,
        triggerChars:  inf.triggerChars || ['.', ' '],
        getExtraBody:  function(code, offset) { return baseBody(code, offset); },
      };
    }
    if (urls.signature && !out.signatureHelp) {
      out.signatureHelp = {
        url:          urls.signature,
        getExtraBody: function(code, offset) { return baseBody(code, offset); },
      };
    }
    if (urls.hover && !out.hoverOptions) {
      out.hoverOptions = {
        hoverUrl:        urls.hover,
        getExtraBody:    function(code, offset) { return baseBody(code, offset); },
        preferDynamic:   inf.preferDynamicHover !== false,
      };
    }
    if (urls.validate && !out.diagnostics) {
      out.diagnostics = {
        validateUrl:    urls.validate,
        debounceMs:     editorDebounce,
        // Validate uses code only; reuse the same prelude+wrap pipeline so
        // diagnostic line/column anchors line up with hover/completions/
        // signature responses (all four are wrapping the same way).
        getRequestBody: function(code) { return baseBody(code, 0); },
        // Markers are in post-prelude strip coordinates; subtract wrap.open only.
        mapMarker: function(m) {
          var lo = wrapOpenLineCount();
          if (lo <= 0) return m;
          var sl = Math.max(1, (m.startLine || 1) - lo);
          var el = Math.max(1, (m.endLine   || 1) - lo);
          return Object.assign({}, m, { startLine: sl, endLine: el });
        },
      };
      if (inf.useValidateForInfer) {
        var mapResponse = typeof inf.mapInferResponse === 'function'
          ? inf.mapInferResponse
          : function(data) {
              if (data && data.status === 'ok') {
                var ret = String(data.returnType || '').trim();
                return { kind: 'ok', text: ret ? ('valid · ' + ret) : 'valid' };
              }
              return { kind: 'err', text: (data && data.error) || 'invalid' };
            };
        out.diagnostics.onResult = function(data) {
          if (typeof inf.onInferStatus !== 'function') return;
          inf.onInferStatus(mapResponse(data), data);
        };
      }
    }
    if (!inf.useValidateForInfer && urls.infer && !out.infer) {
      var inferBodyFn = typeof inf.getInferRequestBody === 'function'
        ? inf.getInferRequestBody
        : function(code) { return baseBody(code, 0); };
      out.infer = {
        inferUrl:       urls.infer,
        getRequestBody: inferBodyFn,
        debounceMs:     editorDebounce,
        onStatus:       inf.onInferStatus || null,
        mapResponse:    inf.mapInferResponse || null,
      };
    }
    if (urls.typeMap && !out.typeMapUrl) out.typeMapUrl = urls.typeMap;
    return out;
  }

  function createEditor(container, options) {
    var opts = _expandInferenceConfig(options || {});
    return new Promise(function(resolve, reject) {
      if (!container) { reject(new Error('container is required')); return; }

      function init() {
        try {
          registerLanguage();
          // Register providers once, globally. Per-editor routing is attached
          // to each model via attachModelOptions() below.
          if (opts.hoverOptions && (opts.hoverOptions.hoverUrl || opts.hoverOptions.getExtraBody)) {
            registerHover(opts.hoverOptions);
          }
          registerCompletions({
            triggerChars: (opts.completions && opts.completions.triggerChars) || ['.', ' '],
          });
          if (opts.signatureHelp && opts.signatureHelp.url) {
            registerSignatureHelp(opts.signatureHelp);
          }

          if (opts.typeMapUrl) loadTypeMap(opts.typeMapUrl);

          var edOpts = Object.assign({}, _defaultEditorOptions, {
            value:       opts.value || '',
            readOnly:    !!opts.readOnly,
            domReadOnly: !!opts.readOnly,
          }, opts.editorOptions || {});

          var editor = monaco.editor.create(container, edOpts);

          // Attach this editor's per-model routing so the shared providers
          // know how to reach THIS editor's backend endpoints.
          attachModelOptions(editor.getModel(), {
            completions:  opts.completions  || null,
            hoverOptions: opts.hoverOptions || null,
            signatureHelp: opts.signatureHelp || null,
          });

          var scheduleDiag = null;
          if (opts.diagnostics) {
            scheduleDiag = createDiagnostics({
              validateUrl:    opts.diagnostics.validateUrl,
              getRequestBody: opts.diagnostics.getRequestBody || null,
              mapMarker:      opts.diagnostics.mapMarker      || null,
              onResult:       opts.diagnostics.onResult       || null,
              editor:         opts.diagnostics.editorRef || editor,
              debounceMs:     opts.diagnostics.debounceMs,
              markerOwner:    opts.diagnostics.markerOwner,
            });
            editor.onDidChangeModelContent(function() { scheduleDiag(); });
            editor.onDidBlurEditorWidget(function() { scheduleDiag.flush(); });
            scheduleDiag();
          }

          var scheduleInfer = null;
          if (opts.infer) {
            scheduleInfer = createInfer({
              inferUrl:       opts.infer.inferUrl,
              getRequestBody: opts.infer.getRequestBody || null,
              editor:         editor,
              debounceMs:     opts.infer.debounceMs,
              onStatus:       opts.infer.onStatus || null,
              mapResponse:    opts.infer.mapResponse || null,
            });
            editor.onDidChangeModelContent(function() { scheduleInfer(); });
            editor.onDidBlurEditorWidget(function() { scheduleInfer.flush(); });
            scheduleInfer();
          }

          function flushOutlineCompile() {
            var p = [];
            if (scheduleDiag && scheduleDiag.flush) p.push(scheduleDiag.flush());
            if (scheduleInfer && scheduleInfer.flush) p.push(scheduleInfer.flush());
            return Promise.all(p);
          }

          resolve({
            editor:              editor,
            scheduleDiagnostics: scheduleDiag,
            scheduleInfer:       scheduleInfer,
            flushOutlineCompile: flushOutlineCompile,
          });
        } catch (err) {
          reject(err);
        }
      }

      if (typeof monaco !== 'undefined' && monaco.editor) {
        init();
        return;
      }

      function doLoad() {
        if (!_monacoLoaderReady) {
          _monacoLoaderReady = true;
          require.config({ paths: { vs: MONACO_CDN } });
        }
        require(['vs/editor/editor.main'], init, function(err) {
          reject(err || new Error('Monaco load failed'));
        });
      }

      if (typeof require === 'function') {
        doLoad();
      } else {
        var loaderScript = document.createElement('script');
        loaderScript.src = MONACO_CDN + '/loader.js';
        loaderScript.onload = doLoad;
        loaderScript.onerror = function() {
          reject(new Error('Monaco loader (vs/loader.js) failed to load from CDN'));
        };
        document.head.appendChild(loaderScript);
      }
    });
  }

  return {
    setEditorDebounceMs: setEditorDebounceMs,
    getEditorDebounceMs: function() { return _editorDebounceMs; },
    registerLanguage:    registerLanguage,
    registerCompletions: registerCompletions,
    setTypeMap:          setTypeMap,
    setSymbols:          setSymbols,
    formatTypeLabel:     formatTypeLabel,
    renderSymbolMd:      renderSymbolMd,
    loadTypeMap:         loadTypeMap,
    invalidateTypeMap:   invalidateTypeMap,
    getMembers:          getMembers,
    lookupType:          lookupType,
    lookupTriggerLocalType: lookupTriggerLocalType,
    createDiagnostics:   createDiagnostics,
    createInfer:         createInfer,
    registerHover:       registerHover,
    registerSignatureHelp: registerSignatureHelp,
    setHoverFallback:    setHoverFallback,
    attachModelOptions:  attachModelOptions,
    createEditor:           createEditor,
    envelopeWrap:         envelopeWrap,
    normalizeEditorManifest: normalizeEditorManifest,
    loadEditorContext:    loadEditorContext,
    inferenceFromContext: inferenceFromContext,
  };
})();
