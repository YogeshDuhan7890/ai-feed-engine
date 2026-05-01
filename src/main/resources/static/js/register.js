/* ================================
   REGISTER.JS — UPDATED
   ✅ Password strength indicator
   ✅ NEW: Real-time validation
   ✅ NEW: Confirm password match
   ✅ NEW: Email format check
   ✅ NEW: Name minimum length
   ✅ NEW: Submit button enable/disable
   ✅ NEW: Show/hide password toggle
================================ */

document.addEventListener('DOMContentLoaded', () => {
	const nameInput = document.getElementById('nameInput') || document.querySelector('[name="name"]')
	const emailInput = document.getElementById('emailInput') || document.querySelector('[name="email"]')
	const passwordInput = document.getElementById('passwordInput') || document.querySelector('[name="password"]')
	const confirmInput = document.getElementById('confirmInput')
	const submitBtn = document.querySelector('button[type="submit"], .auth-btn')
	const strengthBar = document.getElementById('strengthBar')
	const strengthLabel = document.getElementById('strengthLabel')
	const form = document.getElementById('registerForm')

	function validate() {
		const name = nameInput?.value.trim() || ''
		const email = emailInput?.value.trim() || ''
		const pass = passwordInput?.value || ''
		const confirm = confirmInput?.value || ''
		let valid = true

		// Name
		if (nameInput) {
			const ok = name.length >= 2
			nameInput.style.borderColor = name.length === 0 ? '' : ok ? '#27ae60' : '#e74c3c'
			if (!ok && name.length > 0) valid = false
		}

		// Email
		if (emailInput) {
			const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
			emailInput.style.borderColor = email.length === 0 ? '' : ok ? '#27ae60' : '#e74c3c'
			if (!ok && email.length > 0) valid = false
		}

		// Password strength
		if (passwordInput && strengthBar && strengthLabel) {
			if (!pass) {
				strengthBar.className = 'strength-bar'
				strengthLabel.textContent = ''
			} else {
				const len = pass.length
				const hasUpper = /[A-Z]/.test(pass)
				const hasNum = /[0-9]/.test(pass)
				const hasSpec = /[^A-Za-z0-9]/.test(pass)
				const score = [hasUpper, hasNum, hasSpec].filter(Boolean).length
				if (len < 6) {
					strengthBar.className = 'strength-bar weak'
					strengthLabel.textContent = 'Bahut weak — min 6 chars'
					strengthLabel.style.color = '#e74c3c'
					valid = false
				} else if (score >= 2 && len >= 8) {
					strengthBar.className = 'strength-bar strong'
					strengthLabel.textContent = 'Strong ✓'
					strengthLabel.style.color = '#27ae60'
				} else {
					strengthBar.className = 'strength-bar medium'
					strengthLabel.textContent = 'Medium — number ya symbol add karo'
					strengthLabel.style.color = '#f59e0b'
				}
			}
		}

		// Confirm password
		if (confirmInput) {
			const passVal = passwordInput?.value || ''
			const ok = confirmInput.value === passVal && confirmInput.value.length > 0
			confirmInput.style.borderColor = confirmInput.value.length === 0 ? '' : ok ? '#27ae60' : '#e74c3c'
			const confirmMsg = document.getElementById('confirmMsg')
			if (confirmMsg) {
				confirmMsg.textContent = confirmInput.value.length === 0 ? '' : ok ? '✓ Passwords match' : '✗ Passwords match nahi karte'
				confirmMsg.style.color = ok ? '#27ae60' : '#e74c3c'
			}
			if (!ok && confirmInput.value.length > 0) valid = false
		}

		if (submitBtn) submitBtn.disabled = !valid
		return valid
	}

	nameInput?.addEventListener('input', validate)
	emailInput?.addEventListener('input', validate)
	passwordInput?.addEventListener('input', validate)
	confirmInput?.addEventListener('input', validate)

	// Show/hide password toggles
	document.querySelectorAll('.toggle-password, [data-toggle-password]').forEach(btn => {
		btn.addEventListener('click', () => {
			const targetId = btn.dataset.target || 'passwordInput'
			const input = document.getElementById(targetId) || passwordInput
			if (!input) return
			input.type = input.type === 'password' ? 'text' : 'password'
			btn.textContent = input.type === 'password' ? '👁' : '🙈'
		})
	})

	// Prevent submit if invalid
	form?.addEventListener('submit', e => {
		if (!validate()) { e.preventDefault(); return }
		if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = 'Creating account...' }
	})

	// Initial state
	if (submitBtn) submitBtn.disabled = true
})