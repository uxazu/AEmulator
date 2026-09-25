(() => {
  'use strict';

  const C = window.AEMU_CONFIG;          // config.js — прошивки и ссылки (правится руками)
  const I = window.AEMU_I18N;            // i18n.js — переводы (генерируется)
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (_) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (_) {} },
  };
  const prefersReduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const finePointer = matchMedia('(pointer: fine)').matches;
  const root = document.documentElement;
  const LINKS = { ...C.links, releases: C.links.repo + '/releases' };
  const STATUS = ['ok', 'partial', 'wip', 'no'];
  const FEAT_ICONS = ['folder_zip', 'palette', 'sports_esports', 'sd_card', 'translate', 'volunteer_activism'];

  /* ---------- Язык: сохранённый → ?lang → язык системы → английский ---------- */
  function pickLang() {
    const q = new URLSearchParams(location.search).get('lang');
    if (q && I.t[q]) return q;
    const saved = store.get('aemu-lang');
    if (saved && I.t[saved]) return saved;
    for (const l of navigator.languages || [navigator.language || 'en']) {
      if (I.t[l]) return l;
      const base = String(l).split('-')[0].toLowerCase();
      const hit = I.langs.find(x => x.code.toLowerCase() === base || x.code.toLowerCase().startsWith(base + '-'));
      if (hit) return hit.code;
    }
    return 'en';
  }

  /* ---------- Отрисовка текста и списков ---------- */
  function render(lang) {
    const t = I.t[lang];
    root.lang = lang;
    root.dir = I.rtl.includes(lang) ? 'rtl' : 'ltr';
    $$('[data-t]').forEach(e => { e.textContent = t[e.dataset.t] ?? ''; });
    $$('[data-t-html]').forEach(e => { e.innerHTML = t[e.dataset.tHtml] ?? ''; });

    $('#feats').innerHTML = t.feats.map((f, i) =>
      `<div class="skill reveal tilt"><div class="skill__icon"><span class="material-symbols-rounded">${FEAT_ICONS[i % FEAT_ICONS.length]}</span></div><p>${f}</p></div>`).join('');

    const used = STATUS.filter(s => C.firmware.some(f => f.status === s));
    $('#legend').innerHTML = used.map(s => `<span><i class="dot" style="background:var(--${s})"></i>${t['st_' + s]}</span>`).join('');

    $('#fw').innerHTML = C.firmware.map(f => `
      <article class="project reveal tilt">
        <div class="project__top">
          <div class="project__ver">${esc(f.android)}</div>
          <div><h3>${esc(f.device)}</h3><div class="project__skin">${esc(f.skin)}</div></div>
        </div>
        <div class="project__status"><i class="dot" style="background:var(--${f.status})"></i>${t['st_' + f.status] || f.status}
          ${f.note ? `<small>· ${t['n_' + f.note] || esc(f.note)}</small>` : ''}</div>
        <a class="m3-btn m3-btn--tonal m3-btn--small ripple" href="${esc(f.url)}" target="_blank" rel="noopener">
          <span class="material-symbols-rounded">download</span>${t['dl_' + f.kind] || t.c_download}</a>
      </article>`).join('');

    $('#steps').innerHTML = t.steps.map(s => `<li class="reveal"><span>${s}</span></li>`).join('');
    $('#reqs').innerHTML = t.reqs.map(s => `<li>${s}</li>`).join('');

    $('#langMenu').innerHTML = I.langs.map(l =>
      `<li><button role="option" data-lang="${l.code}" aria-selected="${l.code === lang}"><span>${l.flag}</span>${l.name}</button></li>`).join('');

    // ссылки в переведённом тексте открываются в новой вкладке
    $$('main a[href^="http"]').forEach(a => { a.target = '_blank'; a.rel = 'noopener'; });
    wireDynamic();
  }

  const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  /* ---------- Статичные ссылки и значения из конфига ---------- */
  $$('[data-link]').forEach(a => { a.href = LINKS[a.dataset.link]; a.target = '_blank'; a.rel = 'noopener'; });
  $$('[data-cfg]').forEach(e => { e.textContent = e.dataset.cfg === 'version' ? C.version : C.links[e.dataset.cfg]; });
  $('#statFw').dataset.count = String(C.firmware.filter(f => f.status === 'ok' || f.status === 'partial').length);

  /* ---------- Бегущая строка из конфига ---------- */
  const names = [...new Set(C.firmware.flatMap(f => [f.device.replace(/\s*\(.*\)$/, ''), f.skin]))];
  $('#marquee').innerHTML = names.map(n => `<span>${esc(n)}</span><i>✦</i>`).join('');

  /* ---------- Тема ---------- */
  $('.theme-toggle').addEventListener('click', () => {
    root.dataset.theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
    store.set('aemu-theme', root.dataset.theme);
  });

  /* ---------- Меню языков ---------- */
  let lang = pickLang();
  const langBtn = $('#langBtn'), langMenu = $('#langMenu');
  const closeMenu = () => { langMenu.hidden = true; langBtn.setAttribute('aria-expanded', 'false'); };
  langBtn.addEventListener('click', e => {
    e.stopPropagation();
    langMenu.hidden = !langMenu.hidden;
    langBtn.setAttribute('aria-expanded', String(!langMenu.hidden));
  });
  langMenu.addEventListener('click', e => {
    const b = e.target.closest('[data-lang]');
    if (!b) return;
    lang = b.dataset.lang;
    store.set('aemu-lang', lang);
    closeMenu();
    render(lang);
  });
  document.addEventListener('click', e => { if (!e.target.closest('.lang')) closeMenu(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeMenu(); });

  /* ---------- Появление при скролле ---------- */
  const io = new IntersectionObserver(entries => {
    for (const e of entries) {
      if (!e.isIntersecting) continue;
      e.target.classList.add('visible');
      io.unobserve(e.target);
      setTimeout(() => e.target.classList.remove('reveal', 'visible'), 1200);
    }
  }, { threshold: .12, rootMargin: '0px 0px -40px 0px' });

  /* ---------- Прогресс скролла + активный пункт ---------- */
  const progress = $('.scroll-progress');
  const navLinks = $$('.nav__links a');
  const sections = navLinks.map(a => $(a.getAttribute('href'))).filter(Boolean);
  function onScroll() {
    const h = root.scrollHeight - innerHeight;
    progress.style.transform = `scaleX(${h > 0 ? scrollY / h : 0})`;
    let current = null;
    for (const s of sections) if (s.getBoundingClientRect().top <= innerHeight * .4) current = s;
    navLinks.forEach(a => a.classList.toggle('active', !!current && a.getAttribute('href') === '#' + current.id));
  }
  addEventListener('scroll', onScroll, { passive: true });

  /* ---------- Печатная машинка ---------- */
  const tw = $('.typewriter');
  const words = JSON.parse(tw.dataset.words);
  if (prefersReduced) tw.textContent = words[0];
  else {
    let wi = 0, ci = 0, del = false;
    (function tick() {
      const w = words[wi];
      ci += del ? -1 : 1;
      tw.textContent = w.slice(0, ci);
      let d = del ? 38 : 72;
      if (!del && ci === w.length) { d = 1900; del = true; }
      else if (del && ci === 0) { del = false; wi = (wi + 1) % words.length; d = 420; }
      setTimeout(tick, d);
    })();
  }

  /* ---------- Счётчики ---------- */
  const counterIO = new IntersectionObserver(entries => {
    for (const e of entries) {
      if (!e.isIntersecting) continue;
      counterIO.unobserve(e.target);
      const el = e.target, target = parseInt(el.dataset.count, 10);
      if (prefersReduced) { el.textContent = target; continue; }
      const t0 = performance.now(), dur = 1400;
      (function step(t) {
        const p = Math.min((t - t0) / dur, 1);
        el.textContent = Math.round(target * (1 - Math.pow(1 - p, 3)));
        if (p < 1) requestAnimationFrame(step);
      })(t0);
    }
  }, { threshold: .6 });
  $$('.stat__num[data-count]').forEach(el => counterIO.observe(el));

  /* ---------- Рябь, наклон, прожектор (и для перерисованных карточек) ---------- */
  function ripple(btn) {
    if (btn._ripple) return; btn._ripple = true;
    btn.addEventListener('pointerdown', ev => {
      const r = btn.getBoundingClientRect(), size = Math.max(r.width, r.height);
      const ink = document.createElement('span');
      ink.className = 'ripple-ink';
      ink.style.width = ink.style.height = size + 'px';
      ink.style.left = (ev.clientX - r.left - size / 2) + 'px';
      ink.style.top = (ev.clientY - r.top - size / 2) + 'px';
      btn.appendChild(ink);
      ink.addEventListener('animationend', () => ink.remove());
    });
  }

  function tilt(card) {
    if (card._tilt || prefersReduced || !finePointer) return; card._tilt = true;
    const s = { rx: 0, ry: 0, y: 0, sc: 1, trx: 0, tyr: 0, ty: 0, tsc: 1, raf: null };
    const step = () => {
      s.rx += (s.trx - s.rx) * .1; s.ry += (s.tyr - s.ry) * .1; s.y += (s.ty - s.y) * .1; s.sc += (s.tsc - s.sc) * .18;
      card.style.transform = `perspective(900px) rotateX(${s.rx.toFixed(3)}deg) rotateY(${s.ry.toFixed(3)}deg) translateY(${s.y.toFixed(2)}px) scale(${s.sc.toFixed(3)})`;
      const settled = Math.abs(s.trx - s.rx) < .02 && Math.abs(s.tyr - s.ry) < .02 && Math.abs(s.ty - s.y) < .02 && Math.abs(s.tsc - s.sc) < .002;
      if (settled && !s.trx && !s.tyr && !s.ty && s.tsc === 1) { card.style.transform = ''; s.raf = null; }
      else s.raf = requestAnimationFrame(step);
    };
    const kick = () => { if (!s.raf) s.raf = requestAnimationFrame(step); };
    card.addEventListener('pointermove', ev => {
      const r = card.getBoundingClientRect();
      s.trx = (.5 - (ev.clientY - r.top) / r.height) * 7;
      s.tyr = ((ev.clientX - r.left) / r.width - .5) * 9;
      s.ty = -3; kick();
      card.style.setProperty('--mx', (ev.clientX - r.left) + 'px');
      card.style.setProperty('--my', (ev.clientY - r.top) + 'px');
    });
    card.addEventListener('pointerdown', () => { s.tsc = .955; kick(); });
    card.addEventListener('pointerup', () => { s.tsc = 1; kick(); });
    card.addEventListener('pointercancel', () => { s.tsc = 1; kick(); });
    card.addEventListener('pointerleave', () => { s.trx = s.tyr = s.ty = 0; s.tsc = 1; kick(); });
  }

  function wireDynamic() {
    $$('.ripple').forEach(ripple);
    $$('.tilt').forEach(tilt);
    $$('.reveal').forEach(el => io.observe(el));
  }

  /* ---------- Копирование кошельков ---------- */
  const snack = $('#snackbar');
  function toast(msg) {
    snack.textContent = msg;
    snack.classList.add('show');
    clearTimeout(snack._t);
    snack._t = setTimeout(() => snack.classList.remove('show'), 2000);
  }
  $$('[data-copy-key]').forEach(btn => btn.addEventListener('click', async () => {
    const v = C.links[btn.dataset.copyKey];
    try { await navigator.clipboard.writeText(v); } catch (_) {}
    const icon = btn.querySelector('.material-symbols-rounded');
    btn.classList.add('copied'); icon.textContent = 'check';
    setTimeout(() => { btn.classList.remove('copied'); icon.textContent = 'content_copy'; }, 1600);
    toast(I.t[lang].copied);
  }));
  $$('.addr').forEach(a => a.addEventListener('click', () => a.closest('.support-card').querySelector('.copy-btn').click()));

  /* ---------- Лента: бесконечная, с перетаскиванием и инерцией ---------- */
  const marquee = $('.marquee');
  if (marquee) {
    const track = $('.marquee__track', marquee), source = $('.marquee__group', marquee);
    let gw = 0;
    const fill = () => {
      $$('.marquee__group.clone', track).forEach(c => c.remove());
      gw = source.offsetWidth;
      if (gw <= 0) return;
      const need = Math.max(2, Math.ceil((innerWidth + gw) / gw) + 1);
      for (let i = 1; i < need; i++) { const c = source.cloneNode(true); c.classList.add('clone'); track.appendChild(c); }
    };
    fill();
    addEventListener('resize', fill);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(fill);
    const SPEED = 42;
    let auto = 0, dragTarget = 0, dragCur = 0, inertia = 0, down = false, startX = 0, startDrag = 0, lastX = 0, lastMoveT = 0, vel = 0;
    let lastT = performance.now();
    const wrap = v => gw > 0 ? (((v % gw) + gw) % gw) - gw : v;
    marquee.addEventListener('pointerdown', ev => {
      down = true; startX = lastX = ev.clientX; startDrag = dragTarget; vel = 0; inertia = 0; lastMoveT = performance.now();
      marquee.classList.add('dragging'); marquee.setPointerCapture(ev.pointerId);
    });
    marquee.addEventListener('pointermove', ev => {
      if (!down) return;
      dragTarget = startDrag + (ev.clientX - startX);
      const now = performance.now(), dt = now - lastMoveT;
      if (dt > 0) { vel = .8 * vel + .2 * ((ev.clientX - lastX) / dt); lastX = ev.clientX; lastMoveT = now; }
    });
    const release = () => { if (!down) return; down = false; inertia = vel * 16; marquee.classList.remove('dragging'); };
    marquee.addEventListener('pointerup', release);
    marquee.addEventListener('pointercancel', release);
    const frame = t => {
      const dt = Math.min((t - lastT) / 1000, .05); lastT = t;
      if (!down) {
        if (!prefersReduced) auto -= SPEED * dt;
        if (inertia) { dragTarget += inertia; inertia *= .94; if (Math.abs(inertia) < .05) inertia = 0; }
      }
      dragCur += (dragTarget - dragCur) * .14;
      track.style.translate = wrap(auto + dragCur) + 'px';
      requestAnimationFrame(frame);
    };
    requestAnimationFrame(frame);
  }

  /* ---------- Параллакс блобов ---------- */
  if (!prefersReduced && finePointer) {
    const blobs = $$('.blob');
    let raf = null;
    addEventListener('pointermove', ev => {
      if (raf) return;
      raf = requestAnimationFrame(() => {
        const dx = ev.clientX / innerWidth - .5, dy = ev.clientY / innerHeight - .5;
        blobs.forEach((b, i) => { const k = (i + 1) * 12; b.style.translate = `${dx * k}px ${dy * k}px`; });
        raf = null;
      });
    }, { passive: true });
  }

  render(lang);
  onScroll();
})();
