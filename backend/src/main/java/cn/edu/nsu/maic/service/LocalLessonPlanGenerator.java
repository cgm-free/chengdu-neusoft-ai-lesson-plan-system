package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.LessonPlanRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocalLessonPlanGenerator {

    private final ObjectMapper objectMapper;

    public LocalLessonPlanGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String generateJson(LessonPlanRequest request) {
        try {
            return objectMapper.writeValueAsString(generate(request));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("生成教案JSON失败", e);
        }
    }

    private Map<String, Object> generate(LessonPlanRequest request) {
        int periodCount = valueOrDefault(request.getPeriodCount(), 2);
        int minutesPerPeriod = valueOrDefault(request.getMinutesPerPeriod(), 40);
        int totalMinutes = periodCount * minutesPerPeriod;

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("basicInfo", basicInfo(request, totalMinutes));
        plan.put("studentAnalysis", textOrDefault(request.getStudentAnalysis(), studentAnalysis(request)));
        plan.put("objectives", objectives(request));
        plan.put("keyPoints", keyPoints(request));
        plan.put("difficultPoints", difficultPoints(request));
        plan.put("teachingMethods", teachingMethods(request));
        plan.put("resources", resources(request));
        plan.put("ideologyDesign", ideologyDesign(request));
        plan.put("teachingProcess", teachingProcess(request, totalMinutes));
        plan.put("practiceTask", practiceTask(request));
        plan.put("homework", homework(request));
        plan.put("evaluationDesign", evaluationDesign(request));
        plan.put("reflection", "课后根据学生课堂参与度、任务完成情况和问题反馈，调整案例难度与实践任务分层要求。");
        return plan;
    }

    private Map<String, Object> basicInfo(LessonPlanRequest request, int totalMinutes) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseName", request.getCourseName());
        data.put("topic", request.getTopic());
        data.put("major", textOrDefault(request.getMajor(), "相关专业"));
        data.put("grade", textOrDefault(request.getGrade(), "本科学生"));
        data.put("targetStudents", textOrDefault(request.getTargetStudents(), "授课班级学生"));
        data.put("lessonType", request.getLessonType());
        data.put("teachingMode", textOrDefault(request.getTeachingMode(), "案例教学与任务驱动"));
        data.put("totalMinutes", totalMinutes);
        return data;
    }

    private Map<String, Object> objectives(LessonPlanRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (isJavaPolymorphismLesson(request)) {
            data.put("knowledge", List.of(
                    "能够用自己的话区分继承、方法重写、向上转型、向下转型和动态绑定的适用场景。",
                    "能够指出 Employee、Developer、Manager 等类层次中父类引用调用子类重写方法的执行结果。",
                    "能够说明 instanceof 检查与强制类型转换的关系，并解释 ClassCastException 出现的原因。"
            ));
            data.put("ability", List.of(
                    "能够实现一个包含 Employee 父类和至少 3 个子类的员工薪酬计算程序，并通过 List<Employee> 完成统一遍历处理。",
                    "能够在需要访问子类特有行为时使用 instanceof 进行安全向下转型，并提交至少 2 个测试用例。",
                    "能够根据教师给出的典型错误代码定位并修正重写签名错误、错误强转和父类字段访问不当等问题。"
            ));
        } else {
            data.put("knowledge", List.of(
                    "能够说出" + request.getTopic() + "的核心概念、关键术语和适用边界。",
                    "能够区分" + request.getTopic() + "中容易混淆的知识点，并用课堂案例进行说明。"
            ));
            data.put("ability", List.of(
                    "能够结合具体案例完成与" + request.getTopic() + "相关的问题分析、方案设计和结果验证。",
                    "能够按照任务要求提交可检查的成果材料，并根据反馈完成一次修正。"
            ));
        }
        data.put("quality", List.of(
                "能够按照命名规范、提交规范和注释规范整理课堂成果。",
                "能够在小组协作中说明个人贡献、听取同伴反馈并完成改进。"
        ));
        data.put("obeSupport", Boolean.FALSE.equals(request.getIncludeObe()) ? List.of() : List.of(
                "支撑毕业要求指标点 1.3：能够将专业基础知识用于软件问题建模与分析，证据为类图草图和关键代码说明。",
                "支撑毕业要求指标点 3.2：能够设计满足约束的软件模块，证据为分层任务代码、测试用例和运行截图。",
                "支撑毕业要求指标点 9.1：能够在小组中承担角色并完成协作交付，证据为小组互评表和提交记录。"
        ));
        return data;
    }

    private String studentAnalysis(LessonPlanRequest request) {
        if (isJavaPolymorphismLesson(request)) {
            return textOrDefault(request.getGrade(), "大二") + textOrDefault(request.getMajor(), "软件工程")
                    + "学生已完成 Java 基础语法、类与对象、构造方法和封装练习，但在继承与多态实验中通常存在三类具体问题："
                    + "第一，能写出父类和子类，却说不清“父类引用指向子类对象”时方法调用为什么执行子类版本；"
                    + "第二，容易把向下转型当成万能写法，缺少 instanceof 检查，运行时出现 ClassCastException 后不会定位；"
                    + "第三，强学生能很快完成单一父子类示例，弱学生会卡在重写方法签名、构造方法调用和集合遍历上。"
                    + "因此本节课以员工薪酬与角色权限处理任务为主线，通过分层检查点把概念讲解、错误修正和代码验收连起来。";
        }
        return textOrDefault(request.getGrade(), "本科") + textOrDefault(request.getMajor(), "相关专业")
                + "学生具备本课程前置基础，但在" + request.getTopic()
                + "学习中容易出现概念会背、任务不会拆、结果不会验三类问题。课堂需要通过诊断题、过程检查点和分层任务，"
                + "帮助学生把知识点转化为可提交、可运行、可评价的学习成果。";
    }

    private List<Map<String, String>> keyPoints(LessonPlanRequest request) {
        if (isJavaPolymorphismLesson(request)) {
            return List.of(
                    pointItem("继承层次设计", "抽取 Employee 父类公共字段与行为，明确 Developer、Tester、Manager 子类差异。"),
                    pointItem("方法重写与动态绑定", "通过 List<Employee> 统一遍历员工对象，观察 calculateSalary() 调用子类实现的结果。"),
                    pointItem("安全向下转型", "使用 instanceof 判断 Manager 后访问团队奖金、管理人数等子类特有行为。"),
                    pointItem("代码质量要求", "类命名、方法职责、注释、测试用例和提交材料符合软件工程实验规范。")
            );
        }
        return List.of(
                pointItem(request.getTopic() + "的核心概念", "说清适用边界和常见误区，避免把术语背诵当成理解。"),
                pointItem("问题分析路径", "将课堂知识迁移到具体任务中的分析路径和实现步骤。"),
                pointItem("成果输出规范", "结合规范要求完成可检查的课堂成果输出。"),
                pointItem("典型场景判断", "通过课堂案例判断该知识点在不同问题中的使用时机。")
        );
    }

    private List<Map<String, String>> difficultPoints(LessonPlanRequest request) {
        if (isJavaPolymorphismLesson(request)) {
            return List.of(
                    difficultItem("区分继承复用与多态统一处理", "学生容易把“继承复用代码”和“多态统一处理对象”混为一谈。", "在同一段 List<Employee> 遍历代码中替换不同子类对象，让学生先预测再验证输出。"),
                    difficultItem("理解安全向下转型边界", "学生容易直接强转导致 ClassCastException。", "提供错误代码先复现异常，再补充 instanceof 分支并解释为什么必须先判断。"),
                    difficultItem("兼顾不同层次学生进度", "强弱学生完成速度差异大，容易出现一部分学生空等、一部分学生掉队。", "设置基础、提高、挑战三个检查点，基础达标后立即进入扩展任务。")
            );
        }
        return List.of(
                difficultItem("准确判断" + request.getTopic() + "的适用场景", "学生容易只记概念，不会根据问题特征做结构或方法判断。", "用反例和边界条件进行对比，让学生先判断后解释。"),
                difficultItem("独立完成任务拆解与结果验证", "学生能模仿示例，但不会把任务拆成可执行步骤。", "提供检查点和验收清单，要求每一步都产出结果。"),
                difficultItem("把过程经验沉淀为规范表达", "学生更关注结果，忽视问题记录、修正说明和规范提交。", "要求学生提交问题记录和修正说明，并纳入评价。")
        );
    }

    private List<String> teachingMethods(LessonPlanRequest request) {
        Set<String> methods = new LinkedHashSet<>();
        List<String> selectedModes = splitTeachingModes(request.getTeachingMode());
        if (selectedModes.isEmpty()) {
            selectedModes = List.of("案例教学", "任务驱动");
        }

        methods.add("以" + selectedModes.get(0) + "为主导组织课堂主线。");
        if (selectedModes.size() > 1) {
            methods.add("融合" + String.join("、", selectedModes.subList(1, selectedModes.size())) + "，分别用于导入、讲解、实践、展示与评价环节。");
        }
        methods.add("课堂提问与即时反馈，用于检查关键知识点掌握情况。");
        if (isPracticeLesson(request.getLessonType())) {
            methods.add("实验演示与巡视指导，用于支撑学生完成实践任务。");
        }
        return new ArrayList<>(methods);
    }

    private List<String> splitTeachingModes(String value) {
        if (!notBlank(value)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split("[、,，+;/；/]+")) {
            String text = item.trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private List<String> resources(LessonPlanRequest request) {
        List<String> resources = new ArrayList<>();
        resources.add("课程PPT");
        resources.add("课堂案例材料");
        resources.add("在线教学平台或课堂提交工具");
        if (notBlank(request.getTextbook())) {
            resources.add(request.getTextbook());
        }
        if (notBlank(request.getExperimentEnv())) {
            resources.add(request.getExperimentEnv());
        }
        return resources;
    }

    private List<Map<String, String>> ideologyDesign(LessonPlanRequest request) {
        if (Boolean.FALSE.equals(request.getIncludeIdeology())) {
            return List.of();
        }
        if (isJavaPolymorphismLesson(request)) {
            return List.of(
                    ideologyItem("导入环节", "企业员工薪酬计算案例", "讨论软件代码错误可能造成的薪酬计算偏差，引导学生形成质量责任意识。"),
                    ideologyItem("错误代码修正环节", "类型安全与边界检查", "强调不能用“能跑就行”替代类型安全和边界检查，培养严谨、负责的工程态度。"),
                    ideologyItem("小组互评环节", "代码贡献与问题修正说明", "要求学生说明个人贡献和修正过程，强化协作诚信和持续改进意识。")
            );
        }
        return List.of(
                ideologyItem("任务导入环节", "真实应用场景", "引导学生理解专业学习与社会责任、职业规范之间的关系。"),
                ideologyItem("过程检查环节", "数据安全与工程伦理", "强调数据安全、知识产权和团队协作意识，避免思政内容脱离课堂任务。"),
                ideologyItem("结果复盘环节", "学习成果回顾", "围绕质量意识、责任意识和持续改进开展 3 分钟讨论。")
        );
    }

    private List<Map<String, Object>> teachingProcess(LessonPlanRequest request, int totalMinutes) {
        int[] d = distribute(totalMinutes);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (isJavaPolymorphismLesson(request)) {
            rows.add(processRow("问题导入", d[0],
                    "展示员工薪酬计算错误案例：父类引用遍历不同员工对象时输出异常，引出“为什么同一行代码会调用不同实现”。",
                    "观察运行结果，预测 Developer、Tester、Manager 的薪酬计算输出，写下 1 个疑问。",
                    "形成 1 条书面预测和 1 个课堂疑问。",
                    "学生能说出父类引用和实际对象类型不同这一现象。",
                    "把继承与多态放入软件工程业务场景，避免概念空转。",
                    "错误案例代码、运行截图", "随机提问 2 名学生说明预测依据"));
            rows.add(processRow("前测诊断", d[1],
                    "发布 3 道快速诊断题：重写签名判断、父类引用调用结果、错误强转风险。",
                    "独立作答并提交选项，标记自己不确定的问题。",
                    "提交 3 道诊断题结果。",
                    "正确率低于 70% 的题目进入重点讲解。",
                    "识别共性误区，决定讲解重点。",
                    "在线问卷或课堂平台", "正确率低于 70% 的题目进入重点讲解"));
            rows.add(processRow("微讲解", d[2],
                    "用类图和 8 行核心代码说明继承、方法重写、动态绑定、instanceof 与向下转型的关系。",
                    "根据教师代码标注父类引用、实际对象类型和最终执行的方法。",
                    "完成带标注的核心代码记录。",
                    "学生能口头解释 1 个多态调用结果。",
                    "用短讲解建立必要概念，控制理论时间。",
                    "PPT、板书、核心代码片段", "学生能口头解释 1 个多态调用结果"));
            rows.add(processRow("错误驱动演示", d[3],
                    "现场运行错误强转代码，复现 ClassCastException，再改为 instanceof 安全分支。",
                    "记录异常信息，指出出错行，补全安全转换代码。",
                    "提交异常定位结果和修正后的判断分支。",
                    "检查学生是否能写出 instanceof 判断。",
                    "用可复现错误突破向下转型难点。",
                    "IntelliJ IDEA、JDK 17", "检查学生是否能写出 instanceof 判断"));
            rows.add(processRow("基础检查点", d[4],
                    "布置基础任务：实现 Employee、Developer、Tester、Manager，要求每个子类重写 calculateSalary()；每 8 分钟巡视一次。",
                    "完成类结构和薪酬计算方法，提交第一次运行截图；落后学生领取骨架代码继续完成。",
                    "提交类结构代码和第一次运行截图。",
                    "检查 3 个子类、1 次重写、1 张运行截图。",
                    "保证全体学生达成本节课最低目标。",
                    "任务单、骨架代码、提交平台", "检查 3 个子类、1 次重写、1 张运行截图"));
            rows.add(processRow("提高与挑战任务", d[5],
                    "发布提高任务：使用 List<Employee> 统一遍历输出工资；发布挑战任务：对 Manager 使用 instanceof 输出团队奖金和管理人数。",
                    "基础完成者进入提高/挑战任务；未完成者根据检查点反馈修正构造方法和重写签名。",
                    "提交统一遍历结果、挑战代码片段和修正记录。",
                    "统计基础达标率、提高任务完成率和挑战任务提交数。",
                    "解决强弱学生进度差异，体现多态价值。",
                    "分层任务单、测试用例", "基础达标率、提高任务完成率、挑战任务提交数"));
            rows.add(processRow("互评展示", d[6],
                    "抽取 2 份典型代码：一份正确实现，一份含错误强转或重复 if-else 的代码，组织同伴互评。",
                    "用 Rubric 从功能正确性、类型安全、代码规范、说明表达四项给出互评意见。",
                    "生成互评意见表和展示记录。",
                    "检查学生是否能依据 Rubric 给出具体反馈。",
                    "让大部分学生通过互评参与展示，而不是只看少数小组汇报。",
                    "学生代码、Rubric 表", "同伴互评分 + 教师点评"));
            rows.add(processRow("总结与提交", d[7],
                    "总结多态的业务价值、instanceof 使用边界和下节接口/抽象类衔接，说明课后提交要求。",
                    "提交代码、运行截图、测试用例和 100 字问题反思。",
                    "完成课堂提交包和问题反思。",
                    "检查提交物完整性，缺项学生课后补交。",
                    "形成闭环证据，服务 OBE 达成评价。",
                    "课堂平台", "提交物完整性检查"));
            return rows;
        }
        rows.add(processRow("问题导入", d[0], "展示与" + request.getTopic() + "相关的真实案例，提出本节课核心问题。", "观察案例，回答导入问题，明确学习任务。", "形成导入判断结果。", "能说出本节课要解决的核心问题。", "建立问题情境。", "案例材料", "课堂提问"));
        rows.add(processRow("前测诊断", d[1], "发布 3 道诊断题，识别学生已有基础和误区。", "独立完成诊断题，标记不确定问题。", "提交诊断题结果。", "统计正确率并锁定共性误区。", "明确教学靶点。", "课堂平台", "诊断正确率"));
        rows.add(processRow("微讲解", d[2], "讲解" + request.getTopic() + "的核心概念、关键步骤和注意事项。", "听讲记录，围绕关键问题进行提问。", "形成结构化笔记。", "抽查 2 名学生复述关键概念。", "建立知识框架。", "PPT、板书", "提问反馈"));
        rows.add(processRow("示范演示", d[3], "演示一个最小可运行案例，强调常见错误和修正方法。", "跟随演示完成关键步骤。", "完成最小案例运行结果。", "检查是否能复现关键步骤。", "降低实践起步难度。", "示例材料", "关键步骤检查"));
        rows.add(processRow("基础检查点", d[4], "布置基础任务，说明提交物和验收标准，巡视指导。", "完成基础任务并提交阶段成果。", "提交基础任务成果。", "基础成果必须在本环节完成验收。", "保证最低达成。", "任务单", "任务验收"));
        rows.add(processRow("提高与挑战任务", d[5], "发布提高任务和挑战任务，分层指导。", "根据进度选择提高或挑战任务。", "提交提高或挑战任务扩展结果。", "检查任务分层达成情况。", "支持差异化学习。", "拓展材料", "分层完成率"));
        rows.add(processRow("互评展示", d[6], "组织同伴互评，点评典型成果和问题。", "展示成果，参与互评。", "形成展示记录和互评意见。", "互评意见必须具体到一个优点和一个改进点。", "通过反馈强化重点。", "学生作品", "互评表"));
        rows.add(processRow("总结与提交", d[7], "总结本节课重点，布置课后任务和预习要求。", "完成课堂小结，提交学习成果。", "完成课堂提交和个人总结。", "检查提交物完整性。", "形成学习闭环。", "课堂平台", "提交物检查"));
        return rows;
    }

    private int[] distribute(int totalMinutes) {
        double[] weights = {0.06, 0.06, 0.10, 0.09, 0.25, 0.22, 0.10, 0.12};
        int[] minimums = {3, 3, 5, 4, 8, 7, 4, 3};
        int[] result = new int[weights.length];
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            result[i] = Math.max(minimums[i], Math.round(totalMinutes * (float) weights[i]));
            sum += result[i];
        }
        int diff = totalMinutes - sum;
        int[] adjustable = {4, 5, 7, 6, 3, 2, 1, 0};
        int guard = 0;
        while (diff != 0 && guard < 500) {
            for (int index : adjustable) {
                if (diff == 0) {
                    break;
                }
                if (diff > 0) {
                    result[index]++;
                    diff--;
                } else if (result[index] > minimums[index]) {
                    result[index]--;
                    diff++;
                }
            }
            guard++;
        }
        return result;
    }

    private Map<String, Object> processRow(String stage, int duration, String teacherActivity, String studentActivity,
                                           String output, String checkpoint, String designPurpose, String resources, String evaluation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stage", stage);
        row.put("duration", duration);
        row.put("teacherActivity", teacherActivity);
        row.put("studentActivity", studentActivity);
        row.put("output", output);
        row.put("checkpoint", checkpoint);
        row.put("designPurpose", designPurpose);
        row.put("resources", resources);
        row.put("evaluation", evaluation);
        return row;
    }

    private Map<String, Object> practiceTask(LessonPlanRequest request) {
        Map<String, Object> task = new LinkedHashMap<>();
        if (isJavaPolymorphismLesson(request)) {
            task.put("taskName", "员工薪酬与角色权限处理系统");
            task.put("basicTasks", List.of(
                    "定义 Employee 父类，包含 name、baseSalary 字段和 calculateSalary() 方法。",
                    "定义 Developer、Tester、Manager 至少 3 个子类并完成方法重写。"
            ));
            task.put("advancedTasks", List.of(
                    "使用 List<Employee> 存放不同类型员工，通过一次循环输出姓名、角色和工资，禁止为每个子类单独写重复循环。"
            ));
            task.put("challengeTasks", List.of(
                    "对 Manager 使用 instanceof 完成安全向下转型，输出团队奖金、管理人数或审批权限。",
                    "补充一个错误强转反例并解释 ClassCastException 出现原因。"
            ));
            task.put("steps", List.of(
                    "画出 Employee、Developer、Tester、Manager 的简易类图，交付物为类图草图或截图。",
                    "根据类图完成父类字段、构造方法和 calculateSalary() 方法定义，交付物为第一次代码提交。",
                    "分别实现 3 个子类的薪酬计算规则，并在 main 方法中构造不同子类对象，交付物为运行截图。",
                    "用 List<Employee> 统一遍历员工对象，观察动态绑定下的输出结果，交付物为控制台输出结果。",
                    "为 Manager 增加子类特有属性，使用 instanceof 完成安全向下转型，交付物为修正后的代码片段。",
                    "运行测试用例，修正重写签名错误、构造方法调用错误和强转异常，交付物为测试记录和问题说明。"
            ));
            task.put("acceptanceCriteria", List.of(
                    "基础达标：至少 1 个父类、3 个子类、3 个重写方法，程序可运行并输出不同员工工资。",
                    "提高达标：使用 List<Employee> 完成统一遍历，输出结果能证明动态绑定生效。",
                    "挑战达标：使用 instanceof 后再向下转型，能够说明为什么直接强转可能抛出 ClassCastException。",
                    "规范达标：类名、方法名、缩进、注释和提交文件命名符合实验要求。",
                    "证据达标：提交运行截图、测试用例和问题修正说明，缺任一项不得评为优秀。"
            ));
            return task;
        }
        task.put("taskName", request.getTopic() + "分层实践任务");
        task.put("basicTasks", List.of("围绕本节课主题完成最低达成要求，并提交可检查成果。"));
        task.put("advancedTasks", List.of("在基础任务上增加一个真实约束或边界情况。"));
        task.put("challengeTasks", List.of("完成一个开放扩展点，并说明设计理由。"));
        task.put("steps", List.of("阅读任务要求，明确输入、过程和输出。", "完成基础任务并通过检查点。", "根据个人进度完成提高或挑战任务。", "检查结果并根据反馈修改。", "提交成果并进行简要总结。"));
        task.put("acceptanceCriteria", List.of("基础成果完整，符合最低要求。", "关键步骤清晰，能够说明解决思路。", "提交材料规范，包含过程证据和结果证据。", "能够根据反馈完成一次修正。"));
        return task;
    }

    private List<String> homework(LessonPlanRequest request) {
        if (isJavaPolymorphismLesson(request)) {
            return List.of(
                    "基础巩固：整理继承、方法重写、向上转型、向下转型、instanceof、动态绑定 6 个概念，每个概念配 1 行代码例子。",
                    "拓展挑战：为员工系统增加 Intern 或 Sales 子类，并用同一段 List<Employee> 遍历代码输出工资。",
                    "提交要求：上传源代码、运行截图、2 个测试用例和 100 字反思，说明本节课修正过的一个错误。",
                    "教师反馈：下次课前选取 2 个典型提交进行点评，未通过基础验收的学生需根据反馈二次提交。"
            );
        }
        return List.of(
                "基础巩固：整理本节课" + request.getTopic() + "核心知识点，形成学习笔记。",
                "拓展挑战：完成一项带边界条件的案例分析或实践练习，并提交结果。",
                "提交要求：上传过程材料、结果文件和简短反思。",
                "教师反馈：根据共性问题进行下次课前 5 分钟讲评。"
        );
    }

    private List<Map<String, String>> evaluationDesign(LessonPlanRequest request) {
        if (isJavaPolymorphismLesson(request)) {
            return List.of(
                    evaluationItem("教师评价", "50%", "代码、运行截图、测试用例", "功能正确性 20%，多态统一处理 10%，instanceof 安全转型 10%，代码规范 10%。"),
                    evaluationItem("过程评价", "20%", "前测诊断、阶段检查点、课堂问题记录", "前测诊断、基础检查点、提高任务检查点和课堂问题记录各 5%。"),
                    evaluationItem("学生自评", "10%", "自评表、问题修正说明", "学生对照 Rubric 标注自己完成的基础、提高、挑战任务，并写出一个修正过的错误。"),
                    evaluationItem("同伴互评", "10%", "互评表、展示记录", "小组成员从可读性、类型安全和说明清晰度三个维度互评。"),
                    evaluationItem("OBE 证据", "10%", "代码提交记录、运行截图、测试用例、互评表和反思文本", "共同作为课程目标达成证据。")
            );
        }
        return List.of(
                evaluationItem("教师评价", "50%", "任务结果、关键步骤记录", "关注任务结果、关键步骤、规范性和问题修正情况。"),
                evaluationItem("过程评价", "20%", "诊断题、检查点记录", "根据诊断题、检查点完成情况和课堂参与度判定。"),
                evaluationItem("学生自评", "10%", "自评清单", "对照任务清单说明个人达成情况。"),
                evaluationItem("同伴互评", "10%", "互评意见", "围绕成果质量和协作贡献给出反馈。"),
                evaluationItem("OBE 证据", "10%", "任务成果、课堂记录、作业提交和反思报告", "作为课程目标达成的综合证据。")
        );
    }

    private Map<String, String> pointItem(String point, String reason) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("point", point);
        item.put("reason", reason);
        return item;
    }

    private Map<String, String> difficultItem(String point, String reason, String strategy) {
        Map<String, String> item = pointItem(point, reason);
        item.put("strategy", strategy);
        return item;
    }

    private Map<String, String> ideologyItem(String stage, String carrier, String integration) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("stage", stage);
        item.put("carrier", carrier);
        item.put("integration", integration);
        return item;
    }

    private Map<String, String> evaluationItem(String itemName, String weight, String evidence, String standard) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("item", itemName);
        item.put("weight", weight);
        item.put("evidence", evidence);
        item.put("standard", standard);
        return item;
    }

    private boolean isJavaPolymorphismLesson(LessonPlanRequest request) {
        String text = (request.getCourseName() + " " + request.getTopic() + " " + request.getMajor() + " " + request.getLessonType()).toLowerCase();
        return text.contains("java") || text.contains("继承") || text.contains("多态");
    }

    private boolean isPracticeLesson(String lessonType) {
        return lessonType != null && (lessonType.contains("实验") || lessonType.contains("实践") || lessonType.contains("理实"));
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String textOrDefault(String value, String defaultValue) {
        return notBlank(value) ? value : defaultValue;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
