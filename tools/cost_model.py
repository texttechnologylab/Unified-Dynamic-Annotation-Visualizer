#!/usr/bin/env python3
# Recomputes the paper's evaluation numbers from the raw data of one or more reports:
#
#   tools/cost_model.py evaluation/evaluation-report-2026-10-31-run*.txt
#   tools/cost_model.py evaluation/evaluation-report-2026-08-26.txt
#
# Reads the JSON of section 7 of every report and pools the runs of each condition (several
# reports are the JVM runs of run-evaluation.sh --jvm-runs). Prints the rows of the workload
# table, then per format the OLS fit t = a + b*W + c*U over the per-pipeline medians with 95%
# bootstrap intervals for b and c (percentile method, resampling pipelines with a fixed seed),
# the adjusted R^2 and the artefact coefficient c of the same fit for server CPU, browser CPU,
# allocation and archive size, as rows of the cost-model table. Below that the stage fits, the
# browser session setup percentiles, the IQR summary, the stage gap, the baseline and every
# other number the evaluation text quotes. Standard library only.

import json
import math
from decimal import ROUND_HALF_UP, Decimal
import random
import re
import statistics
import sys

FORMATS = ["svg", "png", "json", "csv", "tex"]
RESAMPLES = 100000
SEED = 20260826


def load_raw(path):
    text = open(path, encoding="utf-8").read()
    start = text.index("## 7. RAW DATA (JSON)")
    raw = json.loads(text[text.index("{", start):])
    issued = re.search(r"Total exports issued: (\d+)", text)
    raw["exportsIssued"] = int(issued.group(1)) if issued else None
    return raw


def pool(raws):
    # runs of the same condition are concatenated; descriptors come from the first report
    merged = {"workload": raws[0]["workload"], "samples": {}, "baseline": {}, "pagination": []}
    for raw in raws:
        for key, runs in raw["samples"].items():
            merged["samples"].setdefault(key, []).extend(runs)
        for key, value in raw.get("baseline", {}).items():
            merged["baseline"].setdefault(key, {"individualMs": [], "batchMs": []})
            merged["baseline"][key]["individualMs"].append(value["individualMs"])
            merged["baseline"][key]["batchMs"].append(value["batchMs"])
        slope = raw.get("paginationSlopeMsPerFile")
        if slope is not None and not math.isnan(slope):
            merged["pagination"].append(slope)
    return merged


def percentile(sorted_values, p):
    # nearest rank, rounding half up like the harness (Python's round() would round 10.5 to 10)
    index = int(math.floor(p * (len(sorted_values) - 1) + 0.5))
    return sorted_values[max(0, min(len(sorted_values) - 1, index))]


def half_up(value, digits):
    # the medians are whole milliseconds, so ties like 0.625 s are common; float formatting
    # would round them to even or down
    return str(Decimal(str(value)).quantize(Decimal(1).scaleb(-digits), rounding=ROUND_HALF_UP))


def ols2(rows):
    # rows of (x1, x2, y); returns (a, b, c) or None when singular
    n = len(rows)
    sx1 = sx2 = sy = s11 = s22 = s12 = s1y = s2y = 0.0
    for x1, x2, y in rows:
        sx1 += x1; sx2 += x2; sy += y
        s11 += x1 * x1; s22 += x2 * x2; s12 += x1 * x2
        s1y += x1 * y; s2y += x2 * y
    a = [[n, sx1, sx2, sy], [sx1, s11, s12, s1y], [sx2, s12, s22, s2y]]
    for i in range(3):
        piv = max(range(i, 3), key=lambda r: abs(a[r][i]))
        a[i], a[piv] = a[piv], a[i]
        if abs(a[i][i]) < 1e-9:
            return None
        for r in range(3):
            if r != i:
                f = a[r][i] / a[i][i]
                for c in range(i, 4):
                    a[r][c] -= f * a[i][c]
    return tuple(a[i][3] / a[i][i] for i in range(3))


def adjusted_r2(rows, fit):
    a, b, c = fit
    mean_y = statistics.mean(y for _, _, y in rows)
    ss_res = sum((y - (a + b * x1 + c * x2)) ** 2 for x1, x2, y in rows)
    ss_tot = sum((y - mean_y) ** 2 for _, _, y in rows)
    n, k = len(rows), 2
    return 1 - (1 - (1 - ss_res / ss_tot)) * (n - 1) / (n - k - 1)


