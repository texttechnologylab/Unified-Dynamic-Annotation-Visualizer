import { modal } from "../../../shared/classes/Modal.js";

export function identifierValid(config) {
  // Check for empty id
  const valid = config.id.trim() !== "";

  if (!valid) {
    modal.alert(
      "Missing Identifier",
      "Please provide an identifier for the pipeline."
    );
  }

  return valid;
}

export function generatorsValid(config) {
  // Check for missing sources
  const missing = config.generators.filter((gen) => gen.source.trim() === "");
  const valid = missing.length === 0;

  if (!valid) {
    modal.alert(
      "Missing Sources",
      "The following generators have no source: " +
        missing.map((g) => g.name).join(", ")
    );
  }

  return valid;
}

export function widgetsValid(config) {
  if (config.widgets.length > 0) {
    // Check for empty or removed generators
    const missing = config.widgets.filter((widget) => {
      if (widget.generator) {
        return !config.generators.find((gen) => gen.id === widget.generator.id);
      }
    });
    const valid = missing.length === 0;

    if (!valid) {
      modal.alert(
        "Missing Generators",
        "The following widgets have no generator assigned: " +
          missing.map((w) => w.title).join(", ")
      );
    }

    return valid;
  } else {
    modal.alert(
      "No Widgets Found",
      "Place at least one widget at the grid area."
    );

    return false;
  }
}
