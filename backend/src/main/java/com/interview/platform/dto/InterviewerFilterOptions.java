package com.interview.platform.dto;

import java.util.List;

public class InterviewerFilterOptions {
    private List<String> expertise;
    private List<String> languages;
    private List<String> companies;

    public InterviewerFilterOptions(List<String> expertise, List<String> languages, List<String> companies) {
        this.expertise = expertise;
        this.languages = languages;
        this.companies = companies;
    }

    public List<String> getExpertise() { return expertise; }
    public void setExpertise(List<String> expertise) { this.expertise = expertise; }
    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }
    public List<String> getCompanies() { return companies; }
    public void setCompanies(List<String> companies) { this.companies = companies; }
}
