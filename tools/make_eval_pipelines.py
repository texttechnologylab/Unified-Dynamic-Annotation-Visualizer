#!/usr/bin/env python3
"""
Generates every fixture the batch-export evaluation needs, from plain JSON only:

  src/main/resources/sourcefilesJSON/eval-src-pagesNN.json        XY_dict.json sliced to NN keys
  src/main/resources/sourcefilesJSON/eval-annotations-categories.json   category counts (BarChart/PieChart)
  src/main/resources/sourcefilesJSON/eval-annotations-text.json         text + annotation layers (HighlightText)
  src/main/resources/pipelines/pipeline_eval-pNN.json              the twelve sampled pipelines
  src/main/resources/pipelines/pipeline_eval-pag.json              the pagination (U-only) experiment

Both folders are imported automatically at startup (app.json-data-import / app.pipeline-json-import),
so a fresh UDAV instance on any machine carries the complete evaluation workload. No special code
paths are involved: the HighlightText, BarChart and PieChart widgets sit on TextFormatting and CategoryNumber
generators that read the two eval-annotations-*.json files, exactly like the MapCoordinates
generators read their JSON sources.

Design constraints derived from the UDAV code:
  * U (files produced by a bulk export) is the sum over widgets of the widget's generator group
    size. Only JSON/XML-backed sources can be generator groups; a non-grouped generator contributes 1.
  * A group is declared with "generatorGroup": true and an id containing @ID@, expanded once per
    top-level key of the source file (SourceJsonN). The eval-src-pagesNN.json slices carry exactly
    NN keys, so the group size and hence U are exact.

Equivalence with the campaign of 2026-08-26: the annotation data is generated with a bit-exact port of
java.util.Random and the seed, vocabulary, category list, palette and sizes of that campaign, so
the synthetic document (20 000+ characters, 1500 spans over 3 layers) and the category counts
(4 documents x 12 categories, Zipf-like) are identical to the data the paper's numbers were measured on
(see evaluation/evaluation-report-2026-08-26.txt).

Run from the repository root:  python3 tools/make_eval_pipelines.py
"""
import collections
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "resources", "sourcefilesJSON")
PIPE = os.path.join(ROOT, "src", "main", "resources", "pipelines")


# ------------------------------------------------------------------ java.util.Random, bit-exact
class JavaRandom:
    MULT, ADD, MASK = 0x5DEECE66D, 0xB, (1 << 48) - 1

    def __init__(self, seed):
        self.seed = (seed ^ self.MULT) & self.MASK

    def _next(self, bits):
        self.seed = (self.seed * self.MULT + self.ADD) & self.MASK
        r = self.seed >> (48 - bits)
        return r - (1 << 32) if r >= (1 << 31) else r

    def next_int(self, bound):
        if bound & (bound - 1) == 0:
            return (bound * self._next(31)) >> 31
        while True:
            bits = self._next(31)
            val = bits % bound
            if bits - val + (bound - 1) < (1 << 31):  # no int overflow in Java's check
                return val

    def next_double(self):
        return ((self._next(26) << 27) + self._next(27)) * (1.0 / (1 << 53))


def java_round(x):  # Math.round(double) for the positive values used here
    return int(x + 0.5) if x >= 0 else -int(-x + 0.5)


# ------------------------------------------------------------------ campaign data constants
SEED = 20260825
CATEGORIES = ["NOUN", "VERB", "ADJ", "ADV", "PRON", "DET", "ADP", "NUM", "CONJ", "PART", "PUNCT", "X"]
TYPES = ["POS", "NamedEntity", "Lemma"]
STYLES = ["highlight", "underline", "bold"]          # by type index
PALETTE = ["#4e79a7", "#f28e2b", "#e15759", "#76b7b2", "#59a14f", "#edc948",
           "#b07aa1", "#ff9da7", "#9c755f", "#bab0ac", "#86bcb6", "#d37295"]
FILES = ["eval-doc-001", "eval-doc-002", "eval-doc-003", "eval-doc-004"]
VOCABULARY = ["annotation", "corpus", "visualisation", "pipeline", "document", "segment",
              "category", "analysis", "frequency", "distribution", "token", "sentence",
              "linguistic", "structure", "evaluation", "measurement", "export", "widget"]
TEXT_LENGTH = 20000
SEGMENT_COUNT = 1500


def category_numbers():
    rnd = JavaRandom(SEED)
    out = collections.OrderedDict()
    for file in FILES:
        counts = collections.OrderedDict()
        for i, category in enumerate(CATEGORIES):
            base = 1200.0 / (i + 1)
            counts[category] = java_round(base * (0.75 + rnd.next_double() * 0.5))
        out[file] = counts
    return out


def annotated_document():
    rnd = JavaRandom(SEED)
    text, segments = [], []
    length = 0
    while length < TEXT_LENGTH:
        word = VOCABULARY[rnd.next_int(len(VOCABULARY))]
        start = length
        text.append(word)
        length += len(word)
        if len(segments) < SEGMENT_COUNT:
            segments.append({"type": TYPES[rnd.next_int(len(TYPES))],
                             "begin": start, "end": start + len(word),
                             "category": CATEGORIES[rnd.next_int(len(CATEGORIES))]})
        sep = ". " if rnd.next_int(12) == 0 else " "
        text.append(sep)
        length += len(sep)
    return {"text": "".join(text), "segments": segments}


