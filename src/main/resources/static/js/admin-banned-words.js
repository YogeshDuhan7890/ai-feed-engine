/* ================================
   admin-banned-words.js (REDIS PRO)
================================ */

document.addEventListener("DOMContentLoaded", () => {
	loadWords();
});

/* ================================
   HELPERS
================================ */

function getCsrfHeaders() {
	const token = document.querySelector('meta[name="_csrf"]')?.content;
	const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
	return token ? { [header]: token } : {};
}

function esc(s) {
	return String(s || '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;');
}

/* ================================
   LOAD WORDS (Redis → String list)
================================ */

async function loadWords() {
	try {
		const res = await fetch('/api/admin/banned-words');
		const words = await res.json(); // ["spam","hate",...]

		renderWords(words);

	} catch (e) {
		console.error("Load words error:", e);
	}
}

/* ================================
   RENDER WORDS
================================ */

function renderWords(words) {

	const list = document.getElementById('wordList');
	const count = document.getElementById('wordCount');

	list.innerHTML = '';
	count.textContent = words.length;

	if (!words.length) {
		list.innerHTML = '<div class="text-muted small text-center py-3">No banned words</div>';
		return;
	}

	words.forEach(w => {

		const div = document.createElement('div');
		div.style.cssText = `
			display:flex;
			justify-content:space-between;
			align-items:center;
			padding:8px 10px;
			border-bottom:1px solid rgba(255,255,255,0.08);
		`;

		div.innerHTML = `
			<span style="font-size:13px">${esc(w)}</span>

			<button class="btn btn-sm btn-outline-danger"
				onclick="deleteWord('${w}')">
				✕
			</button>
		`;

		list.appendChild(div);
	});
}

/* ================================
   ADD WORD
================================ */

async function addWord() {

	const input = document.getElementById('newWord');
	let word = input.value.trim();

	if (!word) {
		alert("Word enter karo");
		return;
	}

	word = word.toLowerCase();

	try {
		const res = await fetch('/api/admin/banned-words', {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
				...getCsrfHeaders()
			},
			body: JSON.stringify({ word })
		});

		const data = await res.json();

		if (!data.success) {
			alert(data.message || "Already exists");
			return;
		}

		input.value = '';
		loadWords();

	} catch (e) {
		console.error(e);
		alert("Add fail");
	}
}

/* ================================
   DELETE WORD (Redis uses word, not id)
================================ */

async function deleteWord(word) {

	if (!confirm(`"${word}" delete karna hai?`)) return;

	try {
		await fetch(`/api/admin/banned-words/${encodeURIComponent(word)}`, {
			method: 'DELETE',
			headers: getCsrfHeaders()
		});

		loadWords();

	} catch (e) {
		console.error(e);
	}
}

/* ================================
   SCAN CONTENT
================================ */

async function scanContent() {

	const box = document.getElementById('scanResults');

	box.innerHTML = `
		<div class="text-muted small text-center py-3">
			Scanning posts...
		</div>
	`;

	try {
		const res = await fetch('/api/admin/banned-words/scan', {
			method: 'POST',
			headers: getCsrfHeaders()
		});

		const data = await res.json();

		if (!data.success || data.count === 0) {
			box.innerHTML = `
				<div class="text-success text-center py-3">
					✔ No violations found
				</div>
			`;
			return;
		}

		box.innerHTML = '';

		data.violations.forEach(v => {

			const div = document.createElement('div');

			div.style.cssText = `
				border:1px solid rgba(255,255,255,0.08);
				border-radius:8px;
				padding:10px;
				margin-bottom:8px;
				background:#0d0d1a;
			`;

			div.innerHTML = `
				<div style="font-size:13px;margin-bottom:4px">
					<b>Post #${v.postId}</b>
				</div>

				<div style="font-size:12px;color:#f87171;margin-bottom:4px">
					Words: ${v.foundWords.join(', ')}
				</div>

				<div style="font-size:12px;color:#9ca3af">
					${esc(v.content)}
				</div>
			`;

			box.appendChild(div);
		});

	} catch (e) {
		console.error(e);
		box.innerHTML = '<div class="text-danger text-center">Scan failed</div>';
	}
}