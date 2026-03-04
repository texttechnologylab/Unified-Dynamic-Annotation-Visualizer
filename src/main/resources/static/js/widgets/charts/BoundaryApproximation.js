import D3Visualization from "../D3Visualization.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class BoundaryApproximation extends D3Visualization {
  static defaultConfig = {
    type: "BoundaryApproximation",
    title: "Boundary Approximation",
    generator: { id: "" },
    options: {
      gridRows: 8,
      maxRadius: 35,
      threshold: 0,
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
    "options.gridRows": {
      type: "range",
      label: "Initial grid rows",
      options: { min: 1, max: 100 },
    },
    "options.maxRadius": {
      type: "range",
      label: "Initial max radius",
      options: { min: 1, max: 100 },
    },
    "options.threshold": {
      type: "range",
      label: "Initial threshold",
      options: { min: 0, max: 50 },
    },
  };
  static previewData = [
    { x: 312.3333435058595, y: 75.49191839044741 },
    { x: 274.4747564142397, y: 75.49191839044738 },
    { x: 350.19193059747863, y: 75.49191839044742 },
    { x: 274.47475641423995, y: 75.49191839044738 },
    { x: 236.616169322621, y: 75.49191839044745 },
    { x: 388.0505176890978, y: 75.49191839044737 },
    { x: 388.050517689098, y: 75.49191839044747 },
    { x: 425.90910478071754, y: 75.49191839044732 },
    { x: 236.6161693226209, y: 75.49191839044742 },
    { x: 198.75758223100144, y: 75.49191839044742 },
    { x: 160.8989951393821, y: 87.83267248222326 },
    { x: 123.04040804776277, y: 145.176766135476 },
    { x: 123.0404080477628, y: 121.94848355379966 },
    { x: 123.04040804776277, y: 145.1767661354759 },
    { x: 123.0404080477628, y: 168.40504871715174 },
    { x: 123.0404080477628, y: 168.40504871715186 },
    { x: 425.90910478071714, y: 75.49191839044742 },
    { x: 463.7676918723364, y: 75.49191839044745 },
    { x: 123.0404080477628, y: 191.63333129882812 },
    { x: 123.04040804776277, y: 191.63333129882824 },
    { x: 123.04040804776274, y: 214.86161388050422 },
    { x: 123.04040804776272, y: 214.86161388050422 },
    { x: 463.7676918723368, y: 75.49191839044745 },
    { x: 501.6262789639562, y: 75.49191839044745 },
    { x: 124.29876740373084, y: 111.69380650159887 },
    { x: 123.04040804776277, y: 238.0898964621807 },
    { x: 123.0404080477628, y: 238.08989646218026 },
    { x: 501.6262789639567, y: 75.49191839044745 },
    { x: 537.0364556320666, y: 75.49191839044742 },
    { x: 123.04040804776272, y: 110.15560494376868 },
    { x: 123.04040804776277, y: 261.3181790438565 },
    { x: 123.04040804776282, y: 261.3181790438565 },
    { x: 158.4505847158732, y: 307.7747442072087 },
    { x: 137.07288074655511, y: 284.54646162553297 },
    { x: 160.8989951393821, y: 311.7652828502836 },
    { x: 539.4848660555756, y: 71.50137974737332 },
    { x: 563.0916505009823, y: 52.26363580877077 },
    { x: 537.0364556320661, y: 75.49191839044819 },
    { x: 539.4848660555756, y: 79.48245703352057 },
    { x: 109.0079353489707, y: 98.72020097212364 },
    { x: 184.5057795847885, y: 331.0030267888842 },
    { x: 198.7575822310012, y: 354.231309370561 },
    { x: 87.63023137965229, y: 75.49191839044745 },
    { x: 85.1818209561435, y: 71.50137974737284 },
    { x: 563.0916505009834, y: 98.72020097212304 },
    { x: 61.57503651073645, y: 52.263635808771305 },
    { x: 47.323233864524155, y: 29.035353227095175 },
    { x: 577.3434531471944, y: 121.94848355380023 },
    { x: 577.343453147195, y: 29.035353227094635 },
    { x: 123.04040804776282, y: 273.1110576538875 },
    { x: 123.04040804776268, y: 273.11105765388714 },
    { x: 109.00793534897026, y: 284.5464616255329 },
    { x: 87.63023137965234, y: 307.7747442072088 },
    { x: 85.18182095614341, y: 311.76528285028303 },
    { x: 61.57503651073611, y: 331.00302678888494 },
    { x: 47.323233864524155, y: 354.2313093705608 },
  ];

  constructor(root, config) {
    super(root, config, { top: 0, right: 0, bottom: 0, left: 0 });

    this.draw = {
      grid: false,
      clusters: false,
    };
    this.gridRows = config.options.gridRows || 8;
    this.maxRadius = config.options.maxRadius || 35;
    this.threshold = config.options.threshold || 0;
  }

  async fetch() {
    return await fetch("/skeleton.json").then((response) => response.json());
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
      {
        type: "range",
        label: "Grid rows",
        value: this.gridRows,
        options: { min: 1, max: 100 },
        onchange: (event) => {
          this.gridRows = event.target.value;
          this.render(this.data);
        },
      },
      {
        type: "range",
        label: "Cluster Radius",
        value: this.maxRadius,
        options: { min: 1, max: 100 },
        onchange: (event) => {
          this.maxRadius = event.target.value;
          this.render(this.data);
        },
      },
      {
        type: "range",
        label: "Threshold",
        value: this.threshold,
        options: { min: 0, max: 50 },
        onchange: (event) => {
          this.threshold = event.target.value;
          this.render(this.data);
        },
      },
    ]);
  }

  render(data) {
    this.clear();

    // Create the horizontal and vertical scales
    const xScale = d3
      .scaleLinear()
      .range([0, this.width])
      .domain(this.domain(data, (d) => d.x));

    const yScale = d3
      .scaleLinear()
      .range([0, this.height])
      .domain(this.domain(data, (d) => d.y));

    const points = data.map((d) => [xScale(d.x), yScale(d.y)]);

    // Calculate grid
    const cellSize = this.height / this.gridRows;
    const rows = Math.ceil(this.height / cellSize);
    const cols = Math.ceil(this.width / cellSize);

    // Calculate clusters
    const clusters = this.getClusters(points, rows, cols, cellSize);

    // Prepare scale for cluster radii
    const clusterScale = d3
      .scaleLinear()
      .range([1, this.maxRadius])
      .domain(d3.extent(clusters, (d) => d[2].length));

    // Get a value for each grid cell. The values are the distance
    // from the cell center to the nearest cluster boundary.
    const values = this.getCellValues(
      clusters,
      rows,
      cols,
      cellSize,
      clusterScale,
    );

    // Calculate boundary
    const contour = d3
      .contours()
      .size([cols, rows])
      .thresholds([-this.threshold]);
    const boundary = contour(values);

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

    // Draw medial axis points
    this.drawCircles("point", points, "black");

    // Draw boundary
    const projection = d3.geoIdentity().scale(cellSize);
    const path = d3.geoPath(projection);
    this.drawPath("boundary", boundary, path);

    // Draw clusters
    if (this.draw.clusters) {
      this.drawCircles(
        "cluster",
        clusters,
        "none",
        (d) => clusterScale(d[2].length),
        "teal",
      ).attr("opacity", 0.4);
    }

    // Cache rendered data
    this.data = data;
  }

  search(quadtree, xmin, ymin, xmax, ymax) {
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

  getClusters(points, rows, cols, cellSize) {
    const tree = d3.quadtree(points);
    const clusters = [];

    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        const found = this.search(
          tree,
          x * cellSize,
          y * cellSize,
          x * cellSize + cellSize,
          y * cellSize + cellSize,
        );

        // // Get the center of all found points
        // const center = found.reduce(
        //   (prev, curr) => [prev[0] + curr[0], prev[1] + curr[1]],
        //   [0, 0],
        // );

        // center[0] = center[0] / found.length;
        // center[1] = center[1] / found.length;
        // center.push(found);

        // // center: [x, y, r]
        // if (center[0] && center[1]) {
        //   clusters.push(center);
        // }

        for (const f of found) {
          f.push(found);
          clusters.push(f);
        }
      }
    }

    return clusters;
  }

  getCellValues(clusters, rows, cols, cellSize, clusterScale) {
    const values = [];

    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        // Get cell center
        const px = (x + 0.5) * cellSize;
        const py = (y + 0.5) * cellSize;

        let value = -Infinity;

        for (const c of clusters) {
          // Get distance from cell center to cluster boundary
          const radius = clusterScale(c[2].length);
          const distance = radius - Math.hypot(px - c[0], py - c[1]);

          if (distance > value) value = distance;
        }

        values.push(value);
      }
    }

    return values;
  }

  drawPath(key, data, path, color = "red", width = 2) {
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
