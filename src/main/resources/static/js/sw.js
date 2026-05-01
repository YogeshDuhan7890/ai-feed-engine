const CACHE_VERSION = 'aifeed-v3'
const STATIC_CACHE = `${CACHE_VERSION}-static`
const DYNAMIC_CACHE = `${CACHE_VERSION}-dynamic`

const STATIC_ASSETS = [
	'/offline.html',
	'/manifest.json',
	'/css/main.css',
	'/css/auth.css',
	'/css/pages/reels.css',
	'/css/pages/profile.css',
	'/css/pages/upload.css',
	'/css/pages/notifications.css',
	'/css/pages/search.css',
	'/css/pages/admin.css',
	'/js/header.js',
	'/js/reels.js',
	'/js/upload.js',
	'/js/search.js',
	'/js/notifications.js',
	'/js/stories.js',
	'/js/suggestedUsers.js',
	'/js/theme.js',
	'/images/default.png',
	'/images/icon-192.png',
	'/images/icon-512.png',
	'/images/badge-72.png',
	'/images/screenshot.png'
]

self.addEventListener('install', event => {
	event.waitUntil((async () => {
		const cache = await caches.open(STATIC_CACHE)
		await Promise.allSettled(
			STATIC_ASSETS.map(async asset => {
				try {
					await cache.add(asset)
				} catch (error) {
					console.warn('SW cache skip:', asset, error)
				}
			})
		)
		await self.skipWaiting()
	})())
})

self.addEventListener('activate', event => {
	event.waitUntil((async () => {
		const keys = await caches.keys()
		await Promise.all(
			keys
				.filter(key => key.startsWith('aifeed-') && key !== STATIC_CACHE && key !== DYNAMIC_CACHE)
				.map(key => caches.delete(key))
		)
		await clients.claim()
	})())
})

self.addEventListener('fetch', event => {
	const requestUrl = new URL(event.request.url)

	if (event.request.method !== 'GET') return
	if (requestUrl.origin !== self.location.origin) return

	if (requestUrl.pathname.startsWith('/api/') || requestUrl.pathname.startsWith('/videos/')) {
		return
	}

	if (event.request.mode === 'navigate') {
		event.respondWith(handleNavigationRequest(event.request))
		return
	}

	event.respondWith(handleAssetRequest(event.request))
})

self.addEventListener('push', event => {
	if (!event.data) return

	let data = {}
	try {
		data = event.data.json()
	} catch {
		data = { title: 'AI Feed', body: event.data.text() }
	}

	event.waitUntil(
		self.registration.showNotification(data.title || 'AI Feed', {
			body: data.body || '',
			icon: data.icon || '/images/icon-192.png',
			badge: '/images/badge-72.png',
			data: { url: data.url || '/' },
			vibrate: [200, 100, 200],
			tag: 'aifeed-notification',
			renotify: true,
			actions: [
				{ action: 'open', title: 'Open' },
				{ action: 'close', title: 'Dismiss' }
			]
		})
	)
})

self.addEventListener('notificationclick', event => {
	event.notification.close()
	if (event.action === 'close') return

	const url = event.notification.data?.url || '/'
	event.waitUntil((async () => {
		const windowClients = await clients.matchAll({ type: 'window', includeUncontrolled: true })
		const existing = windowClients.find(client => client.url.startsWith(self.location.origin))
		if (existing) {
			await existing.focus()
			return existing.navigate(url)
		}
		return clients.openWindow(url)
	})())
})

self.addEventListener('sync', event => {
	if (event.tag === 'sync-uploads') {
		event.waitUntil(syncPendingUploads())
	}
})

async function handleNavigationRequest(request) {
	try {
		const response = await fetch(request)
		if (response && response.ok) {
			const cache = await caches.open(DYNAMIC_CACHE)
			cache.put(request, response.clone())
		}
		return response
	} catch {
		const cachedPage = await caches.match(request)
		if (cachedPage) return cachedPage

		const offlinePage = await caches.match('/offline.html')
		if (offlinePage) return offlinePage

		return new Response(
			'<!doctype html><title>Offline</title><h1>Offline</h1><p>Internet connection is unavailable.</p>',
			{ headers: { 'Content-Type': 'text/html; charset=UTF-8' } }
		)
	}
}

async function handleAssetRequest(request) {
	const cached = await caches.match(request)
	if (cached) return cached

	try {
		const response = await fetch(request)
		if (response && response.ok && response.type !== 'opaque') {
			const cache = await caches.open(DYNAMIC_CACHE)
			cache.put(request, response.clone())
		}
		return response
	} catch {
		if (request.destination === 'image') {
			const fallback = await caches.match('/images/default.png')
			if (fallback) return fallback
		}
		throw new Error('Asset unavailable while offline')
	}
}

async function syncPendingUploads() {
	try {
		const db = await openDB()
		const tx = db.transaction('pending-uploads', 'readwrite')
		const store = tx.objectStore('pending-uploads')
		const all = await storeGetAll(store)

		for (const item of all) {
			try {
				const response = await fetch('/videos/upload', {
					method: 'POST',
					headers: { [item.csrfHeader]: item.csrfToken },
					body: item.formData
				})

				if (response.ok) {
					await store.delete(item.id)
					const windowClients = await self.clients.matchAll()
					windowClients.forEach(client => client.postMessage({ type: 'UPLOAD_SYNCED', id: item.id }))
				}
			} catch {
				// Retry during the next sync event.
			}
		}
	} catch (error) {
		console.error('Sync error:', error)
	}
}

function openDB() {
	return new Promise((resolve, reject) => {
		const request = indexedDB.open('aifeed-db', 1)
		request.onupgradeneeded = event => {
			event.target.result.createObjectStore('pending-uploads', { keyPath: 'id', autoIncrement: true })
		}
		request.onsuccess = event => resolve(event.target.result)
		request.onerror = () => reject(request.error)
	})
}

function storeGetAll(store) {
	return new Promise((resolve, reject) => {
		const request = store.getAll()
		request.onsuccess = () => resolve(request.result)
		request.onerror = () => reject(request.error)
	})
}
