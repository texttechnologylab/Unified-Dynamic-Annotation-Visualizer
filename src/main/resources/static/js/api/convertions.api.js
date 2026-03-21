import { api } from "./clients.js";

export async function getTikz(type, svg, data, meta) {
  return await api.post("/convertions/tikz", { type, svg, data, meta });
}

export async function getCsv(type, data, meta) {
  return await api.post("/convertions/csv", { type, data, meta });
}
