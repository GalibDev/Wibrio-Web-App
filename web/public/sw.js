const CACHE = 'wibrio-shell-v1';
self.addEventListener('install', event => { event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(['/offline.html', '/icon.svg']))); self.skipWaiting(); });
self.addEventListener('activate', event => { event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('wibrio-shell-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin || event.request.headers.get('RSC') || url.searchParams.has('_rsc')) return;
  if (event.request.mode === 'navigate') { event.respondWith(fetch(event.request).then(response => { if (response.ok) { const copy = response.clone(); event.waitUntil(caches.open(CACHE).then(cache => cache.put(event.request, copy))); } return response; }).catch(async () => (await caches.match(event.request)) || caches.match('/offline.html'))); }
  else if (url.pathname.startsWith('/_next/static/') || url.pathname === '/icon.svg') { event.respondWith(caches.match(event.request).then(cached => cached || fetch(event.request).then(response => { if (response.ok) { const copy = response.clone(); event.waitUntil(caches.open(CACHE).then(cache => cache.put(event.request, copy))); } return response; }))); }
});
