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

  /* ── 2. Completion provider (single backend path + stale-while-revalidate) ─
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
        var url      = urlResolver(model);
        var extra    = getExtraBody ? getExtraBody(code, offset) : {};
        var body     = Object.assign({ code: code, offset: offset }, extra);
        var cacheKey = url + '::' + textBefore + JSON.stringify(extra);

        var cached = completionCache.get(cacheKey);
        if (cached !== undefined) {
          startFetch(url, body, cacheKey);  // background revalidate
          return buildSuggestions(cached, model, position);
        }
        var items = await startFetch(url, body, cacheKey);
        return buildSuggestions(items || [], model, position);
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
  function _cleanTypeLabel(t) {
    if (!t) return '?';
    var s = String(t).trim();
    // Strip GCP-internal `Lazy{X}` / `Lazy{SymbolIdentifier)` artifacts to `?`.
    // The lazy wrapper is a placeholder during fixed-point inference; if it
    // survives to the hover surface, the resolved type is unknown.
    s = s.replace(/Lazy\{[^{}]*[)}]/g, '?');
    return s || '?';
  }

  function renderSymbolMd(sym) {
    if (!sym || !sym.name) return null;
    var name = String(sym.name);
    var kind = sym.kind ? String(sym.kind) : '';
    var type = _cleanTypeLabel(sym.type);
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
  // Per-model, per-word last-good hover cache. When a hover request comes back
  // empty (typically because the user has a transient inference error
  // somewhere else in the buffer), we fall back to the previous successful
  // result for the same word in the same model — so users keep seeing type
  // info while they're mid-edit instead of an empty popup.
  var _hoverCache = new WeakMap(); // model -> Map<word, {range, content}>
  function _hoverCacheGet(model, word) {
    var m = _hoverCache.get(model); if (!m) return null;
    return m.get(word) || null;
  }
  function _hoverCachePut(model, word, content) {
    var m = _hoverCache.get(model);
    if (!m) { m = new Map(); _hoverCache.set(model, m); }
    m.set(word, content);
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

        var hoverRange = {
          startLineNumber: position.lineNumber, startColumn: wordInfo.startColumn,
          endLineNumber:   position.lineNumber, endColumn:   wordInfo.endColumn,
        };

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
            var offset = model.getOffsetAt(position);
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

        var offset = model.getOffsetAt(position);
        var code   = model.getValue();
        var extra  = getExtraBody ? getExtraBody(code, offset) : {};
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
            var cached = _hoverCacheGet(model, wordInfo.word);
            if (cached) {
              return {
                range: hoverRange,
                contents: [{ value: cached, isTrusted: true }].concat(markerContents),
              };
            }
            return markerContents.length ? { range: hoverRange, contents: markerContents } : null;
          }
          _hoverCachePut(model, wordInfo.word, content);
          return {
            range: hoverRange,
            contents: [{ value: content, isTrusted: true }].concat(markerContents),
          };
        } catch (_) {
          var cachedErr = _hoverCacheGet(model, wordInfo.word);
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
    var debounceMs     = options.debounceMs      != null ? options.debounceMs  : 600;
    var markerOwner    = options.markerOwner     || 'outline-lint';
    var timer = null;

    async function run() {
      var ed = editorRef;
      if (!ed) return;
      var model = ed.getModel();
      if (!model) return;
      var code = ed.getValue().trim();
      if (!code) { monaco.editor.setModelMarkers(model, markerOwner, []); return; }
      try {
        var body = getRequestBody ? getRequestBody(code) : { code: code };
        var resp = await fetch(validateUrl, {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        });
        if (!resp.ok) return;
        var data = await resp.json();
        var userLines = model.getLineCount();
        monaco.editor.setModelMarkers(model, markerOwner,
          (data.markers || [])
            .map(mapMarker)
            .filter(function(m) {
              // Drop markers that don't fall in the user-visible buffer
              // (they belong to the synthetic prelude / wrap envelope and
              // would otherwise be clamped to the last line by Monaco).
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
    }

    return function schedule() {
      clearTimeout(timer);
      timer = setTimeout(run, debounceMs);
    };
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
   *       validate, hover, completions, signature, members, typeMap
   *     },
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
      return body;
    }

    // Number of lines injected by `prelude + wrap.open` before the user's
    // first line. Diagnostics translates marker line/column from wrapped-
    // buffer coordinates back to user coordinates by subtracting this value.
    // Recomputed per call because both prelude and wrap may be dynamic.
    function preludeAndOpenLineCount() {
      var w = wrap('', 0) || { code: '', offset: 0 };
      var openLen = typeof w.offset === 'number' ? w.offset : 0;
      var open = (w.code || '').substring(0, openLen);
      var preludeRaw = resolve(inf.prelude);
      var prelude    = (preludeRaw == null ? '' : String(preludeRaw));
      // Number of newlines == number of lines added before user content.
      var combined = prelude + open;
      var n = 0;
      for (var i = 0; i < combined.length; i++) if (combined.charCodeAt(i) === 10) n++;
      return n;
    }

    var out = Object.assign({}, opts);

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
        // Validate uses code only; reuse the same prelude+wrap pipeline so
        // diagnostic line/column anchors line up with hover/completions/
        // signature responses (all four are wrapping the same way).
        getRequestBody: function(code) {
          var b = baseBody(code, 0);
          var out = { code: b.code };
          if (b.session_id)  out.session_id  = b.session_id;
          if (b.entity_type) out.entity_type = b.entity_type;
          return out;
        },
        // Translate marker line numbers from wrapped-buffer coordinates back
        // to user coordinates by subtracting the prelude+open line count.
        // Markers whose translated line falls outside the user buffer are
        // dropped by createDiagnostics (they belong to the synthetic prelude
        // / wrap open / wrap close and are not user-actionable).
        mapMarker: function(m) {
          var lo = preludeAndOpenLineCount();
          if (lo <= 0) return m;
          var sl = (m.startLine || 1) - lo;
          var el = (m.endLine   || 1) - lo;
          return Object.assign({}, m, { startLine: sl, endLine: el });
        },
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
          registerHover(opts.hoverOptions || {});
          registerCompletions({
            triggerChars: (opts.completions && opts.completions.triggerChars) || ['.', ' '],
          });
          registerSignatureHelp(opts.signatureHelp || {});

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
              editor:         opts.diagnostics.editorRef || editor,
              debounceMs:     opts.diagnostics.debounceMs,
              markerOwner:    opts.diagnostics.markerOwner,
            });
            editor.onDidChangeModelContent(function() { scheduleDiag(); });
            scheduleDiag();
          }

          resolve({ editor: editor, scheduleDiagnostics: scheduleDiag });
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
    registerLanguage:    registerLanguage,
    registerCompletions: registerCompletions,
    setTypeMap:          setTypeMap,
    setSymbols:          setSymbols,
    renderSymbolMd:      renderSymbolMd,
    loadTypeMap:         loadTypeMap,
    invalidateTypeMap:   invalidateTypeMap,
    getMembers:          getMembers,
    lookupType:          lookupType,
    lookupTriggerLocalType: lookupTriggerLocalType,
    createDiagnostics:   createDiagnostics,
    registerHover:       registerHover,
    registerSignatureHelp: registerSignatureHelp,
    setHoverFallback:    setHoverFallback,
    attachModelOptions:  attachModelOptions,
    createEditor:        createEditor,
  };
})();
