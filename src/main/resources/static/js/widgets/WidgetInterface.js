import { getData } from "../api/data.api.js";
import ControlsHandler from "../pages/view/toolbar/ControlsHandler.js";
import ExportHandler from "../pages/view/toolbar/ExportHandler.js";
import state from "../pages/view/utils/viewState.js";

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

  init() {
    throw new Error("Method init() not implemented.");
  }

  render() {
    throw new Error("Method render() not implemented.");
  }

  rerender(fetch = false) {
    if (fetch) {
      this.fetch().then((data) => this.render(data));
    } else {
      this.render(this.data);
    }
  }
}
