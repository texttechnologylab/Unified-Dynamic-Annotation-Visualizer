import D3Visualization from "../D3Visualization.js";
import {
  maxOf,
  minOf,
  getElementDimensions,
} from "../../shared/modules/utils.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class PieChart extends D3Visualization {
  static defaultConfig = {
    type: "PieChart",
    title: "Pie Chart",
    generator: { id: "" },
    options: {
      hole: 0,
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
        max: 500,
      },
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

  constructor(root, getData, { hole = 0 }) {
    const { width, height } = getElementDimensions(root);

    super(root, getData, {
      top: height / 2,
      right: width / 2,
      bottom: height / 2,
      left: width / 2,
    });

    this.radius = minOf([width, height]) / 2;
    this.hole = hole;
  }

  resize(width, height) {
    this.margin = {
      top: height / 2,
      right: width / 2,
      bottom: height / 2,
      left: width / 2,
    };
    this.radius = minOf([width, height]) / 2;

    this.render(this.data);
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    const min = minOf(data.map((d) => d.value));
    const max = maxOf(data.map((d) => d.value));

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

    // Create a color scale
    const color = d3.scaleOrdinal().range(data.map((item) => item.color));

    // Create the pie generator
    const pie = d3.pie().value((item) => item.value);

    // Create the arc generator
    const arc = d3
      .arc()
      .innerRadius(this.hole) // For a pie chart (0 for no hole, >0 for a donut chart)
      .outerRadius(this.radius);

    // Bind data to pie slices
    this.svg
      .select("g")
      .selectAll()
      .data(pie(data))
      .join("path")
      .attr("d", arc)
      .attr("fill", color)
      .attr("stroke", "white")
      .style("stroke-width", "2px");

    if (!this.tooltip.empty()) {
      this.enableTooltip(
        "path",
        (d) => `<strong>${d.data.label}</strong><br>${d.value}`,
      );
    }

    // Cache rendered data
    this.data = data;
  }
}
