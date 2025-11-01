import state from "./state.js";

export function removeGenerator(generator) {
  state.generators.splice(state.generators.indexOf(generator), 1);
}

export function removeWidget(widget) {
  state.widgets.splice(state.widgets.indexOf(widget), 1);
}

export function prepareGenerators(available, allowed) {
  const filtered = available.filter((generator) =>
    allowed.includes(generator.type)
  );

  const mapped = filtered.map((generator) => {
    return { label: generator.name, value: generator.id };
  });

  return mapped;
}

export function safeValue(list, item) {
  list = list.map((o) => o.value);
  return list.includes(item) ? item : "";
}
