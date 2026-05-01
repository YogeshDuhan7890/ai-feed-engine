// LiveStreamPageController.java mein ye methods add karo
// (Existing PageController ya naya controller)

package com.yogesh.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class LivePageController {

	// Live stream shuru karne ka page (host ke liye)
	@GetMapping("/live/start")
	public String liveStartPage() {
		return "live-start";
	}

	// Live stream dekhne ka page (viewers ke liye)
	@GetMapping("/live/watch/{streamKey}")
	public String liveWatchPage(@PathVariable String streamKey, Model model) {
		model.addAttribute("streamKey", streamKey);
		return "live-watch";
	}

	// Active streams browse karo
	@GetMapping("/live")
	public String liveBrowsePage() {
		return "live-browse";
	}
}