import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";
import WidgetInterface from "../WidgetInterface.js";

export default class HighlightText extends WidgetInterface {
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
      options: () => getGeneratorOptions(["TextFormatting"]),
    },
  };

  constructor(root, config) {
    super(root, config);

    this.tooltip = d3.select(".dv-chart-tooltip");
    this.div = this.root.select(".dv-chart-area").append("div");
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
            this.rerender(true);
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
