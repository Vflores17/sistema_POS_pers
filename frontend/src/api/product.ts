const API_URL = "http://localhost:8080";

export async function getProducts() {
    const token = localStorage.getItem("token");

    const response = await fetch(`${API_URL}/products`, {
        headers: {
            "Authorization": `Bearer ${token}`
        }
    });

    if (!response.ok) {
        throw new Error("Error fetching products");
    }

    return response.json();
}