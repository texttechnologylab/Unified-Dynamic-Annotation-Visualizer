import { deepClone, randomId } from "../../../shared/modules/utils.js";
import getter from "../getter.js";
import SourceHandler from "../handler/source/SourceHandler.js";
import state from "./state.js";

export function loadSources(sources) {
  for (const source of sources) {
    state.sources.push(source);

    for (const generator of source.createsGenerators) {
      generator.source = source.id;
      state.generators.push(generator);
    }

    source.createsGenerators = [];
  }
}

export function createSource() {
  const source = deepClone(SourceHandler.defaults);
  source.id = randomId("Source");

  state.sources.push(source);

  return new SourceHandler(source);
}

export function removeSource(source) {
  state.generators = state.generators.filter((g) => g.source !== source.id);
  state.sources.splice(state.sources.indexOf(source), 1);
}

export function saveSources() {
  const sources = structuredClone(state.sources);

  for (const generator of state.generators) {
    const { token, source: id, ...rest } = generator;
    const source = sources.find((item) => item.id === id);

    source.createsGenerators.push(rest);
  }

  return sources;
}

export function createGenerator(config, source) {
  const generator = deepClone(config);
  generator.id = randomId(generator.type);
  generator.name = "New " + generator.name;
  generator.source = source;

  const HandlerClass = getter.generators[config.type];
  state.generators.push(generator);

  return new HandlerClass(generator);
}

export function removeGenerator(generator) {
  state.generators.splice(state.generators.indexOf(generator), 1);
}

export function prepareGenerators(allowed) {
  const filtered = state.generators.filter((generator) =>
    allowed.includes(generator.type)
  );

  const mapped = filtered.map((generator) => {
    return { label: generator.name, value: generator.id };
  });

  return mapped;
}

export function createWidget(item) {
  Object.assign(item, deepClone(item, ["el", "grid"]));
  item.id = item.id || randomId(item.type);

  const HandlerClass = getter.widgets[item.type];

  item.el.classList.remove("dv-available-widget-draggable");
  item.el.querySelector("i")?.remove();

  return new HandlerClass(item);
}

export function safeValue(list, item) {
  list = list.map((o) => o.value);
  return list.includes(item) ? item : "";
}
