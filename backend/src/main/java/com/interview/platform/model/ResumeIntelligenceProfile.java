package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "resume_intelligence_profiles")
public class ResumeIntelligenceProfile {
    @Id
    private String id;
    @Indexed(unique = true)
    private String userId;
    private String activeVersionId;
    private List<ResumeVersion> versions = new ArrayList<>();
    private List<ProgressSnapshot> progressHistory = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getActiveVersionId() {
        return activeVersionId;
    }

    public void setActiveVersionId(String activeVersionId) {
        this.activeVersionId = activeVersionId;
    }

    public List<ResumeVersion> getVersions() {
        return versions == null ? new ArrayList<>() : versions;
    }

    public void setVersions(List<ResumeVersion> versions) {
        this.versions = versions == null ? new ArrayList<>() : versions;
    }

    public List<ProgressSnapshot> getProgressHistory() {
        return progressHistory == null ? new ArrayList<>() : progressHistory;
    }

    public void setProgressHistory(List<ProgressSnapshot> progressHistory) {
        this.progressHistory = progressHistory == null ? new ArrayList<>() : progressHistory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class ResumeVersion {
        private String id;
        private String fileName;
        private String contentType;
        private String resumeUrl;
        private Long fileSizeBytes;
        private Instant uploadedAt;
        private String roleHint;
        private ParsedResume parsedResume = new ParsedResume();
        private AtsSummary atsSummary = new AtsSummary();
        private List<JobMatchSnapshot> jobMatches = new ArrayList<>();
        private Integer interviewReadinessScore = 0;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getResumeUrl() {
            return resumeUrl;
        }

        public void setResumeUrl(String resumeUrl) {
            this.resumeUrl = resumeUrl;
        }

        public Long getFileSizeBytes() {
            return fileSizeBytes;
        }

        public void setFileSizeBytes(Long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
        }

        public Instant getUploadedAt() {
            return uploadedAt;
        }

        public void setUploadedAt(Instant uploadedAt) {
            this.uploadedAt = uploadedAt;
        }

        public String getRoleHint() {
            return roleHint;
        }

        public void setRoleHint(String roleHint) {
            this.roleHint = roleHint;
        }

        public ParsedResume getParsedResume() {
            return parsedResume == null ? new ParsedResume() : parsedResume;
        }

        public void setParsedResume(ParsedResume parsedResume) {
            this.parsedResume = parsedResume == null ? new ParsedResume() : parsedResume;
        }

        public AtsSummary getAtsSummary() {
            return atsSummary == null ? new AtsSummary() : atsSummary;
        }

        public void setAtsSummary(AtsSummary atsSummary) {
            this.atsSummary = atsSummary == null ? new AtsSummary() : atsSummary;
        }

        public List<JobMatchSnapshot> getJobMatches() {
            return jobMatches == null ? new ArrayList<>() : jobMatches;
        }

        public void setJobMatches(List<JobMatchSnapshot> jobMatches) {
            this.jobMatches = jobMatches == null ? new ArrayList<>() : jobMatches;
        }

        public Integer getInterviewReadinessScore() {
            return interviewReadinessScore == null ? 0 : interviewReadinessScore;
        }

        public void setInterviewReadinessScore(Integer interviewReadinessScore) {
            this.interviewReadinessScore = interviewReadinessScore == null ? 0 : interviewReadinessScore;
        }
    }

    public static class ParsedResume {
        private List<String> skills = new ArrayList<>();
        private List<String> technologies = new ArrayList<>();
        private List<String> education = new ArrayList<>();
        private List<String> experience = new ArrayList<>();
        private List<String> projects = new ArrayList<>();
        private List<String> certifications = new ArrayList<>();
        private List<String> companies = new ArrayList<>();
        private List<String> keywords = new ArrayList<>();

        public List<String> getSkills() {
            return skills == null ? new ArrayList<>() : skills;
        }

        public void setSkills(List<String> skills) {
            this.skills = skills == null ? new ArrayList<>() : skills;
        }

        public List<String> getTechnologies() {
            return technologies == null ? new ArrayList<>() : technologies;
        }

        public void setTechnologies(List<String> technologies) {
            this.technologies = technologies == null ? new ArrayList<>() : technologies;
        }

        public List<String> getEducation() {
            return education == null ? new ArrayList<>() : education;
        }

        public void setEducation(List<String> education) {
            this.education = education == null ? new ArrayList<>() : education;
        }

        public List<String> getExperience() {
            return experience == null ? new ArrayList<>() : experience;
        }

        public void setExperience(List<String> experience) {
            this.experience = experience == null ? new ArrayList<>() : experience;
        }

        public List<String> getProjects() {
            return projects == null ? new ArrayList<>() : projects;
        }

        public void setProjects(List<String> projects) {
            this.projects = projects == null ? new ArrayList<>() : projects;
        }

        public List<String> getCertifications() {
            return certifications == null ? new ArrayList<>() : certifications;
        }

        public void setCertifications(List<String> certifications) {
            this.certifications = certifications == null ? new ArrayList<>() : certifications;
        }

        public List<String> getCompanies() {
            return companies == null ? new ArrayList<>() : companies;
        }

        public void setCompanies(List<String> companies) {
            this.companies = companies == null ? new ArrayList<>() : companies;
        }

        public List<String> getKeywords() {
            return keywords == null ? new ArrayList<>() : keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords == null ? new ArrayList<>() : keywords;
        }
    }

    public static class AtsSummary {
        private Integer atsScore = 0;
        private Integer completenessScore = 0;
        private Integer keywordStrength = 0;
        private Integer formattingQualityScore = 0;
        private Integer quantifiedAchievementSignals = 0;
        private Integer keywordDiversityScore = 0;
        private List<String> missingSkills = new ArrayList<>();
        private List<String> formattingQualityIndicators = new ArrayList<>();
        private List<String> recommendations = new ArrayList<>();
        private Map<String, Integer> componentScores = new LinkedHashMap<>();

        public Integer getAtsScore() {
            return atsScore == null ? 0 : atsScore;
        }

        public void setAtsScore(Integer atsScore) {
            this.atsScore = atsScore == null ? 0 : atsScore;
        }

        public Integer getCompletenessScore() {
            return completenessScore == null ? 0 : completenessScore;
        }

        public void setCompletenessScore(Integer completenessScore) {
            this.completenessScore = completenessScore == null ? 0 : completenessScore;
        }

        public Integer getKeywordStrength() {
            return keywordStrength == null ? 0 : keywordStrength;
        }

        public void setKeywordStrength(Integer keywordStrength) {
            this.keywordStrength = keywordStrength == null ? 0 : keywordStrength;
        }

        public Integer getFormattingQualityScore() {
            return formattingQualityScore == null ? 0 : formattingQualityScore;
        }

        public void setFormattingQualityScore(Integer formattingQualityScore) {
            this.formattingQualityScore = formattingQualityScore == null ? 0 : formattingQualityScore;
        }

        public Integer getQuantifiedAchievementSignals() {
            return quantifiedAchievementSignals == null ? 0 : quantifiedAchievementSignals;
        }

        public void setQuantifiedAchievementSignals(Integer quantifiedAchievementSignals) {
            this.quantifiedAchievementSignals = quantifiedAchievementSignals == null ? 0 : quantifiedAchievementSignals;
        }

        public Integer getKeywordDiversityScore() {
            return keywordDiversityScore == null ? 0 : keywordDiversityScore;
        }

        public void setKeywordDiversityScore(Integer keywordDiversityScore) {
            this.keywordDiversityScore = keywordDiversityScore == null ? 0 : keywordDiversityScore;
        }

        public List<String> getMissingSkills() {
            return missingSkills == null ? new ArrayList<>() : missingSkills;
        }

        public void setMissingSkills(List<String> missingSkills) {
            this.missingSkills = missingSkills == null ? new ArrayList<>() : missingSkills;
        }

        public List<String> getFormattingQualityIndicators() {
            return formattingQualityIndicators == null ? new ArrayList<>() : formattingQualityIndicators;
        }

        public void setFormattingQualityIndicators(List<String> formattingQualityIndicators) {
            this.formattingQualityIndicators = formattingQualityIndicators == null ? new ArrayList<>() : formattingQualityIndicators;
        }

        public List<String> getRecommendations() {
            return recommendations == null ? new ArrayList<>() : recommendations;
        }

        public void setRecommendations(List<String> recommendations) {
            this.recommendations = recommendations == null ? new ArrayList<>() : recommendations;
        }

        public Map<String, Integer> getComponentScores() {
            return componentScores == null ? new LinkedHashMap<>() : componentScores;
        }

        public void setComponentScores(Map<String, Integer> componentScores) {
            this.componentScores = componentScores == null ? new LinkedHashMap<>() : componentScores;
        }
    }

    public static class JobMatchSnapshot {
        private String id;
        private String sourceLabel;
        private Integer matchPercent = 0;
        private Integer keywordCoverage = 0;
        private List<String> matchingStrengths = new ArrayList<>();
        private List<String> matchedKeywords = new ArrayList<>();
        private List<String> missingKeywords = new ArrayList<>();
        private String summary;
        private Instant evaluatedAt;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSourceLabel() {
            return sourceLabel;
        }

        public void setSourceLabel(String sourceLabel) {
            this.sourceLabel = sourceLabel;
        }

        public Integer getMatchPercent() {
            return matchPercent == null ? 0 : matchPercent;
        }

        public void setMatchPercent(Integer matchPercent) {
            this.matchPercent = matchPercent == null ? 0 : matchPercent;
        }

        public Integer getKeywordCoverage() {
            return keywordCoverage == null ? 0 : keywordCoverage;
        }

        public void setKeywordCoverage(Integer keywordCoverage) {
            this.keywordCoverage = keywordCoverage == null ? 0 : keywordCoverage;
        }

        public List<String> getMatchingStrengths() {
            return matchingStrengths == null ? new ArrayList<>() : matchingStrengths;
        }

        public void setMatchingStrengths(List<String> matchingStrengths) {
            this.matchingStrengths = matchingStrengths == null ? new ArrayList<>() : matchingStrengths;
        }

        public List<String> getMatchedKeywords() {
            return matchedKeywords == null ? new ArrayList<>() : matchedKeywords;
        }

        public void setMatchedKeywords(List<String> matchedKeywords) {
            this.matchedKeywords = matchedKeywords == null ? new ArrayList<>() : matchedKeywords;
        }

        public List<String> getMissingKeywords() {
            return missingKeywords == null ? new ArrayList<>() : missingKeywords;
        }

        public void setMissingKeywords(List<String> missingKeywords) {
            this.missingKeywords = missingKeywords == null ? new ArrayList<>() : missingKeywords;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public Instant getEvaluatedAt() {
            return evaluatedAt;
        }

        public void setEvaluatedAt(Instant evaluatedAt) {
            this.evaluatedAt = evaluatedAt;
        }
    }

    public static class ProgressSnapshot {
        private Instant capturedAt;
        private Integer atsScore = 0;
        private Integer readinessScore = 0;
        private Integer completedPrepModules = 0;

        public Instant getCapturedAt() {
            return capturedAt;
        }

        public void setCapturedAt(Instant capturedAt) {
            this.capturedAt = capturedAt;
        }

        public Integer getAtsScore() {
            return atsScore == null ? 0 : atsScore;
        }

        public void setAtsScore(Integer atsScore) {
            this.atsScore = atsScore == null ? 0 : atsScore;
        }

        public Integer getReadinessScore() {
            return readinessScore == null ? 0 : readinessScore;
        }

        public void setReadinessScore(Integer readinessScore) {
            this.readinessScore = readinessScore == null ? 0 : readinessScore;
        }

        public Integer getCompletedPrepModules() {
            return completedPrepModules == null ? 0 : completedPrepModules;
        }

        public void setCompletedPrepModules(Integer completedPrepModules) {
            this.completedPrepModules = completedPrepModules == null ? 0 : completedPrepModules;
        }
    }
}
