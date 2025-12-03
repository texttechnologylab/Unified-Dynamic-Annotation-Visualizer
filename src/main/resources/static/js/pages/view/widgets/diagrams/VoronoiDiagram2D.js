import D3Visualization from "../D3Visualization.js";
import ExportHandler from "../../toolbar/ExportHandler.js";
import { randomId } from "../../../../shared/modules/utils.js";

export default class VoronoiDiagram2D extends D3Visualization {
  constructor(root, endpoint, { dots = true }) {
    super(root, endpoint, { top: 40, right: 40, bottom: 40, left: 40 });

    this.exports = new ExportHandler(this.root.select(".dv-dropdown-menu"), [
      "svg",
      "png",
      "csv",
      "json",
    ]);

    this.dots = dots;
  }

  async fetch() {
    return await fetch("/js/pages/view/widgets/diagrams/data.json").then(
      (response) => response.json()
    );
  }

  async init() {
    const data = await this.fetch();
    this.render(data);
  }

  render(data) {
    this.clear();

    const root = this.svg.select("g");
    const id = randomId("clip");

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

    // Add the dots
    if (this.dots) {
      area
        .selectAll("circle")
        .data(data)
        .join("circle")
        .attr("cx", (d) => xScale(d.x))
        .attr("cy", (d) => yScale(d.y))
        .attr("r", 4)
        .style("fill", (d) => d.color);
    }

    // Calculate voronoi
    const points = data.map((d) => [xScale(d.x), yScale(d.y)]);
    const delaunay = d3.Delaunay.from(points);
    const voronoi = delaunay.voronoi([0, 0, this.width, this.height]);

    // Add the vertices
    area
      .selectAll("path")
      .data(data)
      .join("path")
      .attr("d", (_, i) => voronoi.renderCell(i))
      .attr("fill", (d) => d.color)
      .attr("opacity", 0.5)
      .attr("stroke", "#555555");

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
      area
        .selectAll("path")
        .on("mouseover", (event) => this.mouseover(event.currentTarget))
        .on("mousemove", (event, data) => {
          this.mousemove(
            event.pageY,
            event.pageX + 20,
            `<strong>${data.label}</strong>`
          );
        })
        .on("mouseleave", (event) => this.mouseleave(event.currentTarget));
    }

    // Pass data to export handler
    this.exports.update(this.filter, data, this.svg.node());

    this.cachedData = data;
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
