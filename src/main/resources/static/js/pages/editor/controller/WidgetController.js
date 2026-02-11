import state from "../utils/state.js";
import getter from "../../../widgets/widgets.js";
import FormBuilder from "../../../shared/classes/FormBuilder.js";
import { createTemplateElement } from "../../../shared/modules/utils.js";

export default class WidgetController {
  constructor(item) {
    this.root = createTemplateElement(
      item.src ? "#default-static-template" : "#default-chart-template",
    );
    this.span = this.root.querySelector("span");
    this.item = item;
    this.widget = null;
  }

  setTitle(title) {
    this.item.title = title;
    if (this.span) this.span.textContent = title;
  }

  setSrc(src) {
    this.item.src = src;
    this.widget.src = src;
  }

  setOptions(options) {
    this.item.options = options;

    for (const [key, value] of Object.entries(options)) {
      this.widget[key] = value;
    }
  }

  init() {
    this.setTitle(this.item.title);

    const Widget = getter[this.item.type];
    const builder = new FormBuilder(
      state.modal,
      "Widget Options",
      Widget.formConfig,
    );
    const buttons = this.root.querySelectorAll("button");

    this.widget = new Widget(this.root, this.item.src, this.item.options);
    this.widget.render(Widget.previewData || this.item.src);

    buttons[0].addEventListener("click", () => {
      const { title, generator, src, options } = this.item;

      const config = generator
        ? { title, generator, options }
        : { title, src, options };

      builder.buildForm(config, ({ title, generator, src, options }) => {
        this.setTitle(title);
        if (generator) {
          this.item.generator = generator;
        } else {
          this.setSrc(src);
        }
        this.setOptions(options);

        this.widget.render(Widget.previewData || this.item.src);
      });
    });
    buttons[1].addEventListener("click", () => {
      state.grid.removeWidget(this.item.el);
    });
  }
}
