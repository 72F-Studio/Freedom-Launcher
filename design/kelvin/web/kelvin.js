/* Kelvin web runtime: glass texture and level, lens filters and the stretch physics for previews and web pages. */
(function () {
  var root = document.documentElement;
  // Two textures, each with its own intensity: liquid (the Glass level) and frost (Opaque to Sheer).
  var KEY = 'kelvin-glass', KEY_FROST = 'kelvin-frost', KEY_TEXTURE = 'kelvin-texture';
  var state = { texture: 'liquid', level: 0.3, deform: 0.15 };
  var DEFAULT = { liquid: 0.3, frost: 0.5 };
  function keyFor(texture) { return texture === 'frost' ? KEY_FROST : KEY; }
  function saved(texture) {
    try { var v = parseFloat(localStorage.getItem(keyFor(texture))); if (v === v) return v; } catch (e) {}
    return DEFAULT[texture];
  }
  function ss(a, b, x) { var t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
  function lerp(a, b, t) { return a + (b - a) * t; }
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var moreContrast = window.matchMedia && window.matchMedia('(prefers-contrast: more), (prefers-reduced-transparency: reduce)').matches;

  function curve(l, pts) {
    for (var i = 0; i < pts.length - 1; i++) if (l <= pts[i + 1][0]) return pts[i][1] + (pts[i + 1][1] - pts[i][1]) * ss(pts[i][0], pts[i + 1][0], l);
    return pts[pts.length - 1][1];
  }
  function params(level, texture) {
    level = Math.min(1, Math.max(0, level));
    texture = texture || state.texture;
    // Stepless: near-opaque at 0, bare glass at 1. Only "increase contrast" makes it opaque.
    if (moreContrast) return { texture: texture, level: level, frost: 0, alpha: 1, thickness: 0, sat: 1, deform: reduce ? 0 : 0.15 };
    if (texture === 'frost') {
      // Deep frost: the blur stays deep across the range, only the coat thins (Opaque 0 to Sheer 1),
      // and colour is pushed so what is behind glows through. No refraction. [blur] is in CSS px.
      var clarity = level * 0.55;
      return {
        texture: texture,
        level: level,
        blur: curve(level, [[0, 40], [1, 30]]),
        alpha: curve(level, [[0, 0.96], [0.5, 0.62], [1, 0.34]]),
        thickness: 0,
        sat: curve(level, [[0, 1.3], [1, 1.8]]),
        deform: reduce ? 0 : 0.15 + 0.85 * ss(0.3, 0.8, clarity)
      };
    }
    return {
      texture: texture,
      level: level,
      frost: curve(level, [[0, 24], [0.3, 20], [0.55, 10], [0.8, 4], [1, 0]]),
      alpha: curve(level, [[0, 0.94], [0.3, 0.70], [0.55, 0.42], [0.8, 0.16], [1, 0]]),
      thickness: curve(level, [[0, 3], [0.3, 6], [0.55, 12], [0.8, 18], [1, 22]]),
      sat: 1 + 0.2 * level,
      deform: reduce ? 0 : 0.15 + 0.85 * ss(0.3, 0.8, level)
    };
  }

  /** Sets the intensity of the current texture (the Glass level for liquid, Opaque to Sheer for frost). */
  function setGlass(level, persist) {
    var p = params(level);
    state.level = p.level; state.deform = p.deform;
    root.setAttribute('data-k-texture', p.texture);
    root.style.setProperty('--g-alpha', p.alpha.toFixed(3));
    root.style.setProperty('--g-blur', (p.texture === 'frost' ? p.blur : p.frost * 0.6).toFixed(1) + 'px');
    root.style.setProperty('--g-sat', p.sat.toFixed(2));
    root.style.setProperty('--g-deform', p.deform.toFixed(2));
    var b = document.querySelector('#k-bevel feDisplacementMap');
    if (b) b.setAttribute('scale', (p.thickness * 2.2).toFixed(1));
    var g = document.querySelector('#k-grain feDisplacementMap');
    if (g && p.texture !== 'frost') g.setAttribute('scale', (p.frost * 0.5).toFixed(1));
    var els = document.querySelectorAll('.k-glass');
    for (var i = 0; i < els.length; i++) {
      els[i].classList.toggle('bevel', p.texture !== 'frost' && p.thickness > 8);
      els[i].classList.toggle('grain', p.texture !== 'frost' && p.thickness <= 8 && p.frost > 1);
    }
    if (persist) { try { localStorage.setItem(keyFor(p.texture), String(p.level)); } catch (e) {} }
    try { document.dispatchEvent(new CustomEvent('kelvin-glass', { detail: p })); } catch (e) {}
  }

  /** Picks the material, 'liquid' or 'frost', and restores the intensity saved for it. */
  function setTexture(texture, persist) {
    state.texture = texture === 'frost' ? 'frost' : 'liquid';
    if (persist) { try { localStorage.setItem(KEY_TEXTURE, state.texture); } catch (e) {} }
    setGlass(saved(state.texture), false);
    var ctl = document.querySelectorAll('[data-k="glass"]');
    for (var i = 0; i < ctl.length; i++) if (ctl[i].kelvinSync) ctl[i].kelvinSync();
    var pick = document.querySelectorAll('[data-k="texture"] [data-texture]');
    for (var j = 0; j < pick.length; j++) {
      var on = pick[j].getAttribute('data-texture') === state.texture;
      pick[j].classList.toggle('on', on); pick[j].setAttribute('aria-selected', String(on));
    }
  }

  // Displacement map for the rim: a convex edge refracts the view inward, so near the rim the
  // pane shows backdrop from nearer its middle (Snell's law through a quarter-circle bevel).
  function bevelMap() {
    var n = 96, cv = document.createElement('canvas'); cv.width = cv.height = n;
    var g = cv.getContext('2d'), img = g.createImageData(n, n);
    for (var y = 0; y < n; y++) for (var x = 0; x < n; x++) {
      var u = (x + 0.5) / n - 0.5, v = (y + 0.5) / n - 0.5;
      var e = Math.min(1, Math.pow(Math.max(Math.abs(u), Math.abs(v)) * 2, 6));
      var o = (y * n + x) * 4;
      img.data[o] = 128 - Math.sign(u) * e * 127 * (Math.abs(u) >= Math.abs(v) ? 1 : 0.3);
      img.data[o + 1] = 128 - Math.sign(v) * e * 127 * (Math.abs(v) >= Math.abs(u) ? 1 : 0.3);
      img.data[o + 2] = 128; img.data[o + 3] = 255;
    }
    g.putImageData(img, 0, 0);
    return cv.toDataURL();
  }

  function filters() {
    if (document.getElementById('k-filters')) return;
    var ns = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('id', 'k-filters'); svg.setAttribute('width', '0'); svg.setAttribute('height', '0');
    svg.setAttribute('aria-hidden', 'true'); svg.style.position = 'absolute';
    svg.innerHTML =
      '<filter id="k-grain" x="0" y="0" width="100%" height="100%" color-interpolation-filters="sRGB">' +
      '<feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="1" seed="7" result="n"/>' +
      '<feDisplacementMap in="SourceGraphic" in2="n" scale="10" xChannelSelector="R" yChannelSelector="G"/></filter>' +
      '<filter id="k-bevel" x="0" y="0" width="100%" height="100%" color-interpolation-filters="sRGB">' +
      '<feImage href="' + bevelMap() + '" x="0" y="0" width="100%" height="100%" preserveAspectRatio="none" result="m"/>' +
      '<feDisplacementMap in="SourceGraphic" in2="m" scale="0" xChannelSelector="R" yChannelSelector="G"/></filter>';
    document.body.appendChild(svg);
  }

  var BOUNCE = 'cubic-bezier(.3,1.55,.5,1)';
  function spring(el, prop, ms) { el.style.transition = prop + ' ' + (ms || 380) + 'ms ' + BOUNCE; }

  function press(el) {
    el.classList.add('k-sheen');
    el.addEventListener('pointerdown', function (e) {
      var r = el.getBoundingClientRect();
      el.style.setProperty('--px', (e.clientX - r.left) + 'px'); el.style.setProperty('--py', (e.clientY - r.top) + 'px');
      el.classList.add('down');
      if (state.deform > 0) { var s = 0.04 + 0.04 * state.deform; el.style.transition = 'transform 120ms ease-out'; el.style.transform = 'scale(' + (1 - s * 0.6) + ',' + (1 - s) + ')'; }
    });
    var up = function () { el.classList.remove('down'); spring(el, 'transform', 420); el.style.transform = ''; };
    el.addEventListener('pointerup', up); el.addEventListener('pointerleave', up); el.addEventListener('pointercancel', up);
  }

  function toggle(el) {
    var knob = el.querySelector('.knob') || el.appendChild(Object.assign(document.createElement('span'), { className: 'knob' }));
    var startX = 0, startPos = 0, pos = el.getAttribute('aria-checked') === 'true' ? 1 : 0, dragging = false, moved = false;
    function draw(p, lifted) {
      var over = p < 0 ? -p : p > 1 ? p - 1 : 0, cp = Math.min(1, Math.max(0, p));
      var w = 28 + (lifted ? 10 * (0.4 + 0.6 * state.deform) : 0) + 18 * over * state.deform;
      knob.style.width = w + 'px';
      var pop = lifted ? 1 + 0.12 * state.deform : 1;
      knob.style.transform = 'translateX(' + (cp * (52 - 4 - w)) + 'px) scale(' + pop + ')';
      knob.classList.toggle('hot', !!lifted); knob.style.setProperty('--heat', lifted ? (0.3 + 0.7 * state.deform).toFixed(2) : '0');
    }
    draw(pos, false);
    el.addEventListener('pointerdown', function (e) {
      if (el.disabled) return;
      dragging = true; moved = false; startX = e.clientX; startPos = pos; el.setPointerCapture(e.pointerId);
      knob.style.transition = 'width 160ms, transform 160ms'; draw(pos, true);
    });
    el.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      var d = (e.clientX - startX) / 20; if (Math.abs(e.clientX - startX) > 3) moved = true;
      knob.style.transition = 'none'; draw(Math.max(-0.4, Math.min(1.4, startPos + d)), true);
      pos = startPos + d;
    });
    function end() {
      if (!dragging) return; dragging = false;
      var on = moved ? pos > 0.5 : !(startPos > 0.5);
      pos = on ? 1 : 0; el.setAttribute('aria-checked', String(on));
      knob.style.transition = 'width 380ms ' + BOUNCE + ', transform 420ms ' + BOUNCE + ', background-color 200ms';
      draw(pos, false);
    }
    el.addEventListener('pointerup', end); el.addEventListener('pointercancel', end);
    el.addEventListener('keydown', function (e) { if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); pos = pos > 0.5 ? 0 : 1; el.setAttribute('aria-checked', String(pos === 1)); spring(knob, 'transform', 420); draw(pos, false); } });
  }

  function slider(el) {
    var fill = el.querySelector('.fill'), knob = el.querySelector('.knob'), label = el.querySelector('.v');
    var value = parseFloat(el.getAttribute('aria-valuenow') || '50') / 100, lastX = 0, lastT = 0, dragging = false;
    var unit = el.getAttribute('data-unit') || '';
    function draw(v, over, speed, lifted) {
      fill.style.width = (v * 100) + '%';
      if (knob) {
        knob.style.left = 'calc(' + (v * 100) + '% - ' + (v * 28) + 'px + 14px)';
        var pop = lifted ? 1 + 0.3 * (0.3 + 0.7 * state.deform) : 1, sp = Math.min(0.4, speed) * state.deform;
        knob.style.transform = 'scale(' + (pop * (1 + sp)) + ',' + (pop * (1 - sp * 0.4)) + ')';
        knob.classList.toggle('hot', !!lifted); knob.style.setProperty('--heat', lifted ? (0.3 + 0.7 * state.deform).toFixed(2) : '0');
      }
      var s = Math.sign(over) * Math.sqrt(Math.abs(over)) * 0.08 * (0.3 + 0.7 * state.deform);
      el.style.transformOrigin = s < 0 ? 'right center' : 'left center';
      el.style.transform = s ? 'scale(' + (1 + Math.abs(s)) + ',' + (1 - Math.abs(s) * 0.5) + ')' : '';
      el.setAttribute('aria-valuenow', Math.round(v * 100));
      if (label) label.textContent = Math.round(v * 100) + unit;
    }
    if (knob) knob.style.marginLeft = '-14px';
    draw(value, 0, 0, false);
    function at(e) { var r = el.getBoundingClientRect(); return (e.clientX - r.left) / r.width; }
    el.addEventListener('pointerdown', function (e) {
      dragging = true; el.setPointerCapture(e.pointerId); lastX = e.clientX; lastT = e.timeStamp;
      if (knob) knob.style.transition = 'transform 160ms, left 120ms';
      var raw = at(e); value = Math.min(1, Math.max(0, raw)); draw(value, 0, 0, true);
    });
    el.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      var raw = at(e), speed = Math.abs(e.clientX - lastX) / Math.max(1, e.timeStamp - lastT) / 3; lastX = e.clientX; lastT = e.timeStamp;
      if (knob) knob.style.transition = 'none'; el.style.transition = 'none';
      value = Math.min(1, Math.max(0, raw)); draw(value, raw - value, speed, true);
    });
    function end() { if (!dragging) return; dragging = false; if (knob) knob.style.transition = 'transform 420ms ' + BOUNCE; spring(el, 'transform', 460); draw(value, 0, 0, false); el.dispatchEvent(new Event('change')); }
    el.addEventListener('pointerup', end); el.addEventListener('pointercancel', end);
    el.addEventListener('keydown', function (e) {
      var d = e.key === 'ArrowRight' || e.key === 'ArrowUp' ? 0.05 : e.key === 'ArrowLeft' || e.key === 'ArrowDown' ? -0.05 : 0;
      if (d) { e.preventDefault(); value = Math.min(1, Math.max(0, value + d)); draw(value, 0, 0, false); el.dispatchEvent(new Event('change')); }
    });
    el.kelvinValue = function () { return value; };
    el.kelvinSet = function (v) { value = v; draw(v, 0, 0, false); };
  }

  // A damped spring stepped per frame, so the bulb and the cut in the bar move as one.
  function springTo(get, set, target, onFrame, stiffness, damping) {
    var v = 0, last = 0, k = stiffness || 420, d = 2 * (damping || 0.76) * Math.sqrt(k);
    cancelAnimationFrame(set._raf);
    function step(t) {
      var dt = last ? Math.min(0.032, (t - last) / 1000) : 0.016; last = t;
      var x = get(); var a = -k * (x - target) - d * v; v += a * dt; x += v * dt; set(x); onFrame(v);
      if (Math.abs(v) > 0.002 || Math.abs(x - target) > 0.001) set._raf = requestAnimationFrame(step); else { set(target); onFrame(0); }
    }
    set._raf = requestAnimationFrame(step);
  }

  function dock(el) {
    var tabs = Array.prototype.slice.call(el.querySelectorAll('button'));
    var drop = el.querySelector('.drop') || el.insertBefore(Object.assign(document.createElement('span'), { className: 'drop' }), el.firstChild);
    var idx = Math.max(0, tabs.findIndex(function (b) { return b.classList.contains('on'); }));
    var slot = function () { return (el.clientWidth - 12) / tabs.length; };
    var dragging = false, startX = 0, moved = false, lastX = 0, lastT = 0;
    function place(p, speed, lifted, animate) {
      var s = slot(), sp = Math.min(0.45, speed || 0) * state.deform, pop = lifted ? 1 + 0.16 * (0.3 + 0.7 * state.deform) : 1;
      drop.style.width = s + 'px';
      drop.style.transition = animate ? 'transform 520ms ' + BOUNCE + ', background-color 200ms' : 'none';
      drop.style.transform = 'translateX(' + (p * s) + 'px) scale(' + (pop * (1 + sp)) + ',' + (pop * (1 - sp * 0.35)) + ')';
      // The lifted lens is liquid glass; under frost the droplet stays a solid drop.
      drop.classList.toggle('lifted', !!lifted && state.texture !== 'frost' && state.deform > 0.2 && state.level > 0.02);
      tabs.forEach(function (b, i) { var near = 1 - Math.min(1, Math.abs(p - i)); b.style.color = near > 0.5 ? 'var(--accent)' : 'var(--glass-ink-2)'; });
    }
    function select(i) { idx = i; tabs.forEach(function (b, j) { b.classList.toggle('on', j === i); b.setAttribute('aria-selected', String(j === i)); }); }
    place(idx, 0, false, false);
    el.addEventListener('pointerdown', function (e) { dragging = true; moved = false; startX = lastX = e.clientX; lastT = e.timeStamp; });
    el.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      if (!moved && Math.abs(e.clientX - startX) > 6) { moved = true; el.setPointerCapture(e.pointerId); }
      if (!moved) return;
      var r = el.getBoundingClientRect(), p = Math.max(-0.25, Math.min(tabs.length - 0.75, (e.clientX - r.left - 6) / slot() - 0.5));
      var speed = Math.abs(e.clientX - lastX) / Math.max(1, e.timeStamp - lastT) / 2; lastX = e.clientX; lastT = e.timeStamp;
      place(p, speed, true, false); el._p = p;
    });
    function end() {
      if (!dragging) return; dragging = false;
      if (moved) { var i = Math.round(Math.max(0, Math.min(tabs.length - 1, el._p))); select(i); place(i, 0, false, true); }
    }
    el.addEventListener('pointerup', end); el.addEventListener('pointercancel', end);
    tabs.forEach(function (b, i) {
      b.setAttribute('role', 'tab');
      b.addEventListener('click', function () {
        if (moved) return;
        var from = idx; select(i);
        place(from + (i - from) * 0.5, Math.min(0.45, Math.abs(i - from) * 0.25), false, true);
        setTimeout(function () { place(i, 0, false, true); }, 140);
      });
    });
    window.addEventListener('resize', function () { place(idx, 0, false, false); });
  }

  // The intensity slider for the current texture. Put end labels in [data-k-end="clear"] (it reads
  // Clear or Sheer) and the Clear warning in [data-k-warn]; frost keeps its contrast and never warns.
  function glassControl(el) {
    slider(el);
    // Stepless: every position is valid and nothing snaps.
    var out = el.parentNode.querySelector('[data-k-out]');
    function end() { return state.texture === 'frost' ? 'Sheer' : 'Clear'; }
    function name(v) { return end() + ' ' + Math.round(v * 100) + '%'; }
    function label(v) {
      el.setAttribute('aria-valuetext', name(v)); if (out) out.textContent = name(v);
      var e = el.parentNode.querySelector('[data-k-end="clear"]'); if (e) e.textContent = end();
      var warn = el.parentNode.querySelector('[data-k-warn]'); if (warn) warn.hidden = state.texture === 'frost' || v < 0.88;
    }
    function apply() { var v = el.kelvinValue(); setGlass(v, true); label(v); }
    el.kelvinSync = function () { var v = saved(state.texture); el.kelvinSet(v); label(v); };
    el.addEventListener('pointermove', apply); el.addEventListener('change', apply);
    el.kelvinSync(); setGlass(el.kelvinValue(), false);
  }

  // A texture picker: buttons carrying data-texture="liquid" or "frost", e.g. inside a .k-seg.
  function textureControl(el) {
    var b = el.querySelectorAll('[data-texture]');
    for (var i = 0; i < b.length; i++) b[i].addEventListener('click', function (e) { setTexture(e.currentTarget.getAttribute('data-texture'), true); });
  }

  function mount(scope) {
    scope = scope || document;
    filters();
    var each = function (sel, fn) { var l = scope.querySelectorAll(sel); for (var i = 0; i < l.length; i++) fn(l[i]); };
    each('[data-k="press"]', press);
    each('[data-k="switch"]', toggle);
    each('[data-k="slider"]', slider);
    each('[data-k="dock"]', dock);
    try { var t = localStorage.getItem(KEY_TEXTURE); if (t) state.texture = t === 'frost' ? 'frost' : 'liquid'; } catch (e) {}
    each('[data-k="texture"]', textureControl);
    each('[data-k="glass"]', glassControl);
    setTexture(state.texture, false);
  }

  window.addEventListener('storage', function (e) {
    if (e.key === KEY_TEXTURE && e.newValue) setTexture(e.newValue, false);
    else if (e.key === keyFor(state.texture) && e.newValue !== null) setTexture(state.texture, false);
  });
  window.Kelvin = { setGlass: setGlass, setTexture: setTexture, params: params, mount: mount, press: press, toggle: toggle, slider: slider, dock: dock };
})();
