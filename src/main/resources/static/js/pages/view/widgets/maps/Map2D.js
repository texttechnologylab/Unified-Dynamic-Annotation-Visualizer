import D3Visualization from "../D3Visualization.js";
import ExportHandler from "../../toolbar/ExportHandler.js";

export default class Map2D extends D3Visualization {
  constructor(root, endpoint, {}) {
    super(root, endpoint, { top: 0, right: 0, bottom: 0, left: 0 });

    this.exports = new ExportHandler(this.root.select(".dv-dropdown-menu"), [
      "svg",
      "png",
      "csv",
      "json",
    ]);
  }

  async init() {
    const data = await this.fetch();
    this.render(data);
  }

  render(data) {
    this.clear();

    // https://raw.githubusercontent.com/holtzy/D3-graph-gallery/master/DATA/world.geojson
    d3.json("/js/pages/view/widgets/maps/world.geojson").then((world) => {
      // Map and projection
      const projection = d3
        .geoEquirectangular()
        .fitSize([this.width, this.height], world);

      // A path generator
      const path = d3.geoPath().projection(projection);

      // Draw the map
      this.svg
        .select("g")
        .selectAll("path")
        .data(world.features)
        .join("path")
        .attr("d", path)
        .attr("fill", "#b8b8b8")
        .style("stroke", "#fff")
        .style("stroke-width", 0.1);

      // Draw the data
      this.svg
        .select("g")
        .selectAll()
        .data(data)
        .join("path")
        .attr("d", path)
        .style("fill", "none")
        .style("stroke", (item) => item.color)
        .style("stroke-width", 2);

      if (!this.tooltip.empty()) {
        const zoom = d3
          .zoom()
          .scaleExtent([0.8, 18])
          .translateExtent([
            [0, 0],
            [this.width, this.height],
          ])
          .on("zoom", (event) => {
            this.svg.select("g").attr("transform", event.transform);
          });
        this.svg.call(zoom);

        this.svg
          .selectAll("path")
          .on("mouseover", (event) => this.mouseover(event.currentTarget))
          .on("mousemove", (event, data) =>
            this.mousemove(
              event.pageY,
              event.pageX + 20,
              `<strong>${data.label}</strong>`
            )
          )
          .on("mouseleave", (event) => this.mouseleave(event.currentTarget));
      }

      // Pass data to export handler
      this.exports.update(this.filter, data, this.svg.node());

      this.cachedData = data;
    });
  }
}
