/* ================================
   UPLOAD.JS — COMPLETE UPDATED
   ✅ Video upload with XHR progress bar
   ✅ Thumbnail preview before upload
   ✅ File type + size validation
   ✅ NEW: Drag & drop support
   ✅ NEW: Hashtag chips (type + Enter)
   ✅ NEW: Caption char count
   ✅ NEW: Video duration + size info
   ✅ NEW: Success state with redirect options
   ✅ NEW: Upload speed estimate
   ✅ NEW: Cancel upload support
================================ */

function getCsrfToken() {
	const m = document.querySelector('meta[name="_csrf"]')
	if (m) return m.getAttribute('content')
	const c = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
	return c ? decodeURIComponent(c[1]) : ''
}
function getCsrfHeader() {
	const m = document.querySelector('meta[name="_csrf_header"]')
	return m ? m.getAttribute('content') : 'X-CSRF-TOKEN'
}

const tags = []
let activeXHR = null
let uploadStart = 0

document.addEventListener('DOMContentLoaded', () => {
	const form = document.getElementById('uploadForm')
	const fileInput = document.getElementById('videoFile') || form?.querySelector('input[type="file"]')
	const btn = form?.querySelector('.upload-btn')
	const preview = document.getElementById('thumbnailPreview')
	const dropZone = document.getElementById('dropZone')

	if (!form) return

	/* ── File select ── */
	fileInput?.addEventListener('change', () => handleFile(fileInput.files[0]))

	/* ── Drag & Drop ── */
	if (dropZone) {
		dropZone.addEventListener('dragover', e => { e.preventDefault(); dropZone.classList.add('drag-over') })
		dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'))
		dropZone.addEventListener('drop', e => {
			e.preventDefault()
			dropZone.classList.remove('drag-over')
			const file = e.dataTransfer.files[0]
			if (file && fileInput) {
				const dt = new DataTransfer(); dt.items.add(file)
				fileInput.files = dt.files
				handleFile(file)
			}
		})
	}

	/* ── Caption char count ── */
	const captionInput = document.getElementById('captionInput') || form.querySelector('[name="content"]')
	captionInput?.addEventListener('input', e => {
		const el = document.getElementById('captionCount')
		if (el) {
			const len = e.target.value.length
			el.textContent = len + '/500'
			el.style.color = len > 450 ? '#f59e0b' : len >= 500 ? '#ef4444' : '#888'
		}
	})

	/* ── Hashtag input ── */
	document.getElementById('tagInput')?.addEventListener('keydown', e => {
		if (e.key === 'Enter' || e.key === ',') {
			e.preventDefault()
			const val = e.target.value.trim().replace(/^#/, '')
			if (val) addTag(val)
			e.target.value = ''
		}
	})

	/* ── Form submit ── */
	form.addEventListener('submit', e => { e.preventDefault(); startUpload(form, fileInput, btn) })

	/* ── Cancel button ── */
	document.getElementById('cancelUploadBtn')?.addEventListener('click', () => {
		if (activeXHR) { activeXHR.abort(); activeXHR = null }
		resetUploadState(btn)
		showToast('Upload cancel kiya', 'info')
	})
})

/* ── Handle File ── */
function handleFile(file) {
	if (!file) return
	if (file.type !== 'video/mp4' && !file.name.toLowerCase().endsWith('.mp4')) {
		showToast('Sirf MP4 video files allowed hain', 'error')
		clearFile()
		return
	}
	if (file.size > 10 * 1024 * 1024) {
		showToast('File 10MB se badi hai. Chhoti file choose karo.', 'error')
		clearFile()
		return
	}

	// File info
	const info = document.getElementById('fileInfo')
	if (info) {
		info.style.display = ''
		info.textContent = `✅ ${file.name} · ${(file.size / 1024 / 1024).toFixed(1)} MB`
	}
	document.getElementById('dropZone')?.classList.add('has-file')

	// Thumbnail preview
	const preview = document.getElementById('thumbnailPreview')
	if (preview) {
		const url = URL.createObjectURL(file)
		preview.src = url
		preview.style.display = ''
		preview.addEventListener('loadedmetadata', () => {
			const dur = Math.round(preview.duration)
			const size = (file.size / 1024 / 1024).toFixed(1)
			const infoEl = document.getElementById('videoInfo')
			if (infoEl) {
				infoEl.style.display = ''
				infoEl.textContent = `⏱ ${dur}s · 💾 ${size}MB · 📹 ${file.type.split('/')[1].toUpperCase()}`
			}
		}, { once: true })
	}
}

/* ── Upload ── */
function startUpload(form, fileInput, btn) {
	if (!fileInput?.files[0]) {
		showToast('Pehle ek video select karo', 'error')
		return
	}

	const captionInput = document.getElementById('captionInput') || form.querySelector('[name="content"]')
	if (!captionInput?.value.trim()) {
		showToast('Caption likhna zaroori hai', 'error')
		return
	}

	const formData = new FormData(form)
	// Add hashtags
	const tagsVal = document.getElementById('tagsHidden')?.value
	if (tagsVal) formData.set('tags', tagsVal)

	if (btn) { btn.disabled = true; btn.textContent = '⏳ Uploading...' }

	const progressWrap = document.getElementById('progressWrap') || document.getElementById('uploadProgressWrap')
	const progressBar = document.getElementById('progressBar') || document.getElementById('uploadProgressBar')
	const progressLbl = document.getElementById('progressLabel') || document.getElementById('uploadProgressPct')
	const cancelBtn = document.getElementById('cancelUploadBtn')

	if (progressWrap) progressWrap.style.display = ''
	if (cancelBtn) cancelBtn.style.display = ''
	uploadStart = Date.now()

	const xhr = new XMLHttpRequest()
	activeXHR = xhr
	xhr.open('POST', '/api/upload/video', true)
	xhr.setRequestHeader(getCsrfHeader(), getCsrfToken())

	xhr.upload.addEventListener('progress', e => {
		if (!e.lengthComputable) return
		const pct = Math.round(e.loaded / e.total * 100)
		const elapsed = (Date.now() - uploadStart) / 1000
		const speed = elapsed > 0 ? (e.loaded / elapsed / 1024 / 1024).toFixed(1) : '—'
		const eta = elapsed > 0 && e.loaded > 0 ? Math.round((e.total - e.loaded) / (e.loaded / elapsed)) : '—'
		if (progressBar) progressBar.style.width = pct + '%'
		if (progressLbl) progressLbl.textContent = `${pct}% · ${speed} MB/s · ETA: ${eta}s`
	})

	xhr.addEventListener('load', () => {
		activeXHR = null
		resetUploadState(btn)
		if (progressWrap) progressWrap.style.display = 'none'
		if (cancelBtn) cancelBtn.style.display = 'none'
		if (xhr.status >= 200 && xhr.status < 300) {
			showSuccessState()
		} else {
			showToast('Upload fail: ' + (xhr.responseText || xhr.status), 'error')
		}
	})

	xhr.addEventListener('error', () => {
		activeXHR = null
		resetUploadState(btn)
		showToast('Network error — upload fail ho gaya.', 'error')
	})

	xhr.addEventListener('abort', () => { activeXHR = null })
	xhr.send(formData)
}

/* ── Success State ── */
function showSuccessState() {
	document.getElementById('uploadCard')?.style && (document.getElementById('uploadCard').style.display = 'none')
	const successCard = document.getElementById('successCard')
	if (successCard) { successCard.style.display = '' }
	else {
		// Fallback if no success card in HTML
		showToast('🎉 Video upload ho gayi!', 'success')
		setTimeout(() => location.href = '/feed', 2000)
	}
}

/* ── Hashtag Chips ── */
function addTag(tag) {
	if (tags.includes(tag) || tags.length >= 10) return
	tags.push(tag)
	renderTags()
}
function addTagFromSuggest(tag) { addTag(tag) }
function removeTag(tag) {
	const i = tags.indexOf(tag); if (i > -1) tags.splice(i, 1)
	renderTags()
}
function renderTags() {
	const el = document.getElementById('tagChips')
	if (el) el.innerHTML = tags.map(t =>
		`<span style="background:#1a1a2e;color:#818cf8;border:1px solid #3730a3;border-radius:16px;padding:4px 10px;font-size:0.78rem;display:inline-flex;align-items:center;gap:6px">
			#${esc(t)}
			<span onclick="removeTag('${esc(t)}')" style="cursor:pointer;color:#6b7280;font-size:0.7rem">✕</span>
		</span>`
	).join('')
	const hidden = document.getElementById('tagsHidden')
	if (hidden) hidden.value = tags.join(',')
}

/* ── Reset ── */
function resetUploadState(btn) {
	if (btn) { btn.disabled = false; btn.textContent = '🚀 Upload Video' }
}
function resetUpload() {
	document.getElementById('uploadCard')?.style && (document.getElementById('uploadCard').style.display = '')
	document.getElementById('successCard')?.style && (document.getElementById('successCard').style.display = 'none')
	document.getElementById('uploadForm')?.reset()
	const preview = document.getElementById('thumbnailPreview')
	if (preview) preview.style.display = 'none'
	document.getElementById('fileInfo') && (document.getElementById('fileInfo').style.display = 'none')
	document.getElementById('videoInfo') && (document.getElementById('videoInfo').style.display = 'none')
	document.getElementById('tagChips') && (document.getElementById('tagChips').innerHTML = '')
	document.getElementById('progressWrap')?.style && (document.getElementById('progressWrap').style.display = 'none')
	document.getElementById('dropZone')?.classList.remove('has-file')
	document.getElementById('captionCount') && (document.getElementById('captionCount').textContent = '0/500')
	tags.length = 0
}
function clearFile() {
	const fi = document.getElementById('videoFile')
	if (fi) fi.value = ''
	const preview = document.getElementById('thumbnailPreview')
	if (preview) preview.style.display = 'none'
	document.getElementById('dropZone')?.classList.remove('has-file')
	document.getElementById('fileInfo') && (document.getElementById('fileInfo').style.display = 'none')
}

/* ── Utils ── */
function esc(s) { return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') }
function showToast(msg, type = 'info') {
	document.getElementById('__upload-toast')?.remove()
	const colors = { error: '#e74c3c', success: '#27ae60', info: '#333', warning: '#f39c12' }
	const t = document.createElement('div'); t.id = '__upload-toast'
	t.textContent = msg
	t.style.cssText = `position:fixed;bottom:30px;left:50%;transform:translateX(-50%);
		background:${colors[type] || '#333'};color:#fff;padding:10px 22px;border-radius:8px;
		font-size:14px;z-index:9999;box-shadow:0 4px 16px rgba(0,0,0,.5);`
	document.body.appendChild(t)
	setTimeout(() => t.remove(), 3500)
}
