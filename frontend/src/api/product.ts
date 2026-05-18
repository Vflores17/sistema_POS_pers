import { API_URL, fetchWithAuth  } from "./http";

export async function getProducts() {
    const token = localStorage.getItem("token");

    const response = await fetchWithAuth(`${API_URL}/products`, {
        headers: {
            "Authorization": `Bearer ${token}`
        }
    });

    if (!response.ok) {
        throw new Error("Error fetching products");
    }

    return response.json();
}