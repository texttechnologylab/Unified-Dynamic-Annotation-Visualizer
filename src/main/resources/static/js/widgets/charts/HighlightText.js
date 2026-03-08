import { getData } from "../../api/data.api.js";
import ControlsHandler from "../../pages/view/toolbar/ControlsHandler.js";
import ExportHandler from "../../pages/view/toolbar/ExportHandler.js";
import state from "../../pages/view/utils/viewState.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class HighlightText {
  static defaultConfig = {
    type: "HighlightText",
    title: "Highlight Text",
    generator: { id: "" },
    options: {},
    icon: "bi bi-card-text",
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
      options: () => getGeneratorOptions("TextFormatting"),
    },
  };
  static previewData = {
    spans: [
      {
        text: "Lorem ipsum dolor sit amet, consetetur sadipscing elitr",
        style: "text-decoration: underline 2px #00618f;",
      },
      {
        text: ", sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. ",
      },
      {
        text: "Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.",
        style: "text-decoration: underline 2px #3a4856;",
      },
      {
        text: " Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. ",
      },
      {
        text: "Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.",
        style: "text-decoration: underline 2px #9eadbd;",
      },
    ],
  };

  constructor(root, config) {
    this.root = d3.select(root);
    this.config = config;

    this.setTitle(this.config.title);

    this.tooltip = d3.select(".dv-chart-tooltip");
    this.div = this.root.select(".dv-chart-area").append("div");
    this.data = null;

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

  clear() {
    this.div.selectAll("*").remove();
    this.div
      .style("width", "100%")
      .style("height", "100%")
      .style("padding", "0.375rem 0.75rem")
      .style("overflow-y", "auto");
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    this.filter = {
      hide: [],
    };
    this.controls.append(
      data.datasets.map(({ name }) => {
        return {
          type: "switch",
          label: name.split(".").slice(-2).join("."),
          value: true,
          onchange: (event) => {
            if (event.target.checked) {
              this.filter.hide = this.filter.hide.filter((n) => n !== name);
            } else {
              this.filter.hide.push(name);
            }
            this.fetch().then((data) => this.render(data));
          },
        };
      }),
    );
  }

  render(data) {
    this.clear();

    this.div
      .selectAll("span")
      .data(data.spans)
      .join("span")
      .text((d) => d.TEXT || d.text)
      .attr("class", (d) => d.label && "labeled")
      .attr("style", (d) => d.style || null);

    if (!this.tooltip.empty()) {
      this.enableTooltip("span.labeled", (d) => {
        return d.label
          .map(
            (l) =>
              `<span style="font-weight: bold; ${l.style}">${l.text}</span>`,
          )
          .join(", ");
      });
    }

    // Cache rendered data
    this.data = data;
  }

  mouseover(event) {
    this.tooltip.style("opacity", 0.9);
    d3.select(event.currentTarget).style("opacity", 0.8);
  }

  mousemove(event, content) {
    this.tooltip
      .html(DOMPurify.sanitize(content))
      .style("top", event.pageY + "px")
      .style("left", event.pageX + 20 + "px");
  }

  mouseleave(event) {
    this.tooltip.style("opacity", 0);
    d3.select(event.currentTarget).style("opacity", 1);
  }

  enableTooltip(selector, content) {
    this.div
      .selectAll(selector)
      .on("mouseover", (event) => this.mouseover(event))
      .on("mousemove", (event, d) => this.mousemove(event, content(d)))
      .on("mouseleave", (event) => this.mouseleave(event));
  }
}
