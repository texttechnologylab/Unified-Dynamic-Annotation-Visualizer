import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";
import D3Visualization from "../D3Visualization.js";

export default class NetworkGraph extends D3Visualization {
  static defaultConfig = {
    type: "NetworkGraph",
    title: "Network Graph",
    generator: { id: "" },
    options: {},
    icon: "bi bi-diagram-3",
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
  static previewData = {
    nodes: [
      {
        id: 1,
        name: "A",
        color: "#00618f",
      },
      {
        id: 2,
        name: "B",
        color: "#00618f",
      },
      {
        id: 3,
        name: "C",
        color: "#00618f",
      },
    ],
    links: [
      {
        source: 1,
        target: 2,
        color: "#9eadbd",
      },
      {
        source: 1,
        target: 3,
        color: "#9eadbd",
      },
    ],
  };

  constructor(root, config) {
    super(root, config, { top: 20, right: 20, bottom: 20, left: 20 });
  }

  async fetch() {
    return await d3.json("/data/network.json");
  }

  async init() {
    const data = await this.fetch();
    this.render(data);
  }

  render(data) {
    this.clear();

    // Initialize the links
    const link = this.plotArea
      .selectAll("line")
      .data(data.links)
      .join("line")
      .style("stroke", (item) => item.color);

    // Initialize the nodes
    const radius = 10;
    const node = this.plotArea
      .selectAll("circle")
      .data(data.nodes)
      .join("circle")
      .attr("r", radius)
      .style("fill", (item) => item.color);

    d3.forceSimulation(data.nodes)
      // links between nodes
      .force(
        "link",
        d3.forceLink(data.links).id((item) => item.id),
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
    this.fitExtent(this.plotArea.node().getBBox());

    if (!this.tooltip.empty()) {
      this.enableTooltip(
        "circle",
        (d) => `<strong>${d.name}</strong> (${d.id})`,
      );
    }

    this.data = data;
  }

  fitExtent(bbox) {
    const centerX = bbox.x + bbox.width / 2;
    const centerY = bbox.y + bbox.height / 2;

    const scale = Math.min(this.width / bbox.width, this.height / bbox.height);
    const translateX = this.margin.left + this.width / 2 - scale * centerX;
    const translateY = this.margin.top + this.height / 2 - scale * centerY;

    this.plotArea.attr(
      "transform",
      `translate(${translateX},${translateY}) scale(${scale})`,
    );
  }
}
