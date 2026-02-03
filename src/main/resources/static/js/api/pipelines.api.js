import { api } from "./HTTPClient.js";

export async function getPipelines() {
  const response = await api.get("/pipelines");

  return response.json();
}

export async function getPipeline(id) {
  const response = await api.get(`/pipelines/${id}`);

  return response.json();
}

export async function createPipeline(config) {
  const response = await api.post("/pipelines", config);

  return response.json();
}

export async function updatePipeline(config) {
  const response = await api.put("/pipelines", config);

  return response.json();
}

export async function deletePipeline(id) {
  const response = await api.delete(`/pipelines/${id}`);

  return response.text();
}
