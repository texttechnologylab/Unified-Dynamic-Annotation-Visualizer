import { getAnnotations } from "../../../api/annotations.api.js";

export default class Source {
  static defaultConfig = {
    uri: "",
    createsGenerators: [],
  };
  static formConfig = {
    uri: {
      type: "searchselect",
      label: "Annotation type",
      options: { getData: getAnnotations },
    },
  };
}
