export default class CategoryNumber {
  static token = "CN";
  static description = `
  A generator that maps categories/labels to a numeric value and an associated color.
  <br> Compatible with: <b>Bar Chart, Pie Chart, Table</b>`;
  static defaultConfig = {
    name: "New CategoryNumber",
    type: "CategoryNumber",
    generatorGroup: false,
    settings: {},
    extends: [],
  };
  static formConfig = {
    name: {
      type: "text",
      label: "Name",
    },
  };
}
