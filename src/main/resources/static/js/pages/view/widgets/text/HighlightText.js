import D3Visualization from "../D3Visualization.js";
import ControlsHandler from "../../toolbar/ControlsHandler.js";
import ExportHandler from "../../toolbar/ExportHandler.js";

export default class HighlightText extends D3Visualization {
  constructor(root, endpoint, { width = 800, height = 600 }) {
    super(
      root,
      endpoint,
      { top: 0, right: 0, bottom: 0, left: 0 },
      width,
      height
    );

    this.controls = new ControlsHandler(this.root.select(".dv-sidepanel-body"));
    this.exports = new ExportHandler(this.root.select(".dv-dropdown-menu"), [
      "csv",
      "json",
    ]);

    this.svg.remove();
    this.div = this.root
      .select(".dv-chart-area")
      .append("div")
      .style("width", this.width + "px")
      .style("height", this.height + "px")
      .style("padding", "1rem")
      .style("overflow-y", "auto");
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    // for (const item of data.datasets) {
    //   this.controls.appendSwitch(item.name, (value) => {
    //     console.log(value);
    //     this.fetch().then((data) => this.render(data));
    //   });
    // }
  }

  render(data) {
    this.clear();

    this.div
      .selectAll("span")
      .data(data.spans)
      .join("span")
      .text((d) => d.TEXT ?? d.text ?? "")
      .attr("style", (d) => d.style || null)
      .filter((d) => d.label);

    if (!this.tooltip.empty()) {
      this.div
        .selectAll("span")
        .on("mouseover", (event) => this.mouseover(event.currentTarget))
        .on("mousemove", (event, d) =>
          this.mousemove(
            event.pageY,
            event.pageX + 20,
            `<strong>${d.label}</strong>`
          )
        )
        .on("mouseleave", (event) => this.mouseleave(event.currentTarget));
    }

    this.exports.update(this.filter, data.spans, null);
  }
}
