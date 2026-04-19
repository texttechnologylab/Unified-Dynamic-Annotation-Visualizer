import D3Visualization from "../D3Visualization.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";
import {
  dbscanClustering,
  quadtreeClustering,
} from "../../shared/modules/clustering.js";

export default class BoundaryApproximation extends D3Visualization {
  static defaultConfig = {
    type: "BoundaryApproximation",
    title: "Boundary Approximation",
    generator: { id: "" },
    options: {
      interpolate: false,
      clustering: "quadtree",
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
      options: () => getGeneratorOptions(["MapCoordinates"]),
    },
    "options.interpolate": {
      type: "switch",
      label: "Interpolate edges",
    },
    "options.clustering": {
      type: "select",
      label: "Method",
      options: [
        { label: "quadtree clustering", value: "quadtree" },
        { label: "dbscan clustering", value: "dbscan" },
        { label: "kernel density estimation", value: "density" },
      ],
    },
  };

  constructor(root, config) {
    super(root, config, { top: 40, right: 40, bottom: 40, left: 40 });

    this.draw = {
      grid: true,
      circles: true,
    };
    this.interpolate = config.options.interpolate || false;
    this.spacing = 5;

    this.clustering = config.options.clustering || "quadtree";
    this.radiusMultiplier = 1.0;

    // contour
    this.gridRows = 8;
    this.threshold = 0;

    // dbscan
    this.epsilon = 10;
    this.minPts = 2;

    // densitiy estimation
    this.bandwidth = 10;
    this.thresholds = 5;
  }

  async init() {
    const { data, meta } = await this.fetch();
    this.render(data[0]);

    this.exports.init(meta.total > 1);
    this.pagination.init(meta.ids);

    if (this.clustering !== "density")
      this.controls.append([
        {
          type: "switch",
          label: "Grid",
          value: this.draw.grid,
          onchange: () => {
            this.draw.grid = !this.draw.grid;
            this.rerender();
          },
        },
        {
          type: "switch",
          label: "Circles",
          value: this.draw.circles,
          onchange: () => {
            this.draw.circles = !this.draw.circles;
            this.rerender();
          },
        },
      ]);

    if (this.interpolate)
      this.controls.append([
        {
          type: "range",
          label: "Interpolation spacing",
          value: this.spacing,
          options: { min: 1, max: 100 },
          onchange: (event) => {
            this.spacing = event.target.value;
            this.rerender();
          },
        },
      ]);

    if (this.clustering !== "density")
      this.controls.append([
        {
          type: "range",
          label: "Circle radius multiplier",
          value: this.radiusMultiplier,
          options: { min: 0.1, max: 10.0, step: 0.1 },
          onchange: (event) => {
            this.radiusMultiplier = event.target.value;
            this.rerender();
          },
        },
        {
          type: "range",
          label: "Grid rows",
          value: this.gridRows,
          options: { min: 1, max: 100 },
          onchange: (event) => {
            this.gridRows = event.target.value;
            this.rerender();
          },
        },
        {
          type: "range",
          label: "Contour threshold",
          value: this.threshold,
          options: { min: 0, max: 50 },
          onchange: (event) => {
            this.threshold = event.target.value;
            this.rerender();
          },
        },
      ]);

    if (this.clustering === "dbscan")
      this.controls.append([
        {
          type: "range",
          label: "DBSCAN epsilon",
          value: this.epsilon,
          options: { min: 1, max: 100 },
          onchange: (event) => {
            this.epsilon = event.target.value;
            this.rerender();
          },
        },
        {
          type: "range",
          label: "DBSCAN minimum points",
          value: this.minPts,
          options: { min: 1, max: 100 },
          onchange: (event) => {
            this.minPts = event.target.value;
            this.rerender();
          },
        },
      ]);

    if (this.clustering === "density")
      this.controls.append([
        {
          type: "range",
          label: "Contour density bandwidth",
          value: this.bandwidth,
          options: { min: 0, max: 100 },
          onchange: (event) => {
            this.bandwidth = event.target.value;
            this.rerender();
          },
        },
        {
          type: "range",
          label: "Contour density thresholds",
          value: this.thresholds,
          options: { min: 1, max: 50 },
          onchange: (event) => {
            this.thresholds = event.target.value;
            this.rerender();
          },
        },
      ]);
  }

  render(data) {
    this.clear();

    const dataPoints = this.interpolateEdges(data);

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
      const clusterPoints =
        this.clustering === "quadtree"
          ? quadtreeClustering(points, rows, cols, cellSize)
          : dbscanClustering(dataPoints, points, this.epsilon, this.minPts);

      // Get a value for each grid cell. The values are the distance
      // from the cell center to the nearest cluster boundary.
      const values = this.calculateCellValues(
        clusterPoints,
        rows,
        cols,
        cellSize,
      );

      const contours = d3
        .contours()
        .size([cols, rows])
        .thresholds([-this.threshold]);

      // Draw boundary
      const projection = d3.geoIdentity().scale(cellSize);
      const path = d3.geoPath(projection);
      this.drawPath("boundary", contours(values), path);

      // Draw circles
      if (this.draw.circles) {
        this.drawCircles(
          "cluster",
          clusterPoints,
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

  interpolateEdges(edges) {
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

  calculateCellValues(points, rows, cols, cellSize) {
    const values = [];

    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        // Get cell center
        const px = (x + 0.5) * cellSize;
        const py = (y + 0.5) * cellSize;

        let value = -Infinity;

        for (const p of points) {
          // Get distance from cell center to cluster boundary
          const radius = this.radiusMultiplier * p[2].length;
          const distance = radius - Math.hypot(px - p[0], py - p[1]);

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
