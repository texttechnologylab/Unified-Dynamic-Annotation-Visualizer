import D3Visualization from "../D3Visualization.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class PieChart extends D3Visualization {
  static defaultConfig = {
    type: "PieChart",
    title: "Pie Chart",
    generator: { id: "" },
    options: {
      hole: 0,
      legend: false,
    },
    icon: "bi bi-pie-chart",
    w: 6,
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
    "options.hole": {
      type: "range",
      label: "Hole (Doughnut)",
      options: {
        min: 0,
        max: 99,
        unit: "%",
      },
    },
    "options.legend": {
      type: "switch",
      label: "Show legend",
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
    super(root, config, { top: 10, right: 10, bottom: 10, left: 10 });

    this.hole = config.options.hole || 0;
    this.legend = config.options.legend || false;
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    const min = d3.min(data.map((d) => d.value));
    const max = d3.max(data.map((d) => d.value));

    this.controls.append([
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

    this.legendWidth = this.legend ? 140 : 0;

    const cx = (this.width - this.legendWidth) / 2;
    const cy = this.height / 2;
    const radius = Math.min(this.width - this.legendWidth, this.height) / 2;
    const colors = d3.scaleOrdinal().range(data.map((d) => d.color));

    const pie = d3.pie().value((d) => d.value);
    const arc = d3
      .arc()
      .innerRadius((this.hole * radius) / 100) // For a pie chart (0 for no hole, >0 for a donut chart)
      .outerRadius(radius);

    this.svg
      .select("g")
      .append("g")
      .attr("class", "chart")
      .attr("transform", `translate(${cx}, ${cy})`)
      .selectAll("path")
      .data(pie(data))
      .join("path")
      .attr("d", arc)
      .attr("fill", (_, i) => colors(i))
      .attr("stroke", "white");

    if (this.legend) {
      this.createLegend(data, colors);
    }

    if (!this.tooltip.empty()) {
      this.enableTooltip(
        "path",
        (d) => `<strong>${d.data.label}</strong><br>${d.value}`,
      );
    }

    this.data = data;
  }

  createLegend(data, colors) {
    const spacing = 20;
    const cx = this.width - this.legendWidth + spacing;
    const cy = (this.height - data.length * spacing) / 2;

    const items = this.svg
      .select("g")
      .append("g")
      .attr("class", "legend")
      .attr("transform", `translate(${cx}, ${cy})`)
      .selectAll(".legend-item")
      .data(data)
      .join("g")
      .attr("class", "legend-item")
      .attr("transform", (_, i) => `translate(0, ${i * spacing})`);

    items
      .append("rect")
      .attr("width", 14)
      .attr("height", 14)
      .attr("fill", (_, i) => colors(i));

    items
      .append("text")
      .text((d) => d.label)
      .attr("x", 20)
      .attr("y", 11)
      .style("font-size", "12px");
  }
}
