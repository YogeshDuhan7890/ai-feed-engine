let settingsData = {}

document.addEventListener('DOMContentLoaded', () => {
	loadSettings()
})

async function loadSettings() {
	try {
		const res = await fetch('/api/admin/settings')
		const data = await res.json()
		settingsData = data || {}
		renderSettings(settingsData)
	} catch (err) {
		console.error(err)
		document.getElementById('settingsContainer').innerHTML =
			'<div class="text-danger text-center">Failed to load settings</div>'
	}
}

function renderSettings(data) {
	const container = document.getElementById('settingsContainer')
	const groups = {
		Features: [],
		Limits: [],
		UI: [],
		Maintenance: []
	}

	Object.keys(data).forEach(key => {
		if (key.startsWith('feature:')) groups.Features.push(key)
		else if (key.startsWith('limit:')) groups.Limits.push(key)
		else if (key.startsWith('ui:')) groups.UI.push(key)
		else if (key.startsWith('maintenance:')) groups.Maintenance.push(key)
	})

	const groupDescriptions = {
		Features: 'Core app capabilities',
		Limits: 'Validation and content size controls',
		UI: 'Frontend visibility and default behavior',
		Maintenance: 'Global maintenance mode configuration'
	}

	container.innerHTML = Object.entries(groups)
		.filter(([, keys]) => keys.length)
		.map(([group, keys]) => `
			<div class="col-md-6">
				<div class="card shadow-sm p-3 h-100">
					<h6 class="mb-1">${group}</h6>
					<div class="text-muted small mb-3">${groupDescriptions[group]}</div>
					${keys.map(key => renderField(key, data[key])).join('')}
				</div>
			</div>
		`).join('')
}

function renderField(key, value) {
	const label = formatLabel(key)
	const stringValue = value == null ? '' : String(value)
	const isBool = stringValue === 'true' || stringValue === 'false'
	const isNumber = stringValue !== '' && !Number.isNaN(Number(stringValue))

	if (isBool) {
		return `
			<div class="form-check form-switch mb-3">
				<input class="form-check-input setting-input"
					type="checkbox"
					id="${key}"
					${stringValue === 'true' ? 'checked' : ''}>
				<label class="form-check-label small" for="${key}">${label}</label>
			</div>
		`
	}

	return `
		<div class="mb-3">
			<label class="form-label small" for="${key}">${label}</label>
			<input type="${isNumber ? 'number' : 'text'}"
				class="form-control form-control-sm setting-input"
				id="${key}"
				value="${escapeHtml(stringValue)}">
		</div>
	`
}

async function saveAll() {
	const inputs = document.querySelectorAll('.setting-input')
	const payloads = Array.from(inputs).map(input => ({
		key: input.id,
		value: input.type === 'checkbox' ? (input.checked ? 'true' : 'false') : input.value
	}))

	try {
		await Promise.all(payloads.map(item =>
			fetch('/api/admin/settings', {
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
					[CSRF_HEADER]: CSRF_TOKEN
				},
				body: JSON.stringify(item)
			}).then(async res => {
				const data = await res.json()
				if (!res.ok || data.success === false) {
					throw new Error(data.message || 'Save failed')
				}
			})
		))

		showAlert('All settings saved successfully.', 'success')
	} catch (err) {
		console.error(err)
		showAlert(err.message || 'Error saving settings.', 'danger')
	}
}

async function resetSettings() {
	if (!confirm('Reset all settings to default values?')) return

	try {
		const res = await fetch('/api/admin/settings/reset', {
			method: 'POST',
			headers: {
				[CSRF_HEADER]: CSRF_TOKEN
			}
		})
		const data = await res.json()
		if (!res.ok || data.success === false) {
			throw new Error(data.message || 'Reset failed')
		}

		showAlert(data.message || 'Settings reset successfully.', 'success')
		loadSettings()
	} catch (err) {
		console.error(err)
		showAlert(err.message || 'Reset failed.', 'danger')
	}
}

function formatLabel(key) {
	return key
		.split(':')[1]
		.replaceAll('_', ' ')
		.replace(/\b\w/g, ch => ch.toUpperCase())
}

function showAlert(message, type) {
	const el = document.getElementById('settingsAlert')
	if (!el) return
	el.className = `alert alert-${type}`
	el.textContent = message
	el.style.display = 'block'
	setTimeout(() => {
		el.style.display = 'none'
	}, 3500)
}

function escapeHtml(value) {
	return String(value)
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
}
