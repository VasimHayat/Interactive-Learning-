/* Shell behaviour: theme, TOC scrollspy, progress, notes, copy, tabs, shortcuts.
   Storage is namespaced per module so topic files never collide in localStorage. */
(function () {
  'use strict';

  var SLUG = document.documentElement.dataset.topic || 'module';
  var KEY = 'lm:' + SLUG + ':v1';

  // ── persistence ───────────────────────────────────────────────────────────
  var state = { theme: null, done: {}, notes: '', quiz: {}, flash: {} };
  try {
    var raw = localStorage.getItem(KEY);
    if (raw) state = Object.assign(state, JSON.parse(raw));
  } catch (e) { /* private mode or corrupt payload — fall back to defaults */ }

  var saveTimer = null;
  function save() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () {
      try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {}
    }, 200);
  }
  globalThis.LMStore = {
    get: function (k) { return state[k]; },
    set: function (k, v) { state[k] = v; save(); },
    key: KEY
  };

  // ── theme ─────────────────────────────────────────────────────────────────
  function applyTheme(t) {
    if (t) document.documentElement.setAttribute('data-theme', t);
    else document.documentElement.removeAttribute('data-theme');
    var btn = document.querySelector('[data-theme-toggle]');
    if (!btn) return;
    var mql = window.matchMedia('(prefers-color-scheme: dark)');
    var effective = t || (mql.matches ? 'dark' : 'light');
    btn.setAttribute('aria-label', 'Switch to ' + (effective === 'dark' ? 'light' : 'dark') + ' theme');
    var target = effective === 'dark' ? 'light' : 'dark';
    btn.querySelectorAll('[data-theme-icon]').forEach(function (i) {
      // SVGElement does not implement the `hidden` IDL property — assigning to
      // it creates a JS expando and leaves the attribute (and [hidden]{display:none})
      // in place. The attribute methods are the only thing that works here.
      if (i.getAttribute('data-theme-icon') === target) i.removeAttribute('hidden');
      else i.setAttribute('hidden', '');
    });
  }
  applyTheme(state.theme);

  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-theme-toggle]');
    if (!t) return;
    var cur = document.documentElement.getAttribute('data-theme');
    if (!cur) cur = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    state.theme = cur === 'dark' ? 'light' : 'dark';
    applyTheme(state.theme);
    save();
  });

  // ── TOC scrollspy ─────────────────────────────────────────────────────────
  var sections = Array.prototype.slice.call(document.querySelectorAll('.lm-section[id]'));
  var tocLinks = {};
  document.querySelectorAll('.lm-toc a[href^="#"]').forEach(function (a) {
    tocLinks[a.getAttribute('href').slice(1)] = a;
  });

  if (sections.length && 'IntersectionObserver' in window) {
    var visible = new Set();
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) visible.add(en.target.id); else visible.delete(en.target.id);
      });
      var first = sections.find(function (s) { return visible.has(s.id); });
      Object.keys(tocLinks).forEach(function (id) {
        tocLinks[id].setAttribute('aria-current', first && id === first.id ? 'true' : 'false');
      });
    }, { rootMargin: '-72px 0px -65% 0px', threshold: 0 });
    sections.forEach(function (s) { io.observe(s); });
  }

  // mobile TOC drawer
  document.addEventListener('click', function (e) {
    var t = e.target.closest('.lm-toc-toggle');
    if (!t) return;
    var sb = document.querySelector('.lm-sidebar');
    var open = sb.classList.toggle('is-open');
    t.setAttribute('aria-expanded', String(open));
  });
  document.querySelectorAll('.lm-toc a').forEach(function (a) {
    a.addEventListener('click', function () {
      var sb = document.querySelector('.lm-sidebar');
      if (sb) sb.classList.remove('is-open');
      var t = document.querySelector('.lm-toc-toggle');
      if (t) t.setAttribute('aria-expanded', 'false');
    });
  });

  // ── section completion + progress ─────────────────────────────────────────
  var checks = Array.prototype.slice.call(document.querySelectorAll('[data-done-for]'));

  function paintProgress() {
    var total = checks.length;
    var n = checks.filter(function (c) { return state.done[c.dataset.doneFor]; }).length;
    var pct = total ? Math.round((n / total) * 100) : 0;
    document.querySelectorAll('[data-progress-fill]').forEach(function (el) { el.style.width = pct + '%'; });
    document.querySelectorAll('[data-progress-text]').forEach(function (el) {
      el.textContent = n + ' of ' + total + ' sections complete (' + pct + '%)';
    });
    var bar = document.querySelector('[data-progress-bar]');
    if (bar) {
      bar.setAttribute('aria-valuenow', String(pct));
      bar.setAttribute('aria-valuetext', pct + ' percent complete');
    }
    Object.keys(tocLinks).forEach(function (id) {
      tocLinks[id].classList.toggle('is-done', !!state.done[id]);
    });
  }

  checks.forEach(function (c) {
    c.checked = !!state.done[c.dataset.doneFor];
    c.addEventListener('change', function () {
      state.done[c.dataset.doneFor] = c.checked;
      if (!c.checked) delete state.done[c.dataset.doneFor];
      save();
      paintProgress();
    });
  });
  paintProgress();

  // ── notes ─────────────────────────────────────────────────────────────────
  var notes = document.querySelector('[data-notes]');
  if (notes) {
    notes.value = state.notes || '';
    notes.addEventListener('input', function () { state.notes = notes.value; save(); });
  }

  // ── reset ─────────────────────────────────────────────────────────────────
  document.addEventListener('click', function (e) {
    if (!e.target.closest('[data-reset]')) return;
    var dlg = document.getElementById('reset-dialog');
    if (dlg && dlg.showModal) dlg.showModal();
  });
  document.addEventListener('click', function (e) {
    if (!e.target.closest('[data-reset-confirm]')) return;
    try { localStorage.removeItem(KEY); } catch (err) {}
    location.reload();
  });
  document.addEventListener('click', function (e) {
    var c = e.target.closest('[data-dialog-close]');
    if (c) { var d = c.closest('dialog'); if (d) d.close(); }
  });

  // ── copy buttons ──────────────────────────────────────────────────────────
  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-copy]');
    if (!btn) return;
    var block = btn.closest('.lm-code');
    var code = block ? block.querySelector('code') : null;
    if (!code) return;
    // line numbers are a CSS counter, so a line's textContent is clean source
    var text = Array.prototype.map.call(code.querySelectorAll('.cl'), function (s) {
      return s.textContent;
    }).join('\n');
    var label = btn.querySelector('[data-copy-label]');
    function flash(msg) {
      if (!label) return;
      var old = label.textContent;
      label.textContent = msg;
      btn.classList.add('is-ok');
      setTimeout(function () { label.textContent = old; btn.classList.remove('is-ok'); }, 1400);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { flash('Copied'); }, function () { fallback(text, flash); });
    } else { fallback(text, flash); }
  });

  function fallback(text, flash) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.cssText = 'position:fixed;top:-1000px';
    document.body.appendChild(ta);
    ta.select();
    var ok = false;
    try { ok = document.execCommand('copy'); } catch (e) {}
    document.body.removeChild(ta);
    flash(ok ? 'Copied' : 'Press Ctrl+C');
  }

  // ── tabs ──────────────────────────────────────────────────────────────────
  document.querySelectorAll('[role="tablist"]').forEach(function (list) {
    var tabs = Array.prototype.slice.call(list.querySelectorAll('[role="tab"]'));
    function select(tab) {
      tabs.forEach(function (t) {
        var on = t === tab;
        t.setAttribute('aria-selected', String(on));
        t.tabIndex = on ? 0 : -1;
        var panel = document.getElementById(t.getAttribute('aria-controls'));
        if (panel) panel.hidden = !on;
      });
    }
    tabs.forEach(function (tab) {
      tab.addEventListener('click', function () { select(tab); });
      tab.addEventListener('keydown', function (e) {
        var i = tabs.indexOf(tab), next = null;
        if (e.key === 'ArrowRight') next = tabs[(i + 1) % tabs.length];
        else if (e.key === 'ArrowLeft') next = tabs[(i - 1 + tabs.length) % tabs.length];
        else if (e.key === 'Home') next = tabs[0];
        else if (e.key === 'End') next = tabs[tabs.length - 1];
        if (next) { e.preventDefault(); select(next); next.focus(); }
      });
    });
  });

  // ── keyboard shortcuts ────────────────────────────────────────────────────
  function typing(el) {
    return el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable);
  }
  function currentIndex() {
    var y = window.scrollY + 90, idx = 0;
    sections.forEach(function (s, i) { if (s.offsetTop <= y) idx = i; });
    return idx;
  }
  function goto(i) {
    var s = sections[Math.max(0, Math.min(sections.length - 1, i))];
    if (!s) return;
    s.scrollIntoView({ behavior: 'smooth', block: 'start' });
    var h = s.querySelector('h2');
    if (h) { h.setAttribute('tabindex', '-1'); h.focus({ preventScroll: true }); }
  }

  document.addEventListener('keydown', function (e) {
    if (e.metaKey || e.ctrlKey || e.altKey || typing(document.activeElement)) return;
    switch (e.key) {
      case 'j': e.preventDefault(); goto(currentIndex() + 1); break;
      case 'k': e.preventDefault(); goto(currentIndex() - 1); break;
      case 't': e.preventDefault();
        var b = document.querySelector('[data-theme-toggle]'); if (b) b.click(); break;
      case '/': e.preventDefault();
        var f = document.querySelector('[data-toc-filter]'); if (f) f.focus(); break;
      case '?': e.preventDefault();
        var d = document.getElementById('help-dialog'); if (d && d.showModal && !d.open) d.showModal(); break;
    }
  });

  // ── TOC filter ────────────────────────────────────────────────────────────
  var filter = document.querySelector('[data-toc-filter]');
  if (filter) {
    filter.addEventListener('input', function () {
      var q = filter.value.trim().toLowerCase();
      document.querySelectorAll('.lm-toc li').forEach(function (li) {
        li.hidden = q !== '' && li.textContent.toLowerCase().indexOf(q) === -1;
      });
    });
    filter.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { filter.value = ''; filter.dispatchEvent(new Event('input')); filter.blur(); }
    });
  }
})();
