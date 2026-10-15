/* Spring Data JPA §1 — which property source wins for spring.jpa.show-sql.
   A rule table of Spring Boot's externalised-configuration precedence; the
   six toggleable sources each set a fixed value so the winner is visible. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('property-precedence', function (el) {
    var boxes = Array.prototype.slice.call(el.querySelectorAll('[data-sb-src]'));
    var profileBox = el.querySelector('[data-sb-profile]');
    var out = el.querySelector('[data-sb-out]');

    /* highest priority first — Spring Boot reference, "Externalized Configuration" */
    var ORDER = [
      { key: 'test',      label: '@TestPropertySource on a test class',                     value: 'false', toggle: true },
      { key: 'cli',       label: 'command-line arguments (--spring.jpa.show-sql=…)',         value: 'true',  toggle: true },
      { key: 'json',      label: 'SPRING_APPLICATION_JSON',                                  value: null },
      { key: 'sysprops',  label: 'Java system properties (-Dspring.jpa.show-sql=…)',         value: null },
      { key: 'env',       label: 'OS environment variable SPRING_JPA_SHOW_SQL',              value: 'false', toggle: true },
      { key: 'profile',   label: 'application-dev.properties (profile-specific file)',       value: 'true',  toggle: true, needsProfile: true },
      { key: 'appprops',  label: 'application.properties',                                   value: 'false', toggle: true },
      { key: 'propsrc',   label: '@PropertySource on a @Configuration class',                value: null },
      { key: 'defaults',  label: 'SpringApplication.setDefaultProperties',                   value: 'true',  toggle: true }
    ];

    function render() {
      var on = {};
      boxes.forEach(function (b) { on[b.dataset.sbSrc] = b.checked; });
      var profileActive = profileBox.checked;
      var winner = null;
      var skippedProfile = false;
      ORDER.forEach(function (s) {
        if (winner || !s.toggle || !on[s.key]) return;
        if (s.needsProfile && !profileActive) { skippedProfile = true; return; }
        winner = s;
      });

      var rows = ORDER.map(function (s, i) {
        var set = s.toggle && on[s.key];
        var inactive = s.needsProfile && !profileActive;
        var isWinner = winner && winner.key === s.key;
        var cls = isWinner ? ' is-hi' : (set ? '' : ' is-empty');
        var txt = H.esc(s.label) +
          (s.toggle ? (set ? (inactive ? ' — set, but profile dev is not active' : ' — sets <code>' + s.value + '</code>') : ' — not set') : ' — not used here');
        return '<div class="lm-cell' + cls + '" style="width:100%"><span class="lm-cell-idx">' + (i + 1) + '</span>' + txt + (isWinner ? ' <strong>← wins</strong>' : '') + '</div>';
      }).join('');

      var verdict = winner
        ? 'Effective value: <code>spring.jpa.show-sql=' + winner.value + '</code>, from <strong>' + H.esc(winner.label) + '</strong>. Every source below it in the list also sets the key and is ignored.'
        : 'The key is not set anywhere, so Spring Boot’s own default applies: <code>spring.jpa.show-sql=false</code>.';
      var note = '';
      if (skippedProfile && (!winner || winner.key !== 'profile')) {
        note = ' A profile-specific file only participates when its profile is active (<code>spring.profiles.active=dev</code>); tick the profile to see it move up.';
      }
      if (winner && winner.key === 'defaults') {
        note = ' This is why the programs in this module use default properties: they lose to anything a reader sets.';
      }

      out.innerHTML =
        '<div class="lm-mem" style="display:grid;gap:.3rem;margin-bottom:.9rem">' + rows + '</div>' +
        '<div class="lm-callout lm-callout--note"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-5m0-3h.01"/></svg>' +
        '<div><div class="lm-callout-title">Highest priority first</div>' + verdict + note + '</div></div>';
    }

    boxes.forEach(function (b) { b.addEventListener('change', render); });
    profileBox.addEventListener('change', render);
    render();
  });
})();
