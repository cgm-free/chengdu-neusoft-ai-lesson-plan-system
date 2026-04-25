package cn.edu.nsu.maic.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigService {

    private final JdbcTemplate jdbcTemplate;

    public ConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getOptions() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> configs = jdbcTemplate.queryForList(
                "select config_key, config_value, description from system_config order by id asc"
        );
        List<Map<String, Object>> templates = jdbcTemplate.queryForList(
                "select id, name, lesson_type, description from lesson_template where enabled = 1 order by id asc"
        );

        result.put("configs", configs);
        result.put("templates", templates);
        result.put("lessonTypes", List.of("理论课", "实验课", "理实一体课", "项目实训课", "课程设计课", "讨论课"));
        result.put("teachingModes", List.of(
                "讲授法",
                "案例教学",
                "课堂讨论",
                "任务驱动",
                "项目驱动",
                "演示法",
                "小组协作",
                "讨论法",
                "错误驱动教学",
                "翻转课堂",
                "线上线下混合式教学",
                "问题导向学习",
                "同伴互评"
        ));
        result.put("grades", List.of("大一", "大二", "大三", "大四", "专升本", "研究生"));
        result.put("majors", List.of(
                "人工智能",
                "智能科学与技术",
                "软件工程",
                "计算机科学与技术",
                "数据科学与大数据技术",
                "网络工程",
                "信息管理与信息系统",
                "数字媒体技术",
                "电子商务",
                "财务管理"
        ));
        return result;
    }
}
