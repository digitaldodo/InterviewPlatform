package com.interview.platform.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class PrepDtos {

    public record ScorePoint(
            String label,
            Integer value,
            Instant timestamp
    ) {}

    public record RoadmapStep(
            Integer sequence,
            String title,
            String description,
            String difficulty,
            String status,
            List<String> focusAreas
    ) {}

    public record RecommendationCard(
            String title,
            String description,
            String priority,
            List<String> actions
    ) {}

    public record ResumeParsedData(
            List<String> skills,
            List<String> technologies,
            List<String> education,
            List<String> experience,
            List<String> projects,
            List<String> certifications,
            List<String> companies,
            List<String> keywords
    ) {}

    public record AtsAnalysis(
            Integer atsScore,
            Integer completenessScore,
            Integer keywordStrength,
            Integer formattingQualityScore,
            Integer quantifiedAchievementSignals,
            Integer keywordDiversityScore,
            List<String> missingSkills,
            List<String> formattingQualityIndicators,
            List<String> recommendations,
            Map<String, Integer> componentScores
    ) {}

    public record ResumeVersionSummary(
            String id,
            String fileName,
            String contentType,
            String resumeUrl,
            Instant uploadedAt,
            Boolean active,
            Integer atsScore,
            Integer readinessScore,
            Integer latestMatchScore,
            String roleHint
    ) {}

    public record JobMatchResult(
            Integer matchPercent,
            Integer keywordCoverage,
            List<String> matchingStrengths,
            List<String> matchedKeywords,
            List<String> missingKeywords,
            String summary,
            Instant evaluatedAt
    ) {}

    public record PrepIntelligenceDashboard(
            ResumeVersionSummary activeResume,
            List<ResumeVersionSummary> resumeHistory,
            ResumeParsedData parsedData,
            AtsAnalysis atsAnalysis,
            JobMatchResult latestJobMatch,
            List<ScorePoint> atsScoreTrend,
            List<ScorePoint> readinessTrend,
            Integer interviewReadinessScore,
            Map<String, Integer> skillGapAnalysis,
            Map<String, Integer> topicReadiness,
            List<RoadmapStep> roadmap,
            List<String> weakAreas,
            List<String> recommendedTopics,
            List<RecommendationCard> recommendations,
            Integer completedPrepModules
    ) {}

    public static class JobDescriptionMatchRequest {
        private String jobDescription;
        private String roleHint;

        public String getJobDescription() {
            return jobDescription;
        }

        public void setJobDescription(String jobDescription) {
            this.jobDescription = jobDescription;
        }

        public String getRoleHint() {
            return roleHint;
        }

        public void setRoleHint(String roleHint) {
            this.roleHint = roleHint;
        }
    }

    public static class ActivateResumeRequest {
        private String resumeVersionId;

        public String getResumeVersionId() {
            return resumeVersionId;
        }

        public void setResumeVersionId(String resumeVersionId) {
            this.resumeVersionId = resumeVersionId;
        }
    }
}
