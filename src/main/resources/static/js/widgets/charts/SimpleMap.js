import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";
import D3Visualization from "../D3Visualization.js";

export default class SimpleMap extends D3Visualization {
  static defaultConfig = {
    type: "SimpleMap",
    title: "Simple Map",
    generator: { id: "" },
    options: {},
    icon: "bi bi-map",
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
  };
  static previewData = [
    {
      type: "Feature",
      properties: {
        label: "London - New York",
        color: "#00618f",
      },
      geometry: {
        type: "LineString",
        coordinates: [
          [0.1278, 51.5074],
          [-74.0059, 40.7128],
        ],
      },
    },
  ];

  constructor(root, config) {
    super(root, config, { top: 0, right: 0, bottom: 0, left: 0 });
  }

  async fetch() {
    return await d3.json("/data/features.geojson");
  }

  async init() {
    const data = await this.fetch();
    this.render(data);
  }

  render(data) {
    this.clear();

    d3.json("/data/world.geojson").then((world) => {
      // Map and projection
      const projection = d3
        .geoEquirectangular()
        .fitSize([this.width, this.height], world);

      // A path generator
      const path = d3.geoPath().projection(projection);

      // Draw the map
      this.plotArea
        .selectAll("path.world")
        .data(world.features)
        .join("path")
        .attr("class", "world")
        .attr("d", path)
        .attr("fill", "#b8b8b8")
        .style("stroke", "#fff")
        .style("stroke-width", 0.1);

      // Draw the data
      this.plotArea
        .selectAll("path.data")
        .data(data)
        .join("path")
        .attr("class", "data")
        .attr("d", path)
        .style("fill", "none")
        .style("stroke", (d) => d.properties.color)
        .style("stroke-width", 1.5);

      if (!this.tooltip.empty()) {
        const zoom = d3
          .zoom()
          .scaleExtent([1, 30])
          .extent([
            [0, 0],
            [this.width, this.height],
          ])
          .translateExtent([
            [0, 0],
            [this.width, this.height],
          ])
          .on("zoom", (event) => {
            this.plotArea.attr("transform", event.transform);
          });
        this.svg.call(zoom);

        this.enableTooltip("path", ({ properties }) => {
          return `<strong>${properties.label || properties.name}</strong>`;
        });
      }

      this.data = data;
    });
  }
}
