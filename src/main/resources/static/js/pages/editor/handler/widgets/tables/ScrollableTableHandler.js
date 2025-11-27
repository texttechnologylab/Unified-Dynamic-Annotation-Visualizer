import { createElement } from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import ScrollableTable from "../../../../view/widgets/tables/ScrollableTable.js";
import { prepareGenerators, safeValue } from "../../../utils/actions.js";
import state from "../../../utils/state.js";

export default class ScrollableTableHandler extends FormHandler {
  static defaults = {
    type: "ScrollableTable",
    title: "Table",
    generator: { id: "" },
    options: {
      numbers: true,
    },
    icon: "bi bi-table",
    w: 3,
    h: 3,
  };

  constructor(item) {
    const template = document.querySelector("#default-chart-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
    this.span = this.element.querySelector("span");
  }

  init() {
    this.span.textContent = this.item.title;
    this.initButtons("Table Options", () => {
      state.grid.removeWidget(this.item.el);
    });

    this.showAlert(!this.item.generator.id);

    this.element._chart = new ScrollableTable(
      this.element,
      "",
      this.item.options
    );
    this.element._data = data;
    this.element._chart.render(this.element._data);
  }

  createForm() {
    const generatorOptions = prepareGenerators([]);

    const titleInput = this.createTextInput("title", "Title", this.item.title);
    const generatorInput = this.createSelect(
      "generator",
      "Generator",
      generatorOptions,
      safeValue(generatorOptions, this.item.generator.id)
    );
    const numbersInput = this.createSwitch(
      "numbers",
      "Row numbers",
      this.item.options.numbers
    );

    return createElement("form", { className: "dv-form-column" }, [
      titleInput,
      generatorInput,
      numbersInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.item.title = form.title;
    this.item.generator.id = form.generator;
    this.item.options.numbers = form.numbers === "on";

    this.showAlert(!form.generator);

    // Update title
    this.span.textContent = form.title;

    // Update chart
    this.element._chart.numbers = this.item.options.numbers;
    this.element._chart.render(this.element._data);
  }
}

const data = [
  ["Heading", "Heading", "Heading"],
  ["Cell", "Cell", "Cell"],
  ["Cell", "Cell", "Cell"],
  ["Cell", "Cell", "Cell"],
  ["Cell", "Cell", "Cell"],
  ["Cell", "Cell", "Cell"],
  ["Cell", "Cell", "Cell"],
  ["Cell", "Cell", "Cell"],
  ["Cell", "Cell", "Cell"],
  ["Cell", "Cell", "Cell"],
  ["Cell", "Cell", "Cell"],
];
