/**
 * Service Worker for Waqiti Admin Dashboard PWA
 */

const CACHE_NAME = 'waqiti-admin-v1.0.0';
const RUNTIME_CACHE = 'runtime-cache-v1.0.0';

// Assets to cache on install
const STATIC_CACHE_URLS = [
  '/',
  '/static/js/bundle.js',
  '/static/css/main.css',
  '/manifest.json',
  'https://fonts.googleapis.com/css?family=Roboto:300,400,500,700&display=swap',
  'https://fonts.googleapis.com/icon?family=Material+Icons',
];

// API endpoints to cache with network-first strategy
const API_CACHE_PATTERNS = [
  /^\/api\/dashboard/,
  /^\/api\/users\/\d+$/,
  /^\/api\/analytics/,
];

// Resources that should always be fetched from network
const NETWORK_ONLY_PATTERNS = [
  /^\/api\/auth/,
  /^\/api\/payments\/send/,
  /^\/api\/admin\/actions/,
  /\.hot-update\./,
];

self.addEventListener('install', (event: ExtendableEvent) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('SW: Caching static assets');
      return cache.addAll(STATIC_CACHE_URLS.map(url => new Request(url, {
        cache: 'reload'
      })));
    }).then(() => {
      console.log('SW: Installation complete');
      // Force activate immediately
      return self.skipWaiting();
    })
  );
});

self.addEventListener('activate', (event: ExtendableEvent) => {
  event.waitUntil(
    Promise.all([
      // Clean up old caches
      caches.keys().then((cacheNames) => {
        return Promise.all(
          cacheNames.map((cacheName) => {
            if (cacheName !== CACHE_NAME && cacheName !== RUNTIME_CACHE) {
              console.log('SW: Deleting old cache:', cacheName);
              return caches.delete(cacheName);
            }
          })
        );
      }),
      // Take control of all clients
      self.clients.claim()
    ])
  );
});

self.addEventListener('fetch', (event: FetchEvent) => {
  const { request } = event;
  const url = new URL(request.url);

  // Skip non-http requests
  if (!request.url.startsWith('http')) {
    return;
  }

  // Network-only requests
  if (NETWORK_ONLY_PATTERNS.some(pattern => pattern.test(request.url))) {
    return handleNetworkOnly(event);
  }

  // API requests with network-first strategy
  if (url.pathname.startsWith('/api/')) {
    if (API_CACHE_PATTERNS.some(pattern => pattern.test(url.pathname))) {
      return handleNetworkFirst(event);
    }
    return handleNetworkOnly(event);
  }

  // Static assets with cache-first strategy
  if (request.destination === 'script' || 
      request.destination === 'style' || 
      request.destination === 'font' ||
      request.destination === 'image') {
    return handleCacheFirst(event);
  }

  // Navigation requests with network-first strategy
  if (request.mode === 'navigate') {
    return handleNavigate(event);
  }

  // Default: network-first
  event.respondWith(handleNetworkFirst(event));
});

function handleNetworkOnly(event: FetchEvent) {
  event.respondWith(
    fetch(event.request).catch(() => {
      // Return offline page for navigation requests
      if (event.request.mode === 'navigate') {
        return caches.match('/offline.html') || new Response('Offline', {
          status: 503,
          statusText: 'Service Unavailable'
        });
      }
      throw new Error('Network request failed');
    })
  );
}

function handleCacheFirst(event: FetchEvent) {
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      if (cachedResponse) {
        // Update cache in background
        fetch(event.request).then((response) => {
          if (response.status === 200) {
            const responseClone = response.clone();
            caches.open(RUNTIME_CACHE).then((cache) => {
              cache.put(event.request, responseClone);
            });
          }
        }).catch(() => {
          // Ignore network errors for background updates
        });
        
        return cachedResponse;
      }

      // Not in cache, fetch from network
      return fetch(event.request).then((response) => {
        if (response.status === 200) {
          const responseClone = response.clone();
          caches.open(RUNTIME_CACHE).then((cache) => {
            cache.put(event.request, responseClone);
          });
        }
        return response;
      });
    })
  );
}

function handleNetworkFirst(event: FetchEvent) {
  event.respondWith(
    fetch(event.request).then((response) => {
      // Cache successful responses
      if (response.status === 200 && response.type === 'basic') {
        const responseClone = response.clone();
        caches.open(RUNTIME_CACHE).then((cache) => {
          cache.put(event.request, responseClone);
        });
      }
      return response;
    }).catch(() => {
      // Network failed, try cache
      return caches.match(event.request).then((cachedResponse) => {
        if (cachedResponse) {
          return cachedResponse;
        }
        
        // No cache available
        throw new Error('Network and cache failed');
      });
    })
  );
}

function handleNavigate(event: FetchEvent) {
  event.respondWith(
    fetch(event.request).then((response) => {
      // Cache the page
      const responseClone = response.clone();
      caches.open(RUNTIME_CACHE).then((cache) => {
        cache.put(event.request, responseClone);
      });
      return response;
    }).catch(() => {
      // Network failed, try cache
      return caches.match(event.request).then((cachedResponse) => {
        if (cachedResponse) {
          return cachedResponse;
        }
        
        // Fallback to cached index.html for SPA routing
        return caches.match('/').then((indexResponse) => {
          return indexResponse || new Response('Offline', {
            status: 503,
            statusText: 'Service Unavailable'
          });
        });
      });
    })
  );
}

// Background sync for offline actions
self.addEventListener('sync', (event: any) => {
  if (event.tag === 'background-sync') {
    event.waitUntil(handleBackgroundSync());
  }
});

async function handleBackgroundSync() {
  console.log('SW: Background sync triggered');
  
  // Get queued actions from IndexedDB
  const queuedActions = await getQueuedActions();
  
  for (const action of queuedActions) {
    try {
      await fetch(action.url, {
        method: action.method,
        headers: action.headers,
        body: action.body
      });
      
      // Remove from queue on success
      await removeQueuedAction(action.id);
    } catch (error) {
      console.log('SW: Failed to sync action:', action.id);
    }
  }
}

// Push notification handling
self.addEventListener('push', (event: any) => {
  const options = {
    body: 'New admin notification',
    icon: '/icons/icon-192x192.png',
    badge: '/icons/badge.png',
    tag: 'admin-notification',
    data: {},
    actions: [
      {
        action: 'view',
        title: 'View',
        icon: '/icons/view.png'
      },
      {
        action: 'dismiss',
        title: 'Dismiss',
        icon: '/icons/dismiss.png'
      }
    ],
    requireInteraction: true
  };

  if (event.data) {
    const data = event.data.json();
    options.body = data.body || options.body;
    options.tag = data.tag || options.tag;
    options.data = data;
  }

  event.waitUntil(
    self.registration.showNotification('Waqiti Admin', options)
  );
});

self.addEventListener('notificationclick', (event: any) => {
  event.notification.close();

  if (event.action === 'view') {
    event.waitUntil(
      self.clients.openWindow('/dashboard')
    );
  }
});

// Utility functions (would be implemented with IndexedDB)
async function getQueuedActions(): Promise<any[]> {
  // Implementation would use IndexedDB
  return [];
}

async function removeQueuedAction(id: string): Promise<void> {
  // Implementation would use IndexedDB
}

// Export types for TypeScript
export { };

declare const self: ServiceWorkerGlobalScope;