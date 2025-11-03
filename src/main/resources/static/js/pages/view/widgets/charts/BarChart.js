import D3Visualization from "../D3Visualization.js";
import ControlsHandler from "../../toolbar/ControlsHandler.js";
import ExportHandler from "../../toolbar/ExportHandler.js";
import { maxOf, minOf } from "../../../../shared/modules/utils.js";

export default class BarChart extends D3Visualization {
  constructor(
    root,
    endpoint,
    { width = 800, height = 600, horizontal = false }
  ) {
    super(
      root,
      endpoint,
      { top: 30, right: 30, bottom: 70, left: 60 },
      width,
      height
    );

    this.controls = new ControlsHandler(this.root.select(".dv-sidepanel-body"));
    this.exports = new ExportHandler(this.root.select(".dv-dropdown-menu"), [
      "svg",
      "png",
      "csv",
      "json",
    ]);

    this.horizontal = horizontal;
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    const min = minOf(data.map((d) => d.value));
    const max = maxOf(data.map((d) => d.value));

    // Add controls
    this.controls.appendSelectRadio(
      "Sort by",
      ["value", "label"],
      ["desc", "asc"],
      (sort, order) => {
        this.filter.sort = sort;
        this.filter.desc = order === "desc";
        this.fetch().then((data) => this.render(data));
      }
    );

    // this.controls.appendInputRadio(
    //   "Filter labels by",
    //   ["includes", "regex"],
    //   (input, type) => {
    //     this.filter.filter = input;
    //     this.filter.regex = type === "regex";
    //     this.fetch().then((data) => this.render(data));
    //   }
    // );

    this.controls.appendDoubleSlider(min, max, (min, max) => {
      this.filter.min = min;
      this.filter.max = max;
      this.fetch().then((data) => this.render(data));
    });
  }

  render(data) {
    this.clear();

    // Add x axis
    const xAxis = this.horizontal ? this.linear(data) : this.band(data);
    this.svg
      .select("g")
      .append("g")
      .attr("transform", `translate(0, ${this.height})`)
      .call(d3.axisBottom(xAxis))
      .selectAll("text")
      .attr("transform", "translate(-10,0)rotate(-45)")
      .style("text-anchor", "end");

    // Add y axis
    const yAxis = this.horizontal ? this.band(data) : this.linear(data);
    this.svg.select("g").append("g").call(d3.axisLeft(yAxis));

    const x = this.horizontal ? xAxis(0) : (item) => xAxis(item.label);
    const y = this.horizontal
      ? (item) => yAxis(item.label)
      : (item) => yAxis(item.value);
    const width = this.horizontal
      ? (item) => xAxis(item.value)
      : xAxis.bandwidth();
    const height = this.horizontal
      ? yAxis.bandwidth()
      : (item) => this.height - yAxis(item.value);

    // Add the bars
    this.svg
      .select("g")
      .selectAll()
      .data(data)
      .join("rect")
      .attr("x", x)
      .attr("y", y)
      .attr("width", width)
      .attr("height", height)
      .attr("fill", (item) => item.color);

    if (!this.tooltip.empty()) {
      this.svg
        .selectAll("rect")
        .on("mouseover", (event) => this.mouseover(event.currentTarget))
        .on("mousemove", (event, data) =>
          this.mousemove(
            event.pageY,
            event.pageX + 20,
            `<strong>${data.label}</strong><br>${data.value}`
          )
        )
        .on("mouseleave", (event) => this.mouseleave(event.currentTarget));
    }

    // Pass data to export handler
    this.exports.update(this.filter, data, this.svg.node());
  }

  band(data) {
    return d3
      .scaleBand()
      .range(this.horizontal ? [0, this.height] : [0, this.width])
      .domain(data.map((item) => item.label))
      .padding(0.2);
  }

  linear(data) {
    return d3
      .scaleLinear()
      .range(this.horizontal ? [0, this.width] : [this.height, 0])
      .domain([0, maxOf(data.map((d) => d.value))]);
  }
}
