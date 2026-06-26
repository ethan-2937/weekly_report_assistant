package com.yzzhang.weeklyreport.vo;

public class WeekOverviewVO {
    private String week;
    private long expectedCount;
    private long submittedCount;
    private long missingCount;
    private long leaderCandidateCount;
    private boolean hasManagerReport;
    private String generatedAt;

    public String getWeek() { return week; }
    public void setWeek(String week) { this.week = week; }
    public long getExpectedCount() { return expectedCount; }
    public void setExpectedCount(long expectedCount) { this.expectedCount = expectedCount; }
    public long getSubmittedCount() { return submittedCount; }
    public void setSubmittedCount(long submittedCount) { this.submittedCount = submittedCount; }
    public long getMissingCount() { return missingCount; }
    public void setMissingCount(long missingCount) { this.missingCount = missingCount; }
    public long getLeaderCandidateCount() { return leaderCandidateCount; }
    public void setLeaderCandidateCount(long leaderCandidateCount) { this.leaderCandidateCount = leaderCandidateCount; }
    public boolean isHasManagerReport() { return hasManagerReport; }
    public void setHasManagerReport(boolean hasManagerReport) { this.hasManagerReport = hasManagerReport; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
}
