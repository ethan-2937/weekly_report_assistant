package com.yzzhang.weeklyreport.po;

public class ProjectDetailPO {
    private String name;
    private String userid;
    private String department;
    private String productLine;
    private String customerName;
    private String projectName;
    private String investedDays;
    private String travelExpense;
    private String hospitalityExpense;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUserid() { return userid; }
    public void setUserid(String userid) { this.userid = userid; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
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
