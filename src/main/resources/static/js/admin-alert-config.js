let configData = {};

// 🚀 Load on page start
document.addEventListener("DOMContentLoaded", () => {
	loadConfig();
});

// 📡 Fetch config
function loadConfig() {
	fetch("/api/admin/alert-config")
		.then(res => res.json())
		.then(data => {
			console.log("CONFIG 👉", data);
			configData = data;
			renderThresholds(data);
			renderMeta(data);
			loadCurrentStatus(); // optional
		})
		.catch(err => {
			console.error(err);
			document.getElementById("thresholdPanel").innerHTML =
				`<div class="text-danger text-center">Failed to load config</div>`;
		});
}

// 🧾 Render threshold inputs
function renderThresholds(data) {
	const panel = document.getElementById("thresholdPanel");

	const fields = [
		{ key: "threshold:heap_warn", label: "Heap Warning (%)" },
		{ key: "threshold:heap_crit", label: "Heap Critical (%)" },
		{ key: "threshold:cpu_warn", label: "CPU Warning (%)" },
		{ key: "threshold:reports_warn", label: "Reports Threshold" },
		{ key: "threshold:db_ping_warn", label: "DB Ping (ms)" },
		{ key: "threshold:threads_warn", label: "Threads Count" }
	];

	let html = `<h6 class="mb-3">⚙️ Threshold Settings</h6>`;

	fields.forEach(f => {
		html += `
            <div class="mb-2">
                <label class="form-label small fw-semibold">${f.label}</label>
                <input type="number" class="form-control form-control-sm"
                    id="${f.key}" value="${data[f.key] || ''}">
            </div>
        `;
	});

	panel.innerHTML = html;
}

// 📧 Render email + toggle
function renderMeta(data) {
	document.getElementById("alertEmail").value = data["alert:email"] || "";
	document.getElementById("alertsEnabled").checked =
		(data["alert:enabled"] || "true") === "true";
}

// 💾 Save config
function saveConfig() {
	const payload = {};

	const keys = [
		"threshold:heap_warn",
		"threshold:heap_crit",
		"threshold:cpu_warn",
		"threshold:reports_warn",
		"threshold:db_ping_warn",
		"threshold:threads_warn"
	];

	keys.forEach(k => {
		const el = document.getElementById(k);
		if (el) payload[k] = el.value;
	});

	payload["alert:email"] = document.getElementById("alertEmail").value;
	payload["alert:enabled"] = document.getElementById("alertsEnabled").checked ? "true" : "false";

	console.log("SAVE PAYLOAD 👉", payload);

	fetch("/api/admin/alert-config", {
		method: "POST",
		headers: {
			"Content-Type": "application/json",
			[CSRF_HEADER]: CSRF_TOKEN
		},
		body: JSON.stringify(payload)
	})
		.then(res => res.json())
		.then(res => {
			if (res.success) {
				alert("✅ Config saved successfully");
			} else {
				alert("❌ " + res.message);
			}
		})
		.catch(err => {
			console.error(err);
			alert("Error saving config");
		});
}

// 📊 Optional: dummy system status (replace with real API later)
function loadCurrentStatus() {
	const statusEl = document.getElementById("currentStatus");

	// 👉 later replace with real API
	statusEl.innerHTML = `
        <div class="small">
            Heap: 65%<br>
            CPU: 55%<br>
            DB Ping: 120ms<br>
            Threads: 150
        </div>
    `;
}