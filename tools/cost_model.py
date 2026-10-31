#!/usr/bin/env python3
# Recomputes the paper's evaluation numbers from the raw data of one or more reports:
#
#   tools/cost_model.py evaluation/evaluation-report-2026-08-26.txt
#   tools/cost_model.py evaluation-report-run1.txt evaluation-report-run2.txt ...
#
# Reads the JSON of section 7 of every report and pools the runs of each condition (several
# reports are the JVM runs of run-evaluation.sh --jvm-runs). Prints the rows of the workload
# table, then per format the OLS fit t = a + b*W + c*U over the per-pipeline medians with 95%
# bootstrap intervals for b and c (percentile method, resampling pipelines with a fixed seed),
# the adjusted R^2 and the Theil-Sen slopes of server CPU, allocation and archive size per
# artefact, as rows of the cost-model table. Below that the pooled view-init fit, the browser
# session setup percentiles, the IQR summary and the stage gap the paper quotes. Standard
# library only.

import json
import math
import random
import statistics
import sys

FORMATS = ["svg", "png", "json", "csv", "tex"]
RESAMPLES = 5000
SEED = 20260826


def load_raw(path):
    text = open(path, encoding="utf-8").read()
    start = text.index("## 7. RAW DATA (JSON)")
    return json.loads(text[text.index("{", start):])


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


def theil_sen(points):
    slopes = [(y2 - y1) / (x2 - x1)
              for i, (x1, y1) in enumerate(points)
              for x2, y2 in points[i + 1:] if x2 != x1]
    return statistics.median(slopes)


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
        times = " & ".join("%.2f" % (median_of(p, fmt, "wallMs") / 1000) for fmt in FORMATS)
        print("%-3s & %-6s & %2d & %3d (%d) & %s \\\\" % (p, size_class(w, u), w, u, u_svg, times))

    if len(pipelines) < 4:
        print("\n%d pipelines: the cost model needs at least four, so it is skipped (a reduced test run?)" % len(pipelines))
        pipelines_for_model = []
    else:
        pipelines_for_model = pipelines
        print("\ncost model t = a + b*W + c*U, 95%% bootstrap intervals (%d resamples, seed %d)" % (RESAMPLES, SEED))
        print("Format & a & b (ms/widget) & c (ms/artefact) & adj. R^2 & CPU (ms) & Alloc. (MiB) & ZIP (KiB)")
    for fmt in FORMATS if pipelines_for_model else []:
        rows = []
        cpu, alloc, zipped = [], [], []
        for p in pipelines:
            w = workload[p]["W"]
            u = median_of(p, fmt, "filesProduced")
            rows.append((w, u, median_of(p, fmt, "wallMs")))
            cpu.append((u, median_of(p, fmt, "jvmCpuMs")))
            alloc.append((u, median_of(p, fmt, "allocatedBytes") / 2 ** 20))
            zipped.append((u, median_of(p, fmt, "zipBytes") / 1024))
        fit = ols2(rows)
        (b_lo, b_hi), (c_lo, c_hi) = bootstrap(rows, rng)
        print("%-4s & %3.0f & %.1f\\ \\ [%.1f, %.1f] & %.2f\\ [%.1f, %.1f] & %.3f & %5.2f & %.2f & %4.1f \\\\" % (
            fmt.upper(), fit[0], fit[1], b_lo, b_hi, fit[2], c_lo, c_hi, adjusted_r2(rows, fit),
            theil_sen(cpu), theil_sen(alloc), theil_sen(zipped)))

    if pipelines_for_model:
        ws = [workload[p]["W"] for p in pipelines]
        us = [workload[p]["U"] for p in pipelines]
        r = pearson(ws, us)
        print("\nr(W, U) = %.2f, VIF = %.1f" % (r, 1 / (1 - r * r)))

        view = []
        for p in pipelines:
            pooled = [run["pageReadyMs"] for fmt in FORMATS for run in runs(p, fmt)]
            view.append((workload[p]["W"], workload[p]["U"], statistics.median(pooled)))
        a, b, c = ols2(view)
        print("view init, runs pooled across formats: a = %.0f ms, b = %.2f ms/widget, c = %.2f ms/artefact; %s = %.0f ms, %s = %.0f ms"
              % (a, b, c, pipelines[0], view[0][2], pipelines[-1], view[-1][2]))

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

    if raw["baseline"]:
        print("\nbatch versus W single-widget requests, medians over reports (s):")
        for key, value in raw["baseline"].items():
            individual = statistics.median(value["individualMs"]) / 1000
            batch = statistics.median(value["batchMs"]) / 1000
            print("  %-12s batch %.2f, individual %.2f, factor %.1f" % (key, batch, individual, individual / batch))
    if raw["pagination"]:
        print("pagination experiment: %.2f ms per artefact" % statistics.median(raw["pagination"]))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: tools/cost_model.py <evaluation-report.txt> [more reports...]")
    main(sys.argv[1:])
