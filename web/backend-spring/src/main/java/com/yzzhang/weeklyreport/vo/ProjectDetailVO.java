package com.yzzhang.weeklyreport.vo;

public class ProjectDetailVO {
    private int sequence;
    private String productLine;
    private String customerName;
    private String projectName;
    private String investedDays;
    private String travelExpense;
    private String hospitalityExpense;

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public String getProductLine() { return productLine; }
    public void setProductLine(String productLine) { this.productLine = productLine; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getInvestedDays() { return investedDays; }
    public void setInvestedDays(String investedDays) { this.investedDays = investedDays; }
    public String getTravelExpense() { return travelExpense; }
    public void setTravelExpense(String travelExpense) { this.travelExpense = travelExpense; }
    public String getHospitalityExpense() { return hospitalityExpense; }
    public void setHospitalityExpense(String hospitalityExpense) { this.hospitalityExpense = hospitalityExpense; }
}
