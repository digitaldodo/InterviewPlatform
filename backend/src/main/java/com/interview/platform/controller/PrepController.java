package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.MarketplaceDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prep")
public class PrepController {
    @GetMapping("/hub")
    public ResponseEntity<ApiResponse<MarketplaceDtos.PrepHubResponse>> hub() {
        MarketplaceDtos.PrepHubResponse response = new MarketplaceDtos.PrepHubResponse(
                List.of(
                        new MarketplaceDtos.PrepTrack("FAANG backend track", "Systems, API design, and scaling drills for backend-focused loops.", List.of("High-level architecture", "Data modeling", "Incident narratives")),
                        new MarketplaceDtos.PrepTrack("Product company PM track", "Cross-functional product thinking and execution stories.", List.of("Prioritization", "Product sense", "Metrics tradeoffs")),
                        new MarketplaceDtos.PrepTrack("Consulting case track", "Structured market sizing and case delivery practice.", List.of("Frameworks", "Executive communication", "Recommendation clarity"))
                ),
                List.of(
                        new MarketplaceDtos.PrepTrack("Behavioral foundations", "Build stories that stay concise under pressure.", List.of("STAR compression", "Conflict handling", "Leadership without title")),
                        new MarketplaceDtos.PrepTrack("Hiring manager narratives", "Focus on impact, scope, and judgment.", List.of("Ownership", "Ambiguity", "Cross-team influence"))
                ),
                List.of(
                        new MarketplaceDtos.PrepTrack("Coding rounds", "Sharpen speed and pattern recognition for timed screens.", List.of("Arrays and strings", "Graphs", "Dynamic programming")),
                        new MarketplaceDtos.PrepTrack("Machine coding", "Practice implementation depth and tradeoff discussion.", List.of("Requirements framing", "Code structure", "Extensibility")),
                        new MarketplaceDtos.PrepTrack("System design", "Level-aware architecture prep from mid to staff.", List.of("Capacity planning", "Reliability", "Operational readiness"))
                ),
                List.of(
                        new MarketplaceDtos.PrepResource("Resume scorecard", "Checklist", "Review impact bullets, clarity, and seniority signaling before applying.", "Review resume"),
                        new MarketplaceDtos.PrepResource("Interview warm-up agenda", "Template", "A quick pre-session routine for behavioral, coding, and design practice.", "Open agenda"),
                        new MarketplaceDtos.PrepResource("Behavioral answer bank", "Worksheet", "Capture your strongest stories and map them to common prompts.", "Build story bank"),
                        new MarketplaceDtos.PrepResource("Company loop planner", "Planner", "Organize rounds, topic bets, and prep focus areas by target company.", "Plan loop")
                )
        );
        return ResponseEntity.ok(ApiResponse.success("Prep hub fetched", response));
    }
}
