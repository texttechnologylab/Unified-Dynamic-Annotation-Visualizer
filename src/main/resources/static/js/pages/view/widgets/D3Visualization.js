import * as d3 from "https://cdn.jsdelivr.net/npm/d3@7/+esm";
import { corpusFilter } from "../filter/CorpusFilter.js";

export default class D3Visualization {
  constructor(root, endpoint, margin, width, height) {
    this.root = d3.select(root);
    this.endpoint = endpoint;
    this.width = width - margin.left - margin.right;
    this.height = height - margin.top - margin.bottom;
    this.margin = margin;

    this.filter = {};

    this.tooltip = d3.select(".dv-chart-tooltip");
    this.svg = this.root.select(".dv-chart-area").append("svg");

    // Show chart
    this.root.classed("hide", false);
  }

  setDimensions(width, height) {
    this.width = width - this.margin.left - this.margin.right;
    this.height = height - this.margin.top - this.margin.bottom;
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
