<template>
  <div class="page">
    <section v-if="!authChecking && !user" class="login-shell">
      <div class="login-panel">
        <h1>成都东软学院智能教案生成系统</h1>
        <p>教师登录后可生成、编辑、保存并导出 Word 教案。</p>
        <el-form :model="loginForm" label-position="top" @keyup.enter="handleLogin">
          <el-form-item label="用户名">
            <el-input v-model="loginForm.username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" type="password" show-password />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loggingIn" @click="handleLogin">登录</el-button>
        </el-form>
        <p class="hint">默认账号：admin / admin123456，teacher01 / teacher123456</p>
      </div>
    </section>

    <template v-else-if="user">
      <AppTopbar
        :user="user"
        @home="switchModule('wizard')"
        @new="switchModule('wizard')"
        @lessons="switchModule('lessons')"
        @logout="handleLogout"
      />

      <div class="app-layout">
        <main class="workspace app-main" :class="`module-${activeModule}`">
        <section v-show="activeModule === 'wizard'" class="panel builder">
          <div class="section-head">
            <h2>新建课程教案</h2>
            <div class="actions">
              <el-button link :loading="savingDraft" @click="handleCreate">保存草稿</el-button>
            </div>
          </div>
          <el-form :model="form" label-position="top">
            <h3 class="form-section-title">课程基本信息</h3>
            <div class="basic-info-grid">
              <el-form-item class="field-course">
                <template #label>
                  <span class="field-label is-required">课程名称</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>填写课程完整名称，例如：人工智能导论、Java程序设计。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input v-model="form.courseName" placeholder="例如：Java程序设计" />
              </el-form-item>
              <el-form-item class="field-topic">
                <template #label>
                  <span class="field-label is-required">章节主题</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>填写本次课的具体章节或主题，越具体生成质量越稳定。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input v-model="form.topic" placeholder="例如：继承与多态" />
              </el-form-item>
              <el-form-item class="field-major">
                <template #label>
                  <span class="field-label is-required">授课专业</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>用于自动匹配专业学情、案例方向和授课对象。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-select v-model="form.major" filterable allow-create default-first-option placeholder="请选择或输入专业">
                  <el-option v-for="item in options.majors" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item class="field-grade">
                <template #label>
                  <span class="field-label is-required">年级</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>用于调整目标难度、任务分层和课堂活动要求。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-select v-model="form.grade" filterable allow-create default-first-option placeholder="请选择或输入年级">
                  <el-option v-for="item in options.grades" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item class="field-target">
                <template #label>
                  <span class="field-label is-required">授课对象</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>写清专业、年级、人数，例如：人工智能 大一学生，约45人。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input v-model="form.targetStudents" placeholder="例如：人工智能 大一学生，约45人" @input="targetStudentsAuto = false" />
              </el-form-item>
              <el-form-item class="field-type">
                <template #label>
                  <span class="field-label is-required">课程类型</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>理论课、实验课会影响教学过程、任务设计和评价方式。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-select v-model="form.lessonType">
                  <el-option v-for="item in options.lessonTypes" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item class="field-mode teaching-mode-field">
                <template #label>
                  <span class="field-label is-required">教学模式</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>可组合选择，也可输入自定义教学方法后点击添加。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <div class="mode-selector">
                  <button
                    v-for="item in visibleTeachingModes"
                    :key="item"
                    type="button"
                    :class="{ active: form.teachingMode.includes(item) }"
                    @click="toggleTeachingMode(item)"
                  >
                    {{ item }}
                  </button>
                  <el-popover placement="bottom-start" trigger="click" width="320">
                    <template #reference>
                      <button type="button" class="mode-more">更多...</button>
                    </template>
                    <div class="mode-popover">
                      <button
                        v-for="item in extraTeachingModes"
                        :key="item"
                        type="button"
                        :class="{ active: form.teachingMode.includes(item) }"
                        @click="toggleTeachingMode(item)"
                      >
                        {{ item }}
                      </button>
                    </div>
                  </el-popover>
                  <el-input
                    v-model="customTeachingMode"
                    class="mode-custom-input"
                    size="small"
                    placeholder="自定义方法"
                    @keyup.enter.stop.prevent="addCustomTeachingMode"
                  />
                  <button type="button" class="mode-add" @click="addCustomTeachingMode">添加</button>
                </div>
              </el-form-item>
              <el-form-item class="field-period compact-number">
                <template #label>
                  <span class="field-label is-required">课时数</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>本次课包含几个课时，系统会按总分钟数分配教学过程。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input-number v-model="form.periodCount" :min="1" :max="8" />
              </el-form-item>
              <el-form-item class="field-minute compact-number">
                <template #label>
                  <span class="field-label is-required">单课时长</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>成都东软学院常见单课时长为40分钟，可按实际排课调整。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input-number v-model="form.minutesPerPeriod" :min="1" :max="120" />
              </el-form-item>
            </div>

            <h3 class="form-section-title">生成上下文（可选）</h3>
            <div class="context-input-grid">
              <el-form-item>
                <template #label>
                  <span class="field-label">先修基础</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>写已学过的前置知识，例如：Python 列表、函数、类与对象。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input v-model="form.prerequisiteKnowledge" type="textarea" :rows="2" placeholder="例如：已完成 Python 基础语法、函数、列表和类与对象内容。" />
              </el-form-item>
              <el-form-item>
                <template #label>
                  <span class="field-label">常见误区</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>写学生最常见的误解或错误，例如：只会背概念，不会根据场景选数据结构。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input v-model="form.commonMisconceptions" type="textarea" :rows="2" placeholder="例如：容易把栈和列表直接等同，不会根据问题场景判断结构选择。" />
              </el-form-item>
              <el-form-item>
                <template #label>
                  <span class="field-label">班级情况</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>写班级基础层次，例如：中等、偏弱、两极分化，或需要更多演示与练习。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input v-model="form.classLevelProfile" type="textarea" :rows="2" placeholder="例如：整体基础中等，约三分之一学生代码实践较强，其余学生需要更多演示与引导。" />
              </el-form-item>
              <el-form-item>
                <template #label>
                  <span class="field-label">本节重点</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>写本节课你最希望模型重点展开的 1-3 个核心点。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input v-model="form.lessonFocus" type="textarea" :rows="2" placeholder="例如：栈与队列的本质差异、典型应用场景、结构选择依据。" />
              </el-form-item>
              <el-form-item class="context-output-field">
                <template #label>
                  <span class="field-label">预期产出</span>
                  <el-tooltip placement="top" popper-class="form-help-tooltip">
                    <template #content>写课堂结束时学生要交付或展示什么，系统会据此生成任务、检查点和评价证据。</template>
                    <span class="label-help">?</span>
                  </el-tooltip>
                </template>
                <el-input v-model="form.expectedOutputs" type="textarea" :rows="2" placeholder="例如：课堂记录单、结构选择说明、小组展示、随堂练习代码或案例分析表。" />
              </el-form-item>
            </div>

            <div class="context-helper ai-fill-row">
              <el-tooltip placement="top" popper-class="form-help-tooltip">
                <template #content>
                  <div>
                    <strong>补全学情描述、教学资源和其他要求</strong><br />
                    系统会根据课程、章节、专业、年级和课程类型生成下面三个输入框内容。
                  </div>
                </template>
                <el-button class="ai-auto-fill-button" type="primary" :loading="autoFillingContext" @click="autoFillTeachingContext">
                  {{ autoFillingContext ? '正在生成中...' : '✨ AI一键补全' }}
                </el-button>
              </el-tooltip>
            </div>

            <h3 class="form-section-title">教学要求</h3>
            <el-form-item>
              <template #label>
                <span class="field-label is-required">学情描述</span>
                <el-tooltip placement="top" popper-class="form-help-tooltip">
                  <template #content>写清先修基础、常见误区、强弱差异和本节课要解决的问题。</template>
                  <span class="label-help">?</span>
                </el-tooltip>
              </template>
              <div class="field-with-tool">
                <el-input v-model="form.studentAnalysis" class="textarea-large" type="textarea" :rows="5" placeholder="建议写具体问题：先修基础、常见错误、强弱差异、课堂要解决的真实困难。" />
                <el-button circle title="润色优化" :loading="optimizingField === 'studentAnalysis'" @click="polishFormField('studentAnalysis', '学情描述')">✦</el-button>
              </div>
            </el-form-item>

            <el-form-item>
              <template #label>
                <span class="field-label is-required">实验环境 / 教学资源</span>
                <el-tooltip placement="top" popper-class="form-help-tooltip">
                  <template #content>可填写机房、软件、课件、案例材料、教材章节等。</template>
                  <span class="label-help">?</span>
                </el-tooltip>
              </template>
              <div class="field-with-tool">
                <el-input v-model="form.experimentEnv" class="textarea-medium" type="textarea" :rows="2" />
                <el-button circle title="润色优化" :loading="optimizingField === 'experimentEnv'" @click="polishFormField('experimentEnv', '实验环境 / 教学资源')">✦</el-button>
              </div>
            </el-form-item>

            <el-form-item>
              <template #label>
                <span class="field-label">其他要求</span>
                <el-tooltip placement="top" popper-class="form-help-tooltip">
                  <template #content>补充必须覆盖的知识点、任务难度、验收材料或评分方式。</template>
                  <span class="label-help">?</span>
                </el-tooltip>
              </template>
              <div class="field-with-tool">
                <el-input v-model="form.extraRequirements" class="textarea-large" type="textarea" :rows="5" placeholder="可写：必须覆盖的知识点、任务难度、检查点、验收材料、评分方式。" />
                <el-button circle title="润色优化" :loading="optimizingField === 'extraRequirements'" @click="polishFormField('extraRequirements', '其他要求')">✦</el-button>
              </div>
            </el-form-item>

            <el-form-item label="上传参考资料">
              <div
                v-loading="Boolean(parsingResourceFileName)"
                class="upload-zone"
                element-loading-text="正在解析参考资料，扫描版 PDF 会逐页识别，可能需要数分钟"
                element-loading-background="rgba(255, 255, 255, 0.78)"
              >
                <el-upload
                  drag
                  :auto-upload="false"
                  :show-file-list="false"
                  multiple
                  accept=".txt,.md,.docx,.pptx,.pdf"
                  :on-change="handleFormResourceSelected"
                >
                  <div class="upload-text">拖入文件或点击上传</div>
                  <div class="upload-hint">支持 txt、md、docx、pptx、pdf。</div>
                  <div class="upload-hint">ℹ️ 没有资料也可以生成，上传后会优先参考资料内容。</div>
                </el-upload>
              </div>
              <div v-if="form.referenceMaterials.length" class="reference-material-grid">
                <article v-for="item in form.referenceMaterials" :key="item.id || item.fileName" class="reference-material-card">
                  <div class="reference-material-card-head">
                    <div>
                      <strong>{{ item.fileName }}</strong>
                      <span class="reference-material-meta">{{ item.fileType?.toUpperCase() || 'FILE' }} · {{ item.charCount || 0 }} 字<span v-if="item.extractionMethod === 'ocr'"> · OCR</span></span>
                    </div>
                    <el-tag size="small" :type="item.role === 'primary' ? 'success' : 'info'">
                      {{ item.role === 'primary' ? '主参考资料' : '参考资料' }}
                    </el-tag>
                  </div>
                  <p class="reference-material-excerpt">{{ item.excerpt || '暂无摘要' }}</p>
                  <div class="reference-material-actions">
                    <el-button size="small" type="primary" plain round :disabled="item.role === 'primary'" @click="setPrimaryReferenceMaterial(form.referenceMaterials, item.fileName)">设为主资料</el-button>
                    <el-button size="small" type="danger" plain round @click="removeReferenceMaterial(form.referenceMaterials, item.fileName)">删除</el-button>
                  </div>
                </article>
              </div>
            </el-form-item>

            <el-form-item label="教学日历（可选）">
              <div
                v-loading="Boolean(parsingTeachingCalendarFileName)"
                class="upload-zone"
                element-loading-text="正在解析教学日历并提取授课安排，请稍候"
                element-loading-background="rgba(255, 255, 255, 0.78)"
              >
                <el-upload
                  drag
                  :auto-upload="false"
                  :show-file-list="false"
                  accept=".xls,.xlsx"
                  :on-change="handleFormTeachingCalendarSelected"
                >
                  <div class="upload-text">上传教学日历</div>
                  <div class="upload-hint">支持 xls、xlsx，用于按周次和课次限定本次教案范围。</div>
                  <div class="upload-hint">ℹ️ 系统会读取授课内容、课型和学时，不会把整章内容塞进一份教案。</div>
                </el-upload>
              </div>
              <teaching-calendar-card
                v-if="hasTeachingCalendar(form.teachingCalendar)"
                :calendar="form.teachingCalendar"
                @preview="openTeachingCalendarPreview"
                @remove="removeFormTeachingCalendar"
              />
            </el-form-item>

            <div class="switches">
              <el-checkbox v-model="form.includeIdeology">加入课程思政</el-checkbox>
              <el-checkbox v-model="form.includeObe">加入 OBE 支撑</el-checkbox>
              <el-tooltip content="基于成果导向教育理念，增加教学目标与毕业要求的对应关系。" placement="top">
                <span class="obe-help">?</span>
              </el-tooltip>
            </div>

            <div class="actions generate-actions">
              <el-button type="primary" size="large" :loading="generating" @click="handleGenerate">
                {{ generating ? '正在生成中...' : '🚀 一键生成智能教案' }}
              </el-button>
            </div>
            <el-progress v-if="generating" class="generate-progress" :percentage="100" :indeterminate="true" :duration="2" />
          </el-form>
        </section>

        <section v-if="activeModule === 'lessons'" class="panel lesson-manager">
          <div class="section-head">
            <h2>我的教案</h2>
            <div class="actions">
              <el-button type="primary" @click="switchModule('wizard')">新建课程教案</el-button>
              <el-button @click="refreshSideData">刷新</el-button>
            </div>
          </div>
          <div class="lesson-filter-bar">
            <el-select v-model="lessonCourseFilter" clearable placeholder="按课程筛选">
              <el-option v-for="item in lessonCourseOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <el-input v-model="lessonSearchKeyword" clearable placeholder="搜索课程、章节或标题" />
          </div>
          <el-table :data="paginatedLessonPlans" empty-text="您还没有生成任何教案，点击右上角的新建课程教案开始吧" @row-click="handleSelectPlan">
            <el-table-column type="index" label="序号" width="60" :index="lessonRowIndex" />
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-tag :type="row.planKind === 'course-plan' ? 'success' : 'info'">
                  {{ row.typeLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="courseName" label="课程名称" min-width="150" />
            <el-table-column prop="topic" label="章节主题" min-width="220" />
            <el-table-column prop="lessonType" label="教案类别" width="120" />
            <el-table-column label="生成时间" min-width="170">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <div class="lesson-action-buttons">
                  <el-button size="small" type="primary" plain round @click.stop="handleSelectPlan(row)">
                    {{ row.planKind === 'course-plan' ? '打开' : '编辑' }}
                  </el-button>
                  <el-button size="small" type="success" plain round @click.stop="handleExport(row, 'word')">Word</el-button>
                  <el-button v-if="row.planKind === 'course-plan'" size="small" type="success" plain round @click.stop="handleExport(row, 'pdf')">PDF</el-button>
                  <el-button v-if="row.planKind !== 'course-plan'" size="small" type="danger" plain round @click.stop="handleDelete(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="filteredLessonPlans.length" class="lesson-pagination">
            <el-pagination
              v-model:current-page="lessonPage"
              v-model:page-size="lessonPageSize"
              :page-sizes="lessonPageSizes"
              :total="filteredLessonPlans.length"
              background
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>
        </section>

        <section v-if="activeModule === 'editor'" class="panel editor" ref="editorPanelRef">
          <div class="section-head">
            <h2>教案预览与编辑</h2>
            <div class="actions editor-action-buttons" v-if="currentPlan">
              <el-button size="small" type="info" plain round @click="returnToLessonList">返回我的教案</el-button>
              <el-button size="small" type="primary" plain round :loading="savingPlan" @click="handleSave">保存修改</el-button>
              <el-button size="small" type="success" plain round @click="handleExport(currentPlan)">导出 Word</el-button>
            </div>
          </div>

          <el-empty v-if="!currentPlan" description="生成或选择一份教案后开始编辑" />
          <div v-else class="edit-area">
            <el-tabs v-model="activeTab">
              <el-tab-pane label="目标与分析" name="objectives">
                <el-form label-position="top">
                  <div class="card-editor-grid">
                    <el-form-item label="先修基础">
                      <el-input v-model="currentContent.generationContext.prerequisiteKnowledge" type="textarea" :rows="2" />
                    </el-form-item>
                    <el-form-item label="常见误区">
                      <el-input v-model="currentContent.generationContext.commonMisconceptions" type="textarea" :rows="2" />
                    </el-form-item>
                    <el-form-item label="班级情况">
                      <el-input v-model="currentContent.generationContext.classLevelProfile" type="textarea" :rows="2" />
                    </el-form-item>
                    <el-form-item label="本节重点">
                      <el-input v-model="currentContent.generationContext.lessonFocus" type="textarea" :rows="2" />
                    </el-form-item>
                    <el-form-item label="预期产出">
                      <el-input v-model="currentContent.generationContext.expectedOutputs" type="textarea" :rows="2" />
                    </el-form-item>
                  </div>
                  <el-form-item label="学情分析">
                    <el-input v-model="currentContent.studentAnalysis" type="textarea" :rows="4" />
                  </el-form-item>
                  <editable-object-list
                    title="学情诊断与教学对策"
                    :items="currentContent.studentProblems"
                    :fields="studentProblemFields"
                    @add="addStructuredItem(currentContent.studentProblems, studentProblemFields)"
                    @remove="removeListItem(currentContent.studentProblems, $event)"
                  />
                  <div class="card-editor-grid">
                    <editable-list title="教学方法" :items="currentContent.teachingMethods" placeholder="例如：任务驱动、讲授法、案例分析、课堂讨论" @add="addListItem(currentContent.teachingMethods)" @remove="removeListItem(currentContent.teachingMethods, $event)" />
                    <editable-list title="教学资源" :items="currentContent.resources" placeholder="例如：PPT、板书、任务单、Python 运行环境、示例代码" @add="addListItem(currentContent.resources)" @remove="removeListItem(currentContent.resources, $event)" />
                  </div>
                  <div class="card-editor-grid">
                    <editable-list title="知识目标" :items="currentContent.objectives.knowledge" placeholder="例如：能够区分向上转型与向下转型的适用场景" @add="addListItem(currentContent.objectives.knowledge)" @remove="removeListItem(currentContent.objectives.knowledge, $event)" />
                    <editable-list title="能力目标" :items="currentContent.objectives.ability" placeholder="例如：能够用 List<Employee> 完成多态遍历并提交测试用例" @add="addListItem(currentContent.objectives.ability)" @remove="removeListItem(currentContent.objectives.ability, $event)" />
                    <editable-list title="素质目标" :items="currentContent.objectives.quality" placeholder="例如：能够依据代码规范完成提交并说明修正过程" @add="addListItem(currentContent.objectives.quality)" @remove="removeListItem(currentContent.objectives.quality, $event)" />
                    <editable-list title="OBE 支撑" :items="currentContent.objectives.obeSupport" placeholder="例如：支撑毕业要求指标点 3.2，证据为代码和测试用例" @add="addListItem(currentContent.objectives.obeSupport)" @remove="removeListItem(currentContent.objectives.obeSupport, $event)" />
                  </div>
                  <editable-object-list
                    title="OBE 评价体系"
                    :items="currentContent.evaluationDesign"
                    :fields="evaluationDesignFields"
                    @add="addStructuredItem(currentContent.evaluationDesign, evaluationDesignFields)"
                    @remove="removeListItem(currentContent.evaluationDesign, $event)"
                  />
                  <editable-object-list
                    title="Rubric 评分表"
                    :items="currentContent.rubric"
                    :fields="rubricFields"
                    @add="addStructuredItem(currentContent.rubric, rubricFields)"
                    @remove="removeListItem(currentContent.rubric, $event)"
                  />
                </el-form>
              </el-tab-pane>

              <el-tab-pane label="重难点与思政" name="points">
                <div class="card-editor-grid">
                  <editable-object-list
                    title="教学重点"
                    :items="currentContent.keyPoints"
                    :fields="keyPointFields"
                    @add="addStructuredItem(currentContent.keyPoints, keyPointFields)"
                    @remove="removeListItem(currentContent.keyPoints, $event)"
                  />
                  <editable-object-list
                    title="教学难点与突破策略"
                    :items="currentContent.difficultPoints"
                    :fields="difficultPointFields"
                    @add="addStructuredItem(currentContent.difficultPoints, difficultPointFields)"
                    @remove="removeListItem(currentContent.difficultPoints, $event)"
                  />
                </div>
                <editable-object-list
                  title="课程思政融入"
                  :items="currentContent.ideologyDesign"
                  :fields="ideologyFields"
                  @add="addStructuredItem(currentContent.ideologyDesign, ideologyFields)"
                  @remove="removeListItem(currentContent.ideologyDesign, $event)"
                />
              </el-tab-pane>

              <el-tab-pane label="教学过程表" name="process">
                <div class="table-actions">
                  <el-button @click="addProcessRow">新增环节</el-button>
                  <el-tag :type="processTotalMinutes === currentContent.basicInfo.totalMinutes ? 'success' : 'danger'">
                    当前总时长：{{ processTotalMinutes }} / {{ currentContent.basicInfo.totalMinutes }} 分钟
                  </el-tag>
                </div>
                <el-table :data="currentContent.teachingProcess || []" border>
                  <el-table-column label="环节" width="130">
                    <template #default="{ row }"><el-input v-model="row.stage" /></template>
                  </el-table-column>
                  <el-table-column label="分钟" width="95">
                    <template #default="{ row }"><el-input-number v-model="row.duration" :min="1" :max="240" /></template>
                  </el-table-column>
                  <el-table-column label="教师活动" min-width="220">
                    <template #default="{ row }"><el-input v-model="row.teacherActivity" type="textarea" :rows="3" /></template>
                  </el-table-column>
                  <el-table-column label="学生活动" min-width="220">
                    <template #default="{ row }"><el-input v-model="row.studentActivity" type="textarea" :rows="3" /></template>
                  </el-table-column>
                  <el-table-column label="课堂产出" min-width="180">
                    <template #default="{ row }"><el-input v-model="row.output" type="textarea" :rows="2" /></template>
                  </el-table-column>
                  <el-table-column label="检查点" min-width="180">
                    <template #default="{ row }"><el-input v-model="row.checkpoint" type="textarea" :rows="2" /></template>
                  </el-table-column>
                  <el-table-column label="评价" width="130">
                    <template #default="{ row }"><el-input v-model="row.evaluation" /></template>
                  </el-table-column>
                  <el-table-column label="操作" width="80">
                    <template #default="{ $index }"><el-button link type="danger" @click="removeProcessRow($index)">删除</el-button></template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>

              <el-tab-pane label="任务与作业" name="task">
                <el-form label-position="top">
                  <el-form-item label="实践任务名称">
                    <el-input v-model="currentContent.practiceTask.taskName" />
                  </el-form-item>
                  <section class="reference-materials-panel">
                    <div class="reference-materials-panel-head">
                      <div>
                        <h4>参考资料</h4>
                        <p>附件会独立保存在当前教案中，并作为生成与编辑上下文，不再写入“其他要求”。</p>
                      </div>
                    </div>
                    <div
                      v-loading="Boolean(parsingResourceFileName)"
                      class="upload-zone"
                      element-loading-text="正在解析参考资料，扫描版 PDF 会逐页识别，可能需要数分钟"
                      element-loading-background="rgba(255, 255, 255, 0.78)"
                    >
                      <el-upload
                        drag
                        :auto-upload="false"
                        :show-file-list="false"
                        multiple
                        accept=".txt,.md,.docx,.pptx,.pdf"
                        :on-change="handleEditorResourceSelected"
                      >
                        <div class="upload-text">拖入文件或点击上传</div>
                        <div class="upload-hint">支持 txt、md、docx、pptx、pdf。</div>
                        <div class="upload-hint">ℹ️ 没有资料也可以生成，上传后会优先参考资料内容。</div>
                      </el-upload>
                    </div>
                    <div v-if="currentContent.referenceMaterials.length" class="reference-material-grid">
                      <article v-for="item in currentContent.referenceMaterials" :key="item.id || item.fileName" class="reference-material-card">
                        <div class="reference-material-card-head">
                          <div>
                            <strong>{{ item.fileName }}</strong>
                            <span class="reference-material-meta">{{ item.fileType?.toUpperCase() || 'FILE' }} · {{ item.charCount || 0 }} 字<span v-if="item.extractionMethod === 'ocr'"> · OCR</span></span>
                          </div>
                          <el-tag size="small" :type="item.role === 'primary' ? 'success' : 'info'">
                            {{ item.role === 'primary' ? '主参考资料' : '参考资料' }}
                          </el-tag>
                        </div>
                        <p class="reference-material-excerpt">{{ item.excerpt || '暂无摘要' }}</p>
                        <div class="reference-material-actions">
                          <el-button size="small" type="primary" plain round :disabled="item.role === 'primary'" @click="setPrimaryReferenceMaterial(currentContent.referenceMaterials, item.fileName)">设为主资料</el-button>
                          <el-button size="small" type="danger" plain round @click="removeReferenceMaterial(currentContent.referenceMaterials, item.fileName)">删除</el-button>
                        </div>
                      </article>
                    </div>
                    <el-empty v-else description="暂无参考资料" :image-size="52" />
                  </section>
                  <section class="reference-materials-panel teaching-calendar-panel">
                    <div class="reference-materials-panel-head">
                      <div>
                        <h4>教学日历</h4>
                        <p>用于约束本次教案对应的周次、课次、课型和授课主题。</p>
                      </div>
                    </div>
                    <div
                      v-loading="Boolean(parsingTeachingCalendarFileName)"
                      class="upload-zone"
                      element-loading-text="正在解析教学日历并提取授课安排，请稍候"
                      element-loading-background="rgba(255, 255, 255, 0.78)"
                    >
                      <el-upload
                        drag
                        :auto-upload="false"
                        :show-file-list="false"
                        accept=".xls,.xlsx"
                        :on-change="handleEditorTeachingCalendarSelected"
                      >
                        <div class="upload-text">上传教学日历</div>
                        <div class="upload-hint">支持 xls、xlsx。</div>
                        <div class="upload-hint">ℹ️ 保存修改后会随当前教案一起落库。</div>
                      </el-upload>
                    </div>
                    <teaching-calendar-card
                      v-if="hasTeachingCalendar(currentContent.teachingCalendar)"
                      :calendar="currentContent.teachingCalendar"
                      @preview="openTeachingCalendarPreview"
                      @remove="removeCurrentTeachingCalendar"
                    />
                    <el-empty v-else description="暂无教学日历" :image-size="52" />
                  </section>
                  <div class="card-editor-grid">
                    <editable-list title="基础任务" :items="currentContent.practiceTask.basicTasks" placeholder="例如：实现 1 个父类和 3 个子类并完成方法重写" @add="addListItem(currentContent.practiceTask.basicTasks)" @remove="removeListItem(currentContent.practiceTask.basicTasks, $event)" />
                    <editable-list title="提高任务" :items="currentContent.practiceTask.advancedTasks" placeholder="例如：使用 List<Employee> 完成统一遍历并输出工资" @add="addListItem(currentContent.practiceTask.advancedTasks)" @remove="removeListItem(currentContent.practiceTask.advancedTasks, $event)" />
                    <editable-list title="挑战任务" :items="currentContent.practiceTask.challengeTasks" placeholder="例如：补充错误强转反例并解释异常原因" @add="addListItem(currentContent.practiceTask.challengeTasks)" @remove="removeListItem(currentContent.practiceTask.challengeTasks, $event)" />
                    <editable-list title="实施步骤" :items="currentContent.practiceTask.steps" placeholder="例如：先画类图，再实现父类和子类，最后用 List<Employee> 验证多态" @add="addListItem(currentContent.practiceTask.steps)" @remove="removeListItem(currentContent.practiceTask.steps, $event)" />
                    <editable-list title="验收标准" :items="currentContent.practiceTask.acceptanceCriteria" placeholder="例如：提交源代码、运行截图、2 个测试用例和问题修正说明" @add="addListItem(currentContent.practiceTask.acceptanceCriteria)" @remove="removeListItem(currentContent.practiceTask.acceptanceCriteria, $event)" />
                    <editable-list title="常见错误提醒" :items="currentContent.practiceTask.commonErrors" placeholder="例如：把队列直接用列表 pop(0) 实现，忽略时间复杂度问题" @add="addListItem(currentContent.practiceTask.commonErrors)" @remove="removeListItem(currentContent.practiceTask.commonErrors, $event)" />
                    <editable-list title="作业与课后任务" :items="currentContent.homework" placeholder="例如：增加一个 Sales 子类并提交测试截图" @add="addListItem(currentContent.homework)" @remove="removeListItem(currentContent.homework, $event)" />
                  </div>
                  <editable-object-list
                    title="代码示例"
                    :items="currentContent.codeExamples"
                    :fields="codeExampleFields"
                    @add="addStructuredItem(currentContent.codeExamples, codeExampleFields)"
                    @remove="removeListItem(currentContent.codeExamples, $event)"
                  />
                  <el-form-item label="课后反思">
                    <el-input v-model="currentContent.reflection" type="textarea" :rows="4" />
                  </el-form-item>
                </el-form>
              </el-tab-pane>

              <el-tab-pane label="预览" name="preview">
                <div class="word-preview-shell">
                  <div class="word-toolbar">
                    <span>
                      完整文档预览：{{ previewStats.sectionCount }} 个正文板块、{{ previewStats.processCount }} 个教学环节、{{ previewStats.rubricCount }} 个评分维度。
                      可直接修改文字。
                    </span>
                    <el-button :loading="previewAiTarget === 'all'" @click="aiRevisePreview('all', '整份教案')">AI 全文优化</el-button>
                  </div>

                  <article class="word-paper word-paper-document">
                    <header class="word-header-line">
                      <span>成都东软学院</span>
                      <span>教案</span>
                    </header>

                    <h1 contenteditable="true" @blur="titleInput = editableText($event)">{{ titleInput }}</h1>
                    <p class="word-subtitle" contenteditable="true" @blur="updateSubtitle($event)">{{ currentContent.basicInfo.lessonType }} · {{ currentContent.basicInfo.totalMinutes }} 分钟 · {{ currentContent.basicInfo.courseName }}</p>

                    <table class="word-table basic-table">
                      <tbody>
                        <tr>
                          <th>课程名称</th>
                          <td contenteditable="true" @blur="setPlanField('courseName', editableText($event))">{{ currentContent.basicInfo.courseName }}</td>
                          <th>章节主题</th>
                          <td contenteditable="true" @blur="setPlanField('topic', editableText($event))">{{ currentContent.basicInfo.topic }}</td>
                        </tr>
                        <tr>
                          <th>授课专业</th>
                          <td contenteditable="true" @blur="setPlanField('major', editableText($event))">{{ currentContent.basicInfo.major || '-' }}</td>
                          <th>授课对象</th>
                          <td contenteditable="true" @blur="setPlanField('targetStudents', editableText($event))">{{ currentContent.basicInfo.targetStudents || '-' }}</td>
                        </tr>
                        <tr>
                          <th>课程类型</th>
                          <td contenteditable="true" @blur="setPlanField('lessonType', editableText($event))">{{ currentContent.basicInfo.lessonType }}</td>
                          <th>教学模式/方法组合</th>
                          <td contenteditable="true" @blur="setPlanField('teachingMode', editableText($event))">{{ currentContent.basicInfo.teachingMode || '-' }}</td>
                        </tr>
                        <tr>
                          <th>课时安排</th>
                          <td contenteditable="true" @blur="updatePeriodSummary($event)">{{ currentContent.basicInfo.periodCount }} 节，共 {{ currentContent.basicInfo.totalMinutes }} 分钟</td>
                          <th>单课时长</th>
                          <td contenteditable="true" @blur="updateMinutesPerPeriod($event)">{{ currentContent.basicInfo.minutesPerPeriod }} 分钟</td>
                        </tr>
                      </tbody>
                    </table>

                    <div class="word-content-banner" v-if="previewStats.sectionCount === 0">
                      当前教案正文为空，请重新生成或打开一份已生成成功的教案。
                    </div>

                    <word-section title="一、学情分析" :loading="previewAiTarget === 'studentAnalysis'" @revise="aiRevisePreview('studentAnalysis', '学情分析')">
                      <p class="word-paragraph" contenteditable="true" @blur="setScalar('studentAnalysis', editableText($event))">{{ currentContent.studentAnalysis }}</p>
                    </word-section>

                    <word-section v-if="currentContent.studentProblems.length" title="学情诊断与教学对策" level="h2" :loading="previewAiTarget === 'studentProblems'" @revise="aiRevisePreview('studentProblems', '学情诊断与教学对策')">
                      <table class="word-table">
                        <thead>
                          <tr><th>学情问题</th><th>课堂表现</th><th>教学对策</th><th>评价证据</th></tr>
                        </thead>
                        <tbody>
                          <tr v-for="(row, index) in currentContent.studentProblems" :key="index">
                            <td contenteditable="true" @blur="row.problem = editableText($event)">{{ row.problem }}</td>
                            <td contenteditable="true" @blur="row.evidence = editableText($event)">{{ row.evidence }}</td>
                            <td contenteditable="true" @blur="row.strategy = editableText($event)">{{ row.strategy }}</td>
                            <td contenteditable="true" @blur="row.assessment = editableText($event)">{{ row.assessment }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </word-section>

                    <word-section title="二、教学目标" :loading="previewAiTarget === 'objectives'" @revise="aiRevisePreview('objectives', '教学目标')">
                      <h3>1. 知识目标</h3>
                      <word-edit-list :items="currentContent.objectives.knowledge" />
                      <h3>2. 能力目标</h3>
                      <word-edit-list :items="currentContent.objectives.ability" />
                      <h3>3. 素质目标</h3>
                      <word-edit-list :items="currentContent.objectives.quality" />
                      <h3 v-if="currentContent.objectives.obeSupport.length">4. OBE 支撑</h3>
                      <word-edit-list v-if="currentContent.objectives.obeSupport.length" :items="currentContent.objectives.obeSupport" />
                    </word-section>

                    <word-section title="三、教学重点" :loading="previewAiTarget === 'keyPoints'" @revise="aiRevisePreview('keyPoints', '教学重点')">
                      <word-edit-object-list :items="currentContent.keyPoints" :fields="keyPointFields" />
                    </word-section>

                    <word-section title="四、教学难点与突破策略" :loading="previewAiTarget === 'difficultPoints'" @revise="aiRevisePreview('difficultPoints', '教学难点与突破策略')">
                      <word-edit-object-list :items="currentContent.difficultPoints" :fields="difficultPointFields" />
                    </word-section>

                    <word-section title="五、教学方法与资源" :loading="previewAiTarget === 'teachingSupport'" @revise="aiRevisePreview('teachingSupport', '教学方法与资源')">
                      <h3>1. 教学方法</h3>
                      <word-edit-list :items="currentContent.teachingMethods" />
                      <h3>2. 教学资源</h3>
                      <word-edit-list :items="currentContent.resources" />
                      <template v-if="currentContent.referenceMaterials.length">
                        <h3>3. 参考资料</h3>
                        <div class="word-reference-list">
                          <article v-for="item in currentContent.referenceMaterials" :key="item.id || item.fileName" class="word-reference-item">
                            <div class="word-reference-item-head">
                              <strong contenteditable="true" @blur="item.fileName = editableText($event)">{{ item.fileName }}</strong>
                              <span>{{ item.role === 'primary' ? '主参考资料' : '参考资料' }}</span>
                            </div>
                            <p class="word-reference-meta">{{ item.fileType?.toUpperCase() || 'FILE' }} · {{ item.charCount || 0 }} 字<span v-if="item.extractionMethod === 'ocr'"> · OCR</span></p>
                            <p class="word-paragraph word-reference-excerpt" contenteditable="true" @blur="item.excerpt = editableText($event)">{{ item.excerpt || '暂无摘要' }}</p>
                          </article>
                        </div>
                      </template>
                      <template v-if="hasTeachingCalendar(currentContent.teachingCalendar)">
                        <h3>4. 教学日历参考</h3>
                        <table class="word-table teaching-calendar-preview-table">
                          <thead>
                            <tr><th>周次</th><th>课次</th><th>学时</th><th>课型</th><th>授课内容</th></tr>
                          </thead>
                          <tbody>
                            <tr v-for="(row, index) in currentContent.teachingCalendar.entries.slice(0, 12)" :key="index">
                              <td contenteditable="true" @blur="row.week = editableText($event)">{{ row.week }}</td>
                              <td contenteditable="true" @blur="row.session = editableText($event)">{{ row.session }}</td>
                              <td contenteditable="true" @blur="row.periodCount = Number(editableText($event).replace(/[^0-9]/g, '')) || row.periodCount">{{ row.periodCount || '' }}</td>
                              <td contenteditable="true" @blur="row.lessonType = editableText($event)">{{ row.lessonType }}</td>
                              <td contenteditable="true" @blur="row.topic = editableText($event)">{{ row.topic || row.rawText }}</td>
                            </tr>
                          </tbody>
                        </table>
                      </template>
                    </word-section>

                    <word-section title="六、课程思政融入" :loading="previewAiTarget === 'ideologyDesign'" @revise="aiRevisePreview('ideologyDesign', '课程思政融入')">
                      <word-edit-object-list :items="currentContent.ideologyDesign" :fields="ideologyFields" />
                    </word-section>

                    <word-section title="七、教学过程设计" :loading="previewAiTarget === 'teachingProcess'" @revise="aiRevisePreview('teachingProcess', '教学过程设计')">
                      <table class="word-table process-preview-table">
                        <thead>
                          <tr>
                            <th>教学环节</th><th>时间</th><th>教师活动</th><th>学生活动</th><th>课堂产出</th><th>检查点</th><th>设计意图</th><th>教学资源</th><th>评价方式</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(row, index) in currentContent.teachingProcess" :key="index">
                            <td contenteditable="true" @blur="row.stage = editableText($event)">{{ row.stage }}</td>
                            <td contenteditable="true" @blur="row.duration = Number(editableText($event).replace(/[^0-9]/g, '')) || row.duration">{{ row.duration }} 分钟</td>
                            <td contenteditable="true" @blur="row.teacherActivity = editableText($event)">{{ row.teacherActivity }}</td>
                            <td contenteditable="true" @blur="row.studentActivity = editableText($event)">{{ row.studentActivity }}</td>
                            <td contenteditable="true" @blur="row.output = editableText($event)">{{ row.output }}</td>
                            <td contenteditable="true" @blur="row.checkpoint = editableText($event)">{{ row.checkpoint }}</td>
                            <td contenteditable="true" @blur="row.designPurpose = editableText($event)">{{ row.designPurpose }}</td>
                            <td contenteditable="true" @blur="row.resources = editableText($event)">{{ row.resources }}</td>
                            <td contenteditable="true" @blur="row.evaluation = editableText($event)">{{ row.evaluation }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </word-section>

                    <word-section title="八、实践任务设计" :loading="previewAiTarget === 'practiceTask'" @revise="aiRevisePreview('practiceTask', '实践任务设计')">
                      <h3>1. 任务名称</h3>
                      <p class="word-paragraph" contenteditable="true" @blur="currentContent.practiceTask.taskName = editableText($event)">{{ currentContent.practiceTask.taskName }}</p>
                      <h3 v-if="currentContent.practiceTask.scenario">2. 任务情境</h3>
                      <p v-if="currentContent.practiceTask.scenario" class="word-paragraph" contenteditable="true" @blur="currentContent.practiceTask.scenario = editableText($event)">{{ currentContent.practiceTask.scenario }}</p>
                      <h3>3. 基础任务</h3>
                      <word-edit-list :items="currentContent.practiceTask.basicTasks" />
                      <h3>4. 提高任务</h3>
                      <word-edit-list :items="currentContent.practiceTask.advancedTasks" />
                      <h3>5. 挑战任务</h3>
                      <word-edit-list :items="currentContent.practiceTask.challengeTasks" />
                      <h3>6. 实施步骤</h3>
                      <word-edit-list :items="currentContent.practiceTask.steps" />
                      <h3>7. 验收标准</h3>
                      <word-edit-list :items="currentContent.practiceTask.acceptanceCriteria" />
                      <h3 v-if="currentContent.practiceTask.commonErrors.length">8. 常见错误提醒</h3>
                      <word-edit-list v-if="currentContent.practiceTask.commonErrors.length" :items="currentContent.practiceTask.commonErrors" />
                    </word-section>

                    <word-section v-if="currentContent.codeExamples.length" title="代码示例" :loading="previewAiTarget === 'codeExamples'" @revise="aiRevisePreview('codeExamples', '代码示例')">
                      <div v-for="(item, index) in currentContent.codeExamples" :key="index" class="code-preview-block">
                        <h3 contenteditable="true" @blur="item.title = editableText($event)">{{ item.title }}</h3>
                        <p class="word-paragraph" contenteditable="true" @blur="item.purpose = editableText($event)">{{ item.purpose }}</p>
                        <pre contenteditable="true" @blur="item.code = editableText($event)">{{ item.code }}</pre>
                      </div>
                    </word-section>

                    <word-section title="九、作业与课后任务" :loading="previewAiTarget === 'homework'" @revise="aiRevisePreview('homework', '作业与课后任务')">
                      <word-edit-list :items="currentContent.homework" />
                    </word-section>

                    <word-section title="十、OBE 支撑与评价体系" :loading="previewAiTarget === 'evaluationBundle'" @revise="aiRevisePreview('evaluationBundle', 'OBE 支撑与评价体系')">
                      <word-edit-object-list :items="currentContent.evaluationDesign" :fields="evaluationDesignFields" />
                      <h3 v-if="currentContent.rubric.length">Rubric 评分表</h3>
                      <table v-if="currentContent.rubric.length" class="word-table">
                        <thead>
                          <tr><th>评价维度</th><th>权重</th><th>优秀标准</th><th>达标标准</th><th>评价证据</th></tr>
                        </thead>
                        <tbody>
                          <tr v-for="(row, index) in currentContent.rubric" :key="index">
                            <td contenteditable="true" @blur="row.criterion = editableText($event)">{{ row.criterion }}</td>
                            <td contenteditable="true" @blur="row.weight = editableText($event)">{{ row.weight }}</td>
                            <td contenteditable="true" @blur="row.excellent = editableText($event)">{{ row.excellent }}</td>
                            <td contenteditable="true" @blur="row.qualified = editableText($event)">{{ row.qualified }}</td>
                            <td contenteditable="true" @blur="row.evidence = editableText($event)">{{ row.evidence }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </word-section>

                    <word-section title="十一、课后反思" :loading="previewAiTarget === 'reflection'" @revise="aiRevisePreview('reflection', '课后反思')">
                      <p class="word-paragraph" contenteditable="true" @blur="setScalar('reflection', editableText($event))">{{ currentContent.reflection }}</p>
                    </word-section>
                  </article>
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </section>
      </main>
      </div>

      <el-dialog v-model="recordDialogVisible" title="AI 生成记录详情" width="72%">
        <div v-if="recordDetail" class="record-detail">
          <div class="record-meta">
            <el-tag :type="recordDetail.success ? 'success' : 'danger'">{{ recordDetail.success ? '成功' : '失败' }}</el-tag>
            <span>{{ recordDetail.provider }} · {{ recordDetail.modelName }}</span>
            <span>{{ formatTime(recordDetail.createdAt) }}</span>
            <span>{{ formatDuration(recordDetail.durationMs) }}</span>
          </div>
          <el-alert v-if="recordDetail.errorMessage" :title="recordDetail.errorMessage" type="error" :closable="false" show-icon />
          <el-tabs>
            <el-tab-pane label="Prompt">
              <pre>{{ recordDetail.prompt || '无' }}</pre>
            </el-tab-pane>
            <el-tab-pane label="模型返回">
              <pre>{{ recordDetail.response || '无' }}</pre>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-dialog>

      <el-dialog
        v-model="teachingCalendarPreviewVisible"
        title="教学日历预览"
        width="82%"
        top="6vh"
        destroy-on-close
      >
        <div v-if="teachingCalendarPreviewData" class="teaching-calendar-preview-dialog">
          <div class="teaching-calendar-preview-meta">
            <strong>{{ teachingCalendarPreviewData.fileName }}</strong>
            <span>{{ teachingCalendarPreviewData.rowCount || teachingCalendarPreviewData.entries.length }} 条授课安排</span>
          </div>
          <el-table
            class="teaching-calendar-preview-table"
            :data="teachingCalendarPreviewData.entries"
            border
            stripe
            max-height="560"
          >
            <el-table-column prop="week" label="周次" width="110" />
            <el-table-column prop="session" label="课次" width="100" />
            <el-table-column prop="lessonType" label="课型" width="100" />
            <el-table-column prop="periodCount" label="学时" width="90" />
            <el-table-column prop="topic" label="基本教学内容" min-width="360" show-overflow-tooltip />
          </el-table>
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import AppTopbar from '../components/AppTopbar.vue'
import {
  createLessonPlan,
  deleteLessonPlan,
  exportCoursePlanPdf,
  exportCoursePlanWord,
  exportLessonPlanWord,
  getCoursePlans,
  getGenerationRecords,
  getGenerationRecordDetail,
  generateLessonPlan,
  getCurrentUser,
  getLessonPlanDetail,
  getLessonPlans,
  getOptions,
  login,
  logout,
  optimizeText,
  extractResource,
  parseTeachingCalendar,
  saveLessonPlan,
} from '../api/http'

const route = useRoute()
const router = useRouter()
const user = ref(null)
const authChecking = ref(Boolean(localStorage.getItem('nsu_maic_token')))
const loggingIn = ref(false)
const loginForm = reactive({ username: 'admin', password: 'admin123456' })
const activeModule = ref(routeToModule())
const recordDialogVisible = ref(false)
const recordDetail = ref(null)
const teachingCalendarPreviewVisible = ref(false)
const teachingCalendarPreviewData = ref(null)
const editorPanelRef = ref(null)
const lessonCourseFilter = ref('')
const lessonSearchKeyword = ref('')
const lessonPage = ref(1)
const lessonPageSize = ref(10)
const lessonPageSizes = [10, 20, 50]

const workspaceModules = [
  { key: 'wizard', label: '新建课程教案' },
  { key: 'lessons', label: '我的教案' },
]

const EditableList = {
  props: {
    title: { type: String, required: true },
    items: { type: Array, required: true },
    placeholder: { type: String, default: '请输入内容' },
  },
  emits: ['add', 'remove'],
  template: `
    <section class="editable-list">
      <div class="editable-list-head">
        <h4>{{ title }}</h4>
        <el-button size="small" @click="$emit('add')">新增</el-button>
      </div>
      <div v-if="items.length" class="editable-list-items">
        <div v-for="(item, index) in items" :key="index" class="editable-item">
          <span>{{ index + 1 }}</span>
          <el-input v-model="items[index]" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" :placeholder="placeholder" />
          <el-button link type="danger" @click="$emit('remove', index)">删除</el-button>
        </div>
      </div>
      <el-empty v-else :description="'暂无' + title" :image-size="52">
        <el-button size="small" @click="$emit('add')">添加{{ title }}</el-button>
      </el-empty>
    </section>
  `,
}

const EditableObjectList = {
  props: {
    title: { type: String, required: true },
    items: { type: Array, required: true },
    fields: { type: Array, required: true },
  },
  emits: ['add', 'remove'],
  template: `
    <section class="editable-list editable-object-list">
      <div class="editable-list-head">
        <h4>{{ title }}</h4>
        <el-button size="small" @click="$emit('add')">新增</el-button>
      </div>
      <div v-if="items.length" class="editable-list-items">
        <div v-for="(item, index) in items" :key="index" class="editable-object-card">
          <div class="editable-object-card-head">
            <span>{{ index + 1 }}</span>
            <el-button link type="danger" @click="$emit('remove', index)">删除</el-button>
          </div>
          <div class="editable-object-grid">
            <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
              <el-input
                v-model="items[index][field.key]"
                :type="field.type || 'textarea'"
                :rows="field.rows || 2"
                :autosize="field.type === 'textarea' || !field.type ? { minRows: field.rows || 2, maxRows: field.maxRows || 4 } : undefined"
                :placeholder="field.placeholder || ('请输入' + field.label)"
              />
            </el-form-item>
          </div>
        </div>
      </div>
      <el-empty v-else :description="'暂无' + title" :image-size="52">
        <el-button size="small" @click="$emit('add')">添加{{ title }}</el-button>
      </el-empty>
    </section>
  `,
}

const WordSection = {
  props: {
    title: { type: String, required: true },
    level: { type: String, default: 'h1' },
    loading: { type: Boolean, default: false },
  },
  template: `
    <section class="word-section">
      <div class="word-section-head">
        <component :is="level">{{ title }}</component>
        <el-button v-if="$attrs.onRevise" class="ai-revise-button" size="small" type="primary" plain round :loading="loading" @click="$emit('revise')">AI 修改</el-button>
      </div>
      <slot />
    </section>
  `,
}

const WordEditList = {
  props: {
    items: { type: Array, required: true },
  },
  methods: {
    itemText(item) {
      return stringifyListItem(item)
    },
    updateItem(index, event) {
      this.items[index] = event.target.innerText.trim()
    },
  },
  template: `
    <ol class="word-list">
      <li v-for="(item, index) in items" :key="index" contenteditable="true" @blur="updateItem(index, $event)">{{ itemText(item) }}</li>
    </ol>
  `,
}

const WordEditObjectList = {
  props: {
    items: { type: Array, required: true },
    fields: { type: Array, required: true },
  },
  template: `
    <div class="word-object-list">
      <div v-for="(item, index) in items" :key="index" class="word-object-item">
        <div v-for="field in fields" :key="field.key" class="word-object-row">
          <strong>{{ field.label }}：</strong>
          <span
            contenteditable="true"
            @blur="item[field.key] = $event.target.innerText.trim()"
          >{{ item[field.key] }}</span>
        </div>
      </div>
    </div>
  `,
}

const TeachingCalendarCard = {
  props: {
    calendar: { type: Object, required: true },
  },
  emits: ['remove', 'preview'],
  methods: {
    rows() {
      return Array.isArray(this.calendar?.entries) ? this.calendar.entries.slice(0, 8) : []
    },
    countLabel() {
      const count = this.calendar?.rowCount || this.rows().length
      return `${count} 条授课安排`
    },
    rowLabel(row) {
      const session = String(row?.session || '').trim()
      const normalizedSession = session && /^[0-9]+$/.test(session) ? `第${session}次` : session
      return [row.week, normalizedSession, row.lessonType, row.periodCount ? `${row.periodCount}学时` : '']
        .filter(Boolean)
        .join(' · ')
    },
  },
  template: `
    <article class="teaching-calendar-card">
      <div class="teaching-calendar-head">
        <div class="teaching-calendar-title-block">
          <strong>{{ calendar.fileName }}</strong>
          <span class="teaching-calendar-count">{{ countLabel() }}</span>
        </div>
        <div class="teaching-calendar-actions">
          <el-button size="small" type="primary" plain round @click="$emit('preview', calendar)">查看全部</el-button>
          <el-button size="small" type="danger" plain round @click="$emit('remove')">删除</el-button>
        </div>
      </div>
      <div v-if="rows().length" class="teaching-calendar-table">
        <div v-for="(row, index) in rows()" :key="index" class="teaching-calendar-row">
          <span>{{ rowLabel(row) || '未标注' }}</span>
          <strong>{{ row.topic || row.rawText }}</strong>
        </div>
      </div>
      <p v-if="calendar?.rowCount > rows().length" class="teaching-calendar-hint">
        仅展示前 {{ rows().length }} 条，可点击“查看全部”查看完整安排。
      </p>
    </article>
  `,
}

const options = reactive({
  lessonTypes: ['理论课', '实验课', '理实一体课'],
  teachingModes: ['讲授法', '案例教学', '课堂讨论', '实验演示', '小组合作', '任务驱动', '项目驱动', '演示法', '小组协作', '错误驱动教学', '翻转课堂', '线上线下混合式教学'],
  grades: ['大一', '大二', '大三', '大四'],
  majors: ['人工智能', '智能科学与技术', '软件工程', '计算机科学与技术'],
})

const keyPointFields = [
  { key: 'point', label: '重点内容', placeholder: '例如：栈与队列的访问顺序差异' },
  { key: 'reason', label: '设计说明', placeholder: '例如：这是后续场景判断和代码实现的基础' },
]

const difficultPointFields = [
  { key: 'point', label: '难点内容', placeholder: '例如：根据场景判断应使用栈还是队列' },
  { key: 'reason', label: '难点原因', placeholder: '例如：学生容易停留在语法层，不会从问题特征判断结构选择' },
  { key: 'strategy', label: '突破策略', placeholder: '例如：通过对比案例先选再讲解，最后用代码验证', rows: 3 },
]

const ideologyFields = [
  { key: 'stage', label: '融入环节', placeholder: '例如：错误代码修正环节' },
  { key: 'carrier', label: '融入载体', placeholder: '例如：类型安全与边界检查案例' },
  { key: 'integration', label: '融入设计', placeholder: '例如：强调工程责任、严谨验证和协作表达', rows: 3 },
]

const studentProblemFields = [
  { key: 'problem', label: '学情问题', placeholder: '例如：容易把抽象数据类型和具体列表操作混为一谈', rows: 2 },
  { key: 'evidence', label: '课堂表现', placeholder: '例如：能写 append/pop，但说不清结构访问顺序的约束', rows: 2 },
  { key: 'strategy', label: '教学对策', placeholder: '例如：通过顺序可视化和场景判断题帮助学生建立结构本质认识', rows: 3 },
  { key: 'assessment', label: '评价证据', placeholder: '例如：课堂记录单、口头判断、随堂代码验证', rows: 2 },
]

const evaluationDesignFields = [
  { key: 'item', label: '评价项', placeholder: '例如：课堂参与' },
  { key: 'weight', label: '权重', placeholder: '例如：20%' },
  { key: 'evidence', label: '评价证据', placeholder: '例如：提问记录、讨论发言、课堂记录单' },
  { key: 'standard', label: '达标标准', placeholder: '例如：能围绕问题做出有效回应并完成课堂记录', rows: 3 },
]

const rubricFields = [
  { key: 'criterion', label: '评价维度', placeholder: '例如：结构判断与说明', type: 'text' },
  { key: 'weight', label: '权重', placeholder: '例如：30%', type: 'text' },
  { key: 'excellent', label: '优秀标准', placeholder: '例如：能准确判断结构并说明核心原因，表达完整清晰', rows: 3 },
  { key: 'qualified', label: '达标标准', placeholder: '例如：能基本判断结构，但理由说明不够完整', rows: 3 },
  { key: 'evidence', label: '评价证据', placeholder: '例如：课堂展示、记录单、任务提交物', rows: 2 },
]

const codeExampleFields = [
  { key: 'title', label: '标题', placeholder: '例如：Python 栈的基础实现', type: 'text' },
  { key: 'type', label: '类型', placeholder: '例如：standard / common_error / improved', type: 'text' },
  { key: 'language', label: '语言', placeholder: '例如：python', type: 'text' },
  { key: 'purpose', label: '用途说明', placeholder: '例如：用于说明入栈、出栈和判空操作的最小实现', rows: 3 },
  { key: 'code', label: '代码', placeholder: '请输入代码示例', rows: 8, maxRows: 12 },
]

const form = reactive({
  courseName: '人工智能导论',
  topic: '人工智能的基本概念与典型应用',
  major: '人工智能',
  grade: '大一',
  targetStudents: '人工智能 大一学生，约45人',
  lessonType: '理论课',
  teachingMode: ['讲授法', '案例教学', '课堂讨论'],
  periodCount: 2,
  minutesPerPeriod: 40,
  prerequisiteKnowledge: '',
  commonMisconceptions: '',
  classLevelProfile: '',
  lessonFocus: '',
  expectedOutputs: '',
  studentAnalysis: '学生刚进入人工智能专业，对人工智能概念、应用场景和学习路径兴趣较高，但容易把人工智能等同于聊天机器人或自动化工具，对算法、数据、模型和伦理边界缺少系统认识；需要通过真实案例、概念辨析和课堂讨论建立专业认知。',
  experimentEnv: '多媒体教室、课程PPT、典型AI应用案例材料',
  includeIdeology: true,
  includeObe: true,
  extraRequirements: '面向人工智能专业大一新生，突出专业认知建立、典型应用案例、学习路径引导、AI伦理与安全边界；教学过程要包含案例导入、概念讲解、分组讨论、课堂小结和形成性评价。',
  referenceMaterials: [],
  teachingCalendar: null,
})

const lessonPlans = ref([])
const generationRecords = ref([])
const currentPlan = ref(null)
const currentContent = ref(emptyContent())
const titleInput = ref('')
const activeTab = ref('preview')
const savingDraft = ref(false)
const generating = ref(false)
const savingPlan = ref(false)
const optimizingField = ref('')
const autoFillingContext = ref(false)
const previewAiTarget = ref('')
const targetStudentsAuto = ref(true)
const customTeachingMode = ref('')
const parsingResourceFileName = ref('')
const parsingTeachingCalendarFileName = ref('')
const MAX_REFERENCE_MATERIALS = 5
const MAX_REFERENCE_FILE_SIZE = 10 * 1024 * 1024
const SUPPORTED_REFERENCE_TYPES = ['txt', 'md', 'docx', 'pptx', 'pdf']
const MAX_TEACHING_CALENDAR_FILE_SIZE = 10 * 1024 * 1024

const editorText = reactive({
  knowledge: '',
  ability: '',
  quality: '',
  obeSupport: '',
  keyPoints: '',
  difficultPoints: '',
  ideologyDesign: '',
  evaluationDesign: '',
  taskRequirements: '',
  taskSteps: '',
  acceptanceCriteria: '',
  homework: '',
})

const processTotalMinutes = computed(() => (currentContent.value.teachingProcess || []).reduce((sum, row) => sum + Number(row.duration || 0), 0))
const commonTeachingModes = computed(() => ['讲授法', '案例教学', '课堂讨论',  '小组合作'])
const visibleTeachingModes = computed(() => {
  return [...new Set([...commonTeachingModes.value, ...form.teachingMode])]
})
const extraTeachingModes = computed(() => options.teachingModes.filter((item) => !commonTeachingModes.value.includes(item)))
const lessonCourseOptions = computed(() => [...new Set(lessonPlans.value.map((item) => item.courseName).filter(Boolean))])
const filteredLessonPlans = computed(() => {
  const keyword = lessonSearchKeyword.value.trim()
  return lessonPlans.value.filter((item) => {
    const courseMatched = !lessonCourseFilter.value || item.courseName === lessonCourseFilter.value
    const keywordMatched = !keyword || String(item.topic || '').includes(keyword) || String(item.title || '').includes(keyword)
    return courseMatched && keywordMatched
  })
})
const paginatedLessonPlans = computed(() => {
  const start = (lessonPage.value - 1) * lessonPageSize.value
  return filteredLessonPlans.value.slice(start, start + lessonPageSize.value)
})
const previewStats = computed(() => {
  const content = currentContent.value || emptyContent()
  const objectivesCount = [
    ...(content.objectives?.knowledge || []),
    ...(content.objectives?.ability || []),
    ...(content.objectives?.quality || []),
    ...(content.objectives?.obeSupport || []),
  ].filter(Boolean).length
  const taskCount = [
    ...(content.practiceTask?.basicTasks || []),
    ...(content.practiceTask?.advancedTasks || []),
    ...(content.practiceTask?.challengeTasks || []),
    ...(content.practiceTask?.steps || []),
    ...(content.practiceTask?.acceptanceCriteria || []),
    ...(content.practiceTask?.commonErrors || []),
  ].filter(Boolean).length
  const sectionCount = [
    content.studentAnalysis,
    (content.studentProblems || []).length,
    objectivesCount,
    (content.keyPoints || []).length,
    (content.difficultPoints || []).length,
    (content.teachingMethods || []).length,
    (content.resources || []).length,
    (content.ideologyDesign || []).length,
    (content.teachingProcess || []).length,
    taskCount,
    (content.codeExamples || []).length,
    (content.homework || []).length,
    (content.evaluationDesign || []).length,
    (content.rubric || []).length,
    content.reflection,
  ].filter(Boolean).length
  return {
    sectionCount,
    processCount: (content.teachingProcess || []).length,
    rubricCount: (content.rubric || []).length,
  }
})
const mergedObjectives = computed(() => [
  ...(currentContent.value.objectives?.knowledge || []),
  ...(currentContent.value.objectives?.ability || []),
  ...(currentContent.value.objectives?.quality || []),
])
const formQualityChecks = computed(() => {
  const modes = Array.isArray(form.teachingMode) ? form.teachingMode : normalizeTeachingMode(form.teachingMode).split('、').filter(Boolean)
  const totalMinutes = Number(form.periodCount || 0) * Number(form.minutesPerPeriod || 0)
  return [
    {
      label: '学情有靶点',
      pass: form.studentAnalysis.length >= 60 && textHasAny(form.studentAnalysis, ['错误', '困难', '差异', '问题', '基础']),
      tip: '写清楚先修基础、常见错误、强弱差异和本节课要解决的问题。',
    },
    {
      label: '方法是组合式',
      pass: modes.length >= 2,
      tip: '至少包含一个主导模式和一个辅助方法，例如任务驱动 + 错误驱动教学。',
    },
    {
      label: '任务有验收',
      pass: textHasAny(form.extraRequirements, ['验收', '检查点', 'Rubric', '评分', '测试用例', '提交物']),
      tip: '建议写明检查点、提交物、验收标准和评价方式。',
    },
    {
      label: '资源可落地',
      pass: Boolean(form.experimentEnv || form.referenceMaterials.length),
      tip: '至少填写实验环境，或上传课件、逐字稿、教材片段。',
    },
    {
      label: '课时明确',
      pass: totalMinutes > 0 && totalMinutes <= 240,
      tip: '系统会强制要求教学过程总分钟数等于课时安排。',
    },
  ]
})
const formQualityScore = computed(() => formQualityChecks.value.filter((item) => item.pass).length)

watch(
  () => [form.major, form.grade],
  ([major, grade]) => {
    if (targetStudentsAuto.value || !form.targetStudents) {
      form.targetStudents = buildTargetStudents(major, grade)
    }
  },
)

watch(
  () => form.lessonType,
  (type) => {
    form.minutesPerPeriod = String(type || '').includes('实验') ? 90 : 40
  },
)

watch(
  () => [lessonCourseFilter.value, lessonSearchKeyword.value],
  () => {
    lessonPage.value = 1
  },
)

watch(
  () => [filteredLessonPlans.value.length, lessonPageSize.value],
  ([total]) => {
    const maxPage = Math.max(1, Math.ceil(total / lessonPageSize.value))
    if (lessonPage.value > maxPage) {
      lessonPage.value = maxPage
    }
  },
)

watch(
  () => route.fullPath,
  async () => {
    if (user.value) {
      await syncRouteState()
    } else {
      activeModule.value = routeToModule()
    }
  },
)
const contentQualityChecks = computed(() => {
  const content = currentContent.value
  const basicInfo = content.basicInfo || {}
  const objectiveLines = [
    ...(content.objectives?.knowledge || []),
    ...(content.objectives?.ability || []),
    ...(content.objectives?.quality || []),
  ]
  const taskLines = [
    ...(content.practiceTask?.basicTasks || []),
    ...(content.practiceTask?.advancedTasks || []),
    ...(content.practiceTask?.challengeTasks || []),
  ]
  const acceptanceLines = content.practiceTask?.acceptanceCriteria || []
  const evaluationLines = flattenStructuredList(content.evaluationDesign)
  const ideologyLines = flattenStructuredList(content.ideologyDesign)
  const codeExamples = content.codeExamples || []
  const rubricRows = content.rubric || []
  const allTaskText = taskLines.join(' ')
  const allEvaluationText = evaluationLines.join(' ')
  const difficultText = flattenStructuredList(content.difficultPoints).join(' ')
  const lessonType = String(basicInfo.lessonType || currentPlan.value?.lessonType || '')
  const totalMinutes = Number(basicInfo.totalMinutes || currentPlan.value?.totalMinutes || 0)
  const isPractice = lessonType.includes('实验') || lessonType.includes('实践') || lessonType.includes('理实')
  return [
    {
      label: '课时闭合',
      pass: totalMinutes > 0 && processTotalMinutes.value === totalMinutes,
      tip: `教学过程必须等于 ${totalMinutes || 0} 分钟，当前为 ${processTotalMinutes.value} 分钟。`,
    },
    {
      label: '目标可衡量',
      pass: objectiveLines.length >= 6 && objectiveLines.every((item) => !startsWithVagueVerb(item)),
      tip: '目标不要用“掌握、理解、了解、熟悉、具备”开头，应使用说出、区分、实现、调试、提交等行为动词。',
    },
    {
      label: '重难点充分',
      pass: (content.keyPoints || []).length >= 4 && (content.difficultPoints || []).length >= 3 && textHasAny(difficultText, ['突破', '策略', '通过', '采用', '引导']),
      tip: '建议至少 4 条重点、3 条难点，难点要写突破策略。',
    },
    {
      label: '任务分层',
      pass: !isPractice || (
        (content.practiceTask?.basicTasks || []).length >= 1 &&
        (content.practiceTask?.advancedTasks || []).length >= 1 &&
        (content.practiceTask?.challengeTasks || []).length >= 1 &&
        acceptanceLines.length >= 3
      ),
      tip: isPractice ? '实践任务应包含基础、提高、挑战任务，并有可检查的验收标准。' : '理论课以案例分析、讨论产出或课堂小测作为可检查任务。',
    },
    {
      label: '评价可执行',
      pass: evaluationLines.length >= 4 && textHasAny(allEvaluationText, ['权重', '%', '分值', '比例']) && textHasAny(allEvaluationText, ['证据', 'Rubric', '自评', '互评', '同伴']),
      tip: '评价体系要有权重、证据链、教师评价、学生自评或同伴互评。',
    },
    {
      label: 'Word材料完整',
      pass: codeExamples.length >= 1 && rubricRows.length >= 4,
      tip: '正式教案应包含代码或伪代码示例，以及至少 4 个维度的 Rubric 评分表。',
    },
    {
      label: '思政不空泛',
      pass: ideologyLines.length >= 2 && textHasAny(ideologyLines.join(' '), ['环节', '任务', '代码', '案例', '评价', '提交', '讨论', '应用']),
      tip: '课程思政应写清楚在哪个环节、借助什么任务或案例融入。',
    },
  ]
})
const contentQualityScore = computed(() => contentQualityChecks.value.filter((item) => item.pass).length)

async function handleLogin() {
  loggingIn.value = true
  try {
    const data = await login(loginForm)
    localStorage.setItem('nsu_maic_token', data.token)
    user.value = data.user
    ElMessage.success('登录成功')
    await afterLogin()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  } finally {
    loggingIn.value = false
  }
}

async function handleLogout() {
  try {
    await logout()
  } finally {
    localStorage.removeItem('nsu_maic_token')
    user.value = null
    currentPlan.value = null
    generationRecords.value = []
  }
}

async function afterLogin() {
  await loadOptions()
  await refreshSideData()
  await syncRouteState()
}

async function loadOptions() {
  const data = await getOptions()
  options.lessonTypes = data.lessonTypes || options.lessonTypes
  options.teachingModes = data.teachingModes || options.teachingModes
  options.grades = data.grades || options.grades
  options.majors = data.majors || options.majors
}

async function loadLessonPlans() {
  const [coursePlans, singleLessonPlans] = await Promise.all([
    getCoursePlans(),
    getLessonPlans(),
  ])
  lessonPlans.value = [
    ...(coursePlans || []).map(normalizeCoursePlanSummary),
    ...(singleLessonPlans || []).map(normalizeLessonPlanSummary),
  ].sort((left, right) => String(right.updatedAt || '').localeCompare(String(left.updatedAt || '')))
}

async function loadGenerationRecords() {
  generationRecords.value = await getGenerationRecords()
}

async function refreshSideData() {
  await loadLessonPlans()
  await loadGenerationRecords()
}

async function handleCreate() {
  if (!validateForm()) return
  savingDraft.value = true
  try {
    await createLessonPlan(buildRequestPayload())
    ElMessage.success('✅ 保存成功')
    await refreshSideData()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    savingDraft.value = false
  }
}

async function handleGenerate() {
  if (!validateForm()) return
  generating.value = true
  try {
    currentPlan.value = await generateLessonPlan(buildRequestPayload())
    loadContent(currentPlan.value)
    activeTab.value = 'preview'
    ElMessage.success('教案已生成')
    await refreshSideData()
    await router.push({ name: 'lesson-edit', params: { id: currentPlan.value.id } })
  } catch (error) {
    await refreshSideData()
    await showErrorDialog('生成失败', errorMessage(error))
  } finally {
    generating.value = false
  }
}

async function polishFormField(field, fieldName) {
  if (!form[field]) {
    ElMessage.warning(`请先填写${fieldName}`)
    return
  }
  optimizingField.value = field
  try {
    const result = await optimizeText({
      text: form[field],
      fieldName,
      courseName: form.courseName,
      topic: form.topic,
    })
    form[field] = result.text
    ElMessage.success(`${fieldName}已优化`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '润色失败')
  } finally {
    optimizingField.value = ''
  }
}

async function aiRevisePreview(kind, label) {
  if (!currentPlan.value) return
  previewAiTarget.value = kind
  try {
    if (kind === 'all') {
      for (const item of [
        ['studentAnalysis', '学情分析'],
        ['studentProblems', '学情诊断与教学对策'],
        ['objectives', '教学目标'],
        ['keyPoints', '教学重点'],
        ['difficultPoints', '教学难点与突破策略'],
        ['teachingSupport', '教学方法与资源'],
        ['ideologyDesign', '课程思政融入'],
        ['teachingProcess', '教学过程设计'],
        ['practiceTask', '实践任务设计'],
        ['codeExamples', '代码示例'],
        ['homework', '作业与课后任务'],
        ['evaluationBundle', 'OBE 支撑与评价体系'],
        ['reflection', '课后反思'],
      ]) {
        previewAiTarget.value = item[0]
        await revisePreviewKind(item[0], item[1])
      }
      previewAiTarget.value = ''
      ElMessage.success('已完成全文主要章节优化')
      return
    }
    await revisePreviewKind(kind, label)
    ElMessage.success(`${label}已优化`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || `${label}优化失败`)
  } finally {
    previewAiTarget.value = ''
  }
}

async function revisePreviewKind(kind, label) {
  const source = previewSourceText(kind)
  if (!source.trim()) {
    ElMessage.warning(`${label}暂无内容可优化`)
    return
  }
  const basicInfo = currentContent.value.basicInfo || {}
  const result = await optimizeText({
    text: `${source}\n\n请在保留原有含义和教案结构的前提下，改得更具体、更像正式 Word 教案正文。不要解释，不要输出标题。`,
    fieldName: `预览修改-${label}`,
    courseName: basicInfo.courseName || currentPlan.value?.courseName,
    topic: basicInfo.topic || currentPlan.value?.topic,
  })
  applyPreviewRevision(kind, result.text)
}

function previewSourceText(kind) {
  const content = currentContent.value
  if (kind === 'studentAnalysis') return content.studentAnalysis
  if (kind === 'studentProblems') return flattenStructuredListWithFields(content.studentProblems, studentProblemFields).join('\n')
  if (kind === 'keyPoints') return flattenStructuredListWithFields(content.keyPoints, keyPointFields).join('\n')
  if (kind === 'difficultPoints') return flattenStructuredListWithFields(content.difficultPoints, difficultPointFields).join('\n')
  if (kind === 'ideologyDesign') return flattenStructuredListWithFields(content.ideologyDesign, ideologyFields).join('\n')
  if (kind === 'homework') return (content.homework || []).join('\n')
  if (kind === 'evaluationDesign') return flattenStructuredListWithFields(content.evaluationDesign, evaluationDesignFields).join('\n')
  if (kind === 'evaluationBundle') {
    return [
      '评价设计：',
      ...flattenStructuredListWithFields(content.evaluationDesign, evaluationDesignFields),
      'Rubric 评分表：',
      ...flattenStructuredListWithFields(content.rubric, rubricFields),
    ].join('\n')
  }
  if (kind === 'reflection') return content.reflection
  if (kind === 'teachingSupport') {
    return [
      '教学方法：',
      ...(content.teachingMethods || []),
      '教学资源：',
      ...(content.resources || []),
      '参考资料：',
      ...normalizeReferenceMaterials(content.referenceMaterials).map((item) => `${item.role === 'primary' ? '[主参考资料] ' : ''}${item.fileName}：${item.excerpt || '暂无摘要'}`),
    ].join('\n')
  }
  if (kind === 'objectives') {
    return [
      '知识目标：',
      ...(content.objectives.knowledge || []),
      '能力目标：',
      ...(content.objectives.ability || []),
      '素质目标：',
      ...(content.objectives.quality || []),
      'OBE 支撑：',
      ...(content.objectives.obeSupport || []),
    ].join('\n')
  }
  if (kind === 'practiceTask') {
    return [
      '任务名称：',
      content.practiceTask.taskName || '',
      '任务情境：',
      content.practiceTask.scenario || '',
      '基础任务：',
      ...(content.practiceTask.basicTasks || []),
      '提高任务：',
      ...(content.practiceTask.advancedTasks || []),
      '挑战任务：',
      ...(content.practiceTask.challengeTasks || []),
      '实施步骤：',
      ...(content.practiceTask.steps || []),
      '验收标准：',
      ...(content.practiceTask.acceptanceCriteria || []),
      '常见错误：',
      ...(content.practiceTask.commonErrors || []),
    ].join('\n')
  }
  if (kind === 'codeExamples') {
    return (content.codeExamples || [])
      .map((item, index) => [
        `${index + 1}. 标题：${item.title || ''}`,
        `类型：${item.type || ''}`,
        `语言：${item.language || ''}`,
        `用途说明：${item.purpose || ''}`,
        '代码：',
        item.code || '',
      ].join('\n'))
      .join('\n\n')
  }
  if (kind === 'teachingProcess') {
    return (content.teachingProcess || [])
      .map((row, index) => [
        `${index + 1}. ${row.stage || ''}`,
        `时间：${row.duration || ''} 分钟`,
        `教师活动：${row.teacherActivity || ''}`,
        `学生活动：${row.studentActivity || ''}`,
        `课堂产出：${row.output || ''}`,
        `检查点：${row.checkpoint || ''}`,
        `设计意图：${row.designPurpose || ''}`,
        `教学资源：${row.resources || ''}`,
        `评价方式：${row.evaluation || ''}`,
      ].join('\n'))
      .join('\n\n')
  }
  return ''
}

function applyPreviewRevision(kind, text) {
  const content = currentContent.value
  if (kind === 'studentAnalysis' || kind === 'reflection') {
    content[kind] = cleanAutoFilledText(text)
    return
  }
  if (kind === 'studentProblems') {
    const rows = parseStructuredLines(text, studentProblemFields)
    if (rows.length) content.studentProblems = rows
    return
  }
  if (kind === 'objectives') {
    const buckets = splitObjectiveRevision(text)
    content.objectives.knowledge = buckets.knowledge.length ? buckets.knowledge : content.objectives.knowledge
    content.objectives.ability = buckets.ability.length ? buckets.ability : content.objectives.ability
    content.objectives.quality = buckets.quality.length ? buckets.quality : content.objectives.quality
    content.objectives.obeSupport = buckets.obeSupport.length ? buckets.obeSupport : content.objectives.obeSupport
    return
  }
  if (kind === 'keyPoints') {
    const rows = parseStructuredLines(text, keyPointFields)
    if (rows.length) content.keyPoints = rows
    return
  }
  if (kind === 'difficultPoints') {
    const rows = parseStructuredLines(text, difficultPointFields)
    if (rows.length) content.difficultPoints = rows
    return
  }
  if (kind === 'ideologyDesign') {
    const rows = parseStructuredLines(text, ideologyFields)
    if (rows.length) content.ideologyDesign = rows
    return
  }
  if (kind === 'teachingSupport') {
    const buckets = splitSectionBuckets(text, {
      teachingMethods: ['教学方法'],
      resources: ['教学资源'],
    })
    if (buckets.teachingMethods.length) content.teachingMethods = buckets.teachingMethods
    if (buckets.resources.length) content.resources = buckets.resources
    return
  }
  if (kind === 'evaluationDesign') {
    const rows = parseStructuredLines(text, evaluationDesignFields)
    if (rows.length) content.evaluationDesign = rows
    return
  }
  if (kind === 'evaluationBundle') {
    const buckets = splitSectionBuckets(text, {
      evaluationDesign: ['评价设计'],
      rubric: ['Rubric 评分表', '评分表', 'Rubric'],
    })
    if (buckets.evaluationDesign.length) {
      const rows = parseStructuredLines(buckets.evaluationDesign.join('\n'), evaluationDesignFields)
      if (rows.length) content.evaluationDesign = rows
    }
    if (buckets.rubric.length) {
      const rows = parseStructuredLines(buckets.rubric.join('\n'), rubricFields)
      if (rows.length) content.rubric = rows
    }
    return
  }
  if (kind === 'practiceTask') {
    applyPracticeTaskRevision(text)
    return
  }
  if (kind === 'codeExamples') {
    const rows = parseStructuredBlocks(text, codeExampleFields)
    if (rows.length) content.codeExamples = rows
    return
  }
  if (kind === 'teachingProcess') {
    applyTeachingProcessRevision(text)
    return
  }
  if (['homework'].includes(kind)) {
    const rows = splitRevisionLines(text)
    if (rows.length) {
      content[kind] = rows
    }
  }
}

function splitObjectiveRevision(text) {
  const buckets = { knowledge: [], ability: [], quality: [], obeSupport: [] }
  let current = 'knowledge'
  splitRevisionLines(text).forEach((line) => {
    if (line.includes('知识目标')) {
      current = 'knowledge'
      return
    }
    if (line.includes('能力目标')) {
      current = 'ability'
      return
    }
    if (line.includes('素质目标')) {
      current = 'quality'
      return
    }
    if (line.includes('OBE') || line.includes('支撑')) {
      current = 'obeSupport'
      return
    }
    buckets[current].push(line)
  })
  return buckets
}

function splitRevisionLines(text) {
  return String(text || '')
    .replace(/^#+\s*/gm, '')
    .split(/\n+/)
    .map((item) => item.replace(/^[-•\d.、\s]+/, '').trim())
    .filter(Boolean)
}

function parseStructuredLines(text, fields) {
  const lines = splitRevisionLines(text)
  return lines.map((line) => parseStructuredItem(line, fields)).filter((item) => fields.some((field) => item[field.key]))
}

function parseStructuredBlocks(text, fields) {
  const blocks = String(text || '')
    .replace(/^#+\s*/gm, '')
    .split(/\n{2,}/)
    .map((item) => item.trim())
    .filter(Boolean)
  return blocks.map((block) => parseStructuredBlock(block, fields)).filter((item) => fields.some((field) => item[field.key]))
}

function parseStructuredBlock(block, fields) {
  const item = emptyStructuredItem(fields)
  let matched = false
  fields.forEach((field, index) => {
    const nextLabels = fields.slice(index + 1).map((next) => `${escapeRegex(next.label)}[：:]`)
    const tailPattern = nextLabels.length ? `(?=(?:${nextLabels.join('|')})|$)` : '$'
    const pattern = new RegExp(`${escapeRegex(field.label)}[：:]\\s*([\\s\\S]*?)${tailPattern}`)
    const match = block.match(pattern)
    if (match && match[1]) {
      item[field.key] = match[1].trim().replace(/；$/, '')
      matched = true
    }
  })
  if (!matched && fields.length) {
    item[fields[0].key] = block.trim()
  }
  return item
}

function parseStructuredItem(line, fields) {
  const item = emptyStructuredItem(fields)
  let matched = false
  fields.forEach((field, index) => {
    const nextLabels = fields.slice(index + 1).map((next) => `${escapeRegex(next.label)}[：:]`)
    const tailPattern = nextLabels.length ? `(?=(?:${nextLabels.join('|')})|$)` : '$'
    const pattern = new RegExp(`${escapeRegex(field.label)}[：:]\\s*(.*?)${tailPattern}`)
    const match = line.match(pattern)
    if (match && match[1]) {
      item[field.key] = match[1].trim().replace(/；$/, '')
      matched = true
    }
  })
  if (!matched && fields.length) {
    item[fields[0].key] = line.trim()
  }
  return item
}

function emptyStructuredItem(fields) {
  return fields.reduce((acc, field) => {
    acc[field.key] = ''
    return acc
  }, {})
}

function flattenStructuredList(list) {
  return Array.isArray(list) ? list.map((item) => stringifyListItem(item)).filter(Boolean) : []
}

function flattenStructuredListWithFields(list, fields) {
  return Array.isArray(list) ? list.map((item) => stringifyStructuredItemWithFields(item, fields)).filter(Boolean) : []
}

function stringifyStructuredItemWithFields(item, fields) {
  if (!item || typeof item !== 'object') return String(item || '').trim()
  return fields
    .map((field) => {
      const value = String(item[field.key] || '').trim()
      return value ? `${field.label}：${value}` : ''
    })
    .filter(Boolean)
    .join('；')
}

function splitSectionBuckets(text, sections) {
  const result = Object.keys(sections).reduce((acc, key) => {
    acc[key] = []
    return acc
  }, {})
  let current = ''
  splitRevisionLines(text).forEach((line) => {
    const matchedKey = Object.entries(sections).find(([, labels]) => labels.some((label) => line.includes(label)))?.[0]
    if (matchedKey) {
      current = matchedKey
      return
    }
    if (current && result[current]) {
      result[current].push(line)
    }
  })
  return result
}

function escapeRegex(value) {
  return String(value || '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function applyPracticeTaskRevision(text) {
  const buckets = {
    basicTasks: [],
    advancedTasks: [],
    challengeTasks: [],
    steps: [],
    acceptanceCriteria: [],
    commonErrors: [],
  }
  let current = ''
  splitRevisionLines(text).forEach((line) => {
    if (line.includes('任务名称')) {
      currentContent.value.practiceTask.taskName = line.replace(/^任务名称[:：]\s*/, '')
      return
    }
    if (line.includes('任务情境')) {
      currentContent.value.practiceTask.scenario = line.replace(/^任务情境[:：]\s*/, '')
      return
    }
    if (line.includes('基础任务')) { current = 'basicTasks'; return }
    if (line.includes('提高任务')) { current = 'advancedTasks'; return }
    if (line.includes('挑战任务')) { current = 'challengeTasks'; return }
    if (line.includes('实施步骤')) { current = 'steps'; return }
    if (line.includes('验收标准')) { current = 'acceptanceCriteria'; return }
    if (line.includes('常见错误')) { current = 'commonErrors'; return }
    if (current && buckets[current]) buckets[current].push(line)
  })
  Object.entries(buckets).forEach(([key, rows]) => {
    if (rows.length) currentContent.value.practiceTask[key] = rows
  })
}

function applyTeachingProcessRevision(text) {
  const blocks = String(text || '').split(/\n{2,}/).map((item) => item.trim()).filter(Boolean)
  const rows = blocks.map((block) => {
    const row = {
      stage: '',
      duration: 5,
      teacherActivity: '',
      studentActivity: '',
      output: '',
      checkpoint: '',
      designPurpose: '',
      resources: '',
      evaluation: '',
    }
    block.split('\n').map((item) => item.trim()).filter(Boolean).forEach((line, index) => {
      if (index === 0 && !line.includes('：')) {
        row.stage = line.replace(/^\d+[.、]\s*/, '')
        return
      }
      if (line.startsWith('时间')) row.duration = Number((line.match(/\d+/) || [row.duration])[0])
      else if (line.startsWith('教师活动')) row.teacherActivity = line.replace(/^教师活动[:：]\s*/, '')
      else if (line.startsWith('学生活动')) row.studentActivity = line.replace(/^学生活动[:：]\s*/, '')
      else if (line.startsWith('课堂产出')) row.output = line.replace(/^课堂产出[:：]\s*/, '')
      else if (line.startsWith('检查点')) row.checkpoint = line.replace(/^检查点[:：]\s*/, '')
      else if (line.startsWith('设计意图')) row.designPurpose = line.replace(/^设计意图[:：]\s*/, '')
      else if (line.startsWith('教学资源')) row.resources = line.replace(/^教学资源[:：]\s*/, '')
      else if (line.startsWith('评价方式')) row.evaluation = line.replace(/^评价方式[:：]\s*/, '')
    })
    return row
  }).filter((row) => row.stage)
  if (rows.length) currentContent.value.teachingProcess = rows
}

function editableText(event) {
  return event.target.innerText.trim()
}

function setScalar(field, value) {
  currentContent.value[field] = value
}

function recalcPlanMinutes() {
  const basicInfo = currentContent.value.basicInfo || {}
  const periodCount = Number(basicInfo.periodCount || currentPlan.value?.periodCount || 0)
  const minutesPerPeriod = Number(basicInfo.minutesPerPeriod || currentPlan.value?.minutesPerPeriod || 0)
  const totalMinutes = periodCount > 0 && minutesPerPeriod > 0
    ? periodCount * minutesPerPeriod
    : Number(basicInfo.totalMinutes || currentPlan.value?.totalMinutes || 0)
  currentContent.value.basicInfo.periodCount = periodCount
  currentContent.value.basicInfo.minutesPerPeriod = minutesPerPeriod
  currentContent.value.basicInfo.totalMinutes = totalMinutes
  if (currentPlan.value) {
    currentPlan.value.periodCount = periodCount
    currentPlan.value.minutesPerPeriod = minutesPerPeriod
    currentPlan.value.totalMinutes = totalMinutes
  }
  syncContentBasicInfo()
}

function syncPlanTitle() {
  const basicInfo = currentContent.value.basicInfo || {}
  const courseName = basicInfo.courseName || currentPlan.value?.courseName || ''
  const topic = basicInfo.topic || currentPlan.value?.topic || ''
  titleInput.value = `${courseName} - ${topic}`.replace(/\s+-\s+$/, '').trim()
}

function setPlanField(field, value) {
  const normalizedValue = String(value || '').trim()
  if (!currentContent.value.basicInfo) {
    currentContent.value.basicInfo = {}
  }
  currentContent.value.basicInfo[field] = normalizedValue
  if (currentPlan.value) {
    currentPlan.value[field] = normalizedValue
  }
  if (field === 'courseName' || field === 'topic') {
    syncPlanTitle()
  }
  syncContentBasicInfo()
}

function updateMinutesPerPeriod(event) {
  const value = Number(editableText(event).replace(/[^0-9]/g, ''))
  if (value > 0) {
    currentContent.value.basicInfo.minutesPerPeriod = value
    if (currentPlan.value) {
      currentPlan.value.minutesPerPeriod = value
    }
    recalcPlanMinutes()
  }
}

function updatePeriodSummary(event) {
  const numbers = editableText(event).match(/\d+/g) || []
  const periodCount = Number(numbers[0] || 0)
  const totalMinutes = Number(numbers[1] || 0)
  if (periodCount > 0) {
    currentContent.value.basicInfo.periodCount = periodCount
    if (currentPlan.value) {
      currentPlan.value.periodCount = periodCount
    }
  }
  const effectivePeriodCount = Number(currentContent.value.basicInfo.periodCount || currentPlan.value?.periodCount || 0)
  if (totalMinutes > 0 && effectivePeriodCount > 0) {
    const minutesPerPeriod = Math.max(1, Math.round(totalMinutes / effectivePeriodCount))
    currentContent.value.basicInfo.minutesPerPeriod = minutesPerPeriod
    if (currentPlan.value) {
      currentPlan.value.minutesPerPeriod = minutesPerPeriod
    }
  }
  recalcPlanMinutes()
}

function updateSubtitle(event) {
  const parts = editableText(event).split('•').map((item) => item.trim()).filter(Boolean)
  if (parts[0]) {
    currentContent.value.basicInfo.lessonType = parts[0]
    if (currentPlan.value) {
      currentPlan.value.lessonType = parts[0]
    }
  }
  const minuteMatch = parts[1]?.match(/\d+/)
  if (minuteMatch) {
    const totalMinutes = Number(minuteMatch[0])
    if (totalMinutes > 0) {
      currentContent.value.basicInfo.totalMinutes = totalMinutes
      if (currentPlan.value) {
        currentPlan.value.totalMinutes = totalMinutes
      }
      const periodCount = Number(currentContent.value.basicInfo.periodCount || currentPlan.value?.periodCount || 0)
      if (periodCount > 0) {
        const minutesPerPeriod = Math.max(1, Math.round(totalMinutes / periodCount))
        currentContent.value.basicInfo.minutesPerPeriod = minutesPerPeriod
        if (currentPlan.value) {
          currentPlan.value.minutesPerPeriod = minutesPerPeriod
        }
      }
    }
  }
  if (parts[2]) {
    currentContent.value.basicInfo.courseName = parts[2]
    if (currentPlan.value) {
      currentPlan.value.courseName = parts[2]
    }
    syncPlanTitle()
  }
  syncContentBasicInfo()
}

async function autoFillTeachingContext() {
  if (!form.courseName || !form.topic || !form.major || !form.grade || !form.lessonType) {
    ElMessage.warning('请先填写课程名称、章节主题、专业、年级和课程类型')
    return
  }
  autoFillingContext.value = true
  try {
    const base = [
      `课程名称：${form.courseName}`,
      `章节主题：${form.topic}`,
      `授课专业：${form.major}`,
      `年级：${form.grade}`,
      `授课对象：${form.targetStudents || buildTargetStudents(form.major, form.grade)}`,
      `课程类型：${form.lessonType}`,
      `教学方法：${normalizeTeachingMode(form.teachingMode) || '未填写'}`,
      `课时：${Number(form.periodCount || 0) * Number(form.minutesPerPeriod || 0)} 分钟`,
      `先修基础：${form.prerequisiteKnowledge || '未填写'}`,
      `常见误区：${form.commonMisconceptions || '未填写'}`,
      `班级情况：${form.classLevelProfile || '未填写'}`,
      `本节重点：${form.lessonFocus || '未填写'}`,
      `预期产出：${form.expectedOutputs || '未填写'}`,
      referenceMaterialsContextSummary(form.referenceMaterials),
      teachingCalendarContextSummary(form.teachingCalendar),
    ].join('\n')
    const [studentResult, resourceResult, requirementResult] = await Promise.all([
      optimizeText({
        text: `${base}\n请直接生成 100-160 字学情描述，必须包含先修基础、常见误区、能力差异和本节课要解决的问题。`,
        fieldName: '自动填写-学情描述',
        courseName: form.courseName,
        topic: form.topic,
      }),
      optimizeText({
        text: `${base}\n请直接生成本节课可落地使用的教学资源或实验环境，写成一段简洁正文。`,
        fieldName: '自动填写-实验环境 / 教学资源',
        courseName: form.courseName,
        topic: form.topic,
      }),
      optimizeText({
        text: `${base}\n请直接生成 100-160 字其他要求，必须包含课程重点、课堂环节、评价方式、提交物或课堂产出要求。`,
        fieldName: '自动填写-其他要求',
        courseName: form.courseName,
        topic: form.topic,
      }),
    ])
    form.studentAnalysis = cleanAutoFilledText(studentResult.text)
    form.experimentEnv = cleanAutoFilledText(resourceResult.text)
    form.extraRequirements = cleanAutoFilledText(requirementResult.text)
    ElMessage.success('已自动补全学情描述、教学资源和其他要求')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || 'AI 填写失败')
  } finally {
    autoFillingContext.value = false
  }
}

async function handleFormResourceSelected(uploadFile) {
    await handleReferenceSelected(uploadFile, form.referenceMaterials, '新建课程教案')
}

async function handleEditorResourceSelected(uploadFile) {
  await handleReferenceSelected(uploadFile, currentContent.value.referenceMaterials, '当前教案')
}

async function handleFormTeachingCalendarSelected(uploadFile) {
  const calendar = await handleTeachingCalendarSelected(uploadFile)
  if (calendar) {
    form.teachingCalendar = calendar
  }
}

async function handleEditorTeachingCalendarSelected(uploadFile) {
  const calendar = await handleTeachingCalendarSelected(uploadFile)
  if (calendar) {
    currentContent.value.teachingCalendar = calendar
  }
}

async function handleTeachingCalendarSelected(uploadFile) {
  const rawFile = uploadFile?.raw
  if (!rawFile) {
    return null
  }
  const fileName = String(rawFile.name || '').trim()
  if (!/\.xlsx?$/i.test(fileName)) {
    ElMessage.error('教学日历暂只支持 xls、xlsx 文件')
    return null
  }
  if (rawFile.size > MAX_TEACHING_CALENDAR_FILE_SIZE) {
    ElMessage.error(`教学日历不能超过 10MB，当前文件约 ${formatFileSize(rawFile.size)}`)
    return null
  }
  try {
    parsingTeachingCalendarFileName.value = fileName
    const result = await parseTeachingCalendar(rawFile)
    const calendar = normalizeTeachingCalendar(result)
    if (!hasTeachingCalendar(calendar)) {
      ElMessage.error('未从教学日历中识别到授课安排')
      return null
    }
    ElMessage.success('教学日历已读取')
    return calendar
  } catch (error) {
    ElMessage.error(resolveTeachingCalendarErrorMessage(error))
    return null
  } finally {
    parsingTeachingCalendarFileName.value = ''
  }
}

function removeFormTeachingCalendar() {
  form.teachingCalendar = null
}

function removeCurrentTeachingCalendar() {
  currentContent.value.teachingCalendar = null
}

async function handleReferenceSelected(uploadFile, targetList, label) {
  const rawFile = uploadFile?.raw
  if (!rawFile) {
    return
  }
  const fileName = String(rawFile.name || '').trim()
  if (!fileName) {
    ElMessage.error('文件名无效')
    return
  }
  const fileType = detectFileType(fileName)
  if (!SUPPORTED_REFERENCE_TYPES.includes(fileType)) {
    ElMessage.error('暂只支持 txt、md、docx、pptx、pdf 文件')
    return
  }
  if (rawFile.size > MAX_REFERENCE_FILE_SIZE) {
    ElMessage.error(`单个参考资料不能超过 10MB，当前文件约 ${formatFileSize(rawFile.size)}`)
    return
  }
  const existed = Array.isArray(targetList)
    ? targetList.find((item) => String(item?.fileName || '').toLowerCase() === fileName.toLowerCase())
    : null
  const nextDistinctCount = Array.isArray(targetList)
    ? targetList.filter((item) => String(item?.fileName || '').toLowerCase() !== fileName.toLowerCase()).length + 1
    : 1
  if (nextDistinctCount > MAX_REFERENCE_MATERIALS) {
    ElMessage.error('最多上传 5 份参考资料')
    return
  }
  try {
    parsingResourceFileName.value = fileName
    const result = await extractResource(rawFile)
    const material = buildReferenceMaterial(result, existed?.role || (targetList.length ? 'secondary' : 'primary'))
    replaceReferenceMaterialList(targetList, [...targetList, material])
    ElMessage.success(existed ? `${label}的同名参考资料已替换` : `已加入${label}参考资料`)
  } catch (error) {
    ElMessage.error(resolveUploadErrorMessage(error))
  } finally {
    parsingResourceFileName.value = ''
  }
}

function toggleTeachingMode(item) {
  const index = form.teachingMode.indexOf(item)
  if (index >= 0) {
    form.teachingMode.splice(index, 1)
    return
  }
  form.teachingMode.push(item)
}

function addCustomTeachingMode() {
  const value = customTeachingMode.value.trim()
  if (!value) return
  if (!options.teachingModes.includes(value)) {
    options.teachingModes.push(value)
  }
  if (!form.teachingMode.includes(value)) {
    form.teachingMode.push(value)
  }
  customTeachingMode.value = ''
}

function routeToModule() {
  if (route.name === 'lesson-list') return 'lessons'
  if (route.name === 'lesson-edit') return 'editor'
  return 'wizard'
}

function isSidebarActive(key) {
  return activeModule.value === key || (activeModule.value === 'editor' && key === 'lessons')
}

async function syncRouteState() {
  activeModule.value = routeToModule()
  if (activeModule.value === 'editor') {
    await loadPlanFromRoute()
    return
  }
  currentPlan.value = null
  activeTab.value = 'preview'
}

async function loadPlanFromRoute() {
  const id = Number(route.params.id)
  if (!id) {
    currentPlan.value = null
    return
  }
  if (Number(currentPlan.value?.id) === id && titleInput.value) {
    activeTab.value = 'preview'
    return
  }
  try {
    currentPlan.value = await getLessonPlanDetail(id)
    loadContent(currentPlan.value)
    activeTab.value = 'preview'
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取教案失败')
    currentPlan.value = null
    await router.replace({ name: 'lesson-list' })
  }
}

async function handleSelectPlan(row) {
  if (!row?.id) return
  if (row.planKind === 'course-plan') {
    await router.push({ name: 'course-plan-new', query: { id: row.id } })
    return
  }
  await router.push({ name: 'lesson-edit', params: { id: row.id } })
}

function lessonRowIndex(index) {
  return (lessonPage.value - 1) * lessonPageSize.value + index + 1
}

async function handleSave() {
  if (!currentPlan.value) return
  savingPlan.value = true
  try {
    applyEditorText()
    currentPlan.value = await saveLessonPlan(currentPlan.value.id, buildSavePayload())
    loadContent(currentPlan.value)
    await refreshSideData()
    ElMessage.success('✅ 保存成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存修改失败')
  } finally {
    savingPlan.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定要删除这份教案吗？此操作不可恢复。', '删除教案', { type: 'warning' })
    await deleteLessonPlan(row.id)
    if (currentPlan.value?.id === row.id) {
      currentPlan.value = null
    }
    await refreshSideData()
    ElMessage.success('已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

async function handleExport(plan, format = 'word') {
  try {
    if (plan?.planKind === 'course-plan') {
      if (format === 'pdf') {
        exportCoursePlanPdf(plan.id)
      } else {
        exportCoursePlanWord(plan.id)
      }
      return
    }
    applyEditorText()
    if (currentPlan.value?.id === plan.id) {
      currentPlan.value = await saveLessonPlan(currentPlan.value.id, buildSavePayload())
      loadContent(currentPlan.value)
      await refreshSideData()
    }
    await exportLessonPlanWord(plan)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '导出失败')
    }
  }
}

function normalizeCoursePlanSummary(item) {
  return {
    ...item,
    planKind: 'course-plan',
    typeLabel: '课程教案',
    topic: '整门课程教案',
    lessonType: '课程教案',
  }
}

function normalizeLessonPlanSummary(item) {
  return {
    ...item,
    planKind: 'lesson-plan',
    typeLabel: '单次课教案',
    topic: item.topic || '未填写章节主题',
    lessonType: item.lessonType || '单次课教案',
  }
}

async function switchModule(key) {
  if (key === 'wizard') {
    await router.push({ name: 'course-plan-new' })
    return
  }
  if (key === 'lessons') {
    await router.push({ name: 'lesson-list' })
  }
}

function buildSavePayload() {
  const basicInfo = currentContent.value.basicInfo || {}
  return {
    title: titleInput.value,
    courseName: basicInfo.courseName,
    major: basicInfo.major,
    grade: basicInfo.grade,
    targetStudents: basicInfo.targetStudents,
    topic: basicInfo.topic,
    lessonType: basicInfo.lessonType,
    teachingMode: basicInfo.teachingMode,
    periodCount: basicInfo.periodCount,
    minutesPerPeriod: basicInfo.minutesPerPeriod,
    contentJson: JSON.stringify(currentContent.value),
    status: 'draft',
  }
}

async function returnToLessonList() {
  currentPlan.value = null
  activeTab.value = 'preview'
  await router.push({ name: 'lesson-list' })
}

async function openGenerationRecord(row) {
  try {
    recordDetail.value = await getGenerationRecordDetail(row.id)
    recordDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '读取生成记录失败')
  }
}

async function regenerateFromRecord(row) {
  if (!row.errorMessage) {
    ElMessage.warning('这条记录没有失败原因')
    return
  }
  form.extraRequirements = `${form.extraRequirements || ''}\n\n【按上次失败原因修正】\n${row.errorMessage}`.trim()
  await router.push({ name: 'course-plan-new' })
  if (!validateForm()) {
    ElMessage.warning('请先补齐生成表单后再重试')
    return
  }
  await handleGenerate()
}

async function scrollToEditor() {
  await nextTick()
  editorPanelRef.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}

function validateForm() {
  if (!form.courseName || !form.topic || !form.lessonType || normalizeTeachingMode(form.teachingMode) === '') {
    ElMessage.warning('请填写课程名称、章节主题、课程类型和教学模式/方法')
    return false
  }
  return true
}

function buildRequestPayload() {
  return {
    ...form,
    teachingMode: normalizeTeachingMode(form.teachingMode),
    referenceMaterials: serializeReferenceMaterials(form.referenceMaterials),
    teachingCalendar: serializeTeachingCalendar(form.teachingCalendar),
  }
}

function normalizeTeachingMode(value) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item || '').trim()).filter(Boolean).join('、')
  }
  return String(value || '').trim()
}

function loadContent(plan) {
  titleInput.value = plan.title || ''
  activeTab.value = 'preview'
  try {
    currentContent.value = { ...emptyContent(), ...(plan.contentJson ? JSON.parse(plan.contentJson) : {}) }
  } catch (error) {
    currentContent.value = emptyContent()
  }
  ensureContentShape()
  hydratePlanBasicInfoFromContent()
  syncPlanTitle()
  syncEditorText()
}

function syncContentBasicInfo() {
  const basicInfo = currentContent.value.basicInfo || {}
  currentContent.value.basicInfo = {
    courseName: basicInfo.courseName || currentPlan.value?.courseName || '',
    topic: basicInfo.topic || currentPlan.value?.topic || '',
    major: basicInfo.major || currentPlan.value?.major || '',
    grade: basicInfo.grade || currentPlan.value?.grade || '',
    targetStudents: basicInfo.targetStudents || currentPlan.value?.targetStudents || '',
    lessonType: basicInfo.lessonType || currentPlan.value?.lessonType || '',
    teachingMode: basicInfo.teachingMode || currentPlan.value?.teachingMode || '',
    periodCount: Number(basicInfo.periodCount || currentPlan.value?.periodCount || 0),
    totalMinutes: Number(basicInfo.totalMinutes || currentPlan.value?.totalMinutes || 0),
    minutesPerPeriod: Number(basicInfo.minutesPerPeriod || currentPlan.value?.minutesPerPeriod || 0),
  }
}

function hydratePlanBasicInfoFromContent() {
  if (!currentPlan.value) return
  const basicInfo = currentContent.value.basicInfo || {}
  currentPlan.value.courseName = basicInfo.courseName || currentPlan.value.courseName
  currentPlan.value.topic = basicInfo.topic || currentPlan.value.topic
  currentPlan.value.major = basicInfo.major || currentPlan.value.major
  currentPlan.value.grade = basicInfo.grade || currentPlan.value.grade
  currentPlan.value.targetStudents = basicInfo.targetStudents || currentPlan.value.targetStudents
  currentPlan.value.lessonType = basicInfo.lessonType || currentPlan.value.lessonType
  currentPlan.value.teachingMode = basicInfo.teachingMode || currentPlan.value.teachingMode
  currentPlan.value.periodCount = Number(basicInfo.periodCount || currentPlan.value.periodCount || 0)
  currentPlan.value.totalMinutes = Number(basicInfo.totalMinutes || currentPlan.value.totalMinutes || 0)
  currentPlan.value.minutesPerPeriod = Number(basicInfo.minutesPerPeriod || currentPlan.value.minutesPerPeriod || 0)
}

function ensureContentShape() {
  const content = currentContent.value
  content.basicInfo = { ...(content.basicInfo || {}) }
  content.objectives = { knowledge: [], ability: [], quality: [], obeSupport: [], ...(content.objectives || {}) }
  content.generationContext = {
    prerequisiteKnowledge: '',
    commonMisconceptions: '',
    classLevelProfile: '',
    lessonFocus: '',
    expectedOutputs: '',
    ...(content.generationContext || {}),
  }
  content.practiceTask = {
    taskName: '',
    scenario: '',
    basicTasks: [],
    advancedTasks: [],
    challengeTasks: [],
    steps: [],
    acceptanceCriteria: [],
    commonErrors: [],
    ...(content.practiceTask || {}),
  }
  content.referenceMaterials = normalizeReferenceMaterials(content.referenceMaterials)
  content.teachingCalendar = normalizeTeachingCalendar(content.teachingCalendar)
  content.objectives.knowledge = normalizeTextList(content.objectives.knowledge)
  content.objectives.ability = normalizeTextList(content.objectives.ability)
  content.objectives.quality = normalizeTextList(content.objectives.quality)
  content.objectives.obeSupport = normalizeTextList(content.objectives.obeSupport)
  content.keyPoints = normalizeStructuredList(content.keyPoints, keyPointFields)
  content.difficultPoints = normalizeStructuredList(content.difficultPoints, difficultPointFields)
  content.teachingMethods = normalizeTextList(content.teachingMethods)
  content.resources = normalizeTextList(content.resources)
  content.ideologyDesign = normalizeStructuredList(content.ideologyDesign, ideologyFields)
  content.studentProblems = normalizeStructuredList(content.studentProblems, studentProblemFields)
  content.codeExamples = normalizeStructuredList(content.codeExamples, codeExampleFields)
  content.rubric = normalizeStructuredList(content.rubric, rubricFields)
  content.homework = normalizeTextList(content.homework)
  if (Array.isArray(content.practiceTask.requirements) && !content.practiceTask.basicTasks.length && !content.practiceTask.advancedTasks.length && !content.practiceTask.challengeTasks.length) {
    const legacyRequirements = normalizeTextList(content.practiceTask.requirements)
    content.practiceTask.basicTasks = legacyRequirements.filter((item) => item.includes('基础'))
    content.practiceTask.advancedTasks = legacyRequirements.filter((item) => item.includes('提高'))
    content.practiceTask.challengeTasks = legacyRequirements.filter((item) => item.includes('挑战'))
    if (!content.practiceTask.basicTasks.length && legacyRequirements[0]) content.practiceTask.basicTasks = [legacyRequirements[0]]
    if (!content.practiceTask.advancedTasks.length && legacyRequirements[1]) content.practiceTask.advancedTasks = [legacyRequirements[1]]
    if (!content.practiceTask.challengeTasks.length && legacyRequirements[2]) content.practiceTask.challengeTasks = [legacyRequirements[2]]
  }
  content.practiceTask.basicTasks = normalizeTextList(content.practiceTask.basicTasks)
  content.practiceTask.advancedTasks = normalizeTextList(content.practiceTask.advancedTasks)
  content.practiceTask.challengeTasks = normalizeTextList(content.practiceTask.challengeTasks)
  content.practiceTask.steps = normalizeTextList(content.practiceTask.steps)
  content.practiceTask.acceptanceCriteria = normalizeTextList(content.practiceTask.acceptanceCriteria)
  content.practiceTask.commonErrors = normalizeTextList(content.practiceTask.commonErrors)
  content.teachingProcess = Array.isArray(content.teachingProcess)
    ? content.teachingProcess.map((row) => ({
        stage: '',
        duration: 5,
        teacherActivity: '',
        studentActivity: '',
        output: '',
        checkpoint: '',
        designPurpose: '',
        resources: '',
        evaluation: '',
        ...row,
      }))
    : []
  content.evaluationDesign = normalizeStructuredList(content.evaluationDesign, evaluationDesignFields)
  syncContentBasicInfo()
}

function normalizeReferenceMaterials(value) {
  if (!Array.isArray(value)) {
    return []
  }
  const deduplicated = new Map()
  value.forEach((item) => {
    if (!item || typeof item !== 'object') {
      return
    }
    const fileName = String(item.fileName || '').trim()
    if (!fileName) {
      return
    }
    const normalized = {
      id: String(item.id || createReferenceId()),
      fileName,
      fileType: String(item.fileType || detectFileType(fileName)).trim().toLowerCase(),
      charCount: Number(item.charCount || String(item.extractedText || '').length || String(item.excerpt || '').length || 0),
      excerpt: String(item.excerpt || '').trim(),
      extractedText: String(item.extractedText || '').trim(),
      role: normalizeReferenceRole(item.role),
      uploadedAt: String(item.uploadedAt || new Date().toISOString()).trim(),
      extractionMethod: String(item.extractionMethod || '').trim(),
      ocrStatus: String(item.ocrStatus || '').trim(),
      pageCount: item.pageCount == null ? null : Number(item.pageCount || 0),
      ocrConfidence: item.ocrConfidence == null ? null : Number(item.ocrConfidence || 0),
    }
    deduplicated.delete(fileName.toLowerCase())
    deduplicated.set(fileName.toLowerCase(), normalized)
  })
  const normalized = Array.from(deduplicated.values())
  if (!normalized.length) {
    return []
  }
  let primaryAssigned = false
  normalized.forEach((item, index) => {
    if (!primaryAssigned && item.role === 'primary') {
      primaryAssigned = true
      return
    }
    item.role = 'secondary'
    if (!primaryAssigned && index === 0) {
      item.role = 'primary'
      primaryAssigned = true
    }
  })
  if (!primaryAssigned) {
    normalized[0].role = 'primary'
  }
  return normalized
}

function buildReferenceMaterial(result, role = 'secondary') {
  const fileName = String(result?.fileName || '').trim()
  const extractedText = String(result?.extractedText || '').trim()
  const excerpt = String(result?.excerpt || '').trim()
  return {
    id: createReferenceId(),
    fileName,
    fileType: String(result?.fileType || detectFileType(fileName)).trim().toLowerCase(),
    charCount: Number(result?.charCount || extractedText.length || excerpt.length || 0),
    excerpt,
    extractedText,
    role: normalizeReferenceRole(role),
    uploadedAt: new Date().toISOString(),
    extractionMethod: String(result?.extractionMethod || '').trim(),
    ocrStatus: String(result?.ocrStatus || '').trim(),
    pageCount: result?.pageCount == null ? null : Number(result.pageCount || 0),
    ocrConfidence: result?.ocrConfidence == null ? null : Number(result.ocrConfidence || 0),
  }
}

function normalizeTeachingCalendar(value) {
  if (!value || typeof value !== 'object') {
    return null
  }
  const entries = Array.isArray(value.entries)
    ? value.entries
        .map((item) => ({
          week: String(item?.week || '').trim(),
          session: String(item?.session || '').trim(),
          periodCount: item?.periodCount == null || item?.periodCount === '' ? null : Number(item.periodCount || 0),
          lessonType: String(item?.lessonType || '').trim(),
          topic: String(item?.topic || '').trim(),
          rawText: String(item?.rawText || '').trim(),
        }))
        .filter((item) => item.topic || item.rawText)
    : []
  if (!entries.length && !String(value.fileName || '').trim()) {
    return null
  }
  return {
    fileName: String(value.fileName || '').trim(),
    fileType: String(value.fileType || 'xlsx').trim().toLowerCase(),
    rowCount: Number(value.rowCount || entries.length || 0),
    excerpt: String(value.excerpt || '').trim(),
    uploadedAt: String(value.uploadedAt || new Date().toISOString()).trim(),
    entries,
  }
}

function hasTeachingCalendar(value) {
  const normalized = normalizeTeachingCalendar(value)
  return Boolean(normalized?.entries?.length)
}

function openTeachingCalendarPreview(value) {
  const normalized = normalizeTeachingCalendar(value)
  if (!normalized?.entries?.length) {
    ElMessage.warning('当前教学日历没有可展示的授课安排')
    return
  }
  teachingCalendarPreviewData.value = normalized
  teachingCalendarPreviewVisible.value = true
}

function serializeTeachingCalendar(value) {
  return normalizeTeachingCalendar(value)
}

function replaceReferenceMaterialList(targetList, items) {
  const normalized = normalizeReferenceMaterials(items)
  targetList.splice(0, targetList.length, ...normalized)
}

function removeReferenceMaterial(targetList, fileName) {
  replaceReferenceMaterialList(
    targetList,
    targetList.filter((item) => String(item?.fileName || '').toLowerCase() !== String(fileName || '').toLowerCase()),
  )
}

function setPrimaryReferenceMaterial(targetList, fileName) {
  const normalized = normalizeReferenceMaterials(targetList).map((item) => ({
    ...item,
    role: String(item.fileName || '').toLowerCase() === String(fileName || '').toLowerCase() ? 'primary' : 'secondary',
  }))
  replaceReferenceMaterialList(targetList, normalized)
}

function serializeReferenceMaterials(value) {
  return normalizeReferenceMaterials(value).map((item) => ({ ...item }))
}

function normalizeReferenceRole(role) {
  return String(role || '').toLowerCase() === 'primary' ? 'primary' : 'secondary'
}

function createReferenceId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  return `ref-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function detectFileType(fileName) {
  const match = String(fileName || '').trim().toLowerCase().match(/\.([a-z0-9]+)$/)
  return match ? match[1] : 'txt'
}

function formatFileSize(size) {
  const value = Number(size || 0)
  if (value >= 1024 * 1024) {
    return `${(value / 1024 / 1024).toFixed(1)}MB`
  }
  if (value >= 1024) {
    return `${(value / 1024).toFixed(1)}KB`
  }
  return `${value}B`
}

function resolveUploadErrorMessage(error) {
  const responseMessage = error?.response?.data?.message
  if (responseMessage) {
    return responseMessage
  }
  if (error?.code === 'ECONNABORTED') {
    return '资料解析超时。文件大小不是唯一因素，扫描版 PDF 会逐页 OCR，页数较多或图片较复杂时会超过等待时间。请稍后重试，或先拆分 PDF 后再上传。'
  }
  if (error?.code === 'ERR_NETWORK' || /network|connection|reset/i.test(String(error?.message || ''))) {
    return '资料解析连接中断，请检查后端是否仍在运行，或确认文件没有超过 10MB'
  }
  return error?.message ? `资料解析失败：${error.message}` : '资料解析失败'
}

function resolveTeachingCalendarErrorMessage(error) {
  const responseMessage = error?.response?.data?.message
  if (responseMessage) {
    return responseMessage
  }
  if (error?.code === 'ECONNABORTED') {
    return '教学日历解析超时。请稍后重试，或检查表格中是否包含大量格式和合并单元格。'
  }
  if (error?.code === 'ERR_NETWORK' || /network|connection|reset/i.test(String(error?.message || ''))) {
    return '教学日历解析连接中断，请检查后端是否仍在运行'
  }
  return error?.message ? `教学日历解析失败：${error.message}` : '教学日历解析失败'
}

function referenceMaterialsContextSummary(materials) {
  const normalized = normalizeReferenceMaterials(materials)
  if (!normalized.length) {
    return '参考资料：无'
  }
  return [
    '参考资料摘要：',
    ...normalized.map((item, index) => {
      const excerpt = String(item.excerpt || item.extractedText || '').trim()
      return `${index + 1}. ${item.role === 'primary' ? '[主参考资料] ' : ''}${item.fileName}（${(item.fileType || 'file').toUpperCase()}，${item.charCount || 0}字）\n${excerpt}`
    }),
  ].join('\n')
}

function teachingCalendarContextSummary(calendar) {
  const normalized = normalizeTeachingCalendar(calendar)
  if (!normalized?.entries?.length) {
    return '教学日历：无'
  }
  return [
    '教学日历安排：',
    ...normalized.entries.slice(0, 40).map((row, index) => {
      const meta = [
        row.week,
        row.session,
        row.lessonType,
        row.periodCount ? `${row.periodCount}学时` : '',
      ].filter(Boolean).join(' ')
      return `${index + 1}. ${meta} ${row.topic || row.rawText}`.trim()
    }),
    '生成时必须优先匹配当前章节主题所在课次，只生成对应一次课的教案。',
  ].join('\n')
}

function normalizeTextList(value) {
  return Array.isArray(value) ? value.map((item) => stringifyListItem(item)).filter(Boolean) : []
}

function normalizeStructuredList(value, fields) {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => {
      if (item && typeof item === 'object' && !Array.isArray(item)) {
        const normalized = emptyStructuredItem(fields)
        fields.forEach((field) => {
          normalized[field.key] = String(item[field.key] || '').trim()
        })
        return normalized
      }
      const text = stringifyListItem(item)
      return text ? parseStructuredItem(text, fields) : null
    })
    .filter((item) => item && fields.some((field) => item[field.key]))
}

function stringifyListItem(item) {
  if (item == null) return ''
  if (typeof item !== 'object') return String(item).trim()
  const parts = []
  const preferredKeys = [
    'point',
    'reason',
    'strategy',
    'stage',
    'carrier',
    'integration',
    'problem',
    'assessment',
    'item',
    'weight',
    'evidence',
    'standard',
    'criterion',
    'excellent',
    'qualified',
    'method',
    'applicablePhase',
    'teacherOperation',
    'resource',
    'description',
    'design',
    'binding',
    'type',
    'title',
    'language',
    'purpose',
    'code',
    'submission',
    'feedback',
    'OBE_evidence',
    '错误表现',
    '教师干预办法',
  ]
  for (const key of preferredKeys) {
    if (item[key]) {
      parts.push(`${keyLabel(key)}：${String(item[key]).trim()}`)
    }
  }
  if (!parts.length) {
    for (const [key, value] of Object.entries(item)) {
      if (value !== null && value !== undefined && String(value).trim()) {
        parts.push(`${keyLabel(key)}：${String(value).trim()}`)
      }
    }
  }
  return parts.join('；')
}

function keyLabel(key) {
  const labels = {
    point: '难点',
    reason: '说明',
    strategy: '突破策略',
    stage: '融入环节',
    carrier: '融入载体',
    integration: '融入设计',
    problem: '学情问题',
    assessment: '评价证据',
    item: '评价项',
    weight: '权重',
    evidence: '评价证据',
    standard: '达标标准',
    criterion: '评价维度',
    excellent: '优秀标准',
    qualified: '达标标准',
    method: '方法',
    applicablePhase: '适用环节',
    teacherOperation: '教师操作',
    resource: '资源',
    description: '说明',
    design: '融入设计',
    binding: '融入环节',
    type: '类型',
    title: '标题',
    language: '语言',
    purpose: '用途说明',
    code: '代码',
    submission: '提交方式',
    feedback: '反馈方式',
    OBE_evidence: 'OBE证据',
  }
  return labels[key] || key
}

function syncEditorText() {
  ensureContentShape()
}

function applyEditorText() {
  ensureContentShape()
  Object.keys(currentContent.value.generationContext).forEach((key) => {
    currentContent.value.generationContext[key] = String(currentContent.value.generationContext[key] || '').trim()
  })
  currentContent.value.referenceMaterials = normalizeReferenceMaterials(currentContent.value.referenceMaterials)
  currentContent.value.teachingCalendar = normalizeTeachingCalendar(currentContent.value.teachingCalendar)
  trimList(currentContent.value.objectives.knowledge)
  trimList(currentContent.value.objectives.ability)
  trimList(currentContent.value.objectives.quality)
  trimList(currentContent.value.objectives.obeSupport)
  trimStructuredList(currentContent.value.keyPoints, keyPointFields)
  trimStructuredList(currentContent.value.difficultPoints, difficultPointFields)
  trimStructuredList(currentContent.value.ideologyDesign, ideologyFields)
  trimStructuredList(currentContent.value.evaluationDesign, evaluationDesignFields)
  trimStructuredList(currentContent.value.studentProblems, studentProblemFields)
  trimStructuredList(currentContent.value.codeExamples, codeExampleFields)
  trimStructuredList(currentContent.value.rubric, rubricFields)
  trimList(currentContent.value.practiceTask.basicTasks)
  trimList(currentContent.value.practiceTask.advancedTasks)
  trimList(currentContent.value.practiceTask.challengeTasks)
  trimList(currentContent.value.practiceTask.steps)
  trimList(currentContent.value.practiceTask.acceptanceCriteria)
  trimList(currentContent.value.practiceTask.commonErrors)
  trimList(currentContent.value.homework)
  syncContentBasicInfo()
}

function addProcessRow() {
  currentContent.value.teachingProcess.push({
    stage: '新增环节',
    duration: 5,
    teacherActivity: '',
    studentActivity: '',
    output: '',
    checkpoint: '',
    designPurpose: '',
    resources: '',
    evaluation: '',
  })
}

function addStructuredItem(list, fields) {
  list.push(emptyStructuredItem(fields))
}

function removeProcessRow(index) {
  currentContent.value.teachingProcess.splice(index, 1)
}

function addListItem(list) {
  list.push('')
}

function removeListItem(list, index) {
  list.splice(index, 1)
}

function trimList(list) {
  for (let index = list.length - 1; index >= 0; index--) {
    const value = String(list[index] || '').trim()
    if (!value) {
      list.splice(index, 1)
    } else {
      list[index] = value
    }
  }
}

function trimStructuredList(list, fields) {
  for (let index = list.length - 1; index >= 0; index--) {
    const item = list[index] || {}
    fields.forEach((field) => {
      item[field.key] = String(item[field.key] || '').trim()
    })
    if (!fields.some((field) => item[field.key])) {
      list.splice(index, 1)
    } else {
      list[index] = item
    }
  }
}

function lines(value) {
  return String(value || '').split('\n').map((item) => item.trim()).filter(Boolean)
}

function toLines(value) {
  return Array.isArray(value) ? value.join('\n') : ''
}

function textHasAny(value, words) {
  const text = String(value || '')
  return words.some((word) => text.includes(word))
}

function cleanAutoFilledText(value) {
  return String(value || '')
    .replace(/^#+\s*/gm, '')
    .replace(/^(学情描述|教学资源|实验环境|其他要求)[:：]\s*/gm, '')
    .trim()
}

function buildTargetStudents(major, grade) {
  const selectedMajor = major || '人工智能'
  const selectedGrade = grade || '大一'
  return `${selectedMajor} ${selectedGrade}学生，约45人`
}

function handleShortcut(event) {
  if (!event.ctrlKey) return
  const key = String(event.key || '').toLowerCase()
  if (key === 's') {
    event.preventDefault()
    if (currentPlan.value) {
      handleSave()
    } else {
      handleCreate()
    }
  }
  if (key === 'e') {
    event.preventDefault()
    activeTab.value = 'preview'
    scrollToEditor()
  }
  if (key === 'd') {
    event.preventDefault()
    if (currentPlan.value) {
      handleExport(currentPlan.value)
    }
  }
}

function startsWithVagueVerb(value) {
  return ['掌握', '理解', '了解', '熟悉', '具备'].some((word) => String(value || '').trim().startsWith(word))
}

function errorMessage(error) {
  return error.response?.data?.message || error.message || '操作失败'
}

async function showErrorDialog(title, message) {
  try {
    await ElMessageBox.alert(message, title, {
      type: 'error',
      confirmButtonText: '知道了',
      customClass: 'wide-message-box',
    })
  } catch (ignored) {
    // 用户关闭弹窗即可。
  }
}

function formatDuration(value) {
  if (!value && value !== 0) return '-'
  if (value < 1000) return `${value}ms`
  return `${(value / 1000).toFixed(1)}s`
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function emptyContent() {
  return {
    basicInfo: {},
    generationContext: {
      prerequisiteKnowledge: '',
      commonMisconceptions: '',
      classLevelProfile: '',
      lessonFocus: '',
      expectedOutputs: '',
    },
    referenceMaterials: [],
    teachingCalendar: null,
    studentAnalysis: '',
    objectives: { knowledge: [], ability: [], quality: [], obeSupport: [] },
    keyPoints: [],
    difficultPoints: [],
    teachingMethods: [],
    resources: [],
    ideologyDesign: [],
    evaluationDesign: [],
    studentProblems: [],
    codeExamples: [],
    rubric: [],
    teachingProcess: [],
    practiceTask: { taskName: '', scenario: '', basicTasks: [], advancedTasks: [], challengeTasks: [], steps: [], acceptanceCriteria: [], commonErrors: [] },
    homework: [],
    reflection: '',
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleShortcut)
  window.addEventListener('nsu-auth-expired', () => {
    authChecking.value = false
    user.value = null
    currentPlan.value = null
    generationRecords.value = []
    ElMessage.warning('登录已失效，请重新登录')
  })
  if (!localStorage.getItem('nsu_maic_token')) {
    authChecking.value = false
    return
  }
  try {
    user.value = await getCurrentUser()
    await afterLogin()
  } catch (error) {
    localStorage.removeItem('nsu_maic_token')
    user.value = null
  } finally {
    authChecking.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleShortcut)
})
</script>

<style scoped>
.ai-auto-fill-button {
  font-weight: 800;
}

.upload-zone {
  border-radius: 8px;
}

.teaching-calendar-panel {
  margin-top: 18px;
}

.teaching-calendar-card {
  margin-top: 14px;
  padding: 18px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fbff 0%, #f7fff9 100%);
}

.teaching-calendar-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.teaching-calendar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.teaching-calendar-title-block {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.teaching-calendar-head strong {
  color: #102033;
  font-size: 15px;
  line-height: 1.5;
}

.teaching-calendar-count {
  color: #64748b;
  font-size: 13px;
  white-space: nowrap;
}

.teaching-calendar-table {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.teaching-calendar-row {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e5eefc;
  border-radius: 8px;
  background: #fff;
}

.teaching-calendar-row span {
  color: #64748b;
}

.teaching-calendar-row strong {
  color: #102033;
  font-weight: 700;
}

.teaching-calendar-hint {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 13px;
}

.teaching-calendar-preview-dialog {
  display: grid;
  gap: 16px;
}

.teaching-calendar-preview-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  color: #475569;
}

.teaching-calendar-preview-meta strong {
  color: #102033;
  font-size: 16px;
}

.teaching-calendar-preview-table {
  width: 100%;
}

.teaching-calendar-preview-table th:first-child,
.teaching-calendar-preview-table td:first-child {
  width: 90px;
}
</style>
