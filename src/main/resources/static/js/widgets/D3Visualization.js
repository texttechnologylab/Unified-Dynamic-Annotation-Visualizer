import { getData } from "../api/data.api.js";
import ControlsHandler from "../pages/view/toolbar/ControlsHandler.js";
import ExportHandler from "../pages/view/toolbar/ExportHandler.js";
import state from "../pages/view/utils/viewState.js";
import { debounce, randomId } from "../shared/modules/utils.js";

export default class D3Visualization {
  constructor(root, config, margin) {
    this.root = d3.select(root);
    this.config = config;

    this.setTitle(this.config.title);

    const { width, height } = this.getDimensions();
    this.width = width - margin.left - margin.right;
    this.height = height - margin.top - margin.bottom;
    this.margin = margin;

    this.tooltip = d3.select(".dv-chart-tooltip");
    this.svg = this.root.select(".dv-chart-area").append("svg");
    this.data = null;

    this.filter = {};
    this.controls = new ControlsHandler(this);
    this.exports = new ExportHandler(this);

    // Re-render chart on resize of container
    const observer = new ResizeObserver(
      debounce(() => {
        if (this.data) {
          const { width, height } = this.getDimensions();
          if (width && height) this.resize(width, height);
        }
      }, 10),
    );
    observer.observe(root);
  }

  setTitle(title) {
    this.root.select(".dv-toolbar-title").attr("title", title).text(title);
  }

  getDimensions() {
    const area = this.root.select(".dv-chart-area").node();
    const rect = area.getBoundingClientRect();

    return { width: rect.width, height: rect.height };
  }

  resize(width, height) {
    this.width = width - this.margin.left - this.margin.right;
    this.height = height - this.margin.top - this.margin.bottom;

    this.render(this.data);
  }

  async fetch() {
    const { pipeline, generator, type } = this.config;

    return await getData(pipeline, generator.id, type, {
      corpus: state.corpusFilter.filter,
      chart: this.filter,
    });
  }

  clear() {
    this.svg.selectAll("*").remove();
    this.svg
      .attr("width", this.width + this.margin.left + this.margin.right)
      .attr("height", this.height + this.margin.top + this.margin.bottom);
    this.plotArea = this.svg
      .append("g")
      .attr("transform", `translate(${this.margin.left}, ${this.margin.top})`);
  }

  init() {
    throw new Error("Method init() not implemented.");
  }

  render() {
    throw new Error("Method render() not implemented.");
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
    this.svg
      .selectAll(selector)
      .on("mouseover", (event) => this.mouseover(event))
      .on("mousemove", (event, d) => this.mousemove(event, content(d)))
      .on("mouseleave", (event) => this.mouseleave(event));
  }

  domain(data, fn, padding = 0.05) {
    const [min, max] = d3.extent(data, fn);
    const range = max - min;

    return [min - range * padding, max + range * padding];
  }

  createAxis(scale, axisGenerator, transform) {
    if (!scale) return null;

    const g = this.plotArea.append("g");
    if (transform) g.attr("transform", transform);

    return g.call(axisGenerator(scale));
  }

  createAxisZoom(extent, scales) {
    const clipId = randomId("clip");

    // Clip path
    this.plotArea
      .append("clipPath")
      .attr("id", clipId)
      .append("rect")
      .attr("width", this.width)
      .attr("height", this.height);

    // Axes
    const axes = {
      bottom: this.createAxis(
        scales.bottom,
        d3.axisBottom,
        `translate(0, ${this.height})`,
      ),
      left: this.createAxis(scales.left, d3.axisLeft),
      top: this.createAxis(scales.top, d3.axisTop),
      right: this.createAxis(
        scales.right,
        d3.axisRight,
        `translate(${this.width}, 0)`,
      ),
    };

    // Zoom area
    const area = this.plotArea
      .append("g")
      .attr("clip-path", `url(#${clipId})`)
      .append("g")
      .attr("class", "zoom-area");

    // Zoom behavior
    const zoom = d3
      .zoom()
      .scaleExtent(extent)
      .extent([
        [0, 0],
        [this.width, this.height],
      ])
      .translateExtent([
        [0, 0],
        [this.width, this.height],
      ])
      .on("zoom", (event) => {
        const t = event.transform;

        // Move chart content
        area.attr("transform", t);

        // Rescale axes
        if (axes.bottom)
          axes.bottom.call(d3.axisBottom(t.rescaleX(scales.bottom)));
        if (axes.left) axes.left.call(d3.axisLeft(t.rescaleY(scales.left)));
        if (axes.top) axes.top.call(d3.axisTop(t.rescaleX(scales.top)));
        if (axes.right) axes.right.call(d3.axisRight(t.rescaleY(scales.right)));
      });

    return { area, zoom };
  }
}
