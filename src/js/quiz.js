/* Assessment widgets: MCQ / multi-select / predict-output / fix-the-bug,
   code ordering, drag-and-match, flashcards, and the scored final quiz.
   Everything is declarative — the markup carries the answer key. */
(function () {
  'use strict';

  var store = globalThis.LMStore;
  var quizState = (store && store.get('quiz')) || {};
  function persist() { if (store) store.set('quiz', quizState); }

  function announce(el, msg) {
    var live = el.querySelector('[data-live]');
    if (live) live.textContent = msg;
  }

  // ── MCQ / multi-select ────────────────────────────────────────────────────
  function grade(q, opts) {
    var key = (q.dataset.answer || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
    var picked = [];
    q.querySelectorAll('.lm-opt').forEach(function (o) {
      var input = o.querySelector('input');
      if (input && input.checked) picked.push(o.dataset.opt);
    });
    if (!picked.length && !(opts && opts.force)) {
      announce(q, 'Choose an answer first.');
      return null;
    }

    q.querySelectorAll('.lm-opt').forEach(function (o) {
      var id = o.dataset.opt;
      var isKey = key.indexOf(id) > -1;
      var isPicked = picked.indexOf(id) > -1;
      o.classList.remove('is-correct', 'is-wrong', 'is-missed');
      if (isPicked && isKey) o.classList.add('is-correct');
      else if (isPicked && !isKey) o.classList.add('is-wrong');
      else if (!isPicked && isKey) o.classList.add('is-missed');
      var input = o.querySelector('input');
      if (input) input.disabled = true;
    });

    var right = key.length === picked.length && key.every(function (k) { return picked.indexOf(k) > -1; });
    var ex = q.querySelector('[data-explain]');
    if (ex) ex.hidden = false;
    q.querySelectorAll('.lm-opt-why').forEach(function (w) { w.hidden = false; });
    var btn = q.querySelector('[data-check]');
    if (btn) btn.disabled = true;

    q.dataset.result = right ? 'correct' : 'incorrect';
    announce(q, right ? 'Correct.' : 'Incorrect. The explanation is now shown.');

    if (q.id) { quizState[q.id] = { picked: picked, right: right }; persist(); }
    return right;
  }

  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-check]');
    if (!btn) return;
    var q = btn.closest('[data-q]');
    if (q) grade(q);
  });

  // restore previously answered questions
  document.querySelectorAll('[data-q]').forEach(function (q) {
    var saved = q.id && quizState[q.id];
    if (!saved) return;
    saved.picked.forEach(function (id) {
      var o = q.querySelector('.lm-opt[data-opt="' + id + '"] input');
      if (o) o.checked = true;
    });
    grade(q, { force: true });
  });

  // ── scored quiz ───────────────────────────────────────────────────────────
  document.querySelectorAll('[data-quiz]').forEach(function (quiz) {
    var submit = quiz.querySelector('[data-quiz-submit]');
    var review = quiz.querySelector('[data-quiz-review]');
    var out = quiz.querySelector('[data-quiz-score]');
    if (!submit) return;

    submit.addEventListener('click', function () {
      var qs = Array.prototype.slice.call(quiz.querySelectorAll('[data-q]'));
      var unanswered = qs.filter(function (q) {
        return !q.querySelector('.lm-opt input:checked');
      });
      if (unanswered.length) {
        out.hidden = false;
        out.innerHTML = '<strong>' + unanswered.length + ' question' +
          (unanswered.length === 1 ? '' : 's') + ' still unanswered.</strong> ' +
          '<button type="button" class="lm-btn" data-goto-first>Jump to the first</button>';
        out.querySelector('[data-goto-first]').addEventListener('click', function () {
          unanswered[0].scrollIntoView({ behavior: 'smooth', block: 'center' });
          var i = unanswered[0].querySelector('input');
          if (i) i.focus();
        });
        return;
      }

      var right = 0;
      qs.forEach(function (q) { if (grade(q, { force: true })) right++; });
      var pct = Math.round((right / qs.length) * 100);
      var nextMod = document.documentElement.getAttribute('data-next');
      var verdict = pct >= 85 ? (nextMod ? 'Solid — you can move on to ' + nextMod + '.' : 'Solid — you have this module.')
                  : pct >= 65 ? 'Nearly there. Re-read the sections you missed.'
                  : 'Worth another pass through the module before moving on.';
      out.hidden = false;
      out.innerHTML = '<span class="lm-score-num">' + right + ' / ' + qs.length + '</span>' +
        '<span><strong>' + pct + '%</strong> — ' + verdict + '</span>';
      submit.disabled = true;
      if (review) review.hidden = right === qs.length;
    });

    if (review) {
      review.addEventListener('click', function () {
        var wrong = quiz.querySelectorAll('[data-q][data-result="incorrect"]');
        if (!wrong.length) return;
        var i = 0;
        wrong[0].scrollIntoView({ behavior: 'smooth', block: 'center' });
        review.textContent = 'Next wrong answer (1 of ' + wrong.length + ')';
        review.onclick = function () {
          i = (i + 1) % wrong.length;
          wrong[i].scrollIntoView({ behavior: 'smooth', block: 'center' });
          review.textContent = 'Next wrong answer (' + (i + 1) + ' of ' + wrong.length + ')';
        };
      });
    }
  });

  // ── code ordering ─────────────────────────────────────────────────────────
  document.querySelectorAll('[data-order]').forEach(function (wrap) {
    var list = wrap.querySelector('.lm-order');
    var solution = (wrap.dataset.solution || '').split(',');

    function move(item, dir) {
      var items = Array.prototype.slice.call(list.children);
      var i = items.indexOf(item), j = i + dir;
      if (j < 0 || j >= items.length) return;
      if (dir < 0) list.insertBefore(item, items[j]);
      else list.insertBefore(items[j], item);
      renumber();
      item.querySelector(dir < 0 ? '[data-up]' : '[data-down]').focus();
    }
    function renumber() {
      Array.prototype.slice.call(list.children).forEach(function (li, i) {
        li.querySelector('.lm-order-pos').textContent = i + 1;
        li.classList.remove('is-correct', 'is-wrong');
        li.querySelector('[data-up]').disabled = i === 0;
        li.querySelector('[data-down]').disabled = i === list.children.length - 1;
      });
    }

    list.addEventListener('click', function (e) {
      var up = e.target.closest('[data-up]'), down = e.target.closest('[data-down]');
      if (up) move(up.closest('.lm-order-item'), -1);
      if (down) move(down.closest('.lm-order-item'), 1);
    });

    var check = wrap.querySelector('[data-order-check]');
    if (check) check.addEventListener('click', function () {
      var items = Array.prototype.slice.call(list.children);
      var ok = 0;
      items.forEach(function (li, i) {
        var right = li.dataset.id === solution[i];
        li.classList.toggle('is-correct', right);
        li.classList.toggle('is-wrong', !right);
        if (right) ok++;
      });
      var ex = wrap.querySelector('[data-explain]');
      if (ex && ok === items.length) ex.hidden = false;
      announce(wrap, ok === items.length
        ? 'Correct order.'
        : ok + ' of ' + items.length + ' lines are in the right position.');
    });

    var reset = wrap.querySelector('[data-order-reset]');
    if (reset) reset.addEventListener('click', function () {
      var items = Array.prototype.slice.call(list.children);
      items.sort(function (a, b) { return Number(a.dataset.shuffle) - Number(b.dataset.shuffle); });
      items.forEach(function (li) { list.appendChild(li); });
      renumber();
      var ex = wrap.querySelector('[data-explain]');
      if (ex) ex.hidden = true;
      announce(wrap, 'Reset to the starting order.');
    });

    renumber();
  });

  // ── drag & match (mouse + keyboard) ──────────────────────────────────────
  document.querySelectorAll('[data-match]').forEach(function (wrap) {
    var selected = null;

    function place(chip, slot) {
      if (!chip || !slot) return;
      var prev = slot.querySelector('[data-chip]');
      if (prev) { prev.classList.remove('is-placed'); wrap.querySelector('.lm-match-pool').appendChild(prev); }
      slot.querySelector('.lm-slot-answer').appendChild(chip);
      chip.classList.add('is-placed');
      chip.classList.remove('is-selected');
      slot.classList.add('is-filled');
      selected = null;
      wrap.querySelectorAll('.lm-chip').forEach(function (c) { c.classList.remove('is-selected'); });
      announce(wrap, chip.textContent.trim() + ' placed in ' + slot.dataset.label + '.');
    }

    wrap.querySelectorAll('.lm-chip').forEach(function (chip) {
      chip.setAttribute('draggable', 'true');
      chip.addEventListener('dragstart', function (e) {
        e.dataTransfer.setData('text/plain', chip.dataset.chip);
        e.dataTransfer.effectAllowed = 'move';
      });
      chip.addEventListener('click', function () {
        if (chip.classList.contains('is-placed')) return;
        var was = chip.classList.contains('is-selected');
        wrap.querySelectorAll('.lm-chip').forEach(function (c) { c.classList.remove('is-selected'); });
        selected = was ? null : chip;
        chip.classList.toggle('is-selected', !was);
        announce(wrap, was ? 'Selection cleared.' : chip.textContent.trim() + ' selected. Now choose a target.');
      });
    });

    wrap.querySelectorAll('.lm-slot').forEach(function (slot) {
      slot.addEventListener('dragover', function (e) { e.preventDefault(); slot.classList.add('is-over'); });
      slot.addEventListener('dragleave', function () { slot.classList.remove('is-over'); });
      slot.addEventListener('drop', function (e) {
        e.preventDefault();
        slot.classList.remove('is-over');
        var id = e.dataTransfer.getData('text/plain');
        place(wrap.querySelector('.lm-chip[data-chip="' + id + '"]'), slot);
      });
      slot.addEventListener('click', function () {
        if (selected) place(selected, slot);
        else {
          var inside = slot.querySelector('[data-chip]');
          if (inside) {
            inside.classList.remove('is-placed');
            wrap.querySelector('.lm-match-pool').appendChild(inside);
            slot.classList.remove('is-filled', 'is-correct', 'is-wrong');
            announce(wrap, 'Returned ' + inside.textContent.trim() + ' to the pool.');
          }
        }
      });
    });

    var check = wrap.querySelector('[data-match-check]');
    if (check) check.addEventListener('click', function () {
      var slots = Array.prototype.slice.call(wrap.querySelectorAll('.lm-slot'));
      var ok = 0;
      slots.forEach(function (s) {
        var chip = s.querySelector('[data-chip]');
        var right = chip && chip.dataset.chip === s.dataset.slot;
        s.classList.toggle('is-correct', !!right);
        s.classList.toggle('is-wrong', !right);
        if (right) ok++;
      });
      var ex = wrap.querySelector('[data-explain]');
      if (ex && ok === slots.length) ex.hidden = false;
      announce(wrap, ok + ' of ' + slots.length + ' pairs correct.');
    });
  });

  // ── flashcards ────────────────────────────────────────────────────────────
  document.querySelectorAll('[data-flash]').forEach(function (wrap) {
    var cards = Array.prototype.slice.call(wrap.querySelectorAll('.lm-flash-item'));
    if (!cards.length) return;
    var order = cards.map(function (_, i) { return i; });
    var pos = 0;
    var known = (store && store.get('flash')) || {};

    var card = wrap.querySelector('.lm-flash-card');
    var front = wrap.querySelector('[data-flash-front]');
    var back = wrap.querySelector('[data-flash-back]');
    var count = wrap.querySelector('[data-flash-count]');

    function paint() {
      var el = cards[order[pos]];
      card.classList.remove('is-flipped');
      card.setAttribute('aria-pressed', 'false');
      front.innerHTML = el.querySelector('[data-term]').innerHTML;
      back.innerHTML = el.querySelector('[data-def]').innerHTML;
      var id = el.dataset.id;
      var n = Object.keys(known).filter(function (k) { return known[k]; }).length;
      count.textContent = 'Card ' + (pos + 1) + ' of ' + order.length +
        ' · ' + n + ' marked known' + (known[id] ? ' · this one is known' : '');
    }
    function flip() {
      var on = card.classList.toggle('is-flipped');
      card.setAttribute('aria-pressed', String(on));
    }

    card.addEventListener('click', flip);
    card.addEventListener('keydown', function (e) {
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); flip(); }
    });
    wrap.querySelector('[data-flash-next]').addEventListener('click', function () {
      pos = (pos + 1) % order.length; paint();
    });
    wrap.querySelector('[data-flash-prev]').addEventListener('click', function () {
      pos = (pos - 1 + order.length) % order.length; paint();
    });
    wrap.querySelector('[data-flash-shuffle]').addEventListener('click', function () {
      // Fisher–Yates
      for (var i = order.length - 1; i > 0; i--) {
        var j = Math.floor(Math.random() * (i + 1));
        var t = order[i]; order[i] = order[j]; order[j] = t;
      }
      pos = 0; paint();
      announce(wrap, 'Deck shuffled.');
    });
    wrap.querySelector('[data-flash-known]').addEventListener('click', function () {
      var id = cards[order[pos]].dataset.id;
      known[id] = !known[id];
      if (store) store.set('flash', known);
      paint();
      announce(wrap, known[id] ? 'Marked known.' : 'Unmarked.');
    });

    paint();
  });
})();
