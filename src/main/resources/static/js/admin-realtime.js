function startRealtime() {
	setInterval(async () => {
		try {
			const res = await fetch('/api/admin/comments').then(r => r.json())
			const newData = res.comments || res.data || res || []

			if (!Array.isArray(window.allComments)) {
				window.allComments = []
			}

			if (newData.length !== window.allComments.length) {
				window.allComments = newData

				if (typeof buildTree !== 'undefined') {
					renderTree(buildTree(window.allComments))
					updateAnalytics(window.allComments)
					renderChart(window.allComments)
				}

				toast('New comments updated')
			}
		} catch (e) {
			console.log('Realtime error', e)
		}
	}, 5000)
}
