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

  /* ── 2. Completion provider (stale-while-revalidate + optional schema-first) ─ */

  var _completionRegistered = false;

  function registerCompletions(options) {
    if (_completionRegistered) return;
    _completionRegistered = true;

    var urlResolver      = (options && options.urlResolver)       || function() { return '/api/completions'; };
    var getExtraBody     = (options && options.getExtraBody)       || null;
    var getSchemaMembers = (options && options.getSchemaMembers)   || null;
    var triggerChars     = (options && options.triggerChars)       || ['.'];

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

    monaco.languages.registerCompletionItemProvider('outline', {
      triggerCharacters: triggerChars,
      provideCompletionItems: async function(model, position) {
        var offset     = model.getOffsetAt(position);
        var code       = model.getValue();
        var textBefore = code.substring(0, offset);

        if (getSchemaMembers) {
          var members = resolveFromSchema(textBefore, getSchemaMembers());
          if (members) return buildSuggestions(members, model, position);
        }

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
    'String':  '**`String`** — 基础类型 · 字符串',
    'Int':     '**`Int`** — 基础类型 · 32 位整数',
    'Long':    '**`Long`** — 基础类型 · 64 位整数',
    'Float':   '**`Float`** — 基础类型 · 32 位浮点数',
    'Double':  '**`Double`** — 基础类型 · 64 位浮点数',
    'Bool':    '**`Bool`** — 基础类型 · 布尔值',
    'Date':    '**`Date`** — 基础类型 · 日期',
    'Unit':    '**`Unit`** — 基础类型 · 空类型',
    'Nothing': '**`Nothing`** — 基础类型 · 空/null',
  };

  var _sessionTypeMap = {};

  function setTypeMap(data) {
    _sessionTypeMap = data ? Object.assign({}, data) : {};
  }

  function lookupType(word) {
    if (!word) return null;
    return _PRIMITIVES[word] || _sessionTypeMap[word] || null;
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

  /* ── 4. Hover provider (backed by internal type map + dynamic fallback) ── */

  var _hoverRegistered = false;
  var _hoverFallbackFn = null;

  /**
   * Set a dynamic hover fallback function.
   * Called when the type map has no match. Signature:
   *   fn(word, code, offset) → Promise<string|null>
   * The returned string is treated as Markdown hover content.
   */
  function setHoverFallback(fn) {
    _hoverFallbackFn = fn || null;
  }

  function registerHover(options) {
    if (_hoverRegistered) return;
    _hoverRegistered = true;

    var opts         = options || {};
    var urlResolver  = typeof opts.hoverUrl === 'function'
        ? opts.hoverUrl : function() { return opts.hoverUrl || null; };
    var getExtraBody = opts.getExtraBody || null;

    monaco.languages.registerHoverProvider('outline', {
      provideHover: async function(model, position) {
        var wordInfo = model.getWordAtPosition(position);
        if (!wordInfo) return null;

        var hoverRange = {
          startLineNumber: position.lineNumber, startColumn: wordInfo.startColumn,
          endLineNumber:   position.lineNumber, endColumn:   wordInfo.endColumn,
        };

        var markdown = lookupType(wordInfo.word);
        if (markdown) {
          return { range: hoverRange, contents: [{ value: markdown, isTrusted: true }] };
        }

        // Dynamic fallback (set by the active editor via setHoverFallback)
        if (_hoverFallbackFn) {
          try {
            var offset = model.getOffsetAt(position);
            var code   = model.getValue();
            var result = await _hoverFallbackFn(wordInfo.word, code, offset);
            if (result) {
              return { range: hoverRange, contents: [{ value: result, isTrusted: true }] };
            }
          } catch (_) {}
        }

        // Static URL fallback (for schema-hover etc.)
        var url = urlResolver(model);
        if (!url) return null;

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
          if (!data.contents) return null;
          return { range: hoverRange, contents: [{ value: data.contents, isTrusted: true }] };
        } catch (_) { return null; }
      },
    });
  }

  /* ── 5. Diagnostics (debounced validate + setModelMarkers) ──────────────── */

  function createDiagnostics(options) {
    var validateUrl    = options.validateUrl;
    var getRequestBody = options.getRequestBody  || null;
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
        monaco.editor.setModelMarkers(model, markerOwner,
          (data.markers || []).map(function(m) {
            return {
              startLineNumber: m.startLine,
              startColumn:     m.startColumn + 1,
              endLineNumber:   m.endLine,
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
  function createEditor(container, options) {
    var opts = options || {};
    return new Promise(function(resolve, reject) {
      if (!container) { reject(new Error('container is required')); return; }

      function init() {
        try {
          registerLanguage();
          registerHover(opts.hoverOptions || {});
          if (opts.completions) {
            registerCompletions({
              urlResolver:  function() { return opts.completions.url || '/api/completions'; },
              getExtraBody: opts.completions.getExtraBody || null,
              triggerChars: opts.completions.triggerChars || ['.'],
            });
          }

          if (opts.typeMapUrl) loadTypeMap(opts.typeMapUrl);

          var edOpts = Object.assign({}, _defaultEditorOptions, {
            value:       opts.value || '',
            readOnly:    !!opts.readOnly,
            domReadOnly: !!opts.readOnly,
          }, opts.editorOptions || {});

          var editor = monaco.editor.create(container, edOpts);

          var scheduleDiag = null;
          if (opts.diagnostics) {
            scheduleDiag = createDiagnostics({
              validateUrl:    opts.diagnostics.validateUrl,
              getRequestBody: opts.diagnostics.getRequestBody || null,
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

      if (!_monacoLoaderReady) {
        _monacoLoaderReady = true;
        require.config({ paths: { vs: MONACO_CDN } });
      }

      require(['vs/editor/editor.main'], init, function(err) {
        reject(err || new Error('Monaco load failed'));
      });
    });
  }

  return {
    registerLanguage:    registerLanguage,
    registerCompletions: registerCompletions,
    setTypeMap:          setTypeMap,
    loadTypeMap:         loadTypeMap,
    invalidateTypeMap:   invalidateTypeMap,
    lookupType:          lookupType,
    createDiagnostics:   createDiagnostics,
    registerHover:       registerHover,
    setHoverFallback:    setHoverFallback,
    createEditor:        createEditor,
  };
})();
