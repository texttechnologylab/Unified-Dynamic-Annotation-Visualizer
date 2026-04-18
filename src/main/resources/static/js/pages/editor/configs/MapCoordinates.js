export default class MapCoordinates {
  static token = "MC";
  static description = `
  A generator for storing labeled and color-coded positions within a map or spatial environment.
  <br> Compatible with: <b>Line Chart, Simple Map, Network Graph, Table</b>`;
  static defaultConfig = {
    name: "New MapCoordinates",
    type: "MapCoordinates",
    generatorGroup: false,
    settings: {
      keysMap: {},
      fixedKeys: {},
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
      label: "Generator group",
    },
    "settings.keysMap": {
      type: "json",
      label: "Keys mapping (json)",
      options: {
        rows: 5,
        message: "Invalid json mapping.",
      },
    },
    "settings.fixedKeys": {
      type: "json",
      label: "Fixed keys (json)",
      options: {
        rows: 5,
        message: "Invalid json mapping.",
      },
    },
  };
}
