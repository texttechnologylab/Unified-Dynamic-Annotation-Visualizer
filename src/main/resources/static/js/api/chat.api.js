import { api } from "./clients.js";

export async function getModels() {
  const response = await api.get("/chat/models");

  return response.json();
}

export async function getCompletion(model, messages) {
  const response = await api.post("/chat/completions", { model, messages });

  return response.json();
}
