import Multiselect from "../classes/Multiselect.js";
import Searchbox from "../classes/Searchbox.js";
import { createElement } from "./utils.js";

export default {
  text(key, value) {
    return createElement("input", {
      name: key,
      type: "text",
      value,
      className: "dv-text-input",
    });
  },

  textarea(key, value) {
    return createElement("textarea", {
      name: key,
      value,
      rows: 3,
      className: "dv-text-input dv-resize-none",
    });
  },

  number(key, value, { min, max }) {
    return createElement("input", {
      name: key,
      type: "number",
      min,
      max,
      value,
      className: "dv-text-input",
    });
  },

  range(key, value, { min, max }) {
    const input = createElement("input", {
      name: key,
      type: "range",
      min,
      max,
      value,
      className: "dv-slider",
    });

    const output = createElement("output", { textContent: value });
    input.addEventListener("input", (e) => {
      output.textContent = e.target.value;
    });

    return createElement("div", { className: "dv-range-slider" }, [
      input,
      output,
    ]);
  },

  select(key, selected, options) {
    return createElement(
      "select",
      { name: key, className: "dv-select" },
      options.map((opt) =>
        createElement("option", {
          textContent: typeof opt === "string" ? opt : opt.label,
          value: typeof opt === "string" ? opt : opt.value,
          selected: (typeof opt === "string" ? opt : opt.value) === selected,
        }),
      ),
    );
  },

  multiselect(key, selected, options) {
    const multiselect = new Multiselect(options);

    return multiselect.create(key, selected);
  },

  searchbox(key, selected, getData) {
    const searchbox = new Searchbox(getData);

    return searchbox.create(key, selected);
  },

  switch(key, on) {
    const input = createElement("input", {
      name: key,
      type: "checkbox",
      checked: on,
    });

    const track = createElement("span", {
      className: "dv-switch-track",
    });

    return createElement("div", { className: "dv-switch" }, [input, track]);
  },
};
