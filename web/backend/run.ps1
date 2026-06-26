$root = Resolve-Path "$PSScriptRoot\..\.."
java -cp "$PSScriptRoot\bin" com.yzzhang.weeklyreport.WeeklyReportServer $root
