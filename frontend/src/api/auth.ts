import { API_URL, persistTokens } from "./http";
import { apiErrorFromResponse, reportNetworkError } from "./errors";

interface LoginResponseBody {
    data: {
        accessToken: string;
        refreshToken: string;
        expiresIn?: number;
    };
}

export async function login(username: string, password: string): Promise<LoginResponseBody> {
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

    const body = (await response.json()) as LoginResponseBody;
    persistTokens(body.data.accessToken, body.data.refreshToken, body.data.expiresIn);
    return body;
}