export default class Source {
  static defaultConfig = {
    uri: "",
    createsGenerators: [],
  };
  static formConfig = {
    uri: {
      type: "text",
      label: "Annotation type",
    },
  };
}
