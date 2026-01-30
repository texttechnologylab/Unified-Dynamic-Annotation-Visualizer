import D3Visualization from "../D3Visualization.js";
import { randomId } from "../../../../shared/modules/utils.js";
import { getChanges } from "../../../../api/llm.api.js";

export default class VoronoiDiagram extends D3Visualization {
  constructor(root, getData, {}) {
    super(root, getData, { top: 40, right: 40, bottom: 40, left: 40 });

    this.draw = {
      points: true,
      polygons: false,
    };
  }

  async fetch() {
    return await fetch("/data.json").then((response) => response.json());
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    this.controls.appendSwitch("Center points", this.draw.points, () => {
      this.draw.points = !this.draw.points;
      this.render(this.data);
    });
    this.controls.appendSwitch("Center polygons", this.draw.polygons, () => {
      this.draw.polygons = !this.draw.polygons;
      this.render(this.data);
    });
  }

  render(data) {
    this.clear();

    const root = this.svg.select("g");
    const id = randomId("clip");

    // Create clip path for zoom area
    root
      .append("clipPath")
      .attr("id", id)
      .append("rect")
      .attr("width", this.width)
      .attr("height", this.height);

    // Create the horizontal and vertical scales
    const xScale = d3
      .scaleLinear()
      .range([0, this.width])
      .domain(this.domain(data, (d) => d.x));

    const yScale = d3
      .scaleLinear()
      .range([0, this.height])
      .domain(this.domain(data, (d) => d.y));

    // Add the axes
    const axisTop = root.append("g").call(d3.axisTop(xScale));

    const axisRight = root
      .append("g")
      .attr("transform", `translate(${this.width}, 0)`)
      .call(d3.axisRight(yScale));

    const axisBottom = root
      .append("g")
      .attr("transform", `translate(0, ${this.height})`)
      .call(d3.axisBottom(xScale));

    const axisLeft = root.append("g").call(d3.axisLeft(yScale));

    // Create a dedicated zoom area
    const area = root
      .append("g")
      .attr("clip-path", `url(#${id})`)
      .append("g")
      .attr("class", "zoom-area");

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
      .attr("class", "cell")
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
      // Add zoom
      const zoom = d3
        .zoom()
        .scaleExtent([1, 40])
        .extent([
          [0, 0],
          [this.width, this.height],
        ])
        .translateExtent([
          [0, 0],
          [this.width, this.height],
        ])
        .on("zoom", (event) => {
          // Zoom chart content
          area.attr("transform", event.transform);

          // Rescale axes (without translating them)
          axisTop.call(d3.axisTop(event.transform.rescaleX(xScale)));
          axisRight.call(d3.axisRight(event.transform.rescaleY(yScale)));
          axisBottom.call(d3.axisBottom(event.transform.rescaleX(xScale)));
          axisLeft.call(d3.axisLeft(event.transform.rescaleY(yScale)));
        });
      this.svg.call(zoom);

      // Add tooltips
      this.enableTooltip(
        ".zoom-area > path.cell",
        (d) => `<strong>${d.label}</strong>`,
      );
    }

    // Cache rendered data
    this.data = data;
  }

  domain(data, fn, padding = 0.05) {
    const [min, max] = d3.extent(data, fn);
    const range = max - min;

    return [min - range * padding, max + range * padding];
  }
}
