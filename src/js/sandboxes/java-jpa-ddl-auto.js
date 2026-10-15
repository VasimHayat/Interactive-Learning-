/* JPA & Hibernate §10 — is this schema-generation setting safe here?
   Pure rule table: what each hbm2ddl.auto / schema-generation value does to
   an existing database, crossed with the environment it runs in. */
(function () {
  'use strict';
  var reg = globalThis.LMSandbox.reg, H = globalThis.LMHighlight;

  reg('ddl-auto', function (el) {
    var envSel = el.querySelector('[data-sb-env]');
    var modeSel = el.querySelector('[data-sb-mode]');
    var out = el.querySelector('[data-sb-out]');

    var MODES = {
      'create':      { jpa: 'drop-and-create', does: 'Drops every mapped table at startup, then creates them from the mappings. All existing rows are gone.' },
      'create-drop': { jpa: 'drop-and-create + drop at close', does: 'Like <code>create</code>, and drops the tables again when the factory closes. Nothing survives the process.' },
      'update':      { jpa: '(no JPA equivalent)', does: 'Compares mappings to the live schema and issues <code>ALTER</code>/<code>CREATE</code> for anything missing. It never drops a column or table, never renames, never narrows a type, never adds data, and nobody reviews the DDL it runs.' },
      'validate':    { jpa: '(no JPA equivalent)', does: 'Compares mappings to the live schema and refuses to start on a mismatch. Touches nothing.' },
      'none':        { jpa: 'none', does: 'Does nothing. The schema is someone else’s responsibility, usually a migration tool’s.' }
    };

    var VERDICT = {
      local:   { 'create': 'good', 'create-drop': 'good', 'update': 'warn', 'validate': 'good', 'none': 'good' },
      ci:      { 'create': 'good', 'create-drop': 'good', 'update': 'warn', 'validate': 'good', 'none': 'warn' },
      staging: { 'create': 'never', 'create-drop': 'never', 'update': 'never', 'validate': 'good', 'none': 'good' },
      prod:    { 'create': 'never', 'create-drop': 'never', 'update': 'never', 'validate': 'good', 'none': 'good' }
    };

    var WHY = {
      'local|update':   'Convenient while you are still inventing the model, and the habit that later gets pasted into a production config. Prefer <code>create-drop</code> for throwaway work and a migration file the moment the schema matters.',
      'ci|update':      'Tests pass against a schema that <code>update</code> patched together, then fail in staging where the migration scripts are what actually ran. Run the migrations in CI instead, then <code>validate</code>.',
      'ci|none':        'Fine only if something else creates the schema before the tests — a migration tool on the test container. If nothing does, the first query fails with “table not found”.',
      'staging|create': 'Drops every table on deploy. Staging data is how you rehearse the production migration; destroying it on every start defeats that.',
      'prod|create':    'Drops every table on deploy. There is no undo.',
      'staging|create-drop': 'Same as <code>create</code>, plus the tables vanish on shutdown.',
      'prod|create-drop':    'Same as <code>create</code>, plus the tables vanish on shutdown.',
      'staging|update': 'Unreviewed DDL running with the application’s credentials, at startup, possibly taking table locks mid-deploy, and silently leaving renamed or removed fields as orphan columns. Use versioned migrations and start with <code>validate</code>.',
      'prod|update':    'Unreviewed DDL running with the application’s credentials, at startup, possibly taking table locks mid-deploy, and silently leaving renamed or removed fields as orphan columns. Use versioned migrations and start with <code>validate</code>.',
      'default|good':   'Safe here. ',
      'validate':       'The right production default: the migration tool owns the schema, and Hibernate refuses to start if the two have drifted — at deploy time, not on the first request that hits the missing column.',
      'none':           'Acceptable when the schema is owned elsewhere, but you lose the fail-fast check; <code>validate</code> costs one metadata query at startup and would have caught the drift.'
    };

    function render() {
      var env = envSel.value, mode = modeSel.value;
      var m = MODES[mode];
      var v = VERDICT[env][mode];
      var why = WHY[env + '|' + mode] || (mode === 'validate' ? WHY['validate'] : mode === 'none' ? WHY['none'] : 'Safe here: the data is disposable and a fresh schema per run is exactly what you want.');
      var kind = v === 'good' ? 'good' : v === 'warn' ? 'gotcha' : 'danger';
      var title = v === 'good' ? 'Fine' : v === 'warn' ? 'Risky habit' : 'Never';
      var icon = kind === 'good' ? '<path d="M20 6 9 17l-5-5"/>'
               : kind === 'gotcha' ? '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>'
               : '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6m0-6 6 6"/>';
      var props = 'hibernate.hbm2ddl.auto = ' + mode + '\n' +
                  '# JPA spelling: jakarta.persistence.schema-generation.database.action = ' + m.jpa;
      out.innerHTML =
        H.renderCode(props, { lang: 'plain', noBar: true }) +
        '<p class="text-sm" style="margin:.6rem 0 0"><strong>What it does to an existing database:</strong> ' + m.does + '</p>' +
        '<div class="lm-callout lm-callout--' + kind + '" style="margin-top:.9rem"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' + icon + '</svg>' +
        '<div><div class="lm-callout-title">' + title + ' in ' + H.esc(envSel.options[envSel.selectedIndex].text) + '</div>' + why + '</div></div>';
    }

    envSel.addEventListener('change', render);
    modeSel.addEventListener('change', render);
    render();
  });
})();
