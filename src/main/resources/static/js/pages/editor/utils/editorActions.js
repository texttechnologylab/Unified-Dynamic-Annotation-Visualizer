import { deepClone, randomId } from "../../../shared/modules/utils.js";
import state from "./editorState.js";
import WidgetController from "../controller/WidgetController.js";
import SourceController from "../controller/SourceController.js";
import GeneratorController from "../controller/GeneratorController.js";

export function loadSources(configs) {
  const container = document.querySelector(".dv-sources-container");

  for (const config of configs) {
    const controller = createSource(config);

    container.prepend(controller.root);
    controller.init();
  }
}

export function saveSources() {
  const sources = structuredClone(state.sources);

  for (const generator of state.generators) {
    const { source: id, ...rest } = generator;
    const source = sources.find((item) => item.id === id);

    source.createsGenerators.push(rest);
  }

  return sources;
}

export function createSource(config) {
  const source = deepClone(config);
  source.id = source.id || randomId("Source");

  state.sources.push(source);

  return new SourceController(source);
}

export function removeSource(source) {
  state.generators = state.generators.filter((g) => g.source !== source.id);
  state.sources.splice(state.sources.indexOf(source), 1);
}

export function createGenerator(config, source) {
  const generator = deepClone(config);
  generator.id = generator.id || randomId(generator.type);
  generator.source = source;

  state.generators.push(generator);

  return new GeneratorController(generator);
}

export function removeGenerator(generator) {
  state.generators.splice(state.generators.indexOf(generator), 1);
}

export function createWidget(item) {
  Object.assign(item, deepClone(item, ["el", "grid"]));
  item.id = item.id || randomId(item.type);

  item.el.classList.remove("dv-available-widget-draggable");
  item.el.querySelector("i")?.remove();

  return new WidgetController(item);
}

export function getGeneratorOptions(type) {
  const configs = state.generators.filter((config) => config.type === type);

  return configs.map((generator) => {
    return { label: generator.name, value: generator.id };
  });
}
