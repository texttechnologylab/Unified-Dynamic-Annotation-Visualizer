import * as d3 from "https://cdn.jsdelivr.net/npm/d3@7/+esm";
import D3Visualization from "../D3Visualization.js";
import ExportHandler from "../../toolbar/ExportHandler.js";

export default class Network2D extends D3Visualization {
  constructor(root, endpoint, { width = 800, height = 600 }) {
    super(
      root,
      endpoint,
      { top: 10, right: 30, bottom: 30, left: 40 },
      width,
      height
    );
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

    if (!this.tooltip.empty()) {
      const zoom = d3.zoom().on("zoom", (event) => {
        this.svg.select("g").attr("transform", event.transform);
      });
      this.svg.call(zoom);

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

    // This function is run at each iteration of the force algorithm, updating the nodes position.
    const onTick = () => {
      link
        .attr("x1", (item) => item.source.x)
        .attr("y1", (item) => item.source.y)
        .attr("x2", (item) => item.target.x)
        .attr("y2", (item) => item.target.y);

      node.attr("cx", (item) => item.x).attr("cy", (item) => item.y);
    };

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
      // nodes position is updated every tick
      .on("tick", onTick);

    // Pass data to export handler
    this.exports.update(this.filter, data, this.svg.node());
  }
}
