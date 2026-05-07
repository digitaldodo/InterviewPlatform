package com.interview.platform.dto;

import java.util.List;

public class InterviewerFilterOptions {
    private List<String> expertise;
    private List<String> languages;
    private List<String> companies;
    private List<String> experienceLevels;
    private List<String> timeZones;
    private List<String> topics;
    private List<Integer> sessionDurations;

    public InterviewerFilterOptions(List<String> expertise, List<String> languages, List<String> companies,
                                    List<String> experienceLevels, List<String> timeZones,
                                    List<String> topics, List<Integer> sessionDurations) {
        this.expertise = expertise;
        this.languages = languages;
        this.companies = companies;
        this.experienceLevels = experienceLevels;
        this.timeZones = timeZones;
        this.topics = topics;
        this.sessionDurations = sessionDurations;
    }

    public List<String> getExpertise() { return expertise; }
    public void setExpertise(List<String> expertise) { this.expertise = expertise; }
    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }
    public List<String> getCompanies() { return companies; }
    public void setCompanies(List<String> companies) { this.companies = companies; }
    public List<String> getExperienceLevels() { return experienceLevels; }
    public void setExperienceLevels(List<String> experienceLevels) { this.experienceLevels = experienceLevels; }
    public List<String> getTimeZones() { return timeZones; }
    public void setTimeZones(List<String> timeZones) { this.timeZones = timeZones; }
    public List<String> getTopics() { return topics; }
    public void setTopics(List<String> topics) { this.topics = topics; }
    public List<Integer> getSessionDurations() { return sessionDurations; }
    public void setSessionDurations(List<Integer> sessionDurations) { this.sessionDurations = sessionDurations; }
}
