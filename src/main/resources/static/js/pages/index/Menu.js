import { deletePipeline } from "../../api/pipelines.api.js";
import { modal } from "../../shared/classes/Modal.js";
import fileInput from "../../shared/modules/fileInput.js";

export default class Menu {
  constructor() {}

  init() {
    fileInput.init(modal);

    document.querySelectorAll("[data-dv-toggle='modal']").forEach((node) => {
      node.addEventListener("click", () => {
        modal.confirm(
          "Delete " + node.dataset.pipeline,
          "Do you want to delete this pipeline?",
          () => this.removePipeline(node.dataset.pipeline),
        );
      });
    });
  }

  removePipeline(id) {
    deletePipeline(id).then(() => this.removeItem(id));
  }

  removeItem(id) {
    document.querySelector("#pipeline-" + id).remove();
  }
}
