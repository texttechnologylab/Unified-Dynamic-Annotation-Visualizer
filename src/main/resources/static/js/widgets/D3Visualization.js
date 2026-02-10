import { corpusFilter } from "../pages/view/filter/CorpusFilter.js";
import ControlsHandler from "../pages/view/toolbar/ControlsHandler.js";
import ExportHandler from "../pages/view/toolbar/ExportHandler.js";
import { debounce, getElementDimensions } from "../shared/modules/utils.js";

export default class D3Visualization {
  constructor(root, getData, margin) {
    this.root = d3.select(root);
    this.getData = getData;

    const { width, height } = getElementDimensions(root);
    this.width = width - margin.left - margin.right;
    this.height = height - margin.top - margin.bottom;
    this.margin = margin;

    this.filter = {};
    this.controls = new ControlsHandler(this);
    this.exports = new ExportHandler(this, ["svg", "png", "csv", "json"]);

    this.tooltip = d3.select(".dv-chart-tooltip");
    this.svg = this.root.select(".dv-chart-area").append("svg");
    this.data = null;

    // Re-render chart on resize of container
    const observer = new ResizeObserver(
      debounce(() => {
        if (this.data) {
          const { width, height } = getElementDimensions(root);
          if (width && height) this.resize(width, height);
        }
      }, 10),
    );
    observer.observe(root);

    // Show chart
    this.root.classed("hide", false);
  }

  resize(width, height) {
    this.width = width - this.margin.left - this.margin.right;
    this.height = height - this.margin.top - this.margin.bottom;

    this.render(this.data);
  }

  async fetch() {
    return await this.getData({
      corpus: corpusFilter.filter,
      chart: this.filter,
    });
  }

  clear() {
    this.svg.selectAll("*").remove();
    this.svg
      .attr("width", this.width + this.margin.left + this.margin.right)
      .attr("height", this.height + this.margin.top + this.margin.bottom);
    this.svg
      .append("g")
      .attr("transform", `translate(${this.margin.left}, ${this.margin.top})`);
  }

  init() {
    throw new Error("Method init() not implemented.");
  }

  render() {
    throw new Error("Method render() not implemented.");
  }

  mouseover(event) {
    this.tooltip.style("opacity", 0.9);
    d3.select(event.currentTarget).style("opacity", 0.8);
  }

  mousemove(event, content) {
    this.tooltip
      .html(content)
      .style("top", event.pageY + "px")
      .style("left", event.pageX + 20 + "px");
  }

  mouseleave(event) {
    this.tooltip.style("opacity", 0);
    d3.select(event.currentTarget).style("opacity", 1);
  }

  enableTooltip(selector, content) {
    this.svg
      .selectAll(selector)
      .on("mouseover", (event) => this.mouseover(event))
      .on("mousemove", (event, d) => this.mousemove(event, content(d)))
      .on("mouseleave", (event) => this.mouseleave(event));
  }
}
