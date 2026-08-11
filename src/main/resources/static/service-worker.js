const RAW_BUILD_ID = "__DUTYLOG_BUILD_ID__";
const BUILD_ID = RAW_BUILD_ID.startsWith("__") ? "local" : RAW_BUILD_ID;
const CACHE_NAME = `dutylog-shell-v27.40.1-${BUILD_ID}`; // unique per immutable image build

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
    caches.keys()
      .then(keys => Promise.all(keys
        .filter(k => k.startsWith("dutylog-shell-") && k !== CACHE_NAME)
        .map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("message", event => {
  if (event.data?.type === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("fetch", event => {
  const url = new URL(event.request.url);

  // Браузерные расширения и внешние URL нельзя класть в Cache API.
  // Иначе service worker может шуметь ошибками вида
  // "Request scheme 'chrome-extension' is unsupported".
  if (url.origin !== self.location.origin || !["http:", "https:"].includes(url.protocol)) {
    return;
  }

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
          if (res && res.ok) {
            const copy = res.clone();
            caches.open(CACHE_NAME).then(c => c.put(event.request, copy)).catch(() => {});
          }
          return res;
        })
        .catch(() => caches.match(event.request))
    );
    return;
  }

  // JS/CSS — network-first: HTML может обновиться раньше shell-кэша.
  // Если отдать старые JS-файлы к новому index.html, получим фантомные баги.
  if (url.pathname.endsWith(".js") || url.pathname.endsWith(".css")) {
    event.respondWith(
      fetch(event.request)
        .then(res => {
          if (res && res.ok) {
            const copy = res.clone();
            caches.open(CACHE_NAME).then(c => c.put(event.request, copy)).catch(() => {});
          }
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

self.addEventListener("notificationclick", event => {
  event.notification.close();
  const targetUrl = new URL(event.notification?.data?.url || "/#calendar", self.location.origin).href;
  event.waitUntil(
    self.clients.matchAll({ type:"window", includeUncontrolled:true }).then(clients => {
      for (const client of clients) {
        if ("focus" in client) {
          client.navigate(targetUrl).catch(() => {});
          return client.focus();
        }
      }
      return self.clients.openWindow ? self.clients.openWindow(targetUrl) : undefined;
    })
  );
});

