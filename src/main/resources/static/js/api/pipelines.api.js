import { api } from "./clients.js";

export async function getPipelines() {
  return await api.get("/pipelines");
}

export async function getPipeline(id) {
  return await api.get(`/pipelines/${id}`);
}

export async function createPipeline(config) {
  return await api.post("/pipelines", config);
}

export async function updatePipeline(config) {
  return await api.put("/pipelines", config);
}

export async function deletePipeline(id) {
  return await api.delete(`/pipelines/${id}`);
}
