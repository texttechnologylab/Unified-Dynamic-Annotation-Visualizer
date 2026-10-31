#!/usr/bin/env python3
# Recomputes the paper's cost model from the raw data of an evaluation report:
#
#   tools/cost_model.py evaluation/evaluation-report-2026-08-26.txt
#
# Reads the JSON of section 7 and prints, per format, the OLS fit t = a + b*W + c*U over the
# per-pipeline medians with 95% bootstrap intervals for b and c (percentile method, resampling
# pipelines with a fixed seed), the adjusted R^2 and the Theil-Sen slopes of server CPU,
# allocation and archive size per artefact, as rows of the paper's cost-model table. Below that
# the pooled view-init fit, the browser session setup percentiles, the IQR summary and the stage
# gap the paper quotes. Standard library only.

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


def main(path):
    raw = load_raw(path)
    samples = raw["samples"]
    workload = raw["workload"]
    pipelines = sorted(workload, key=lambda p: int(p[1:]))
    rng = random.Random(SEED)

    def runs(pipeline, fmt):
        return samples[pipeline + "|" + fmt]

    def median_of(pipeline, fmt, key):
        return statistics.median(r[key] for r in runs(pipeline, fmt))

    print("cost model t = a + b*W + c*U, 95%% bootstrap intervals (%d resamples, seed %d)" % (RESAMPLES, SEED))
    print("Format & a & b (ms/widget) & c (ms/artefact) & adj. R^2 & CPU (ms) & Alloc. (MiB) & ZIP (KiB)")
    for fmt in FORMATS:
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

    print("pagination experiment: %.2f ms per artefact" % raw.get("paginationSlopeMsPerFile", float("nan")))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: tools/cost_model.py <evaluation-report.txt>")
    main(sys.argv[1])
