import D3Visualization from "../D3Visualization.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class BarChart extends D3Visualization {
  static defaultConfig = {
    type: "BarChart",
    title: "Bar Chart",
    generator: { id: "" },
    options: {
      horizontal: false,
    },
    icon: "bi bi-bar-chart",
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
      options: () => getGeneratorOptions("CategoryNumber"),
    },
    "options.horizontal": {
      type: "switch",
      label: "Horizontal",
    },
  };
  static previewData = [
    {
      label: "Label 1",
      value: 140,
      color: "#00618f",
    },
    {
      label: "Label 2",
      value: 73,
      color: "#3a4856",
    },
    {
      label: "Label 3",
      value: 56,
      color: "#9eadbd",
    },
  ];

  constructor(root, config) {
    super(root, config, { top: 30, right: 30, bottom: 70, left: 60 });

    this.horizontal = config.options.horizontal || false;
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    const min = d3.min(data.map((d) => d.value));
    const max = d3.max(data.map((d) => d.value));

    this.controls.append([
      {
        type: "select",
        label: "Sort by",
        value: "value",
        options: ["value", "label"],
        onchange: (event) => {
          this.filter.sort = event.target.value;
          this.fetch().then((data) => this.render(data));
        },
      },
      {
        type: "switch",
        label: "Desc",
        value: true,
        onchange: (event) => {
          this.filter.desc = event.target.checked;
          this.fetch().then((data) => this.render(data));
        },
      },
      {
        type: "rangedouble",
        label: "Range",
        value: [min, max],
        options: { min, max },
        onchange: (min, max) => {
          this.filter.min = min;
          this.filter.max = max;
          this.fetch().then((data) => this.render(data));
        },
      },
    ]);
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
      this.enableTooltip(
        "rect",
        (d) => `<strong>${d.label}</strong><br>${d.value}`,
      );
    }

    // Cache rendered data
    this.data = data;
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
      .domain([0, d3.max(data.map((d) => d.value))]);
  }
}
