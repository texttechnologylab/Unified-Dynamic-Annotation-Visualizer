package org.texttechnologylab.udav.api.export;

/**
 * JavaScript injected into the rendered view to drive exports.
 *
 * <p>{@link #INSTALL} is installed once per page and defines {@code globalThis.__UDAV_EXPORT__},
 * so the source crosses CDP and is parsed once per page rather than once per widget.
 *
 * <p>The driver reuses the widgets' own {@code ExportHandler} logic verbatim - it only swaps the
 * two download sinks for collectors, so the API and the UI produce identical artefacts.
 */
public final class ExportScripts {

    private ExportScripts() {
    }

    public static final String INSTALL = """
            (() => {
              const TEXT_TYPES = new Set(["svg", "tex", "csv", "json"]);

              async function toPayload(blob, name, format) {
                // svg/tex/csv/json blobs are built from JS strings, so they can cross the CDP
                // boundary as text. Base64 would inflate them by a third and add a FileReader hop.
                if (TEXT_TYPES.has(format)) {
                  return {
                    name,
                    contentType: blob.type || "application/octet-stream",
                    encoding: "utf8",
                    data: await blob.text(),
                  };
                }

                const buffer = new Uint8Array(await blob.arrayBuffer());
                let binary = "";
                const CHUNK = 8192;
                for (let i = 0; i < buffer.length; i += CHUNK) {
                  binary += String.fromCharCode.apply(null, buffer.subarray(i, i + CHUNK));
                }
                return {
                  name,
                  contentType: blob.type || "application/octet-stream",
                  encoding: "base64",
                  data: btoa(binary),
                };
              }

              function selectTarget(charts, selector) {
                let target = null;
                if (selector && selector.id) {
                  target = charts.find((chart) => chart && chart.config && chart.config.id === selector.id) || null;
                }
                if (!target && selector && selector.generatorId && selector.type) {
                  target = charts.find((chart) => {
                    const config = chart && chart.config;
                    return config && config.generator && config.generator.id === selector.generatorId
                      && config.type === selector.type;
                  }) || null;
                }
                if (!target && selector && selector.generatorId) {
                  target = charts.find((chart) => {
                    const config = chart && chart.config;
                    return config && config.generator && config.generator.id === selector.generatorId;
                  }) || null;
                }
                return target;
              }

              function describe(chart) {
                const config = (chart && chart.config) || {};
                return {
                  id: config.id || null,
                  type: config.type || null,
                  title: config.title || null,
                  generatorId: (config.generator && config.generator.id) || null,
                };
              }

              function exportChart(chart, index, format, bulk, timeoutMs, emit) {
                return new Promise((resolve) => {
                  let settled = false;
                  const handler = chart.exports;
                  if (!handler || typeof handler.startExport !== "function") {
                    resolve({ ok: false, error: "Widget export handler is not available." });
                    return;
                  }

                  const originalSingle = handler.downloadSingleBlob;
                  const originalMany = handler.downloadBlobs;
                  const restore = () => {
                    handler.downloadSingleBlob = originalSingle;
                    handler.downloadBlobs = originalMany;
                  };

                  const timer = setTimeout(() => {
                    if (!settled) {
                      settled = true;
                      restore();
                      resolve({ ok: false, error: "Export timeout after " + timeoutMs + " ms." });
                    }
                  }, timeoutMs);

                  const finish = (fileCount, error) => {
                    if (settled) return;
                    settled = true;
                    clearTimeout(timer);
                    restore();
                    resolve(error ? { ok: false, error } : { ok: true, fileCount });
                  };

                  // Stream each artefact out as it is produced rather than returning them all in
                  // one evaluate result: a bulk PNG export of a large pipeline would otherwise
                  // exist as a wire buffer, a parsed map and decoded bytes all at once.
                  const send = async (blob, name, fileIndex) => {
                    const payload = await toPayload(blob, name, format);
                    payload.index = index;
                    payload.fileIndex = fileIndex;
                    await emit(payload);
                  };

                  handler.downloadSingleBlob = async (blob, name) => {
                    try {
                      await send(blob, name || (handler.filename + "." + format), 0);
                      finish(1, null);
                    } catch (error) {
                      finish(0, String(error));
                    }
                  };

                  handler.downloadBlobs = async (blobs, type) => {
                    try {
                      for (let i = 0; i < blobs.length; i++) {
                        const name = handler.filename + "-" + String(i).padStart(3, "0") + "." + type;
                        await send(blobs[i], name, i);
                      }
                      finish(blobs.length, null);
                    } catch (error) {
                      finish(0, String(error));
                    }
                  };

                  try {
                    const started = handler.startExport(format, bulk);
                    if (started && typeof started.then === "function") {
                      // startExport returns its promise, so an async failure surfaces here
                      // instead of leaving this promise pending until the timeout fires.
                      started.catch((error) => finish(0, String((error && error.message) || error)));
                    }
                  } catch (error) {
                    finish(0, String(error));
                  }
                });
              }

              async function runAll({ format, bulk, selectors, concurrency, timeoutMs }) {
                const state = globalThis.__UDAV_VIEW_STATE__ || {};
                const charts = state.charts || [];
                const emit = globalThis.__udavEmitFile;
                const entries = new Array(selectors.length);
                const origin = performance.now();

                // Concurrency is safe across charts (each owns its own ExportHandler, and
                // D3Visualization.export only swaps its own svg/data) but NOT within one chart:
                // two overlapping exports would clobber each other's patched download sinks and
                // neither would settle. Selectors can name the same chart twice, so serialise
                // per chart while still overlapping different ones.
                const inFlight = new WeakMap();
                const exclusive = (chart, task) => {
                  const previous = inFlight.get(chart) || Promise.resolve();
                  const next = previous.then(task, task);
                  inFlight.set(chart, next.catch(() => {}));
                  return next;
                };

                let cursor = 0;
                const worker = async () => {
                  while (true) {
                    const index = cursor++;
                    if (index >= selectors.length) return;

                    const selector = selectors[index];
                    const startMs = performance.now() - origin;
                    const target = selectTarget(charts, selector);

                    let result;
                    if (!target) {
                      result = { ok: false, error: "Widget not found or not generator-backed in view state." };
                    } else if ((format === "svg" || format === "png") && !target.svg) {
                      // Same predicate ExportHandler.init() uses to decide whether to offer the
                      // svg/png buttons. Without it these widgets stall until the timeout.
                      const type = (target.config && target.config.type) || "unknown";
                      result = {
                        ok: false,
                        error: "Widget type '" + type + "' does not support " + format
                          + " export (widget renders no SVG).",
                      };
                    } else {
                      result = await exclusive(target, () =>
                        exportChart(target, index, format, bulk, timeoutMs, emit));
                    }

                    entries[index] = Object.assign({
                      index,
                      startMs,
                      endMs: performance.now() - origin,
                      widget: target ? describe(target) : selector,
                    }, result);
                  }
                };

                const workers = [];
                const width = Math.max(1, Math.min(concurrency || 1, selectors.length || 1));
                for (let i = 0; i < width; i++) workers.push(worker());
                await Promise.all(workers);

                return { entries };
              }

              globalThis.__UDAV_EXPORT__ = { runAll };
            })();
            """;

    public static final String RUN_ALL = "(args) => globalThis.__UDAV_EXPORT__.runAll(args)";

    public static final String READY_PREDICATE = "() => globalThis.__UDAV_EXPORT_READY === true";
}
