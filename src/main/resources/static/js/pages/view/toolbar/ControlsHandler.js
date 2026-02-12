import factories from "../../../shared/modules/inputFactories.js";
import { randomId, createElement } from "../../../shared/modules/utils.js";

export default class ControlsHandler {
  constructor(widget) {
    const root = widget.root.node ? widget.root.node() : widget.root;
    const sidepanel = root.querySelector(".dv-sidepanel-body");

    if (sidepanel) {
      this.controls = createElement("div", { className: "dv-chart-controls" });
      sidepanel.append(this.controls);
    }
  }

  append(configs) {
    if (this.controls) {
      for (const config of configs) {
        const factory = factories[config.type];

        const label = createElement(
          "label",
          { className: "d-flex flex-column" },
          [
            createElement("span", { textContent: config.label }),
            factory(
              randomId(config.type),
              config.value,
              config.options,
              config.onchange,
            ),
          ],
        );

        this.controls.append(label);
      }
    }
  }
}
