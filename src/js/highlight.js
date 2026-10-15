/* Minimal tokenizer for java | sql | bash | json | xml.
   Written as an IIFE onto globalThis so tools/build.mjs can evaluate the exact
   same source it ships to the browser — one implementation, no drift. */
(function () {
  'use strict';

  var KW_JAVA = 'abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|var|record|yield|sealed|permits|true|false|null';

  var KW_SQL = 'ADD|ALL|ALTER|AND|ANY|AS|ASC|BEGIN|BETWEEN|BY|CASCADE|CASE|CAST|CHECK|COALESCE|COLUMN|COMMIT|CONSTRAINT|CREATE|CROSS|DEFAULT|DELETE|DESC|DISTINCT|DROP|ELSE|END|EXCEPT|EXISTS|EXPLAIN|FALSE|FETCH|FILTER|FOREIGN|FROM|FULL|GROUP|HAVING|IN|INDEX|INNER|INSERT|INTERSECT|INTO|IS|JOIN|KEY|LEFT|LIKE|LIMIT|NOT|NULL|NULLS|OFFSET|ON|OR|ORDER|OUTER|OVER|PARTITION|PRIMARY|REFERENCES|RETURNING|RIGHT|ROLLBACK|ROW|SELECT|SET|TABLE|THEN|TRUE|UNION|UNIQUE|UPDATE|USING|VALUES|VIEW|WHEN|WHERE|WINDOW|WITH';

  var KW_BASH = 'if|then|else|elif|fi|for|while|do|done|case|esac|function|return|export|local|source|echo|cd|exit|set|unset';

  var RE = {
    java: new RegExp(
      '(?<c>//[^\\n]*|/\\*[\\s\\S]*?\\*/)' +
      '|(?<s>"""[\\s\\S]*?"""|"(?:\\\\.|[^"\\\\])*"|\'(?:\\\\.|[^\'\\\\])*\')' +
      '|(?<a>@[A-Za-z_]\\w*)' +
      '|(?<n>\\b\\d[\\d_]*(?:\\.[\\d_]+)?(?:[eE][+-]?\\d+)?[dDfFlL]?\\b|\\b0[xXbB][0-9a-fA-F_]+[lL]?\\b)' +
      '|(?<k>\\b(?:' + KW_JAVA + ')\\b)' +
      '|(?<t>\\b[A-Z][A-Za-z0-9_]*\\b)' +
      '|(?<p>[{}()\\[\\];,.<>+\\-*/%=!&|^~?:]+)', 'g'),
    sql: new RegExp(
      '(?<c>--[^\\n]*|/\\*[\\s\\S]*?\\*/)' +
      '|(?<s>\'(?:\'\'|[^\'])*\'|\\$\\$[\\s\\S]*?\\$\\$)' +
      '|(?<n>\\b\\d+(?:\\.\\d+)?\\b)' +
      '|(?<k>\\b(?:' + KW_SQL + ')\\b)' +
      '|(?<t>\\b(?:bigint|boolean|bytea|char|date|decimal|double|float|int|integer|interval|json|jsonb|numeric|real|serial|smallint|text|time|timestamp|timestamptz|uuid|varchar)\\b)' +
      '|(?<p>[(),;.*=<>+\\-/%|]+)', 'gi'),
    bash: new RegExp(
      '(?<c>#[^\\n]*)' +
      '|(?<s>"(?:\\\\.|[^"\\\\])*"|\'[^\']*\')' +
      '|(?<a>\\$\\{?[A-Za-z_]\\w*\\}?)' +
      '|(?<n>\\b\\d+\\b)' +
      '|(?<k>\\b(?:' + KW_BASH + ')\\b)' +
      '|(?<p>[|&;<>()$]+)', 'g'),
    json: new RegExp(
      '(?<s>"(?:\\\\.|[^"\\\\])*")' +
      '|(?<n>-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)' +
      '|(?<k>\\b(?:true|false|null)\\b)' +
      '|(?<p>[{}\\[\\],:]+)', 'g'),
    xml: new RegExp(
      '(?<c><!--[\\s\\S]*?-->)' +
      '|(?<s>"(?:[^"]*)"|\'(?:[^\']*)\')' +
      '|(?<t></?[A-Za-z_][\\w.:-]*)' +
      '|(?<p>[<>/=]+)', 'g')
  };

  var CLS = { c: 'c', s: 's', a: 'a', n: 'n', k: 'k', t: 't', p: 'p' };

  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  /* -> [{ text, cls }] with cls null for plain runs */
  function tokenize(code, lang) {
    var re = RE[lang];
    if (!re) return [{ text: code, cls: null }];
    re.lastIndex = 0;
    var out = [], last = 0, m;
    while ((m = re.exec(code)) !== null) {
      if (m[0] === '') { re.lastIndex++; continue; }
      if (m.index > last) out.push({ text: code.slice(last, m.index), cls: null });
      var g = m.groups, key = null;
      for (var k in g) { if (g[k] !== undefined) { key = k; break; } }
      out.push({ text: m[0], cls: key ? CLS[key] : null });
      last = m.index + m[0].length;
    }
    if (last < code.length) out.push({ text: code.slice(last), cls: null });
    return out;
  }

  /* Split the token stream at newlines so a span never straddles a line —
     block comments and text blocks reopen their span on the next line. */
  function toLines(tokens) {
    var lines = [[]];
    tokens.forEach(function (tok) {
      var parts = tok.text.split('\n');
      parts.forEach(function (part, i) {
        if (i > 0) lines.push([]);
        if (part !== '') lines[lines.length - 1].push({ text: part, cls: tok.cls });
      });
    });
    return lines;
  }

  /* Class names here are deliberately terse — they repeat once per line and
     once per token across hundreds of code blocks, and the long form cost
     ~70 kB of the shipped file. See _components.scss for what they map to. */
  function lineHtml(tokens) {
    return tokens.map(function (t) {
      var e = esc(t.text);
      return t.cls ? '<span class="' + t.cls + '">' + e + '</span>' : e;
    }).join('');
  }

  /* opts: { lang, file, hi:[2,3], bad:[7], start:1, noBar:false, wrapClass:'' } */
  function renderCode(code, opts) {
    opts = opts || {};
    var lang = opts.lang || 'plain';
    var src = String(code).replace(/\r\n/g, '\n').replace(/\s+$/, '');
    var lines = toLines(tokenize(src, lang));
    var start = opts.start || 1;
    var hi = opts.hi || [], bad = opts.bad || [], active = opts.active || [];

    /* Line numbers come from a CSS counter rather than a DOM node — same
       rendering, no per-line <span> holding a digit, and copy-to-clipboard
       gets clean source because the number is generated content. */
    var body = lines.map(function (toks, i) {
      var n = start + i, cls = 'cl';
      if (hi.indexOf(n) > -1) cls += ' hi';
      if (bad.indexOf(n) > -1) cls += ' bad';
      if (active.indexOf(n) > -1) cls += ' act';
      return '<span class="' + cls + '">' + (lineHtml(toks) || ' ') + '</span>';
    }).join('\n');

    if (opts.linesOnly) return body;

    var bar = '';
    if (!opts.noBar) {
      bar = '<div class="lm-code-bar">' +
        '<span class="lm-code-lang">' + esc(lang) + '</span>' +
        (opts.file ? '<span class="lm-code-file">' + esc(opts.file) + '</span>' : '') +
        '<span class="grow"></span>' +
        '<button type="button" class="lm-iconbtn" data-copy aria-label="Copy code to clipboard">' +
          '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
          '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>' +
          '<span data-copy-label>Copy</span>' +
        '</button></div>';
    }

    return '<div class="lm-code ' + (opts.wrapClass || '') + '">' + bar +
           '<div class="lm-code-body"><pre><code>' + body + '</code></pre></div></div>';
  }

  globalThis.LMHighlight = {
    esc: esc,
    tokenize: tokenize,
    toLines: toLines,
    lineHtml: lineHtml,
    renderCode: renderCode
  };
})();
