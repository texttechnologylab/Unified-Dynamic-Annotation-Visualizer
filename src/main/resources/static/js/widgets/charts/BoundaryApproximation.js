import D3Visualization from "../D3Visualization.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class BoundaryApproximation extends D3Visualization {
  static defaultConfig = {
    type: "BoundaryApproximation",
    title: "Boundary Approximation",
    generator: { id: "" },
    options: {
      interpolate: false,
      spacing: 5,
      clustering: "quadtree",
      radiusMultiplier: 1.0,
      gridRows: 8,
      threshold: 0,
      epsilon: 10,
      minPts: 2,
      bandwidth: 20,
      thresholds: 5,
    },
    icon: "bi bi-bounding-box-circles",
    w: 8,
    h: 6,
  };
  static formConfig = {
    title: {
      type: "text",
      label: "Title",
    },
    "generator.id": {
      type: "select",
      label: "Generator",
      options: () => getGeneratorOptions("MapCoordinates"),
    },
    "options.interpolate": {
      type: "switch",
      label: "Interpolate edges",
    },
    "options.spacing": {
      type: "range",
      label: "Interpolation spacing",
      options: { min: 1, max: 100 },
    },
    "options.clustering": {
      type: "select",
      label: "Clustering method",
      options: ["quadtree", "dbscan", "density"],
    },
    "options.radiusMultiplier": {
      type: "range",
      label: "Cluster radius multiplier",
      options: { min: 0.1, max: 10.0, step: 0.1 },
    },
    "options.gridRows": {
      type: "range",
      label: "Grid rows",
      options: { min: 1, max: 100 },
    },
    "options.threshold": {
      type: "range",
      label: "Contour threshold",
      options: { min: 0, max: 50 },
    },
    "options.epsilon": {
      type: "range",
      label: "DBSCAN epsilon",
      options: { min: 1, max: 100 },
    },
    "options.minPts": {
      type: "range",
      label: "DBSCAN minimum points",
      options: { min: 1, max: 100 },
    },
    "options.bandwidth": {
      type: "range",
      label: "Contour density bandwidth",
      options: { min: 0, max: 100 },
    },
    "options.thresholds": {
      type: "range",
      label: "Contour density thresholds",
      options: { min: 1, max: 50 },
    },
  };
  static previewData = [
    [
      [276.4694937085933, 210.96513455773257],
      [231.44104269965737, 212.1801872596352],
    ],
    [
      [279.8532649869678, 233.57060329542853],
      [258.5205158524833, 232.82647918776243],
    ],
    [
      [258.5205158524833, 232.82647918776243],
      [241.73193780364903, 233.5652548110095],
    ],
    [
      [354.0914634285864, 159.91399739642264],
      [344.64352898661446, 156.26693179627298],
    ],
    [
      [354.0914634285864, 159.91399739642264],
      [355.3915585448194, 160.5893335465575],
    ],
    [
      [298.0951585828476, 253.41146322242082],
      [296.1249795167421, 253.9237691377335],
    ],
  ];

  constructor(root, config) {
    super(root, config, { top: 40, right: 40, bottom: 40, left: 40 });

    this.draw = {
      grid: true,
      clusters: true,
    };
    this.interpolate = config.options.interpolate || false;
    this.spacing = config.options.spacing || 5;

    this.clustering = config.options.clustering || "quadtree";
    this.radiusMultiplier = config.options.radiusMultiplier || 1.0;

    // contour
    this.gridRows = config.options.gridRows || 8;
    this.threshold = config.options.threshold || 0;

    // dbscan
    this.epsilon = config.options.epsilon || 10;
    this.minPts = config.options.minPts || 2;

    // densitiy estimation
    this.bandwidth = config.options.bandwidth || 10;
    this.thresholds = config.options.thresholds || 5;
  }

  async fetch() {
    return await d3.json("/data/edges.json");
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    this.controls.append([
      {
        type: "switch",
        label: "Grid",
        value: this.draw.grid,
        onchange: () => {
          this.draw.grid = !this.draw.grid;
          this.render(this.data);
        },
      },
      {
        type: "switch",
        label: "Clusters",
        value: this.draw.clusters,
        onchange: () => {
          this.draw.clusters = !this.draw.clusters;
          this.render(this.data);
        },
      },
    ]);
  }

  render(data) {
    this.clear();

    const dataPoints = this.getDataPoints(data);

    // Create the horizontal and vertical scales
    const xScale = d3
      .scaleLinear()
      .range([0, this.width])
      .domain(this.domain(dataPoints, (d) => d.x));

    const yScale = d3
      .scaleLinear()
      .range([this.height, 0])
      .domain(this.domain(dataPoints, (d) => d.y));

    const { area, zoom } = this.createAxisZoom([1, 40], {
      bottom: xScale,
      left: yScale,
      top: xScale,
      right: yScale,
    });
    this.plotArea = area;

    const points = dataPoints.map((d) => [xScale(d.x), yScale(d.y)]);

    if (this.clustering === "density") {
      const densityEstimator = d3
        .contourDensity()
        .bandwidth(this.bandwidth)
        .thresholds(this.thresholds);

      const contours = densityEstimator(points);

      this.drawPath("boundary", [contours[0]], d3.geoPath());
    } else {
      // Calculate grid
      const cellSize = this.height / this.gridRows;
      const rows = Math.ceil(this.height / cellSize);
      const cols = Math.ceil(this.width / cellSize);

      // Draw the grid
      if (this.draw.grid) {
        this.drawLines(
          "grid-vertical",
          d3.range(0, this.width + 1, cellSize),
          (d) => d,
          0,
          (d) => d,
          this.height,
        );
        this.drawLines(
          "grid-horizontal",
          d3.range(0, this.height + 1, cellSize),
          0,
          (d) => d,
          this.width,
          (d) => d,
        );
      }

      // Calculate clusters
      const clusters =
        this.clustering === "quadtree"
          ? this.quadtreeClustering(points, rows, cols, cellSize)
          : this.dbscanClustering(dataPoints, points);

      // Get a value for each grid cell. The values are the distance
      // from the cell center to the nearest cluster boundary.
      const values = this.getCellValues(clusters, rows, cols, cellSize);

      const contours = d3
        .contours()
        .size([cols, rows])
        .thresholds([-this.threshold]);

      // Draw boundary
      const projection = d3.geoIdentity().scale(cellSize);
      const path = d3.geoPath(projection);
      this.drawPath("boundary", contours(values), path);

      // Draw clusters
      if (this.draw.clusters) {
        this.drawCircles(
          "cluster",
          clusters,
          "none",
          (d) => this.radiusMultiplier * d[2].length,
          "teal",
        ).attr("opacity", 0.4);
      }
    }

    // Draw medial axis points
    this.drawCircles("point", points, "black");

    if (!this.tooltip.empty()) {
      this.svg.call(zoom);
    }

    // Cache rendered data
    this.data = data;
  }

  getDataPoints(edges) {
    const points = [];

    if (this.interpolate) {
      for (const edge of edges) {
        const dx = edge[1].x - edge[0].x;
        const dy = edge[1].y - edge[0].y;
        const length = Math.sqrt(dx * dx + dy * dy);

        // Number of segments based on spacing
        const steps = Math.max(1, Math.floor(length / this.spacing));

        for (let i = 0; i <= steps; i++) {
          const t = i / steps;
          points.push({
            x: edge[0].x + t * dx,
            y: edge[0].y + t * dy,
          });
        }
      }
    } else {
      for (const edge of edges) {
        points.push(...edge);
      }
    }

    return points;
  }

  searchInTree(quadtree, xmin, ymin, xmax, ymax) {
    const results = [];

    quadtree.visit((node, x1, y1, x2, y2) => {
      if (!node.length) {
        do {
          let d = node.data;
          if (d[0] >= xmin && d[0] < xmax && d[1] >= ymin && d[1] < ymax) {
            results.push(d);
          }
        } while ((node = node.next));
      }
      return x1 >= xmax || y1 >= ymax || x2 < xmin || y2 < ymin;
    });

    return results;
  }

  quadtreeClustering(points, rows, cols, cellSize) {
    const tree = d3.quadtree(points);
    const clusters = [];

    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        const found = this.searchInTree(
          tree,
          x * cellSize,
          y * cellSize,
          x * cellSize + cellSize,
          y * cellSize + cellSize,
        );

        for (const f of found) {
          f.push(found);
          clusters.push(f);
        }
      }
    }

    return clusters;
  }

  dbscan(points, epsilon, minPts) {
    const labels = new Array(points.length).fill(undefined);
    let clusterId = 0;

    function euclideanDist(a, b) {
      const dx = a[0] - b[0];
      const dy = a[1] - b[1];
      return Math.sqrt(dx * dx + dy * dy);
    }

    function rangeQuery(idx) {
      return points.reduce((neighbors, point, i) => {
        if (euclideanDist(points[idx], point) <= epsilon) neighbors.push(i);
        return neighbors;
      }, []);
    }

    for (let i = 0; i < points.length; i++) {
      if (labels[i] !== undefined) continue;

      const neighbors = rangeQuery(i);

      if (neighbors.length < minPts) {
        labels[i] = -1; // noise
        continue;
      }

      labels[i] = clusterId;

      const seeds = neighbors.filter((n) => n !== i);

      for (let j = 0; j < seeds.length; j++) {
        const s = seeds[j];

        if (labels[s] === -1) labels[s] = clusterId;
        if (labels[s] !== undefined) continue;

        labels[s] = clusterId;

        const newNeighbors = rangeQuery(s);
        if (newNeighbors.length >= minPts) {
          seeds.push(
            ...newNeighbors.filter((n) => !seeds.includes(n) && n !== s),
          );
        }
      }

      clusterId++;
    }

    return labels;
  }

  dbscanClustering(data, points) {
    const labels = this.dbscan(points, this.epsilon, this.minPts);
    const groups = d3.group(data, (_, i) => labels[i]);
    groups.delete(-1);

    const clusters = [];

    points.forEach((p, i) => {
      const found = groups.get(labels[i]);
      p.push(found || p);
      clusters.push(p);
    });

    return clusters;
  }

  getCellValues(clusters, rows, cols, cellSize) {
    const values = [];

    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        // Get cell center
        const px = (x + 0.5) * cellSize;
        const py = (y + 0.5) * cellSize;

        let value = -Infinity;

        for (const c of clusters) {
          // Get distance from cell center to cluster boundary
          const radius = this.radiusMultiplier * c[2].length;
          const distance = radius - Math.hypot(px - c[0], py - c[1]);

          if (distance > value) value = distance;
        }

        values.push(value);
      }
    }

    return values;
  }

  drawPath(key, data, path, color = "red", width = 1.5) {
    return this.plotArea
      .selectAll("path." + key)
      .data(data)
      .join("path")
      .attr("class", key)
      .attr("d", path)
      .attr("fill", "none")
      .attr("stroke", color)
      .attr("stroke-width", width);
  }

  drawLines(key, edges, x1, y1, x2, y2, color = "lightgray", width = 1) {
    return this.plotArea
      .selectAll("line." + key)
      .data(edges)
      .join("line")
      .attr("class", key)
      .attr("x1", x1)
      .attr("y1", y1)
      .attr("x2", x2)
      .attr("y2", y2)
      .attr("stroke", color)
      .attr("stroke-width", width);
  }

  drawCircles(key, points, fill = "red", radius = 2, stroke = "none") {
    return this.plotArea
      .selectAll("circle." + key)
      .data(points)
      .join("circle")
      .attr("class", key)
      .attr("cx", (d) => d[0])
      .attr("cy", (d) => d[1])
      .attr("r", radius)
      .attr("fill", fill)
      .attr("stroke", stroke);
  }
}
