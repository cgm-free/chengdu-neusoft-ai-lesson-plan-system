<template>
  <section v-if="plan" class="preview-shell">
    <div class="preview-topbar">
      <div>
        <h2>{{ previewTitle }}</h2>
      </div>
      <div class="topbar-actions">
        <el-button @click="emit('back')">返回修改材料</el-button>
        <el-button :loading="saving" @click="emit('save')">保存</el-button>
        <el-button type="primary" plain @click="emit('export-word')">导出 Word</el-button>
        <el-button type="primary" @click="emit('export-pdf')">导出 PDF</el-button>
      </div>
    </div>

    <div class="pdf-preview-card">
      <div v-if="previewLoading" class="pdf-preview-state">
        <el-skeleton :rows="8" animated />
      </div>
      <el-alert
        v-else-if="previewError"
        type="error"
        :title="previewError"
        show-icon
        :closable="false"
      />
      <iframe
        v-else-if="previewUrl"
        class="pdf-preview-frame"
        :src="previewUrl"
        title="课程教案 PDF 预览"
      />
      <el-empty v-else description="生成后将显示与导出文件一致的 PDF 预览" />
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { fetchCoursePlanPreviewPdf } from '../../api/http'

const props = defineProps({
  plan: { type: Object, default: null },
  saving: { type: Boolean, default: false },
})

const emit = defineEmits(['back', 'save', 'export-word', 'export-pdf'])

const previewUrl = ref('')
const previewLoading = ref(false)
const previewError = ref('')
const previewTitle = computed(() => `${props.plan?.content?.basicInfo?.courseName || props.plan?.courseName || '课程教案'}课程教案预览`)

watch(
  () => props.plan?.id,
  async (id) => {
    revokePreviewUrl()
    previewError.value = ''
    if (!id) return
    previewLoading.value = true
    try {
      const blob = await fetchCoursePlanPreviewPdf(id)
      previewUrl.value = URL.createObjectURL(blob)
    } catch (error) {
      previewError.value = await resolvePreviewError(error)
    } finally {
      previewLoading.value = false
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  revokePreviewUrl()
})

function revokePreviewUrl() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

async function resolvePreviewError(error) {
  const data = error?.response?.data
  if (data instanceof Blob) {
    const text = await data.text()
    if (text) {
      try {
        const payload = JSON.parse(text)
        return payload.message || 'PDF 预览加载失败'
      } catch {
        return text
      }
    }
  }
  return error?.response?.data?.message || error?.message || 'PDF 预览加载失败'
}
</script>

<style scoped>
.preview-shell {
  display: grid;
  gap: 20px;
}

.preview-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.preview-topbar h2 {
  margin: 0;
  color: #102a43;
}

.topbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
}

.pdf-preview-card {
  min-height: 78vh;
  padding: 12px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #d9e2ec;
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.06);
}

.pdf-preview-state {
  padding: 24px;
}

.pdf-preview-frame {
  width: 100%;
  min-height: 76vh;
  border: 0;
  border-radius: 14px;
  background: #f8fafc;
}

@media (max-width: 960px) {
  .preview-topbar,
  .topbar-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
