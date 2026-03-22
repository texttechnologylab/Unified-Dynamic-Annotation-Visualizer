import ControlsHandler from "../../pages/view/toolbar/ControlsHandler.js";
import ExportHandler from "../../pages/view/toolbar/ExportHandler.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";
import { getData } from "../../api/data.api.js";
import state from "../../pages/view/utils/viewState.js";

export default class ScrollTable {
  static defaultConfig = {
    type: "ScrollTable",
    title: "Table",
    generator: { id: "" },
    options: {
      numbers: true,
    },
    icon: "bi bi-table",
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
      options: () => getGeneratorOptions(),
    },
    "options.numbers": {
      type: "switch",
      label: "Row numbers",
    },
  };
  static previewData = [
    ["Heading 1", "Heading 2", "Heading 3"],
    ["Cell 4", "Cell 5", "Cell 6"],
    ["Cell 7", "Cell 8", "Cell 9"],
    ["Cell 10", "Cell 11", "Cell 12"],
    ["Cell 13", "Cell 14", "Cell 15"],
    ["Cell 16", "Cell 17", "Cell 18"],
    ["Cell 19", "Cell 20", "Cell 21"],
    ["Cell 22", "Cell 23", "Cell 24"],
    ["Cell 25", "Cell 26", "Cell 27"],
    ["Cell 28", "Cell 29", "Cell 30"],
    ["Cell 31", "Cell 32", "Cell 33"],
  ];

  constructor(root, config) {
    this.root = d3.select(root);
    this.config = config;

    this.setTitle(this.config.title);

    this.div = this.root.select(".dv-chart-area").append("div");
    this.data = null;

    this.filter = {};
    this.controls = new ControlsHandler(this);
    this.exports = new ExportHandler(this);

    this.numbers = config.options.numbers || true;
  }

  setTitle(title) {
    this.root.select(".dv-toolbar-title").attr("title", title).text(title);
  }

  async fetch() {
    const { pipeline, generator, type } = this.config;

    return await getData(pipeline, generator.id, type, {
      corpus: state.corpusFilter.filter,
      chart: this.filter,
    });
  }

  clear() {
    this.div.selectAll("*").remove();
    this.div
      .style("width", "100%")
      .style("height", "100%")
      .style("overflow-y", "auto");
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    const values = Object.values(data[0]);

    this.filter = {
      sort: values[0],
      desc: true,
    };
    this.controls.append([
      {
        type: "select",
        label: "Sort by",
        value: this.filter.sort,
        options: values,
        onchange: (event) => {
          this.filter.sort = event.target.value;
          this.fetch().then((data) => this.render(data));
        },
      },
      {
        type: "switch",
        label: "Desc",
        value: this.filter.desc,
        onchange: (event) => {
          this.filter.desc = event.target.checked;
          this.fetch().then((data) => this.render(data));
        },
      },
    ]);
  }

  render(data) {
    this.clear();

    const keys = Object.keys(data[0]);

    let rows = data.map((row) => keys.map((k) => row[k] || ""));
    if (this.numbers) {
      rows = rows.map((row, i) => [i, ...row]);
      rows[0][0] = "#";
    }

    const table = this.div.append("table").attr("class", "dv-scroll-table");

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

    // Cache rendered data
    this.data = data;
  }
}