def bootstrap(rows, rng):
    bs, cs = [], []
    for _ in range(RESAMPLES):
        sample = [rows[rng.randrange(len(rows))] for _ in rows]
        fit = ols2(sample)
        if fit is None:
            continue
        bs.append(fit[1])
        cs.append(fit[2])
    bs.sort(); cs.sort()
    if not bs:
        nan = float("nan")
        return (nan, nan), (nan, nan)
    return (percentile(bs, 0.025), percentile(bs, 0.975)), (percentile(cs, 0.025), percentile(cs, 0.975))


def pearson(xs, ys):
    mx, my = statistics.mean(xs), statistics.mean(ys)
    cov = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    return cov / math.sqrt(sum((x - mx) ** 2 for x in xs) * sum((y - my) ** 2 for y in ys))


def size_class(w, u):
    if w > 30 or u > 150:
        return "large"
    if w <= 10 and u <= 25:
        return "small"
    return "medium"


def main(paths):
    raws = [load_raw(p) for p in paths]
    raw = pool(raws)
    samples = raw["samples"]
    workload = raw["workload"]
    pipelines = sorted(workload, key=lambda p: int(p[1:]))
    rng = random.Random(SEED)

    def runs(pipeline, fmt):
        return samples[pipeline + "|" + fmt]

    def median_of(pipeline, fmt, key):
        return statistics.median(r[key] for r in runs(pipeline, fmt))

    runs_per_condition = sorted({len(v) for v in samples.values()})
    print("%d report(s), %s runs per condition" % (len(raws), "/".join(str(n) for n in runs_per_condition)))

    print("\nworkload table: pipeline, class, W, U (svg/png), median export time in s per format")
    for p in pipelines:
        w, u = workload[p]["W"], workload[p]["U"]
        u_svg = int(median_of(p, "svg", "filesProduced"))
        times = " & ".join(half_up(median_of(p, fmt, "wallMs") / 1000, 2) for fmt in FORMATS)
        print("%-3s & %-6s & %2d & %3d (%d) & %s \\\\" % (p, size_class(w, u), w, u, u_svg, times))

    if len(pipelines) < 4:
        print("\n%d pipelines: the cost model needs at least four, so it is skipped (a reduced test run?)" % len(pipelines))
        pipelines_for_model = []
    else:
        pipelines_for_model = pipelines
        print("\ncost model t = a + b*W + c*U, 95%% bootstrap intervals (%d resamples, seed %d)" % (RESAMPLES, SEED))
        print("Format & a & b (ms/widget) & c (ms/artefact) & adj. R^2 & server CPU (ms) & browser CPU (ms) & Alloc. (MiB) & ZIP (KiB)")
        print("(resource columns: c of the same fit applied to each resource; the b line below each row is its per-widget term)")
    fits, resource_c = {}, {}
    for fmt in FORMATS if pipelines_for_model else []:
        rows = [(workload[p]["W"], median_of(p, fmt, "filesProduced"), median_of(p, fmt, "wallMs"))
                for p in pipelines]
        fit = ols2(rows)
        fits[fmt] = fit
        (b_lo, b_hi), (c_lo, c_hi) = bootstrap(rows, rng)
        cells, per_widget = [], []
        for key, scale, digits in (("jvmCpuMs", 1, 1), ("browserCpuMs", 1, 1),
                                   ("allocatedBytes", 2 ** 20, 2), ("zipBytes", 1024, 1)):
            points = [(w, u, median_of(p, fmt, key) / scale) for (w, u, _), p in zip(rows, pipelines)]
            # -1 in the raw data means the browser CPU could not be measured
            if all(y < 0 for _, _, y in points):
                cells.append("n/a")
                per_widget.append("n/a")
                continue
            _, rb, rc = ols2(points)
            resource_c[fmt, key] = rc
            cells.append("%.*f" % (digits, rc))
            per_widget.append("%.*f" % (digits, rb))
        print("%-4s & %3.0f & %.1f\\ \\ [%.1f, %.1f] & %.2f\\ [%.1f, %.1f] & %.3f & %s \\\\" % (
            fmt.upper(), fit[0], fit[1], b_lo, b_hi, fit[2], c_lo, c_hi, adjusted_r2(rows, fit),
            " & ".join(cells)))
        print("       per widget (b): server CPU %s ms, browser CPU %s ms, alloc. %s MiB, ZIP %s KiB" % tuple(per_widget))

    if pipelines_for_model:
        others = [f for f in FORMATS if f != "tex"]
        for key, name in (("jvmCpuMs", "server CPU"), ("allocatedBytes", "allocation")):
            ratios = [resource_c["tex", key] / resource_c[f, key] for f in others]
            print("tex %s per artefact: %.1f to %.1f times the other formats" % (name, min(ratios), max(ratios)))
        deep = max(pipelines, key=lambda p: workload[p]["U"])
        wide = max(pipelines, key=lambda p: workload[p]["W"])
        a, b, c = fits["svg"]
        for p in (deep, wide):
            u = median_of(p, "svg", "filesProduced")
            print("svg %s: widgets b*W = %.2f s, artefacts c*U = %.2f s, measured %s s" % (
                p, b * workload[p]["W"] / 1000, c * u / 1000, half_up(median_of(p, "svg", "wallMs") / 1000, 2)))
        print("svg artefacts %s / %s: %.2f" % (deep, wide, median_of(deep, "svg", "filesProduced") / median_of(wide, "svg", "filesProduced")))

        ws = [workload[p]["W"] for p in pipelines]
        us = [workload[p]["U"] for p in pipelines]
        r = pearson(ws, us)
        print("\nr(W, U) = %.2f, VIF = %.1f" % (r, 1 / (1 - r * r)))

        view = []
        for p in pipelines:
            pooled = [run["pageReadyMs"] for fmt in FORMATS for run in runs(p, fmt)]
            view.append((workload[p]["W"], workload[p]["U"], statistics.median(pooled)))
        a, b, c = ols2(view)
        print("view init, runs pooled across formats: a = %.0f ms, b = %.2f ms/widget, c = %.2f ms/artefact"
              % (a, b, c))
        print("  view init per pipeline (ms): " + ", ".join("%s %.0f" % (p, v[2]) for p, v in zip(pipelines, view)))
        for stage in ("browserInitMs", "pageReadyMs", "exportPhaseMs"):
            parts = []
            for fmt in FORMATS:
                sa, sb, sc = ols2([(workload[p]["W"], median_of(p, fmt, "filesProduced"), median_of(p, fmt, stage))
                                   for p in pipelines])
                parts.append("%s a=%.0f b=%.2f c=%.2f" % (fmt, sa, sb, sc))
            print("  %s per format: %s" % (stage, "; ".join(parts)))
        smallest, deepest = pipelines[0], max(pipelines, key=lambda p: workload[p]["U"])
        print("  share of the intercept a in the median request: %s %s, %s %s" % (
            smallest, " ".join("%s %.0f%%" % (f, 100 * fits[f][0] / median_of(smallest, f, "wallMs")) for f in FORMATS),
            deepest, " ".join("%s %.0f%%" % (f, 100 * fits[f][0] / median_of(deepest, f, "wallMs")) for f in FORMATS)))

    browser = sorted(run["browserInitMs"] for key in samples for run in samples[key])
    print("browser session setup: median %.0f ms, p10 %.0f, p90 %.0f" % (
        statistics.median(browser), percentile(browser, 0.10), percentile(browser, 0.90)))

    browser_cpu = [run["browserCpuMs"] for key in samples for run in samples[key]]
    if all(v < 0 for v in browser_cpu):
        print("browser CPU: not measured (every run reports -1)")
    else:
        print("browser CPU per request: median %.0f ms" % statistics.median(v for v in browser_cpu if v >= 0))

    ratios = []
    for key, rs in samples.items():
        wall = sorted(run["wallMs"] for run in rs)
        iqr = percentile(wall, 0.75) - percentile(wall, 0.25)
        ratios.append((iqr / statistics.median(wall), key))
    ratios.sort()
    print("IQR / median within a condition: median %.1f%%, max %.1f%% (%s)" % (
        100 * statistics.median(v for v, _ in ratios), 100 * ratios[-1][0], ratios[-1][1]))

    gaps = [run["wallMs"] - run["browserInitMs"] - run["pageReadyMs"] - run["exportPhaseMs"]
            for key in samples for run in samples[key]]
    print("wall time minus the three stages: median %.0f ms, min %d, max %d" % (
        statistics.median(gaps), min(gaps), max(gaps)))
    per_pipeline_gap = [statistics.median(run["wallMs"] - run["browserInitMs"] - run["pageReadyMs"] - run["exportPhaseMs"]
                                          for fmt in FORMATS for run in runs(p, fmt)) for p in pipelines]
    print("  per pipeline median: %.0f to %.0f ms" % (min(per_pipeline_gap), max(per_pipeline_gap)))

    if raw["baseline"]:
        print("\nbatch versus W single-widget requests, medians over reports (s):")
        # the harness takes the first pipeline of each size class
        first_of_class = {}
        for p in pipelines:
            first_of_class.setdefault(size_class(workload[p]["W"], workload[p]["U"]), p)
        excess = []
        for key, value in raw["baseline"].items():
            size, fmt = key.split("|")
            p = first_of_class[size]
            w = workload[p]["W"]
            individual = statistics.median(value["individualMs"])
            batch = statistics.median(value["batchMs"])
            excess.append(batch - median_of(p, fmt, "wallMs"))
            line = "  %-12s %-3s W=%2d batch %.2f, individual %.2f, factor %.1f, individual per request %.0f ms" % (
                key, p, w, batch / 1000, individual / 1000, individual / batch, individual / w)
            if fmt in fits:
                a, b, c = fits[fmt]
                # an endpoint that initialised only its own widget would still pay a per request
                ideal = w * (a + b) + c * median_of(p, fmt, "filesProduced")
                line += ", ideal single-widget endpoint %.1f s (%.1fx the batch)" % (ideal / 1000, ideal / batch)
            print(line)
        print("  client batch time minus the workload-table cell: %.0f to %.0f ms" % (min(excess), max(excess)))
        for fmt in sorted({key.split("|")[1] for key in raw["baseline"]}):
            points = [(math.log(workload[first_of_class[key.split("|")[0]]]["W"]),
                       math.log(statistics.median(value["individualMs"])))
                      for key, value in raw["baseline"].items() if key.endswith("|" + fmt)]
            mx = statistics.mean(x for x, _ in points)
            my = statistics.mean(y for _, y in points)
            exponent = sum((x - mx) * (y - my) for x, y in points) / sum((x - mx) ** 2 for x, _ in points)
            print("  individual requests grow as W^%.2f (%s)" % (exponent, fmt))
    if raw["pagination"]:
        print("pagination experiment: %.2f ms per artefact (per report: %s)" % (
            statistics.median(raw["pagination"]), ", ".join("%.2f" % v for v in raw["pagination"])))

    print("\nother numbers in the text")
    issued = [r.get("exportsIssued") for r in raws]
    analysis = sum(len(v) for v in samples.values())
    print("  batch requests issued: %s (%s); analysis runs: %d; conditions per report: %d" % (
        sum(issued) if None not in issued else "?", " + ".join(str(v) for v in issued), analysis, len(samples)))
    if raw["baseline"]:
        replicates = int(raws[0]["environment"].get("harness.analysisRuns", 15))
        single = sum(workload[first_of_class[key.split("|")[0]]]["W"] * replicates
                     for r in raws for key in r.get("baseline", {}))
        print("  single-widget requests of the baseline: %d" % single)
    unsettled = {}
    for r in raws:
        for key in r.get("nonSteady", []):
            unsettled[key] = unsettled.get(key, 0) + 1
    print("  never settled: %s of %d condition runs" % (
        ", ".join("%s (%dx)" % item for item in sorted(unsettled.items())) or "none", len(samples) * len(raws)))
    warmups = int(raws[0]["environment"].get("harness.warmupRuns", 2))
    searches = []
    for r in raws:
        # conditions run back to back, so the run indices between two analysis samples are the
        # next condition's warm-ups and search runs; the first condition of a report is skipped
        spans = sorted((min(x["runIndex"] for x in v), max(x["runIndex"] for x in v)) for v in r["samples"].values())
        searches += [first - previous_last - 1 - warmups for (_, previous_last), (first, _) in zip(spans, spans[1:])]
    # a smoke run (bugHunt) has no steady-state search
    if searches and raws[0]["environment"].get("harness.bugHuntMode") != "true":
        print("  steady-state search: %.1f runs per condition on average" % statistics.mean(searches))
    for fmt in ("svg", "png"):
        refused = sum(run["widgetsFailed"] for p in pipelines for run in runs(p, fmt))
        attempts = sum(workload[p]["W"] * len(runs(p, fmt)) for p in pipelines)
        print("  %s refusals: %d of %d widget exports (%.1f%%)" % (fmt, refused, attempts, 100 * refused / attempts))
    print("  failure bound (rule of three over the analysis runs): %.2f%%" % (300 / analysis))
    spreads = []
    for key in samples:
        medians = [statistics.median(x["wallMs"] for x in r["samples"][key]) for r in raws if key in r["samples"]]
        spreads.append(max(medians) / min(medians) - 1)
    print("  per condition, max/min of the per-report medians: median %.1f%%, max %.1f%%" % (
        100 * statistics.median(spreads), 100 * max(spreads)))
    print("  GC pause per request, median: %s; max %d ms" % (
        ", ".join("%s %.0f ms" % (f, statistics.median(run["gcPauseMs"] for p in pipelines for run in runs(p, f)))
                  for f in FORMATS),
        max(run["gcPauseMs"] for key in samples for run in samples[key])))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: tools/cost_model.py <evaluation-report.txt> [more reports...]")
    main(sys.argv[1:])
