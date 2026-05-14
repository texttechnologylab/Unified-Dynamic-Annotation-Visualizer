import { getAnnotations } from "../../../api/annotations.api.js";

export default class Source {
  static defaultConfig = {
    name: "Source",
    uri: "",
    settings: {
      sourceFilesWhitelist: [],
      sourceFilesBlacklist: [],
    },
  };
  static formConfig = {
    name: {
      type: "text",
      label: "Name",
    },
    uri: {
      type: "searchselect",
      label: "Annotation type",
      options: {
        headers: ["Annotation", "#"],
        keys: ["annotation", "rowCount"],
        getData: getAnnotations,
      },
    },
    "settings.sourceFilesWhitelist": {
      type: "json",
      label: "Source files whitelist (json)",
      options: {
        rows: 2,
        validator: (json) =>
          Array.isArray(json) && json.every((item) => typeof item === "string"),
        message: "Invalid json. Only an array of strings is allowed.",
      },
    },
    "settings.sourceFilesBlacklist": {
      type: "json",
      label: "Source files blacklist (json)",
      options: {
        rows: 2,
        validator: (json) =>
          Array.isArray(json) && json.every((item) => typeof item === "string"),
        message: "Invalid json. Only an array of strings is allowed.",
      },
    },
  };
}
