import {
  debounce,
  getElementDimensions,
} from "../../../shared/modules/utils.js";
import { corpusFilter } from "../filter/CorpusFilter.js";

export default class D3Visualization {
  constructor(root, endpoint, margin) {
    this.root = d3.select(root);
    this.endpoint = endpoint;

    const { width, height } = getElementDimensions(root);
    this.width = width - margin.left - margin.right;
    this.height = height - margin.top - margin.bottom;
    this.margin = margin;

    this.filter = {};

    this.tooltip = d3.select(".dv-chart-tooltip");
    this.svg = this.root.select(".dv-chart-area").append("svg");

    // Re-render chart on resize of container
    const observer = new ResizeObserver(
      debounce(() => {
        if (this.cachedData) {
          console.log("handle");

          const { width, height } = getElementDimensions(root);
          this.resize(width, height);
        }
      }, 10)
    );
    observer.observe(root);

    this.cachedData = null;

    // Show chart
    this.root.classed("hide", false);
  }

  resize(width, height) {
    this.width = width - this.margin.left - this.margin.right;
    this.height = height - this.margin.top - this.margin.bottom;

    this.render(this.cachedData);
  }

  async fetch() {
    const options = {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        corpus: corpusFilter.filter,
        chart: this.filter,
      }),
    };

    return await fetch(this.endpoint, options).then((response) =>
      response.json()
    );
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

  mouseover(target) {
    this.tooltip.style("opacity", 0.9);
    d3.select(target).style("opacity", 0.8);
  }

  mousemove(top, left, html) {
    this.tooltip
      .html(html)
      .style("top", top + "px")
      .style("left", left + "px");
  }

  mouseleave(target) {
    this.tooltip.style("opacity", 0);
    d3.select(target).style("opacity", 1);
  }
}
