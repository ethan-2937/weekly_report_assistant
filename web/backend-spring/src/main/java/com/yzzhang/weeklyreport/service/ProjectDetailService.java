package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.vo.ProjectDetailVO;

import java.nio.file.Path;
import java.util.List;

public interface ProjectDetailService {
    List<ProjectDetailVO> list(String week);
    Path exportXlsx(String week);
}
