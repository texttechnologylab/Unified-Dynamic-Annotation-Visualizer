import Modal from "../../shared/classes/Modal.js";
import fileInput from "../../shared/modules/fileInput.js";

export default class Menu {
  constructor() {
    this.modal = new Modal(document.querySelector(".dv-modal").parentElement);
  }

  init() {
    fileInput.init(this.modal);

    document.querySelectorAll("[data-dv-toggle='modal']").forEach((node) => {
      node.addEventListener("click", () => {
        this.modal.confirm(
          "Delete " + node.dataset.pipeline,
          "Do you want to delete this pipeline?",
          () => this.deletePipeline(node.dataset.pipeline)
        );
      });
    });
  }

  deletePipeline(id) {
    fetch("/api/pipelines/" + id, {
      method: "DELETE",
    }).then(() => this.removeItem(id));
  }

  removeItem(id) {
    document.querySelector("#pipeline-" + id).remove();
  }
}
