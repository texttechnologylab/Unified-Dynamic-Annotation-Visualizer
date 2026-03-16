export default class MapCoordinates {
  static token = "MC";
  static description = `
  A generator for storing labeled and color-coded positions within a map or spatial environment.
  <br> Compatible with: <strong>Line Chart</strong>`;
  static defaultConfig = {
    name: "New MapCoordinates",
    type: "MapCoordinates",
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
