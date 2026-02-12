import D3Visualization from "../D3Visualization.js";
import { flatData } from "../../shared/modules/utils.js";
import { getGeneratorOptions } from "../../pages/editor/utils/actions.js";

export default class LineChart extends D3Visualization {
  static defaultConfig = {
    type: "LineChart",
    title: "Line Chart",
    generator: { id: "" },
    options: {
      line: true,
      dots: true,
    },
    icon: "bi bi-graph-up",
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
    "options.line": {
      type: "switch",
      label: "Draw lines",
    },
    "options.dots": {
      type: "switch",
      label: "Draw dots",
    },
  };
  static previewData = [
    {
      name: "Dataset",
      color: "#00618f",
      coordinates: [
        {
          y: 5,
          x: 0,
        },
        {
          y: 20,
          x: 20,
        },
        {
          y: 10,
          x: 40,
        },
        {
          y: 40,
          x: 60,
        },
        {
          y: 5,
          x: 80,
        },
        {
          y: 60,
          x: 100,
        },
      ],
    },
  ];

  constructor(root, getData, { line = true, dots = true }) {
    super(root, getData, { top: 10, right: 30, bottom: 30, left: 60 });

    this.line = line;
    this.dots = dots;
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    for (const item of data) {
      this.controls.append([
        {
          type: "switch",
          label: item.name,
          value: true,
          onchange: (event) => {
            console.log(event.target.value);
            this.fetch().then((data) => this.render(data));
          },
        },
      ]);
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
      this.enableTooltip("circle", (d) => `(${d.x}, ${d.y})`);
    }

    // Cache rendered data
    this.data = data;
  }
}
