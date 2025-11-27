import D3Visualization from "../D3Visualization.js";
import ExportHandler from "../../toolbar/ExportHandler.js";

export default class ScrollableTable extends D3Visualization {
  constructor(root, endpoint, { numbers = true }) {
    super(root, endpoint, { top: 2, right: 2, bottom: 2, left: 2 });

    this.exports = new ExportHandler(this.root.select(".dv-dropdown-menu"), [
      "csv",
      "json",
    ]);

    this.svg.remove();
    this.div = this.root.select(".dv-chart-area").append("div");

    this.numbers = numbers;
  }

  clear() {
    this.div.selectAll("*").remove();
    this.div
      .style("width", this.width + this.margin.left + this.margin.right + "px")
      .style(
        "height",
        this.height + this.margin.top + this.margin.bottom + "px"
      )
      .style("padding-top", this.margin.top + "px")
      .style("padding-right", this.margin.right + "px")
      .style("padding-bottom", this.margin.bottom + "px")
      .style("padding-left", this.margin.left + "px")
      .style("overflow-y", "auto");
  }

  async init() {
    const data = await this.fetch();
    this.render(data);
  }

  render(data) {
    this.clear();

    let rows = data;
    if (this.numbers) {
      rows = data.map((row, i) => [i, ...row]);
      rows[0][0] = "#";
    }

    const table = this.div.append("table").attr("class", "dv-scrollable-table");

    // Append table head (first row)
    table
      .append("thead")
      .append("tr")
      .selectAll("th")
      .data(rows[0])
      .join("th")
      .text((d) => d);

    // Append table body (remaining rows)
    table
      .append("tbody")
      .selectAll("tr")
      .data(rows.slice(1)) // skip header
      .join("tr")
      .selectAll("td")
      .data((d) => d)
      .join("td")
      .text((d) => d);

    this.exports.update(this.filter, data, null);

    this.cachedData = data;
  }
}
