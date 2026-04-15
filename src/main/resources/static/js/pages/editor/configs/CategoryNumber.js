export default class CategoryNumber {
  static token = "CN";
  static description = `
  A generator that maps categories/labels to a numeric value and an associated color.
  <br> Compatible with: <b>Bar Chart, Pie Chart, Table</b>`;
  static defaultConfig = {
    name: "New CategoryNumber",
    type: "CategoryNumber",
    generatorGroup: false,
    settings: {
      categoriesWhitelist: [],
      categoriesBlacklist: [],
    },
    extends: [],
  };
  static formConfig = {
    name: {
      type: "text",
      label: "Name",
    },
    "settings.categoriesWhitelist": {
      type: "json",
      label: "Categories whitelist (json)",
      options: {
        rows: 2,
        validator: (json) =>
          Array.isArray(json) && json.every((item) => typeof item === "string"),
        message: "Invalid json. Only an array of strings is allowed.",
      },
    },
    "settings.categoriesBlacklist": {
      type: "json",
      label: "Categories blacklist (json)",
      options: {
        rows: 2,
        validator: (json) =>
          Array.isArray(json) && json.every((item) => typeof item === "string"),
        message: "Invalid json. Only an array of strings is allowed.",
      },
    },
  };
}
