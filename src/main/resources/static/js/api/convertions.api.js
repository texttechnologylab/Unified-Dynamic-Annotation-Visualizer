import { api } from "./clients.js";

export async function getTikz(svg) {
  return await api.post("/convertions/tikz", { svg });
}