# ------------------------------------------------------------------ sources
XY = json.load(open(os.path.join(SRC, "XY_dict.json")))
keys = list(XY.keys())
GROUP_SIZES = [2, 3, 4, 5, 6, 8, 10]
for n in GROUP_SIZES:
    out = {k: XY[k] for k in keys[:n]}
    json.dump(out, open(os.path.join(SRC, f"eval-src-pages{n:02d}.json"), "w"))
    print(f"  source  eval-src-pages{n:02d}.json  sub-sources={len(out)}")

CATEGORY_FILE = "eval-annotations-categories.json"
TEXT_FILE = "eval-annotations-text.json"
json.dump(category_numbers(), open(os.path.join(SRC, CATEGORY_FILE), "w"), indent=1)
doc = annotated_document()
json.dump(doc, open(os.path.join(SRC, TEXT_FILE), "w"))
print(f"  source  {CATEGORY_FILE}  files={len(FILES)} categories={len(CATEGORIES)}")
print(f"  source  {TEXT_FILE}  chars={len(doc['text'])} segments={len(doc['segments'])} layers={len(TYPES)}")

# keysMap used by every MapCoordinates generator over an XY_dict slice; copied verbatim from the
# working voronoi-demo.json so the generator settings are a proven combination.
XY_KEYSMAP = {
    "1_0": {
        "X": "coordinates@0",
        "Y": "coordinates@1",
        "Z_abs": "scale",
        "colors_S": ["fillColor@Red", "fillColor@Green", "fillColor@Blue", "fillColor@Alpha"],
        "colors_SE": ["strokeColor@Red", "strokeColor@Green", "strokeColor@Blue", "strokeColor@Alpha"],
    }
}
# Settings for the non-grouped points.json generator, copied from medialaxis-demo.json.
POINTS_SETTINGS = {
    "keys": {"coordinates": ["x", "y"], "test": "x"},
    "fixedKeys": {"fillColor": "#000000", "strokeColor": "#000000",
                  "outsideColor": "#ffffff", "scale": 1},
}

SVG_TYPES = ["VoronoiDiagram", "MedialAxis", "LineChart"]
NONSVG_TYPE = "ScrollTable"   # renders no SVG, so svg/png exports of it legitimately fail

# The three annotation widgets of the campaign pipelines. Non-grouped, so each adds 1 to U.
ANNOTATION_SOURCES = [
    {"uri": TEXT_FILE, "settings": {}, "id": "Source-eval-annotations-text"},
    {"uri": CATEGORY_FILE, "settings": {}, "id": "Source-eval-annotations-categories"},
]
ANNOTATION_GENERATORS = [
    {"name": "Annotation layers", "type": "TextFormatting", "source": "Source-eval-annotations-text",
     "settings": {"styles": {t: STYLES[i % len(STYLES)] for i, t in enumerate(TYPES)},
                  "colors": {t: {c: PALETTE[i % len(PALETTE)] for i, c in enumerate(CATEGORIES)} for t in TYPES}},
     "extends": [], "id": "TextFormatting-eval-annotations"},
    {"name": "Category counts", "type": "CategoryNumber", "source": "Source-eval-annotations-categories",
     "settings": {"colors": {c: PALETTE[i % len(PALETTE)] for i, c in enumerate(CATEGORIES)}},
     "extends": [], "id": "CategoryNumber-eval-annotations"},
]
ANNOTATION_WIDGETS = [("HighlightText", "TextFormatting-eval-annotations", 0),
                      ("BarChart", "CategoryNumber-eval-annotations", 6),
                      ("PieChart", "CategoryNumber-eval-annotations", 12)]


