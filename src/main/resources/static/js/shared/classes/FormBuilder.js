import inputs from "../modules/inputFactories.js";
import { createElement, isObject } from "../modules/utils.js";

export default class FormBuilder {
  constructor(modal, title, formConfig) {
    this.modal = modal;
    this.title = title;
    this.formConfig = formConfig;
  }

  buildForm(config, onSave) {
    const fields = this.parseConfig(config);
    const form = createElement("form", { className: "dv-form-column" }, fields);

    this.modal.render(this.title, form, () => {
      onSave(this.writeToConfig(config, new FormData(form)));
    });
  }

  parseConfig(config, path = []) {
    const fields = [];

    for (const [key, value] of Object.entries(config)) {
      if (isObject(value)) {
        fields.push(...this.parseConfig(value, [...path, key]));
      } else {
        fields.push(this.createInputField([...path, key].join("."), value));
      }
    }

    return fields;
  }

  writeToConfig(config, formData, path = []) {
    for (const [key, value] of Object.entries(config)) {
      if (isObject(value)) {
        config[key] = this.writeToConfig(value, formData, [...path, key]);
      } else {
        config[key] = this.parseValue([...path, key].join("."), formData);
      }
    }

    return config;
  }

  createInputField(key, value) {
    const field = this.formConfig[key];

    if (field) {
      const createInput = inputs[field.type];
      const options =
        typeof field.options === "function" ? field.options() : field.options;

      const label = createElement(
        "label",
        { className: "d-flex flex-column" },
        [
          createElement("span", { textContent: field.label }),
          createInput(key, value, options),
        ],
      );

      return label;
    } else {
      console.warn(`FormBuilder: no config for "${key}"`);
    }
    return "";
  }

  parseValue(key, formData) {
    const config = this.formConfig[key];

    switch (config.type) {
      case "number":
      case "range":
        return Number(formData.get(key));

      case "switch":
        return formData.has(key);

      default:
        return formData.get(key);
    }
  }
}
