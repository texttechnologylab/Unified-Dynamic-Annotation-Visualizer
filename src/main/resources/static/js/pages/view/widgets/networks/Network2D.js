import D3Visualization from "../D3Visualization.js";
import ExportHandler from "../../toolbar/ExportHandler.js";

export default class Network2D extends D3Visualization {
  constructor(root, endpoint, {}) {
    super(root, endpoint, { top: 20, right: 20, bottom: 20, left: 20 });
    this.exports = new ExportHandler(this.root.select(".dv-dropdown-menu"), [
      "svg",
      "png",
      "json",
    ]);
  }

  async init() {
    const data = await this.fetch();
    this.render(data);
  }

  render(data) {
    this.clear();

    // Initialize the links
    const link = this.svg
      .select("g")
      .selectAll("line")
      .data(data.links)
      .join("line")
      .style("stroke", (item) => item.color);

    // Initialize the nodes
    const radius = 10;
    const node = this.svg
      .select("g")
      .selectAll("circle")
      .data(data.nodes)
      .join("circle")
      .attr("r", radius)
      .style("fill", (item) => item.color);

    d3.forceSimulation(data.nodes)
      // links between nodes
      .force(
        "link",
        d3.forceLink(data.links).id((item) => item.id)
      )
      // avoid node overlaps
      .force("collide", d3.forceCollide().radius(radius))
      // attraction or repulsion between nodes
      .force("charge", d3.forceManyBody())
      // nodes are attracted by the center of the chart area
      .force("center", d3.forceCenter(this.width / 2, this.height / 2))
      // compute positions without animation
      .stop()
      .tick(300);

    // draw links and nodes at computed positions
    link
      .attr("x1", (item) => item.source.x)
      .attr("y1", (item) => item.source.y)
      .attr("x2", (item) => item.target.x)
      .attr("y2", (item) => item.target.y);

    node.attr("cx", (item) => item.x).attr("cy", (item) => item.y);

    // zoom to network
    this.fitExtent(this.svg.select("g").node().getBBox());

    if (!this.tooltip.empty()) {
      this.svg
        .selectAll("circle")
        .on("mouseover", (event) => this.mouseover(event.currentTarget))
        .on("mousemove", (event, data) =>
          this.mousemove(
            event.pageY,
            event.pageX + 20,
            `<strong>${data.name}</strong> (${data.id})`
          )
        )
        .on("mouseleave", (event) => this.mouseleave(event.currentTarget));
    }

    // Pass data to export handler
    this.exports.update(this.filter, data, this.svg.node());

    this.cachedData = data;
  }

  fitExtent(bbox) {
    const centerX = bbox.x + bbox.width / 2;
    const centerY = bbox.y + bbox.height / 2;

    const scale = Math.min(this.width / bbox.width, this.height / bbox.height);
    const translateX = this.margin.left + this.width / 2 - scale * centerX;
    const translateY = this.margin.top + this.height / 2 - scale * centerY;

    this.svg
      .select("g")
      .attr(
        "transform",
        `translate(${translateX},${translateY}) scale(${scale})`
      );
  }
}
