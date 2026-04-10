import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";
import WidgetInterface from "../WidgetInterface.js";

export default class ScrollTable extends WidgetInterface {
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
      options: () => getGeneratorOptions(["CategoryNumber", "MapCoordinates"]),
    },
    "options.numbers": {
      type: "switch",
      label: "Row numbers",
    },
  };

  constructor(root, config) {
    super(root, config);

    this.div = this.root.select(".dv-chart-area").append("div");

    this.numbers = config.options.numbers || true;
  }

  setTitle(title) {
    this.root.select(".dv-toolbar-title").attr("title", title).text(title);
  }

  clear() {
    this.div.selectAll("*").remove();
    this.div
      .style("width", "100%")
      .style("height", "100%")
      .style("overflow-y", "auto");
  }

  async init() {
    const { data, meta } = await this.fetch();
    this.render(data[0]);

    if (meta.total > 1) this.pagination.init(meta.ids);

    const values = Object.values(data[0][0]);

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
          this.rerender(true);
        },
      },
      {
        type: "switch",
        label: "Desc",
        value: this.filter.desc,
        onchange: (event) => {
          this.filter.desc = event.target.checked;
          this.rerender(true);
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
