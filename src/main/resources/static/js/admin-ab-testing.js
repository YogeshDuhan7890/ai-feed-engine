let allTests = [];

document.addEventListener("DOMContentLoaded", () => {
	fetchTests();
});

// 📡 Fetch tests
function fetchTests() {
	fetch("/api/admin/ab-tests")
		.then(res => res.json())
		.then(data => {
			console.log("RAW API 👉", data);

			// ✅ Parse data field
			allTests = data.map(t => {
				let parsed = {};
				try {
					parsed = JSON.parse(t.data);
				} catch (e) {
					console.error("JSON parse failed 👉", t.data);
				}

				return {
					id: t.id,
					name: parsed.name,
					description: parsed.description,
					variantAName: parsed.variantA,
					variantBName: parsed.variantB,
					split: parsed.split,
					status: parsed.status,
					created: parsed.created,

					// stats
					variantACount: t.variantA || 0,
					variantBCount: t.variantB || 0,
					total: t.total || 0
				};
			});

			console.log("PARSED 👉", allTests);

			renderTests(allTests);
		})
		.catch(err => {
			console.error(err);
			document.getElementById("testsContainer").innerHTML =
				`<div class="text-danger text-center">Failed to load tests</div>`;
		});
}

// 🧾 Render UI
function renderTests(tests) {
	const container = document.getElementById("testsContainer");

	if (!tests.length) {
		container.innerHTML = `<div class="text-muted text-center">No tests found</div>`;
		return;
	}

	let html = "";

	tests.forEach(t => {
		html += `
        <div class="col-md-4">
            <div class="card shadow-sm h-100">
                <div class="card-body">

                    <div class="d-flex justify-content-between">
                        <h6>${t.name || t.id}</h6>
                        <span class="badge ${t.status === 'active' ? 'bg-success' : 'bg-secondary'}">
                            ${t.status}
                        </span>
                    </div>

                    <p class="small text-muted">ID: ${t.id}</p>

                    <div class="mt-2">
                        <strong>A:</strong> ${t.variantAName || '-'}<br>
                        <strong>B:</strong> ${t.variantBName || '-'}
                    </div>

                    <div class="mt-2">
                        <small>Split: ${t.split}% / ${100 - t.split}%</small>
                    </div>

                    <hr>

                    <div class="mt-2">
                        <small>
                            A Users: ${t.variantACount} <br>
                            B Users: ${t.variantBCount} <br>
                            Total: ${t.total}
                        </small>
                    </div>

                    <div class="mt-3">
                        <button class="btn btn-sm btn-danger"
                            onclick="deleteTest('${t.id}')">
                            Delete
                        </button>
                    </div>

                </div>
            </div>
        </div>`;
	});

	container.innerHTML = html;
}

// ➕ Create Test
function createTest() {
	const payload = {
		id: document.getElementById("testId").value,
		name: document.getElementById("testName").value,
		description: "",
		variantA: document.getElementById("variantA").value,
		variantB: document.getElementById("variantB").value,
		split: document.getElementById("split").value
	};

	if (!payload.id || !payload.name) {
		alert("ID and Name required");
		return;
	}

	fetch("/api/admin/ab-tests", {
		method: "POST",
		headers: {
			"Content-Type": "application/json",
			[CSRF_HEADER]: CSRF_TOKEN
		},
		body: JSON.stringify(payload)
	})
		.then(res => res.json())
		.then(res => {
			console.log("CREATE RES 👉", res);
			hideModal();
			fetchTests();
		})
		.catch(err => {
			console.error(err);
			alert("Failed to create test");
		});
}

// 🗑 Delete
function deleteTest(id) {
	if (!confirm("Delete this test?")) return;

	fetch(`/api/admin/ab-tests/${id}`, {
		method: "DELETE",
		headers: {
			[CSRF_HEADER]: CSRF_TOKEN
		}
	})
		.then(() => fetchTests())
		.catch(err => console.error(err));
}

// 🪟 Modal
function showCreateModal() {
	const modal = new bootstrap.Modal(document.getElementById("createModal"));
	modal.show();
}

function hideModal() {
	const modalEl = document.getElementById("createModal");
	const modal = bootstrap.Modal.getInstance(modalEl);
	modal.hide();
}