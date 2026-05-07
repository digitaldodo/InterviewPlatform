package com.interview.platform.dto;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsDtos {
    public static class SummaryResponse {
        private int upcoming;
        private int completed;
        private Double averageRating;
        private Integer streakDays;
        private List<TopicTrend> topicTrends = new ArrayList<>();

        public SummaryResponse() {}

        public SummaryResponse(int upcoming, int completed, Double averageRating, Integer streakDays, List<TopicTrend> topicTrends) {
            this.upcoming = upcoming;
            this.completed = completed;
            this.averageRating = averageRating;
            this.streakDays = streakDays;
            this.topicTrends = topicTrends == null ? new ArrayList<>() : topicTrends;
        }

        public int getUpcoming() { return upcoming; }
        public void setUpcoming(int upcoming) { this.upcoming = upcoming; }
        public int getCompleted() { return completed; }
        public void setCompleted(int completed) { this.completed = completed; }
        public Double getAverageRating() { return averageRating; }
        public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }
        public Integer getStreakDays() { return streakDays; }
        public void setStreakDays(Integer streakDays) { this.streakDays = streakDays; }
        public List<TopicTrend> getTopicTrends() { return topicTrends == null ? new ArrayList<>() : topicTrends; }
        public void setTopicTrends(List<TopicTrend> topicTrends) { this.topicTrends = topicTrends == null ? new ArrayList<>() : topicTrends; }
    }

    public static class TopicTrend {
        private String topic;
        private Double averageRating;
        private int count;

        public TopicTrend() {}

        public TopicTrend(String topic, Double averageRating, int count) {
            this.topic = topic;
            this.averageRating = averageRating;
            this.count = count;
        }

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public Double getAverageRating() { return averageRating; }
        public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}

