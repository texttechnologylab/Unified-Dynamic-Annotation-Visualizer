import { getData } from "../api/data.api.js";
import ControlsHandler from "../pages/view/toolbar/ControlsHandler.js";
import ExportHandler from "../pages/view/toolbar/ExportHandler.js";
import state from "../pages/view/utils/viewState.js";
import WidgetPagination from "../shared/classes/WidgetPagination.js";

export default class WidgetInterface {
  static defaultConfig;
  static formConfig;

  constructor(root, config) {
    this.root = d3.select(root);
    this.config = config;
    this.data = null;

    this.setTitle(config.title);

    this.filter = {};
    this.controls = new ControlsHandler(this);
    this.exports = new ExportHandler(this);

    this.pagination = new WidgetPagination({
      container: this.root.select(".dv-chart-area").node(),
      onPageChange: () => this.rerender(true),
    });
  }

  setTitle(title) {
    this.root.select(".dv-toolbar-title").attr("title", title).text(title);
  }

  async fetch(all = false) {
    const { pipeline, generator, type } = this.config;
    const page = all ? 1 : this.pagination.currentPage;
    const size = all ? this.pagination.elements.length : 1;

    try {
      const result = await getData(pipeline, generator.id, type, page, size, {
        corpus: state.corpusFilter.filter,
        chart: this.filter,
      });
      this.root.select(".dv-chart-area .dv-chart-message").remove();
      this.root.select(".dv-chart-area svg").style("display", null);
      return result;
    } catch (error) {
      this.showMessage(error.message || "Data could not be loaded");
      throw error;
    }
  }

  // Replaces the chart with a message, e.g. when its data cannot be loaded
  showMessage(text) {
    const area = this.root.select(".dv-chart-area");
    // the chart's svg keeps its size, so it is hidden rather than emptied
    area.select("svg").style("display", "none");
    area.select(".dv-chart-message").remove();
    area
      .append("div")
      .attr("class", "dv-chart-message p-3 text-muted")
      .text(text);
  }

  clear() {
    throw new Error("Method clear() not implemented.");
  }

  init() {
    throw new Error("Method init() not implemented.");
  }

  render() {
    throw new Error("Method render() not implemented.");
  }

  rerender(fetch = false) {
    if (fetch) {
      // fetch() has already shown the message when it fails
      this.fetch()
        .then(({ data }) => this.render(data[0]))
        .catch(() => {});
    } else {
      this.render(this.data);
    }
  }

  async export(all = false) {
    if (this.data == null) {
      throw new Error("Nothing to export, the widget has no data");
    }

    let items = [{ json: this.data }];

    if (all && this.pagination.elements.length > 1) {
      const { data } = await this.fetch(true);

      items = data.map((dataset) => ({ json: dataset.data[0] }));
    }

    return {
      items,
      meta: {
        corpus: state.corpusFilter.filter,
        chart: this.filter,
      },
    };
  }
}
