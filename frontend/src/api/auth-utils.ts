export function getCurrentUserRoles(): string[] {
    try {
        const token = localStorage.getItem("token");
        if (!token) return [];
        const payload = JSON.parse(atob(token.split(".")[1]));
        return payload.roles ?? [];
    } catch {
        return [];
    }
}

export function hasRole(role: string): boolean {
    return getCurrentUserRoles().includes(role);
}

export function isAdmin(): boolean {
    return hasRole("ROLE_ADMIN");
}

export function isVendedor(): boolean {
    return hasRole("ROLE_VENDEDOR"); // 👈 con prefijo ROLE_
}