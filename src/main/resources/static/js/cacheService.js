/* ================================
   CacheService.js
   Report Section 2: services/ folder
   In-memory cache to avoid redundant API calls
   Section 14: ES6 module pattern
================================ */

export class CacheService {
	constructor(ttl = 60000) {
		this._store = new Map()
		this._ttl = ttl  // default 1 minute
	}

	set(key, value, ttl = this._ttl) {
		this._store.set(key, { value, expiresAt: Date.now() + ttl })
	}

	get(key) {
		const entry = this._store.get(key)
		if (!entry) return null
		if (Date.now() > entry.expiresAt) { this._store.delete(key); return null }
		return entry.value
	}

	has(key) { return this.get(key) !== null }

	delete(key) { this._store.delete(key) }

	clear() { this._store.clear() }

	/**
	 * Fetch with cache: returns cached value or fetches and caches
	 * @param {string} key   - cache key
	 * @param {Function} fn  - async function that returns data
	 * @param {number} [ttl]
	 */
	async getOrFetch(key, fn, ttl) {
		const cached = this.get(key)
		if (cached !== null) return cached
		const data = await fn()
		this.set(key, data, ttl)
		return data
	}
}

// Singleton instances
export const feedCache = new CacheService(30000)  // 30s for feed
export const profileCache = new CacheService(120000) // 2min for profiles
export const hashtagCache = new CacheService(60000)  // 1min for hashtags