import D3Visualization from "../D3Visualization.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class VoronoiDiagram extends D3Visualization {
  static defaultConfig = {
    type: "VoronoiDiagram",
    title: "Voronoi Diagram",
    generator: { id: "" },
    options: {},
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
      options: () => getGeneratorOptions("MapCoordinates"),
    },
  };
  static previewData = [
    {
      x: 10,
      y: 10,
      cell: "#00618f",
      fill: "#00618f",
      stroke: "#555555",
      label: "Cell 1",
      abs: 0.5,
    },
    {
      x: 12,
      y: 32,
      cell: "#3a4856",
      fill: "#3a4856",
      stroke: "#555555",
      label: "Cell 7",
      abs: 0.2,
    },
    {
      x: 23,
      y: 23,
      cell: "#9eadbd",
      fill: "#9eadbd",
      stroke: "#555555",
      label: "Cell 10",
      abs: 0.1,
    },
  ];

  constructor(root, config) {
    super(root, config, { top: 40, right: 40, bottom: 40, left: 40 });

    this.draw = {
      points: true,
      polygons: false,
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
          this.render(this.data);
        },
      },
      {
        type: "switch",
        label: "Center polygons",
        value: this.draw.polygons,
        onchange: () => {
          this.draw.polygons = !this.draw.polygons;
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
      .range([this.height, 0])
      .domain(this.domain(data, (d) => d.y));

    const { area, zoom } = this.createAxisZoom([1, 40], {
      bottom: xScale,
      left: yScale,
      top: xScale,
      right: yScale,
    });

    // Calculate voronoi
    const points = data.map((d) => [xScale(d.x), yScale(d.y)]);
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
        .data(data)
        .join("path")
        .attr("class", "polygon")
        .attr("d", (d, i) => renderPolygon(i, d.abs))
        .attr("fill", (_, i) => data[i].fill)
        .attr("opacity", 0.7)
        .attr("stroke", (_, i) => data[i].stroke)
        .attr("stroke-width", 2);
    }

    // Add the cells
    area
      .selectAll("path.cell")
      .data(data)
      .join("path")
      .attr("class", (d) => (d.label ? "cell labeled" : "cell"))
      .attr("d", (_, i) => voronoi.renderCell(i))
      .attr("fill", (d) => d.cell || "transparent")
      .attr("stroke", "#555555");

    // Draw points
    if (this.draw.points) {
      area
        .selectAll("circle")
        .data(data)
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
}
