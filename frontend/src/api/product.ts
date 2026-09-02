import { API_URL, fetchWithAuth  } from "./http";
import { parseApiResponse } from "./errors";

export async function getProducts() {
    const token = localStorage.getItem("token");

    const response = await fetchWithAuth(`${API_URL}/products`, {
        headers: {
            "Authorization": `Bearer ${token}`
        }
    });

    return parseApiResponse(response, "No se pudieron cargar los productos.");

    
}
