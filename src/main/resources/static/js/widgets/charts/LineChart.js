import D3Visualization from "../D3Visualization.js";
import { flatData } from "../../shared/modules/utils.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class LineChart extends D3Visualization {
  static defaultConfig = {
    type: "LineChart",
    title: "Line Chart",
    generator: { id: "" },
    options: {
      points: true,
      curve: "curveCatmullRom",
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
      options: () => getGeneratorOptions(["MapCoordinates"]),
    },
    "options.points": {
      type: "switch",
      label: "Draw points",
    },
    "options.curve": {
      type: "select",
      label: "Interpolation",
      options: [
        { label: "Linear", value: "curveLinear" },
        { label: "Basis spline", value: "curveBasis" },
        { label: "Cardinal spline", value: "curveCardinal" },
        { label: "Catmull-Rom spline", value: "curveCatmullRom" },
      ],
    },
  };

  constructor(root, config) {
    super(root, config, { top: 20, right: 30, bottom: 30, left: 40 });

    this.points = config.options.points || true;
    this.curve = config.options.curve || "curveCatmullRom";
  }

  async init() {
    const { data, meta } = await this.fetch();
    this.render(data[0]);

    this.exports.init(meta.total > 1);
    this.pagination.init(meta.ids);

    this.filter = {
      hide: [],
    };
    this.controls.append(
      data[0].map(({ name }) => {
        return {
          type: "switch",
          label: name,
          value: true,
          onchange: (event) => {
            if (event.target.checked) {
              this.filter.hide = this.filter.hide.filter((n) => n !== name);
            } else {
              this.filter.hide.push(name);
            }
            this.rerender(true);
          },
        };
      }),
    );
  }

  render(data) {
    this.clear();

    const coordinates = flatData(data, "coordinates");

    // Add x axis
    const xScale = d3
      .scaleLinear()
      .range([0, this.width])
      .domain(this.domain(coordinates, (item) => item.x));

    // Add y axis
    const yScale = d3
      .scaleLinear()
      .range([this.height, 0])
      .domain(this.domain(coordinates, (item) => item.y));

    const { area, zoom } = this.createAxisZoom([1, 40], {
      bottom: xScale,
      left: yScale,
    });

    const line = d3
      .line()
      .x((item) => xScale(item.x))
      .y((item) => yScale(item.y))
      .curve(d3[this.curve]);

    // Add the line
    area
      .selectAll("path")
      .data(data)
      .join("path")
      .attr("d", (item) => line(item.coordinates))
      .attr("fill", "none")
      .attr("stroke", (item) => item.color)
      .attr("stroke-width", 2);

    // Add the points
    area
      .selectAll("circle")
      .data(coordinates)
      .join("circle")
      .attr("cx", (item) => xScale(item.x))
      .attr("cy", (item) => yScale(item.y))
      .attr("r", 3)
      .attr("fill", this.points ? (item) => item.color : "transparent");

    if (!this.tooltip.empty()) {
      this.svg.call(zoom);
      this.enableTooltip(
        "circle",
        (d) => `<strong>${d.name}</strong><br>x: ${d.x}, y: ${d.y}`,
      );
      this.enableTooltip("path", (d) => `<strong>${d.name}</strong>`);
    }

    // Cache rendered data
    this.data = data;
  }
}
