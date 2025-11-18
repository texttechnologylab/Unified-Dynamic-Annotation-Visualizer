import D3Visualization from "../D3Visualization.js";
import ExportHandler from "../../toolbar/ExportHandler.js";

export default class BarChart extends D3Visualization {
  constructor(root, endpoint, { width = 800, height = 600, dots = true }) {
    super(
      root,
      endpoint,
      { top: 0, right: 0, bottom: 0, left: 0 },
      width,
      height
    );

    this.exports = new ExportHandler(this.root.select(".dv-dropdown-menu"), [
      "svg",
      "png",
      "csv",
      "json",
    ]);

    this.dots = dots;
  }

  async fetch() {
    return await fetch("/js/pages/view/widgets/diagrams/small-data.json").then(
      (response) => response.json()
    );
  }

  async init() {
    const data = await this.fetch();
    this.render(data);
  }

  render(data) {
    this.clear();

    const xScale = d3
      .scaleLinear()
      .range([0, this.width])
      .domain(this.domain(data, (d) => d.x));
    const yScale = d3
      .scaleLinear()
      .range([0, this.height])
      .domain(this.domain(data, (d) => d.y));

    // Add the dots
    if (this.dots) {
      this.svg
        .select("g")
        .selectAll("dot")
        .data(data)
        .join("circle")
        .attr("cx", (d) => xScale(d.x))
        .attr("cy", (d) => yScale(d.y))
        .attr("r", 4)
        .style("fill", (d) => d.color);
    }

    const points = data.map((d) => [xScale(d.x), yScale(d.y)]);
    const delaunay = d3.Delaunay.from(points);
    const voronoi = delaunay.voronoi([0, 0, this.width, this.height]);

    // Add the vertices
    this.svg
      .select("g")
      .selectAll("path")
      .data(data)
      .join("path")
      .attr("d", (_, i) => voronoi.renderCell(i))
      .attr("fill", (d) => d.color)
      .attr("opacity", 0.5)
      .attr("stroke", "#555555");

    if (!this.tooltip.empty()) {
      this.svg
        .selectAll("path")
        .on("mouseover", (event) => this.mouseover(event.currentTarget))
        .on("mousemove", (event, data) =>
          this.mousemove(
            event.pageY,
            event.pageX + 20,
            `<strong>${data.label}</strong>`
          )
        )
        .on("mouseleave", (event) => this.mouseleave(event.currentTarget));
    }

    // Pass data to export handler
    this.exports.update(this.filter, data, this.svg.node());
  }

  domain(data, fn, padding = 0.05) {
    const [min, max] = d3.extent(data, fn);
    const range = max - min;

    return [min - range * padding, max + range * padding];
  }

  mouseover(target) {
    this.tooltip.style("opacity", 0.9);
    d3.select(target).style("opacity", 0.4);
  }

  mouseleave(target) {
    this.tooltip.style("opacity", 0);
    d3.select(target).style("opacity", 0.5);
  }
}
