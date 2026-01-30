(() => {
  const BASE = "/api";

  async function request(path, options = {}) {
    const res = await fetch(BASE + path, {
      headers: { "Content-Type": "application/json", ...(options.headers || {}) },
      ...options,
    });

    // Spring Boot가 에러를 JSON으로 내려주는 경우도 있고, 아닌 경우도 있어서 방어적으로 처리
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = text; }

    if (!res.ok) {
      const msg =
        (data && typeof data === "object" && (data.message || data.error)) ||
        (typeof data === "string" && data) ||
        `${res.status} ${res.statusText}`;
      throw new Error(msg);
    }
    return data;
  }

  const api = {
    getOwners: () => request("/owners", { method: "GET" }),
    getRegions: () => request("/regions", { method: "GET" }),
    getOccupation: () => request("/occupation", { method: "GET" }),
    occupy: (regionKey, ownerId) => request("/occupation", { method: "POST", body: JSON.stringify({ regionKey, ownerId }) }),
    resetOccupation: () => request("/occupation/reset", { method: "POST" }),
    getAdjacency: () => request("/adjacency", { method: "GET" }),
    gameState: () => request("/game/state", { method: "GET" }),
    gameInspect: (regionKey) => request(`/game/inspect?regionKey=${encodeURIComponent(regionKey)}`, { method: "GET" }),
    gameStart: (userHomeKey, aiCount = 1) => request("/game/start", { method: "POST", body: JSON.stringify({ userHomeKey, aiCount }) }),
    gameAction: (payload) => request("/game/action", { method: "POST", body: JSON.stringify(payload) }),
    gameUnlockTech: (techCode) => request("/game/tech/unlock", { method: "POST", body: JSON.stringify({ techCode }) }),
    gameReset: () => request("/game/reset", { method: "POST" }),
  };

  window.OCC_API = api;
})();
