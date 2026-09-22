const objectValidator = (json) =>
  typeof json === "object" && json !== null && !Array.isArray(json);
const stringArrayValidator = (json) =>
  Array.isArray(json) && json.every((item) => typeof item === "string");

export default class CategoryNumber {
  static token = "CN";
  static description = `
  A generator that maps categories/labels to a numeric value and an associated color.
  <br> Compatible with: <b>Bar Chart, Pie Chart, Table</b>
  <br> Sources: a UIMA annotation type (counts per feature value) or a JSON file, either
  <code>{"NOUN": 12, "VERB": 7}</code>, <code>{"doc-1": {"NOUN": 12}, ...}</code> or a list of rows
  <code>[{"category": "NOUN", "number": 12, "file": "doc-1"}]</code> (rename fields with the keys mapping).`;
  static defaultConfig = {
    name: "New CategoryNumber",
    type: "CategoryNumber",
    generatorGroup: false,
    settings: {
      categoriesWhitelist: [],
      categoriesBlacklist: [],
      keys: {},
      fixedKeys: {},
      colors: {},
    },
    extends: [],
  };
  static formConfig = {
    name: {
      type: "text",
      label: "Name",
    },
    generatorGroup: {
      type: "switch",
      label: "Generator group (JSON sources: one generator per top-level key)",
    },
    "settings.categoriesWhitelist": {
      type: "json",
      label: "Categories whitelist (json)",
      options: {
        rows: 2,
        validator: stringArrayValidator,
        message: "Invalid json. Only an array of strings is allowed.",
      },
    },
    "settings.categoriesBlacklist": {
      type: "json",
      label: "Categories blacklist (json)",
      options: {
        rows: 2,
        validator: stringArrayValidator,
        message: "Invalid json. Only an array of strings is allowed.",
      },
    },
    "settings.keys": {
      type: "json",
      label: "JSON sources: keys mapping (json), e.g. {\"category\": \"pos\", \"number\": \"count\"}",
      options: {
        rows: 3,
        validator: objectValidator,
        message: "Invalid json mapping.",
      },
    },
    "settings.fixedKeys": {
      type: "json",
      label: "JSON sources: fixed keys (json), e.g. {\"file\": \"corpus-a\"}",
      options: {
        rows: 2,
        validator: objectValidator,
        message: "Invalid json mapping.",
      },
    },
    "settings.colors": {
      type: "json",
      label: "Category colors (json), e.g. {\"NOUN\": \"#4e79a7\"}",
      options: {
        rows: 3,
        validator: objectValidator,
        message: "Invalid json mapping.",
      },
    },
  };
}
