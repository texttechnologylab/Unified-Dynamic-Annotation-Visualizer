import D3Visualization from "../D3Visualization.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class VoronoiDiagram extends D3Visualization {
  static defaultConfig = {
    type: "VoronoiDiagram",
    title: "Voronoi Diagram",
    generator: { id: "" },
    options: {
      min: -1,
      max: 1,
      step: 0.5,
    },
    icon: "bi bi-columns",
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
    "options.min": {
      type: "number",
      label: "Boundary min",
      options: {},
    },
    "options.max": {
      type: "number",
      label: "Boundary max",
      options: {},
    },
    "options.step": {
      type: "range",
      label: "Boundary step length",
      options: { min: 0.1, max: 10, step: 0.1 },
    },
  };

  constructor(root, config) {
    super(root, config, { top: 40, right: 40, bottom: 40, left: 40 });

    this.min = config.options.min || -1;
    this.max = config.options.max || 1;
    this.step = config.options.step || 0.5;

    this.draw = {
      points: true,
      polygons: false,
      boundary: false,
    };
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    this.controls.append([
      {
        type: "switch",
        label: "Center points",
        value: this.draw.points,
        onchange: () => {
          this.draw.points = !this.draw.points;
          this.rerender();
        },
      },
      {
        type: "switch",
        label: "Center polygons",
        value: this.draw.polygons,
        onchange: () => {
          this.draw.polygons = !this.draw.polygons;
          this.rerender();
        },
      },
      {
        type: "switch",
        label: "Boundary points",
        value: this.draw.boundary,
        onchange: () => {
          this.draw.boundary = !this.draw.boundary;
          this.rerender();
        },
      },
    ]);
  }

  render(data) {
    this.clear();

    const boundaryPoints = this.draw.boundary
      ? this.generateBoundaryPoints({
          minX: this.min,
          minY: this.min,
          maxX: this.max,
          maxY: this.max,
          step: this.step,
        })
      : [];
    const dataPoints = [...boundaryPoints, ...data];

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

    // Calculate voronoi
    const points = dataPoints.map((d) => [xScale(d.x), yScale(d.y)]);
    const delaunay = d3.Delaunay.from(points);
    const voronoi = delaunay.voronoi([0, 0, this.width, this.height]);

    function renderPolygon(index, scale) {
      const cellPolygon = voronoi.cellPolygon(index);
      const [cx, cy] = points[index];

      const scaledPolygon = cellPolygon.map(([x, y]) => [
        cx + scale * (x - cx),
        cy + scale * (y - cy),
      ]);

      return "M" + scaledPolygon.join("L") + "Z";
    }

    // Draw the scaled polygons
    if (this.draw.polygons) {
      area
        .selectAll("path.polygon")
        .data(dataPoints)
        .join("path")
        .attr("class", "polygon")
        .attr("d", (d, i) => renderPolygon(i, d.abs))
        .attr("fill", (_, i) => dataPoints[i].fill)
        .attr("opacity", 0.7)
        .attr("stroke", (_, i) => dataPoints[i].stroke)
        .attr("stroke-width", 2);
    }

    // Add the cells
    area
      .selectAll("path.cell")
      .data(dataPoints)
      .join("path")
      .attr("class", (d) => (d.label ? "cell labeled" : "cell"))
      .attr("d", (_, i) => voronoi.renderCell(i))
      .attr("fill", (d) => d.cell || "transparent")
      .attr("stroke", "#555555");

    // Draw points
    if (this.draw.points) {
      area
        .selectAll("circle")
        .data(dataPoints)
        .join("circle")
        .attr("cx", (d) => xScale(d.x))
        .attr("cy", (d) => yScale(d.y))
        .attr("r", 4)
        .style("fill", (d) => d.fill);
    }

    if (!this.tooltip.empty()) {
      this.svg.call(zoom);
      this.enableTooltip(
        ".zoom-area > path.cell.labeled",
        (d) => `<strong>${d.label}</strong>`,
      );
    }

    // Cache rendered data
    this.data = data;
  }

  generateBoundaryPoints({ minX, maxX, minY, maxY, step }) {
    const points = [];

    const stepsX = Math.round((maxX - minX) / step);
    const stepsY = Math.round((maxY - minY) / step);

    // Top & bottom edges (include corners)
    for (let i = 0; i <= stepsX; i++) {
      const x = minX + i * step;
      points.push({ x, y: minY, fill: "#aaaaaa" }); // bottom
      points.push({ x, y: maxY, fill: "#aaaaaa" }); // top
    }

    // Left & right edges (exclude corners)
    for (let i = 1; i < stepsY; i++) {
      const y = minY + i * step;
      points.push({ x: minX, y, fill: "#aaaaaa" }); // left
      points.push({ x: maxX, y, fill: "#aaaaaa" }); // right
    }

    return points;
  }
}
