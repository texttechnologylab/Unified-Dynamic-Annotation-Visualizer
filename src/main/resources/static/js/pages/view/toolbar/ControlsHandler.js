import { minOf, maxOf, randomId } from "../../../shared/modules/utils.js";

export default class ControlsHandler {
  constructor(node, icons) {
    this.node = node;
    this.icons = {
      asc: "bi bi-sort-up",
      desc: "bi bi-sort-down",
      includes: "bi bi-braces-asterisk",
      regex: "bi bi-regex",
      ...icons,
    };
  }

  appendInputRadio(title, radios, onchange) {
    const values = ["", radios[0]];
    const name = randomId(radios.join("-"));

    this.node.append("label").attr("class", "dv-label").text(title);

    const group = this.node.append("div").attr("class", "dv-input-group");

    // Append text input
    group
      .append("input")
      .attr("class", "dv-text-input")
      .attr("type", "text")
      .attr("placeholder", "Press Enter to apply")
      .on("change", (event) => {
        const input = event.target.value.trim();
        if (input !== "") {
          values[0] = input;
          onchange(...values);
        }
      });

    const segments = group.append("div").attr("class", "dv-segments");

    // Append radio buttons
    radios.forEach((radio, index) => {
      segments
        .append("input")
        .attr("type", "radio")
        .attr("id", name + index)
        .attr("class", "dv-segment-input")
        .attr("name", name)
        .property("checked", index === 0)
        .on("change", () => {
          values[1] = radio;
          onchange(...values);
        });
      // Append icon
      segments
        .append("label")
        .attr("for", name + index)
        .attr("title", radio)
        .attr("class", "dv-segment-label")
        .append("i")
        .attr("class", this.icons[radio]);
    });
  }

  appendSelectRadio(title, options, radios, onchange) {
    const values = [options[0], radios[0]];
    const name = randomId(radios.join("-"));

    this.node.append("label").attr("class", "dv-label").text(title);

    const group = this.node.append("div").attr("class", "dv-input-group");

    // Append select
    const select = group
      .append("select")
      .attr("class", "dv-select")
      .on("change", (event) => {
        values[0] = event.target.value;
        onchange(...values);
      });
    options.forEach((option) => select.append("option").text(option));

    const segments = group.append("div").attr("class", "dv-segments");

    // Append radio buttons
    radios.forEach((radio, index) => {
      segments
        .append("input")
        .attr("type", "radio")
        .attr("id", name + index)
        .attr("class", "dv-segment-input")
        .attr("name", name)
        .property("checked", index === 0)
        .on("change", () => {
          values[1] = radio;
          onchange(...values);
        });
      // Append icon
      segments
        .append("label")
        .attr("for", name + index)
        .attr("title", radio)
        .attr("class", "dv-segment-label")
        .append("i")
        .attr("class", this.icons[radio]);
    });
  }

  appendSwitch(name, onchange) {
    this.node
      .append("div")
      .attr("class", "form-check form-switch")
      .append("label")
      .attr("class", "form-check-label")
      .text(name)
      .append("input")
      .attr("type", "checkbox")
      .attr("class", "form-check-input")
      .attr("value", name)
      .property("checked", true)
      .on("change", (event) => onchange(event.target.value));
  }

  appendDoubleSlider(min, max, onchange) {
    const values = [min, max];

    const label = this.node
      .append("label")
      .attr("class", "dv-label")
      .text(`Range: ${min} - ${max}`);

    const group = this.node.append("div").attr("class", "dv-input-group");

    // Append first slider
    group
      .append("input")
      .attr("type", "range")
      .attr("class", "dv-slider-double")
      .attr("min", min)
      .attr("max", max)
      .attr("value", max)
      .on("input", (event) => {
        values[1] = parseInt(event.target.value);
        label.text(`Range: ${minOf(values)} - ${maxOf(values)}`);
      })
      .on("change", () => {
        onchange(minOf(values), maxOf(values));
      });

    // Append second slider
    group
      .append("input")
      .attr("type", "range")
      .attr("class", "dv-slider-double")
      .attr("min", min)
      .attr("max", max)
      .attr("value", min)
      .on("input", (event) => {
        values[0] = parseInt(event.target.value);
        label.text(`Range: ${minOf(values)} - ${maxOf(values)}`);
      })
      .on("change", () => {
        onchange(minOf(values), maxOf(values));
      });
  }
}
