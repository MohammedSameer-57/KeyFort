(function () {
  'use strict';

  var API = '/api/demo';
  var STEP_NAMES = ['Link created', 'Signed in once', 'Copied link refused', 'Vault key renewed'];
  var KNOWN = { idle: 'idle', link_ready: 'link_ready', ready: 'link_ready', signed_in: 'signed_in', ended: 'ended', renewed: 'renewed' };
  var state = null;
  var busy = false;
  var logsKey = '';
  var copyTimer = null;
  var debugOn = new URLSearchParams(location.search).has('debug');

  function el(id) { return document.getElementById(id); }
  function text(id, value) { var n = el(id); if (n && n.textContent !== value) { n.textContent = value; } }
  function badge(id, value, kind) { text(id, value); el(id).className = 'badge badge-' + kind; }
  function button(id, label, enabled, kind) {
    var b = el(id);
    text(id, label);
    b.disabled = !enabled;
    b.className = 'btn btn-' + kind;
  }
  function fmt(sec) {
    var s = Math.max(0, Math.floor(Number(sec) || 0));
    var m = Math.floor(s / 60);
    return (m < 10 ? '0' : '') + m + ':' + (s % 60 < 10 ? '0' : '') + (s % 60);
  }
  function note(msg) { var n = el('notice'); n.textContent = msg; n.hidden = false; }
  function clearNote() { var n = el('notice'); n.hidden = true; n.textContent = ''; }

  function call(method, path, body) {
    var opts = { method: method, headers: {} };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    return fetch(path, opts).then(function (r) {
      if (r.status === 404) { throw new Error('off'); }
      if (!r.ok) { throw new Error('http ' + r.status); }
      return r.json();
    });
  }
  document.body.classList.remove('booting');
  function onError(e) {
    var m = e && e.message ? e.message : '';
    if (m === 'off') { note('Demo mode is off. Start the app with RUN_DEMO so the demo profile is active.'); }
    else if (m.indexOf('http') === 0) { note('The demo server answered with an error (' + m.slice(5) + ').'); }
    else { note('Cannot reach the demo server. Is RUN_DEMO still running?'); }
  }

  function act(method, path, body) {
    busy = true;
    return call(method, API + path, body)
      .then(function (s) { render(s); clearNote(); })
      .catch(onError)
      .then(function () { busy = false; });
  }

  function poll() {
    if (busy) { return; }
    call('GET', API + '/state').then(function (s) { render(s); clearNote(); }).catch(onError);
  }

  function stage(s) {
    var p = String(s.phase || '').toLowerCase().replace(/[^a-z]+/g, '_');
    if (KNOWN[p]) { return KNOWN[p]; }
    if (s.sessionActive) { return 'signed_in'; }
    if (p.indexOf('renew') >= 0 && !s.rotationPending) { return 'renewed'; }
    if (s.rotationPending) { return 'ended'; }
    if (s.rawLinkUrl) { return 'link_ready'; }
    return 'idle';
  }

  function renderOwner(s, st) {
    text('owner-key', s.rotationPending ? 'Vault key renewal pending' : 'Vault key v' + s.vaultKeyVersion + ' current');
    text('owner-link', st === 'link_ready' && s.rawLinkUrl ? s.rawLinkUrl : 'No link yet');
    el('btn-copy').disabled = !(st === 'link_ready' && s.rawLinkUrl);
    if (st === 'idle') {
      badge('owner-badge', 'No active link', 'neutral');
      text('owner-countdown', 'Not issued');
      text('owner-helper', 'Create a one-time link to let your friend in without sharing the password.');
      button('btn-owner', 'Create one-time link', true, 'primary');
    } else if (st === 'link_ready') {
      badge('owner-badge', 'Link ready', 'ok');
      text('owner-countdown', fmt(s.secondsLeft) + ' remaining');
      text('owner-helper', 'Your friend can sign in once without seeing or typing the real password.');
      button('btn-owner', 'Waiting for your friend', false, 'primary');
    } else if (st === 'signed_in') {
      badge('owner-badge', 'Guest signed in', 'warn');
      text('owner-countdown', 'Already used');
      text('owner-helper', s.copierUsedFirst
        ? 'Someone who copied the link used it first. End the session now.'
        : 'Your friend is in. Your vault is flagged and will renew its key at your next unlock.');
      button('btn-owner', 'End session now', true, 'danger');
    } else if (st === 'ended') {
      badge('owner-badge', 'Session ended', 'warn');
      text('owner-countdown', 'Already used');
      text('owner-helper', 'Guest access ended. Unlock your vault to renew its encryption key.');
      button('btn-owner', 'Unlock and renew vault key', true, 'primary');
    } else {
      badge('owner-badge', 'Vault key renewed', 'ok');
      text('owner-countdown', 'Already used');
      text('owner-helper', 'Your vault now uses a fresh encryption key. The old link can’t be used again.');
      button('btn-owner', 'Create another link', true, 'primary');
    }
  }

  function renderFriend(s, st) {
    var showSession = false;
    if (st === 'idle') {
      badge('friend-badge', 'Waiting for link', 'neutral');
      text('friend-helper', 'Waiting for the vault owner to create a one-time link.');
      button('btn-friend', 'Waiting for link', false, 'primary');
    } else if (st === 'link_ready') {
      badge('friend-badge', 'Ready to open', 'neutral');
      text('friend-helper', 'You were invited to sign in once. The password stays hidden inside KeyFort.');
      button('btn-friend', 'Sign in once', true, 'primary');
    } else if (st === 'signed_in' && s.copierUsedFirst) {
      badge('friend-badge', 'Refused', 'err');
      text('friend-helper', 'Someone else used the link first, so it was refused for you.');
      button('btn-friend', 'Link already used', false, 'primary');
    } else if (st === 'signed_in') {
      badge('friend-badge', 'Signed in once', 'ok');
      text('friend-helper', 'You are signed in to CinemaStream Family. The link is now marked already used.');
      text('friend-timer', fmt(s.sessionSeconds) + ' left');
      showSession = true;
      button('btn-friend', 'Link already used', false, 'primary');
    } else {
      badge('friend-badge', 'Session ended', 'err');
      text('friend-helper', 'Your guest session was ended by the owner. Ask for a new link if you need access again.');
      button('btn-friend', 'Session ended', false, 'primary');
    }
    el('friend-session').hidden = !showSession;
  }

  function renderCopier(s, st) {
    text('copier-link', st === 'idle' || !s.rawLinkUrl ? 'No link yet' : s.rawLinkUrl);
    var hasLink = st !== 'idle';
    var label = 'Try copied link';
    if (!hasLink) {
      badge('copier-badge', 'No link yet', 'neutral');
      text('copier-helper', 'No link has been shared yet.');
    } else if (s.copierUsedFirst) {
      badge('copier-badge', 'Used first', 'warn');
      text('copier-helper', 'This person used the link first. First use wins. If this happens, end the session.');
    } else if (s.copierBlocked) {
      badge('copier-badge', 'Refused · Already used', 'err');
      text('copier-helper', 'Refused: this link was already used once. Every later attempt with a copied link is blocked.');
      label = 'Try copied link again';
    } else {
      badge('copier-badge', 'Has a copy', 'neutral');
      text('copier-helper', 'Once your friend signs in, this copied link is marked already used and will be refused.');
    }
    button('btn-copier', label, hasLink, 'secondary');
  }

  function renderTimeline(s, st) {
    var done = [st !== 'idle', st === 'signed_in' || st === 'ended' || st === 'renewed', !!s.copierBlocked, st === 'renewed'];
    var last = 0, i;
    for (i = 0; i < 4; i++) { if (done[i]) { last = i + 1; } }
    for (i = 0; i < 4; i++) {
      el('step-' + (i + 1)).className = 'step' + (done[i] ? (i + 1 === last ? ' is-active' : ' is-done') : '');
      text('step-' + (i + 1) + '-mark', done[i] ? '✓' : String(i + 1));
    }
    text('timeline-status', last === 0 ? 'Ready · Create a link to begin' : 'Step ' + last + ' of 4 · ' + STEP_NAMES[last - 1]);
  }

  function renderSafe(s) {
    text('safe-1-live', 'Vault key v' + s.vaultKeyVersion);
    text('safe-2-live', s.tokenFingerprint ? 'Fingerprint ' + String(s.tokenFingerprint).slice(0, 8) : 'No link yet');
    text('safe-3-live', (s.signatureVerified ? 'Signature verified' : 'Waiting for the site') + ' · Replays refused: ' + s.replaysRefusedCount);
    text('safe-4-live', 'Latest fingerprint ' + (s.latestLedgerHashPrefix ? String(s.latestLedgerHashPrefix).slice(0, 8) : '—'));
    text('safe-5-live', s.rotationPending ? 'Renewal waiting for your next unlock' : 'Key is current (v' + s.vaultKeyVersion + ')');
  }

  function renderLogs(s) {
    var logs = s.logs || [];
    var key = JSON.stringify(logs);
    if (key === logsKey) { return; }
    logsKey = key;
    var ul = el('history-list');
    while (ul.firstChild) { ul.removeChild(ul.firstChild); }
    if (!logs.length) {
      var empty = document.createElement('li');
      empty.className = 'history-row muted';
      empty.textContent = 'No events yet.';
      ul.appendChild(empty);
      return;
    }
    logs.forEach(function (e) {
      var li = document.createElement('li');
      li.className = 'history-row';
      var a = document.createElement('span');
      a.textContent = e.text;
      var b = document.createElement('span');
      b.className = 'mono muted';
      b.textContent = e.meta;
      li.appendChild(a);
      li.appendChild(b);
      ul.appendChild(li);
    });
  }
  var wasUnlocked = null;
  function gateState(s) {
    var unlocked = s.unlocked !== false;
    document.body.classList.remove('booting');
    el('gate').hidden = unlocked;
    el('app').hidden = !unlocked;
    el('btn-lock').hidden = !unlocked;
    text('gate-hint', s.masterKeyHint ? 'Demo vault · demo master key: ' + s.masterKeyHint : '');
    if (!unlocked && wasUnlocked !== false) { el('master-key').value = ''; el('master-key').focus(); }
    if (unlocked) { el('master-key').value = ''; }
    wasUnlocked = unlocked;
  }
  function unlock() {
    var input = el('master-key');
    if (!input.value) { text('gate-msg', 'Enter your master key.'); return; }
    var btn = el('btn-unlock');
    btn.disabled = true;
    text('gate-msg', 'Unlocking…');
    call('POST', API + '/unlock', { masterKey: input.value }).then(function (s) {
      input.value = '';
      text('gate-msg', '');
      el('gate-lock').classList.add('open');
      setTimeout(function () { render(s); el('gate-lock').classList.remove('open'); }, 350);
    }).catch(function (e) {
      input.value = '';
      var m = e && e.message ? e.message : '';
      if (m === 'http 401') { text('gate-msg', 'That master key did not open the vault. Try again.'); input.focus(); }
      else if (m === 'http 429') { text('gate-msg', 'Too many tries. Wait a moment, then try again.'); }
      else { text('gate-msg', ''); onError(e); }
    }).then(function () { btn.disabled = false; });
  }
  function render(s) {
    gateState(s);
    state = s;
    var st = stage(s);
    renderOwner(s, st);
    renderFriend(s, st);
    renderCopier(s, st);
    renderTimeline(s, st);
    renderSafe(s);
    renderLogs(s);
    if (debugOn) {
      var d = el('debug');
      d.hidden = false;
      d.textContent = 'stage: ' + st + '\n' + JSON.stringify(s, null, 2);
    }
  }

  function loadFacts() {
    call('GET', '/api/crypto-facts').then(function (f) {
      text('gate-kdf', 'The short pause is normal: your key is stretched ' + Number(f.kdfIterations).toLocaleString() + ' times to slow down guessing.');
      text('safe-1-more', 'Sealed with ' + f.cipherAlgorithm + '. Your master secret is stretched with ' + f.kdfAlgorithm + ' (' + Number(f.kdfIterations).toLocaleString() + ' rounds).');
      text('safe-2-more', f.tokenSecretBits + '-bit random secret with a ' + f.tokenIdBits + '-bit lookup id. Only a ' + f.tokenHashAlgorithm + ' fingerprint is stored.');
      text('safe-3-more', 'Messages are signed with ' + f.connectorHmacAlgorithm + '. Anything older than ' + f.clockSkewAllowanceSeconds + ' seconds is refused, and each message can be used once.');
      text('safe-4-more', 'Each entry stores a ' + f.ledgerHashAlgorithm + ' fingerprint of the entry before it. Changing any entry breaks every fingerprint after it.');
      text('safe-5-more', 'Recovery kit: ' + f.recoveryKeyAlgorithm + '. Key derivation helper: ' + f.hkdfAlgorithm + '. After a link is used the vault is flagged, and the owner’s device re-encrypts everything under a new key at the next unlock.');
    }).catch(function () {
      ['1', '2', '3', '4', '5'].forEach(function (n) { text('safe-' + n + '-more', 'Details are not available right now.'); });
    });
  }

  function pickBool(r) {
    var keys = ['valid', 'verified', 'intact', 'ok', 'success'];
    for (var i = 0; i < keys.length; i++) {
      if (r && typeof r[keys[i]] === 'boolean') { return r[keys[i]]; }
    }
    return null;
  }

  function checkHistory() {
    var out = el('history-result');
    out.textContent = 'Checking…';
    call('GET', API + '/check-history').then(function (r) {
      var ok = pickBool(r);
      if (ok === true) { out.textContent = 'History checked: nothing was changed.'; }
      else if (ok === false) { out.textContent = 'History check failed: something was changed.'; }
      else { out.textContent = 'Check finished.'; console.log('check-history answer', r); }
    }).catch(function () { out.textContent = 'The check could not run.'; });
  }

  function copyLink() {
    var link = state && state.rawLinkUrl;
    if (!link || !navigator.clipboard) { text('copy-status', 'Copy is not available here. Select the link by hand.'); return; }
    navigator.clipboard.writeText(link).then(function () {
      text('copy-status', 'Copied. The clipboard clears in 30 seconds.');
      clearTimeout(copyTimer);
      copyTimer = setTimeout(function () {
        navigator.clipboard.writeText('').catch(function () { });
        text('copy-status', 'Clipboard cleared.');
      }, 30000);
    }).catch(function () { text('copy-status', 'Copy failed. Select the link by hand.'); });
  }

  function onOwner() {
    var st = state ? stage(state) : 'idle';
    if (st === 'idle') { act('POST', '/create-link', { durationMinutes: 10 }); }
    else if (st === 'signed_in') { act('POST', '/end-session'); }
    else if (st === 'ended') { act('POST', '/renew-key'); }
    else if (st === 'renewed') { act('POST', '/create-link', { durationMinutes: 10 }); }
  }

  el('btn-owner').addEventListener('click', onOwner);
  el('btn-friend').addEventListener('click', function () { act('POST', '/friend-login'); });
  el('btn-copier').addEventListener('click', function () { act('POST', '/copier-try'); });
  el('btn-reset').addEventListener('click', function () { act('POST', '/reset'); });
  el('btn-copy').addEventListener('click', copyLink);
  el('btn-history').addEventListener('click', checkHistory);
  el('btn-unlock').addEventListener('click', unlock);
  el('btn-lock').addEventListener('click', function () { act('POST', '/lock'); });
  el('master-key').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') { unlock(); }
    el('caps-warn').hidden = !(e.getModifierState && e.getModifierState('CapsLock'));
  });
  el('btn-show-key').addEventListener('click', function () {
    var i = el('master-key');
    var show = i.type === 'password';
    i.type = show ? 'text' : 'password';
    this.textContent = show ? 'Hide' : 'Show';
    this.setAttribute('aria-pressed', String(show));
  });

  loadFacts();
  poll();
  setInterval(poll, 1000);
})();
