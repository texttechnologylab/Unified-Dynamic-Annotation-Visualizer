import { http } from "./HTTPClient.js";

export async function getAnnotations(query, size) {
  const response = await http.get(`/annotations?q=${query}&size=${size}`);

  return response.json();
}
