import ExportHandler from "../../pages/view/toolbar/ExportHandler.js";
import D3Visualization from "../D3Visualization.js";

export default class HighlightText extends D3Visualization {
  static defaultConfig = {
    type: "HighlightText",
    title: "Highlight Text",
    generator: { id: "" },
    options: {},
    icon: "bi bi-card-text",
    w: 6,
    h: 6,
  };
  static formConfig = {
    title: {
      type: "text",
      label: "Title",
    },
    "generator.id": {
      type: "text",
      label: "Generator",
    },
  };
  static previewData = {
    spans: [
      {
        text: "Lorem ipsum dolor sit amet, consetetur sadipscing elitr",
        style: "text-decoration: underline 2px #00618f;",
      },
      {
        text: ", sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. ",
      },
      {
        text: "Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.",
        style: "text-decoration: underline 2px #3a4856;",
      },
      {
        text: " Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. ",
      },
      {
        text: "Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.",
        style: "text-decoration: underline 2px #9eadbd;",
      },
    ],
    datasets: [],
  };

  constructor(root, getData, {}) {
    super(root, getData, { top: 16, right: 16, bottom: 16, left: 16 });

    this.exports = new ExportHandler(this, ["csv", "json"]);

    this.svg.remove();
    this.svg = this.root.select(".dv-chart-area").append("div");
  }

  clear() {
    this.svg.selectAll("*").remove();
    this.svg
      .style("width", this.width + this.margin.left + this.margin.right + "px")
      .style(
        "height",
        this.height + this.margin.top + this.margin.bottom + "px",
      )
      .style("padding-top", this.margin.top + "px")
      .style("padding-right", this.margin.right + "px")
      .style("padding-bottom", this.margin.bottom + "px")
      .style("padding-left", this.margin.left + "px")
      .style("overflow-y", "auto");
  }

  async init() {
    const data = await this.fetch();
    this.render(data);
  }

  render(data) {
    this.clear();

    this.svg
      .selectAll("span")
      .data(data.spans)
      .join("span")
      .text((d) => d.TEXT ?? d.text ?? "")
      .attr("class", (d) => d.label && "labeled")
      .attr("style", (d) => d.style || null);

    if (!this.tooltip.empty()) {
      this.enableTooltip("span.labeled", (d) => `<strong>${d.label}</strong>`);
    }

    // Cache rendered data
    this.data = data;
  }
}
