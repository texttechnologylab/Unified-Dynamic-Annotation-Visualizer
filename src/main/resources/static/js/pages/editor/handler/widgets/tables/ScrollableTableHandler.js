import {
  createElement,
  getElementDimensions,
} from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import ScrollableTable from "../../../../view/widgets/tables/ScrollableTable.js";

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

  init(modal, grid) {
    this.span.textContent = this.item.title;
    this.initButtons(modal, "Table Options", () =>
      grid.removeWidget(this.item.el)
    );

    this.element._chart = new ScrollableTable(this.element, "", {
      ...getElementDimensions(this.element),
      ...this.item.options,
    });
    this.element._data = data;
    this.element._chart.render(this.element._data);
  }

  createForm() {
    const titleInput = this.createTextInput("title", "Title", this.item.title);
    const generatorInput = this.createTextInput(
      "generator",
      "Generator",
      this.item.generator.id
    );
    const numbersInput = this.createSelect(
      "numbers",
      "Row numbers",
      ["show", "hide"],
      this.item.options.numbers ? "show" : "hide"
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
    this.item.options.numbers = form.numbers === "show";

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
