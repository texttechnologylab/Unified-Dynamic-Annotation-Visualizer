import { api } from "./clients.js";

export async function getAnnotations(query, size) {
  const response = await api.get(`/annotations?q=${query}&size=${size}`);

  return response.json();
}
