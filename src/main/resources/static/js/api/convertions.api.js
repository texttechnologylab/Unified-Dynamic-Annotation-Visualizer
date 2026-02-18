import { api } from "./clients.js";

export async function getTikz(type, svg, data, meta) {
  return await api.post("/convertions/tikz", { type, svg, data, meta });
}
