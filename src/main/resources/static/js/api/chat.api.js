import { api } from "./clients.js";

export async function getModels() {
  return await api.get("/chat/models");
}

export async function getCompletion(model, messages) {
  return await api.post("/chat/completions", { model, messages });
}
