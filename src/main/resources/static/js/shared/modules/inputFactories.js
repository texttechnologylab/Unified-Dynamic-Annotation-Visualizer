import Multiselect from "../classes/Multiselect.js";
import Searchselect from "../classes/Searchselect.js";
import { createElement, maxOf, minOf } from "./utils.js";

export default {
  text(key, value, _, onchange) {
    return createElement("input", {
      name: key,
      type: "text",
      value,
      className: "dv-text-input",
      onchange,
    });
  },

  textarea(key, value, _, onchange) {
    return createElement("textarea", {
      name: key,
      value,
      rows: 3,
      className: "dv-text-input dv-resize-none",
      onchange,
    });
  },

  number(key, value, { min, max }, onchange) {
    return createElement("input", {
      name: key,
      type: "number",
      min,
      max,
      value,
      className: "dv-text-input",
      onchange,
    });
  },

  range(key, value, { min, max }, onchange) {
    const input = createElement("input", {
      name: key,
      type: "range",
      min,
      max,
      value,
      className: "dv-slider",
      onchange,
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

  rangedouble(key, values, { min, max }, onchange) {
    const output = createElement("output", {
      textContent: `${minOf(values)} - ${maxOf(values)}`,
    });

    const sliders = createElement("div", { className: "dv-input-group" }, [
      createElement("input", {
        name: key,
        type: "range",
        min,
        max,
        value: maxOf(values),
        className: "dv-slider-double",
        oninput: (event) => {
          values[1] = parseInt(event.target.value);
          output.textContent = `${minOf(values)} - ${maxOf(values)}`;
        },
        onchange: () => {
          onchange(minOf(values), maxOf(values));
        },
      }),
      createElement("input", {
        name: key,
        type: "range",
        min,
        max,
        value: minOf(values),
        className: "dv-slider-double",
        oninput: (event) => {
          values[0] = parseInt(event.target.value);
          output.textContent = `${minOf(values)} - ${maxOf(values)}`;
        },
        onchange: () => {
          onchange(minOf(values), maxOf(values));
        },
      }),
    ]);

    return createElement("div", {}, [sliders, output]);
  },

  select(key, selected, options, onchange) {
    return createElement(
      "select",
      { name: key, className: "dv-select", onchange },
      options.map((opt) =>
        createElement("option", {
          textContent: typeof opt === "string" ? opt : opt.label,
          value: typeof opt === "string" ? opt : opt.value,
          selected: (typeof opt === "string" ? opt : opt.value) === selected,
        }),
      ),
    );
  },

  multiselect(key, selected, options, _) {
    const multiselect = new Multiselect(options);

    return multiselect.create(key, selected);
  },

  searchselect(key, selected, { getData }, _) {
    const searchselect = new Searchselect(getData);

    return searchselect.create(key, selected);
  },

  switch(key, on, _, onchange) {
    const input = createElement("input", {
      name: key,
      type: "checkbox",
      checked: on,
      onchange,
    });

    const track = createElement("span", {
      className: "dv-switch-track",
    });

    return createElement("div", { className: "dv-switch" }, [input, track]);
  },
};
