import D3Visualization from "../D3Visualization.js";
import ControlsHandler from "../../toolbar/ControlsHandler.js";
import ExportHandler from "../../toolbar/ExportHandler.js";
import { flatData } from "../../../../shared/modules/utils.js";

export default class LineChart extends D3Visualization {
  constructor(root, getData, { line = true, dots = true }) {
    super(root, getData, { top: 10, right: 30, bottom: 30, left: 60 });

    this.controls = new ControlsHandler(this.root.select(".dv-sidepanel-body"));
    this.exports = new ExportHandler(this.root.select(".dv-dropdown-menu"), [
      "svg",
      "png",
      "csv",
      "json",
    ]);

    this.line = line;
    this.dots = dots;
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    // Add controls
    for (const item of data) {
      this.controls.appendSwitch(item.name, true, (value) => {
        console.log(value);
        this.fetch().then((data) => this.render(data));
      });
    }
  }

  render(data) {
    this.clear();

    const coordinates = flatData(data, "coordinates");

    // Add x axis
    const xAxis = d3
      .scaleLinear()
      .range([0, this.width])
      .domain(d3.extent(coordinates, (item) => item.x));
    this.svg
      .select("g")
      .append("g")
      .attr("transform", `translate(0, ${this.height})`)
      .call(d3.axisBottom(xAxis));

    // Add y axis
    const yAxis = d3
      .scaleLinear()
      .range([this.height, 0])
      .domain(d3.extent(coordinates, (item) => item.y));
    this.svg.select("g").append("g").call(d3.axisLeft(yAxis));

    const line = d3
      .line()
      .x((item) => xAxis(item.x))
      .y((item) => yAxis(item.y));

    // Add the line
    if (this.line) {
      this.svg
        .select("g")
        .selectAll(".line")
        .data(data)
        .join("path")
        .attr("d", (item) => line(item.coordinates))
        .attr("fill", "none")
        .attr("stroke", (item) => item.color)
        .attr("stroke-width", 2.5);
    }

    // Add the dots
    this.svg
      .select("g")
      .selectAll(".circle")
      .data(coordinates)
      .join("circle")
      .attr("cx", (item) => xAxis(item.x))
      .attr("cy", (item) => yAxis(item.y))
      .attr("r", 4)
      .attr("fill", this.dots ? (item) => item.color : "transparent");

    if (!this.tooltip.empty()) {
      this.svg
        .selectAll("circle")
        .on("mouseover", (event) => this.mouseover(event.currentTarget))
        .on("mousemove", (event, data) =>
          this.mousemove(
            event.pageY,
            event.pageX + 20,
            `(${data.x}, ${data.y})`,
          ),
        )
        .on("mouseleave", (event) => this.mouseleave(event.currentTarget));
    }

    // Pass data to export handler
    this.exports.update(this.filter, coordinates, this.svg.node());

    this.cachedData = data;
  }
}