def build(pid, name, singles, groups, with_annotations):
    """singles = number of non-grouped widget slots (U += 1 each); the last three are the
                 annotation widgets when with_annotations is set, else all are points.json widgets
       groups  = list of (count, group_size) on XY_dict slices (U += size each)"""
    sources = [{"uri": "points.json", "settings": {}, "id": "Source-eval-points"}]
    generators = [{"name": "Points", "type": "MapCoordinates", "source": "Source-eval-points",
                   "settings": POINTS_SETTINGS, "extends": [], "id": "MapCoordinates-eval-points"}]
    for _, size in groups:
        sid, gid = f"Source-eval-p{size:02d}", f"MapCoordinates-eval-p{size:02d}-@ID@"
        if not any(s["id"] == sid for s in sources):
            sources.append({"uri": f"eval-src-pages{size:02d}.json", "settings": {}, "id": sid})
            generators.append({"name": f"Group of {size}", "type": "MapCoordinates", "source": sid,
                               "generatorGroup": True,
                               "settings": {"keysMap": XY_KEYSMAP, "fixedKeys": {"outsideColor": "#ffffff"}},
                               "extends": [], "id": gid})
    if with_annotations:
        sources += [dict(s) for s in ANNOTATION_SOURCES]
        generators += [json.loads(json.dumps(g)) for g in ANNOTATION_GENERATORS]

    widgets, slot, u = [], 0, 0

    def place(wtype, gen, wid=None, title=None, x=None, y=None):
        nonlocal slot
        w = {"type": wtype, "title": title or f"{wtype} {slot}", "generator": {"id": gen},
             "options": {}, "w": 6, "h": 5,
             "x": (slot % 4) * 6 if x is None else x, "y": 1 + (slot // 4) * 5 if y is None else y,
             "id": wid or f"{wtype}-eval{slot:03d}"}
        widgets.append(w)
        slot += 1

    annotation_slots = 3 if with_annotations else 0
    # one non-SVG widget first, so every pipeline has a legitimate svg/png failure
    place(NONSVG_TYPE, "MapCoordinates-eval-points"); u += 1
    for i in range(singles - 1 - annotation_slots):
        place(SVG_TYPES[i % len(SVG_TYPES)], "MapCoordinates-eval-points"); u += 1
    for count, size in groups:
        gid = f"MapCoordinates-eval-p{size:02d}-@ID@"
        for i in range(count):
            wtype = NONSVG_TYPE if i == 0 and size == groups[0][1] else SVG_TYPES[i % len(SVG_TYPES)]
            place(wtype, gid); u += size
    if with_annotations:
        # placement matching the 2026-08-26 campaign: a fresh row below everything laid out so far
        next_y = max([1] + [w["y"] + w["h"] for w in widgets])
        for wtype, gen, x in ANNOTATION_WIDGETS:
            place(wtype, gen, wid=f"{wtype}-eval-annotations", title=f"{wtype} (annotations)", x=x, y=next_y)
            u += 1

    pipeline = {"id": pid, "name": name, "sources": sources, "generators": generators, "widgets": widgets}
    return pipeline, len(widgets), u


# label -> (uuid, class, singles, groups)   groups = [(count, group_size), ...]
SPEC = collections.OrderedDict([
    ("p01", ("e0000001-0000-4000-8000-000000000001", "small",  5,  [])),
    ("p02", ("e0000002-0000-4000-8000-000000000002", "small",  4,  [(2, 5)])),
    ("p03", ("e0000003-0000-4000-8000-000000000003", "small",  8,  [])),
    ("p04", ("e0000004-0000-4000-8000-000000000004", "small",  5,  [(5, 4)])),
    ("p05", ("e0000005-0000-4000-8000-000000000005", "medium", 6,  [(4, 5), (4, 6)])),
    ("p06", ("e0000006-0000-4000-8000-000000000006", "medium", 6,  [(12, 5)])),
    ("p07", ("e0000007-0000-4000-8000-000000000007", "medium", 16, [(6, 4)])),
    ("p08", ("e0000008-0000-4000-8000-000000000008", "medium", 4,  [(16, 6)])),
    ("p09", ("e0000009-0000-4000-8000-000000000009", "large",  18, [(20, 8)])),
    # P10: high U, moderate W (deep groups)
    ("p10", ("e0000010-0000-4000-8000-000000000010", "large",  12, [(20, 10)])),
    # P11: high W, moderate U (the mirror image; breaks the W/U collinearity)
    ("p11", ("e0000011-0000-4000-8000-000000000011", "large",  32, [(14, 2)])),
    ("p12", ("e0000012-0000-4000-8000-000000000012", "large",  8,  [(32, 5)])),
    # Pagination experiment: every widget grouped, so bulk=false gives W and bulk=true gives U
    ("pag", ("e00000aa-0000-4000-8000-0000000000aa", "medium", 1,  [(15, 8)])),
])

print("\n  pipeline                      class    W     U   widget types")
rows = []
for label, (pid, cls, singles, groups) in SPEC.items():
    # the pagination pipeline is deliberately all-groups and has only one single slot,
    # so it keeps its ScrollTable and takes no annotation widgets
    pipeline, w, u = build(pid, f"pipeline_eval-{label}", singles, groups, with_annotations=(label != "pag"))
    fname = f"pipeline_eval-{label}.json"
    json.dump(pipeline, open(os.path.join(PIPE, fname), "w"), indent=2)
    if cls == "small":
        assert w <= 10 and u <= 25, f"{label} violates small: W={w} U={u}"
    elif cls == "large":
        assert w > 30 or u > 150, f"{label} violates large: W={w} U={u}"
    else:
        assert not (w <= 10 and u <= 25) and not (w > 30 or u > 150), f"{label} not medium: W={w} U={u}"
    types = sorted({wd["type"] for wd in pipeline["widgets"]})
    rows.append((label, pid, cls, w, u))
    print(f"  {fname:<29} {cls:<7} {w:<5} {u:<5} {', '.join(types)}")

print("\n  Java constants for BatchExportEvaluationIT:")
for label, pid, cls, w, u in rows:
    print(f'    {label}: id={pid}  class={cls}  W={w} U={u}')
