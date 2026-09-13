/* UDAV documentation site: navigation, theme, demos. No build step, no framework. */
(function () {
  "use strict";

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  /* ---------- theme ---------- */
  /* The initial theme is already set by the inline script in <head>, so there is no flash. */
  const root = document.documentElement;
  function applyTheme(theme) {
    root.setAttribute("data-theme", theme);
    const btn = $("#theme-btn");
    if (btn) btn.setAttribute("aria-label", theme === "dark" ? "Switch to light theme" : "Switch to dark theme");
  }
  applyTheme(root.getAttribute("data-theme") || "light");
  $("#theme-btn")?.addEventListener("click", () => {
    const next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
    applyTheme(next);
    try { localStorage.setItem("udav-docs-theme", next); } catch (e) { /* ignore */ }
  });

  /* ---------- table of contents ---------- */
  const tocEl = $("#toc");
  const tocNav = $("#toc-nav");
  const sections = $$("section.doc-section[id]");
  const headingLinks = new Map();

  function buildToc() {
    const ul = document.createElement("ul");
    sections.forEach((section, i) => {
      const h2 = $("h2", section);
      if (!h2) return;
      const li = document.createElement("li");
      const subs = $$("h3[id]", section);
      const group = document.createElement("div");
      group.className = "toc-group";
      const a = document.createElement("a");
      a.href = "#" + section.id;
      a.innerHTML = `<span class="num">${i + 1}</span>${h2.dataset.toc || h2.textContent.trim()}`;
      a.dataset.target = section.id;
      group.appendChild(a);
      headingLinks.set(section.id, a);
      if (subs.length) {
        const tgl = document.createElement("button");
        tgl.className = "tgl";
        tgl.type = "button";
        tgl.setAttribute("aria-label", "Toggle subsections");
        tgl.innerHTML = '<svg viewBox="0 0 16 16"><path d="M5 2l6 6-6 6z"/></svg>';
        tgl.addEventListener("click", (e) => { e.preventDefault(); li.classList.toggle("open"); });
        group.appendChild(tgl);
        const sub = document.createElement("ul");
        subs.forEach((h3) => {
          const sli = document.createElement("li");
          const sa = document.createElement("a");
          sa.href = "#" + h3.id;
          sa.textContent = h3.dataset.toc || h3.textContent.trim();
          sa.dataset.target = h3.id;
          sli.appendChild(sa);
          sub.appendChild(sli);
          headingLinks.set(h3.id, sa);
        });
        li.appendChild(group);
        li.appendChild(sub);
      } else {
        li.appendChild(group);
      }
      ul.appendChild(li);
    });
    tocNav.innerHTML = "";
    tocNav.appendChild(ul);
  }
  buildToc();

  /* heading anchors — added after buildToc, which reads the headings' text */
  function addAnchor(heading, targetId) {
    const a = document.createElement("a");
    a.className = "anchor";
    a.href = "#" + targetId;
    a.textContent = "#";
    a.setAttribute("aria-label", "Link to this section");
    heading.appendChild(a);
  }
  sections.forEach((s) => { const h2 = $("h2", s); if (h2) addAnchor(h2, s.id); });
  $$("section.doc-section h3[id], section.doc-section h4[id]").forEach((h) => addAnchor(h, h.id));

  /* ---------- scrollspy ---------- */
  const targets = [];
  sections.forEach((s) => { targets.push(s); $$("h3[id]", s).forEach((h) => targets.push(h)); });
  let activeId = null;

  /* Keep the active entry in view by scrolling the sidebar itself. Element.scrollIntoView()
     would scroll every scrollable ancestor including the window, which cancels the smooth
     scroll a link click just started — the page would then stop short of its target. */
  function revealInToc(link) {
    const pad = 40;
    const box = link.getBoundingClientRect();
    const view = tocEl.getBoundingClientRect();
    if (box.top < view.top + pad) tocEl.scrollTop += box.top - view.top - pad;
    else if (box.bottom > view.bottom - pad) tocEl.scrollTop += box.bottom - view.bottom + pad;
  }

  function setActive(id) {
    if (id === activeId) return;
    activeId = id;
    $$("a.active", tocNav).forEach((a) => a.classList.remove("active"));
    const link = headingLinks.get(id);
    if (!link) return;
    link.classList.add("active");
    const sub = link.closest("ul")?.closest("li");
    if (sub) sub.classList.add("open");
    if (tocFollows) revealInToc(link);
  }

  /* While a click-initiated scroll is under way the clicked entry is on screen anyway,
     so the sidebar stays put until the page has settled. */
  let tocFollows = true;
  let settle = 0;
  function pauseTocFollow(ms) {
    tocFollows = false;
    clearTimeout(settle);
    settle = setTimeout(() => { tocFollows = true; }, ms);
  }

  function updateSpy() {
    const scrollable = document.documentElement.scrollHeight - window.innerHeight;
    let current;
    if (scrollable > 0 && window.scrollY >= scrollable - 2) {
      current = targets[targets.length - 1];  // the last heading never reaches the probe line
    } else {
      const line = 110;                       // probe line, just below the sticky top bar
      current = targets[0];
      for (const t of targets) {
        if (t.getBoundingClientRect().top <= line) current = t; else break;
      }
    }
    if (current) setActive(current.id);
    const pct = scrollable > 0 ? (window.scrollY / scrollable) * 100 : 0;
    $("#progress").style.width = Math.max(0, Math.min(100, pct)) + "%";
    $("#back-top").classList.toggle("show", window.scrollY > 800);
  }

  let spyQueued = false;
  function requestSpy() {
    if (spyQueued) return;
    spyQueued = true;
    requestAnimationFrame(() => { spyQueued = false; updateSpy(); });
  }
  window.addEventListener("scroll", () => {
    requestSpy();
    if (!tocFollows) pauseTocFollow(150);   // re-arm: the page is still moving
  }, { passive: true });
  window.addEventListener("resize", requestSpy);
  updateSpy();

  /* expand / collapse all */
  $("#toc-expand")?.addEventListener("click", () => {
    const lis = $$("#toc-nav > ul > li");
    const anyClosed = lis.some((li) => $("ul", li) && !li.classList.contains("open"));
    lis.forEach((li) => li.classList.toggle("open", anyClosed));
  });

  /* filter */
  const filter = $("#toc-filter");
  const empty = $("#toc-empty");
  filter?.addEventListener("input", () => {
    const q = filter.value.trim().toLowerCase();
    let visible = 0;
    $$("#toc-nav > ul > li").forEach((li) => {
      const top = $(".toc-group > a", li);
      const subs = $$("ul li", li);
      let anySub = false;
      subs.forEach((s) => {
        const hit = !q || s.textContent.toLowerCase().includes(q);
        s.classList.toggle("hidden", !hit);
        if (hit) anySub = true;
      });
      const topHit = !q || top.textContent.toLowerCase().includes(q);
      const show = topHit || anySub;
      li.classList.toggle("hidden", !show);
      if (q) li.classList.toggle("open", anySub);
      if (show) visible++;
    });
    empty.style.display = visible ? "none" : "block";
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "/" && !e.metaKey && !e.ctrlKey && !/input|textarea|select/i.test(document.activeElement?.tagName || "")) {
      e.preventDefault();
      document.body.classList.add("toc-open");
      filter?.focus();
    }
    if (e.key === "Escape") {
      document.body.classList.remove("toc-open");
      $(".lightbox.open")?.classList.remove("open");
    }
  });

  /* mobile drawer */
  $("#menu-btn")?.addEventListener("click", () => document.body.classList.toggle("toc-open"));
  $("#toc-backdrop")?.addEventListener("click", () => document.body.classList.remove("toc-open"));
  tocNav.addEventListener("click", (e) => {
    if (!e.target.closest("a")) return;
    document.body.classList.remove("toc-open");
    pauseTocFollow(1200);                   // fallback in case the click scrolls nowhere
  });
  $("#back-top")?.addEventListener("click", () => window.scrollTo({ top: 0, behavior: "smooth" }));

  /* ---------- code blocks: header + copy ---------- */
  $$("pre").forEach((pre) => {
    if (pre.closest(".code")) return;
    const code = $("code", pre);
    const lang = (code?.className.match(/language-([\w-]+)/) || [])[1] || "";
    const wrap = document.createElement("div");
    wrap.className = "code";
    const head = document.createElement("div");
    head.className = "code-header";
    const title = pre.dataset.title;
    head.innerHTML = `<span class="${title ? "title" : ""}">${title || lang || "code"}</span>`;
    const btn = document.createElement("button");
    btn.className = "copy-btn";
    btn.type = "button";
    btn.textContent = "Copy";
    btn.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(code ? code.textContent : pre.textContent);
        btn.textContent = "Copied";
        btn.classList.add("ok");
        setTimeout(() => { btn.textContent = "Copy"; btn.classList.remove("ok"); }, 1400);
      } catch (err) {
        btn.textContent = "Select & copy";
      }
    });
    head.appendChild(btn);
    pre.parentNode.insertBefore(wrap, pre);
    wrap.appendChild(head);
    wrap.appendChild(pre);
  });
  if (window.hljs) {
    $$("pre code[class*='language-']").forEach((el) => window.hljs.highlightElement(el));
  }

  /* ---------- click-to-load embeds ----------
     Third-party tools are only fetched once the reader asks for them, so opening this page
     does not pull in another site for everyone who scrolls past. */
  $$(".embed-facade[data-src]").forEach((facade) => {
    $("button", facade)?.addEventListener("click", () => {
      const frame = document.createElement("iframe");
      frame.className = "embed-frame";
      frame.src = facade.dataset.src;
      frame.title = facade.dataset.frameTitle || "Embedded tool";
      frame.referrerPolicy = "no-referrer";
      frame.allow = "clipboard-write";      // the embedded tool's copy button needs this
      facade.replaceWith(frame);
    });
  });

  /* ---------- tabs ---------- */
  $$(".tabs").forEach((tabs) => {
    const buttons = $$(".tab-list button", tabs);
    const panels = $$(":scope > .tab-panel", tabs);
    function select(name) {
      buttons.forEach((b) => b.setAttribute("aria-selected", String(b.dataset.tab === name)));
      panels.forEach((p) => { if (p.dataset.tab === name) p.setAttribute("data-active", ""); else p.removeAttribute("data-active"); });
    }
    buttons.forEach((b) => b.addEventListener("click", () => select(b.dataset.tab)));
    select(buttons[0]?.dataset.tab);
  });

  /* ---------- lightbox ---------- */
  const lb = $("#lightbox");
  $$("figure.shot img").forEach((img) => {
    img.addEventListener("click", () => {
      $("img", lb).src = img.src;
      $("img", lb).alt = img.alt;
      lb.classList.add("open");
    });
  });
  lb?.addEventListener("click", () => lb.classList.remove("open"));

  /* ---------- demo: generator groups + pagination ---------- */
  const ggSource = $("#gg-source");
  const ggTemplate = $("#gg-template");
  const ggGroup = $("#gg-group");
  const ggOut = $("#gg-out");
  const ggError = $("#gg-error");
  const pgSelect = $("#pg-select");
  const pgInd = $("#pg-ind");
  const pgTitle = $("#pg-title");
  const pgSvg = $("#pg-svg");
  let ggIds = [];
  let ggKeys = [];
  let ggPage = 0;

  // Mirrors Pipeline.resolveExpandedGeneratorId / DataService.expandIds on the server.
  function expandIds(template, keys) {
    const used = new Set();
    const ids = [];
    const base = template.trim() || "Generator";
    keys.forEach((key, i) => {
      const safe = key === "" ? String(i) : key;
      let candidate = base.includes("@ID@") ? base.replace("@ID@", safe) : base + "_" + safe;
      if (used.has(candidate)) {
        let n = 0;
        do {
          candidate = base.includes("@ID@") ? base.replace("@ID@", String(n)) : base + "_" + n;
          n++;
        } while (used.has(candidate));
      }
      used.add(candidate);
      ids.push(candidate);
    });
    return ids;
  }

  function hashColor(str) {
    let h = 0;
    for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
    return `hsl(${h % 360} 60% 50%)`;
  }

  function renderPage() {
    if (!pgSvg) return;
    const total = ggIds.length;
    if (!total) {
      pgSvg.innerHTML = "";
      pgInd.textContent = "0 / 0";
      pgTitle.textContent = "no data";
      return;
    }
    ggPage = ((ggPage % total) + total) % total;
    const id = ggIds[ggPage];
    const key = ggKeys[ggPage];
    pgInd.textContent = `${ggPage + 1} / ${total}`;
    pgTitle.textContent = id;
    pgSelect.value = String(ggPage);
    let seed = 0;
    for (const ch of key) seed = (seed * 131 + ch.charCodeAt(0)) >>> 0;
    const bars = [];
    for (let i = 0; i < 6; i++) {
      seed = (seed * 1103515245 + 12345) >>> 0;
      bars.push(20 + (seed % 100));
    }
    const w = 300, h = 140, pad = 20, bw = (w - pad * 2) / bars.length;
    const color = hashColor(key);
    pgSvg.setAttribute("viewBox", `0 0 ${w} ${h}`);
    pgSvg.innerHTML =
      `<line x1="${pad}" y1="${h - pad}" x2="${w - pad}" y2="${h - pad}" stroke="currentColor" opacity=".4"/>` +
      bars.map((v, i) => {
        const bh = ((h - pad * 2) * v) / 120;
        return `<rect x="${pad + i * bw + 4}" y="${h - pad - bh}" width="${bw - 8}" height="${bh}" rx="2" fill="${color}" opacity="${0.55 + i * 0.07}"><title>${key}: ${v}</title></rect>`;
      }).join("") +
      `<text x="${pad}" y="14" font-size="10" fill="currentColor" opacity=".7" font-family="monospace">sub-source "${key}"</text>`;
  }

  function updateGg() {
    if (!ggSource) return;
    ggError.textContent = "";
    let keys = [];
    try {
      const parsed = JSON.parse(ggSource.value);
      if (Array.isArray(parsed) || parsed === null || typeof parsed !== "object") {
        throw new Error("A grouped source must be a JSON object; its top-level keys become the sub-sources.");
      }
      keys = Object.keys(parsed);
    } catch (e) {
      ggError.textContent = e.message;
    }
    const grouped = ggGroup.checked;
    const template = ggTemplate.value;
    ggIds = grouped ? expandIds(template, keys) : [template.trim() || "Generator"];
    ggKeys = grouped ? keys : ["(whole file)"];
    ggOut.innerHTML =
      `<div class="k">${grouped ? `Expanded generators (${ggIds.length})` : "Generators (1, not grouped)"}</div>` +
      `<div class="chips">${ggIds.map((id, i) => `<span class="chip${i === ggPage ? " active" : ""}" data-i="${i}">${id}</span>`).join("")}</div>` +
      `<div class="k" style="margin-top:.6rem">Widget referencing <code>${(template.trim() || "Generator")}</code></div>` +
      `<div>${grouped ? `gets <strong>${ggIds.length} page${ggIds.length === 1 ? "" : "s"}</strong> (<code>meta.total = ${ggIds.length}</code>); a bulk export writes ${ggIds.length} file${ggIds.length === 1 ? "" : "s"}` : "shows one dataset built from the whole file; no pager appears"}</div>`;
    pgSelect.innerHTML = ggIds.map((id, i) => `<option value="${i}">${id}</option>`).join("");
    if (ggPage >= ggIds.length) ggPage = 0;
    renderPage();
    $$(".chip", ggOut).forEach((c) => c.addEventListener("click", () => { ggPage = +c.dataset.i; updateGg(); }));
  }
  if (ggSource) {
    [ggSource, ggTemplate, ggGroup].forEach((el) => el.addEventListener("input", () => { ggPage = 0; updateGg(); }));
    $("#pg-prev")?.addEventListener("click", () => { ggPage--; updateGg(); });
    $("#pg-next")?.addEventListener("click", () => { ggPage++; updateGg(); });
    pgSelect?.addEventListener("change", () => { ggPage = +pgSelect.value; updateGg(); });
    updateGg();
  }

  /* ---------- demo: batch request builder ---------- */
  const bb = {
    base: $("#bb-base"), scope: $("#bb-scope"), pipeline: $("#bb-pipeline"), format: $("#bb-format"),
    bulk: $("#bb-bulk"), selector: $("#bb-selector"), id: $("#bb-id"), gen: $("#bb-gen"), type: $("#bb-type"),
    method: $("#bb-method"), out: $("#bb-out"), expect: $("#bb-expect"), selWrap: $("#bb-selector-wrap"),
  };
  function updateBb() {
    if (!bb.base) return;
    const base = (bb.base.value || "http://localhost:8080").replace(/\/+$/, "");
    const pipeline = bb.pipeline.value || "<pipelineId>";
    const format = bb.format.value;
    const bulk = bb.bulk.checked;
    const scope = bb.scope.value;
    const outName = pipeline.replace(/[^a-zA-Z0-9._-]/g, "_");
    let cmd, expect;
    bb.selWrap.style.display = scope === "widget" ? "" : "none";
    bb.method.parentElement.style.display = scope === "pipeline" ? "" : "none";
    if (scope === "widget") {
      const sel = bb.selector.value;
      let widget;
      if (sel === "id") widget = `{ "id": "${bb.id.value || "BarChart-cspd570"}" }`;
      else if (sel === "gen-type") widget = `{ "generatorId": "${bb.gen.value || "CategoryNumber-eky02g0"}", "type": "${bb.type.value || "BarChart"}" }`;
      else widget = `{ "generatorId": "${bb.gen.value || "CategoryNumber-eky02g0"}" }`;
      cmd = `curl -X POST "${base}/api/batch/export/${format}" \\\n  -H "Content-Type: application/json" \\\n  -d '{ "pipeline": "${pipeline}", "bulk": ${bulk}, "widget": ${widget} }' \\\n  -o widget-export.${bulk ? "zip" : format}`;
      expect = bulk
        ? `One file when the widget has a single page, otherwise a ZIP <code>&lt;title&gt;-export.zip</code> with one entry per page (<code>000-…</code>, <code>001-…</code>). HTTP 500 with the widget's error message when the export fails (for example <code>${format}</code> on a widget that renders no SVG).`
        : `The exported file itself (<code>Content-Disposition: attachment</code>), MIME type of the format. Only the widget's first page is exported.`;
    } else if (bb.method.value === "get") {
      cmd = `curl "${base}/api/batch/export/pipeline/${encodeURIComponent(pipeline)}/${format}?bulk=${bulk}" \\\n  -o ${outName}-${format}-exports.zip`;
      expect = pipelineExpect(outName, format, bulk);
    } else {
      cmd = `curl -X POST "${base}/api/batch/export/pipeline/${format}" \\\n  -H "Content-Type: application/json" \\\n  -d '{ "pipeline": "${pipeline}", "bulk": ${bulk} }' \\\n  -o ${outName}-${format}-exports.zip`;
      expect = pipelineExpect(outName, format, bulk);
    }
    bb.out.textContent = cmd;
    bb.expect.innerHTML = expect;
  }
  function pipelineExpect(name, format, bulk) {
    return `ZIP <code>${name}-${format}-exports.zip</code>: one entry per generator-backed widget${bulk ? " and per page of grouped widgets" : ""} named <code>NNN-&lt;file&gt;</code> in widget order, plus <code>_summary.json</code> (pipeline, exported, failed, generatedAt)${format === "svg" || format === "png" ? " and <code>_errors.json</code> listing widgets that render no SVG (Table, Highlight Text)" : " and, if anything failed, <code>_errors.json</code>"}. With <code>EXPORT_METRICS=true</code> the response also carries the <code>X-UDAV-Export-Metrics</code> header.`;
  }
  if (bb.base) {
    Object.values(bb).forEach((el) => {
      if (!el || !/^(INPUT|SELECT|TEXTAREA)$/.test(el.tagName)) return;
      el.addEventListener(el.type === "checkbox" || el.tagName === "SELECT" ? "change" : "input", updateBb);
    });
    updateBb();
  }

  /* ---------- deep link ----------
     The browser's own jump to the fragment happens while the page is still growing — code
     blocks get their header, images arrive without declared dimensions — so by the time the
     layout is final the target has moved. Land on it again once things have settled, unless
     the reader has taken over in the meantime. */
  if (location.hash) {
    const id = decodeURIComponent(location.hash.slice(1));
    const target = document.getElementById(id);
    if (target && headingLinks.has(id)) {
      let userMoved = false;
      ["wheel", "touchstart", "keydown"].forEach((type) =>
        window.addEventListener(type, () => { userMoved = true; }, { once: true, passive: true }));
      const land = () => {
        if (userMoved) return;
        pauseTocFollow(1500);
        target.scrollIntoView();            // honours scroll-padding-top; no ancestor scrolls
        setActive(id);
      };
      land();
      window.addEventListener("load", land, { once: true });
    }
  }
})();
