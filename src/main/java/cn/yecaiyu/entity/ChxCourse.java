package cn.yecaiyu.entity;

import lombok.Data;

import java.util.List;

@Data
public class ChxCourse {
    private String clazzId;
    private String courseId;
    private String courseName;
    private String teacherName;
    private String cpi;

    private List<ChxSection> sections;
}
