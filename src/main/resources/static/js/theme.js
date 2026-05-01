(function () {
	const STORAGE_KEY = 'ai-feed-theme'
	const DARK = 'dark'
	const LIGHT = 'light'

	function getSavedTheme() {
		const saved = localStorage.getItem(STORAGE_KEY)
		if (saved) return saved
		return window.matchMedia('(prefers-color-scheme: light)').matches ? LIGHT : DARK
	}

	function updateToggleButtons(theme) {
		const nextModeLabel = theme === DARK ? 'Light mode' : 'Dark mode'
		document.querySelectorAll('[data-theme-toggle]').forEach(btn => {
			const labelTarget = btn.querySelector('[data-theme-label]')
			const hasCustomMarkup = btn.children.length > 0

			if (labelTarget) {
				labelTarget.textContent = nextModeLabel
			} else if (!hasCustomMarkup) {
				btn.textContent = nextModeLabel
			}

			btn.title = nextModeLabel
			btn.setAttribute('aria-label', theme === DARK ? 'Switch to light mode' : 'Switch to dark mode')
		})
	}

	function applyTheme(theme, animate = false) {
		const root = document.documentElement
		if (animate) root.classList.add('theme-transitioning')

		root.setAttribute('data-theme', theme)
		localStorage.setItem(STORAGE_KEY, theme)
		updateToggleButtons(theme)
		window.dispatchEvent(new CustomEvent('themechange', { detail: { theme } }))

		if (animate) {
			setTimeout(() => root.classList.remove('theme-transitioning'), 400)
		}
	}

	function toggleTheme() {
		const current = document.documentElement.getAttribute('data-theme') || DARK
		applyTheme(current === DARK ? LIGHT : DARK, true)
	}

	function init() {
		applyTheme(getSavedTheme(), false)

		document.addEventListener('DOMContentLoaded', () => {
			document.querySelectorAll('[data-theme-toggle]').forEach(btn => {
				btn.addEventListener('click', toggleTheme)
			})

			document.addEventListener('keydown', event => {
				if (event.ctrlKey && event.shiftKey && event.key === 'T') {
					event.preventDefault()
					toggleTheme()
				}
			})

			updateToggleButtons(document.documentElement.getAttribute('data-theme') || DARK)
		})

		window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', event => {
			if (!localStorage.getItem(STORAGE_KEY)) {
				applyTheme(event.matches ? LIGHT : DARK, true)
			}
		})
	}

	window.aiFeedTheme = {
		toggle: toggleTheme,
		apply: applyTheme,
		get: () => document.documentElement.getAttribute('data-theme')
	}

	init()
})()
