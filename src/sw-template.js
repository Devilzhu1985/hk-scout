const CACHE='scout-v2-__CACHE_VERSION__';
const FILES=__CACHE_FILES__;
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(FILES))));
// Activation is explicit, so an update cannot replace a running capture screen.
self.addEventListener('message',event=>{if(event.data?.type==='ACTIVATE')self.skipWaiting();});
self.addEventListener('activate',event=>event.waitUntil((async()=>{
  for(const name of await caches.keys())if(name.startsWith('scout-v2-')&&name!==CACHE)await caches.delete(name);
  await self.clients.claim();
})()));
self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET'||new URL(event.request.url).origin!==self.location.origin)return;
  const url=new URL(event.request.url);
  const allowed=new Set(FILES.map(f=>new URL(f,self.registration.scope).href));
  if(event.request.mode==='navigate'){
    event.respondWith(caches.open(CACHE).then(c=>c.match(new URL('./index.html',self.registration.scope))).then(r=>r||fetch(event.request)));
  }else if(allowed.has(url.href)){
    event.respondWith(caches.open(CACHE).then(c=>c.match(event.request)).then(r=>r||fetch(event.request)));
  }
});
