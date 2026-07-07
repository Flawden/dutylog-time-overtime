const CACHE_NAME = "dutylog-shell-v22.2"; // bump: сбрасывает старый кэш при активации

const SHELL = [
  "/manifest.json",
  "/icons/icon-192.png",
  "/icons/icon-512.png"
];

self.addEventListener("install", event => {
  event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(SHELL)));
  self.skipWaiting();
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(
      keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
    ))
  );
  self.clients.claim();
});

self.addEventListener("fetch", event => {
  const url = new URL(event.request.url);

  // API и авторизацию не трогаем вообще.
  if (url.pathname.startsWith("/api/") || url.pathname === "/perform_login" || url.pathname === "/logout") {
    return;
  }

  // HTML — только network-first: страницы должны приходить с сервера
  // (там ставятся cookie, в т.ч. CSRF-токен). Кэш — лишь оффлайн-запаска.
  const isHtml = event.request.mode === "navigate" || url.pathname.endsWith(".html");
  if (isHtml) {
    event.respondWith(
      fetch(event.request)
        .then(res => {
          const copy = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(event.request, copy));
          return res;
        })
        .catch(() => caches.match(event.request))
    );
    return;
  }

  // JS/CSS — network-first: HTML может обновиться раньше shell-кэша.
  // Если отдать старый app.js к новому index.html, получим фантомные баги.
  if (url.pathname.endsWith(".js") || url.pathname.endsWith(".css")) {
    event.respondWith(
      fetch(event.request)
        .then(res => {
          const copy = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(event.request, copy));
          return res;
        })
        .catch(() => caches.match(event.request))
    );
    return;
  }

  // Иконки и манифест: cache-first, это безопасно.
  event.respondWith(
    caches.match(event.request).then(cached => cached || fetch(event.request))
  );
});
