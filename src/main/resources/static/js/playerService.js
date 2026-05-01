/* ================================
   PlayerService.js
   Report Section 2: services/ folder
   Manages all video players on page,
   ensures only one plays at a time (Section 5)
================================ */

export class PlayerService {
	constructor() {
		this._active = null   // currently playing video element
		this._hlsMap = new WeakMap() // video el → Hls instance
	}

	/**
	 * Register a video element. Attaches HLS if needed.
	 * @param {HTMLVideoElement} videoEl
	 * @param {string} src
	 */
	register(videoEl, src) {
		if (src?.endsWith('.m3u8')) {
			if (typeof Hls !== 'undefined' && Hls.isSupported()) {
				const hls = new Hls({ startPosition: 0 })
				hls.loadSource(src)
				hls.attachMedia(videoEl)
				this._hlsMap.set(videoEl, hls)
			} else if (videoEl.canPlayType('application/vnd.apple.mpegurl')) {
				videoEl.src = src
			}
		} else if (src) {
			const source = document.createElement('source')
			source.src = src
			source.type = 'video/mp4'
			videoEl.appendChild(source)
		}
	}

	/** Play a video — pauses the previous one */
	play(videoEl) {
		if (this._active && this._active !== videoEl) {
			this._active.pause()
		}
		this._active = videoEl
		return videoEl.play().catch(() => { })
	}

	/** Pause a video */
	pause(videoEl) {
		videoEl.pause()
		if (this._active === videoEl) this._active = null
	}

	/** Destroy a video and clean up HLS */
	destroy(videoEl) {
		videoEl.pause()
		const hls = this._hlsMap.get(videoEl)
		if (hls) { hls.destroy(); this._hlsMap.delete(videoEl) }
		videoEl.removeAttribute('src')
		videoEl.innerHTML = ''
		videoEl.load()
		if (this._active === videoEl) this._active = null
	}

	/** Pause all playing videos */
	pauseAll() {
		document.querySelectorAll('video').forEach(v => v.pause())
		this._active = null
	}
}

// Singleton
export const playerService = new PlayerService()