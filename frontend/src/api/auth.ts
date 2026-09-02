import { API_URL } from "./http";
import { apiErrorFromResponse, reportNetworkError } from "./errors";

export async function login(username: string, password: string) {
    let response: Response;
    try {
      response = await fetch(`${API_URL}/auth/login`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ username, password })
      });
    } catch {
      throw reportNetworkError();
    }

    if (!response.ok) {
        throw await apiErrorFromResponse(response, "No se pudo iniciar sesión.");
    }

    return response.json();
}
