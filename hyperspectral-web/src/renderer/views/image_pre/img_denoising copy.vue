<template>
  <div class="img-denoising-page">
    <!-- 顶部选择按钮和算法下拉 -->
    <div class="toolbar">
      <el-button type="primary" @click="onSelectClick">选择文件</el-button>
      <el-select v-model="selectedAlgo" placeholder="选择去噪算法">
        <el-option
          v-for="algo in algos"
          :key="algo.value"
          :label="algo.label"
          :value="algo.value"
        />
      </el-select>
      <input
        ref="fileInput"
        type="file"
        accept="image/*,.mat"
        style="display: none"
        @change="onFileChange"
      />
    </div>

    <!-- 文件预览区 -->
    <div class="images">
      <div class="image-panel">
        <h3>原始预览</h3>
        <div class="image-wrapper">
          <img v-if="originalPreviewUrl" :src="originalPreviewUrl" />
        </div>
      </div>
      <div class="image-panel">
        <h3>去噪后</h3>
        <div class="image-wrapper">
          <img v-if="denoisedImageUrl" :src="denoisedImageUrl" />
        </div>
        <div v-if="denoisedFilePath" class="path">
          存放路径：<code>{{ denoisedFilePath }}</code>
        </div>
      </div>
    </div>

    <!-- 底部按钮 -->
    <div class="actions">
      <el-button
        type="success"
        :disabled="!fileToProcess || !selectedAlgo || isProcessing"
        @click="startDenoise"
      >
        开始去噪
      </el-button>
      <el-button type="warning" @click="reset">重新选择</el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ImageDenoising',
  data() {
    return {
      fileToProcess: null,        // 原始文件 (.png/.jpg/.mat)
      originalPreviewUrl: '',     // 原始预览 URL
      denoisedImageUrl: '',
      denoisedFilePath: '',
      isProcessing: false,
      selectedAlgo: '',
      algos: [
        { label: '均值+中值滤波', value: 'mean_median' },
        { label: '高斯滤波', value: 'gaussian' },
        { label: '双边滤波', value: 'bilateral' }
      ]
    }
  },
  methods: {
    onSelectClick() {
      this.$refs.fileInput.click();
    },
    async onFileChange(e) {
      const file = e.target.files[0];
      if (!file) return;
      this.resetPreview();
      this.fileToProcess = file;
      const ext = file.name.split('.').pop().toLowerCase();
      if (ext === 'mat') {
        // 对 .mat 文件调用预览接口
        const form = new FormData();
        form.append('file', file);
        try {
          const resp = await fetch('http://127.0.0.1:8000/preview', { method: 'POST', body: form });
          if (!resp.ok) throw new Error('预览接口返回错误');
          const j = await resp.json();
          this.originalPreviewUrl = j.preview_url;
          this.denoisedImageUrl = '';
          this.denoisedFilePath = '';
        } catch (err) {
          this.$message.error('预览失败：' + err.message);
        }
      } else {
        // 普通图片直接本地预览
        this.originalPreviewUrl = URL.createObjectURL(file);
      }
    },
    reset() {
      this.fileToProcess = null;
      this.resetPreview();
      this.selectedAlgo = '';
      this.isProcessing = false;
      this.$refs.fileInput.value = null;
    },
    resetPreview() {
      this.originalPreviewUrl = '';
      this.denoisedImageUrl = '';
      this.denoisedFilePath = '';
    },
    async startDenoise() {
      if (!this.fileToProcess || !this.selectedAlgo) return;
      this.isProcessing = true;
      const form = new FormData();
      form.append('file', this.fileToProcess);
      form.append('algo', this.selectedAlgo);
      try {
        const resp = await fetch('http://127.0.0.1:8000/denoise', { method: 'POST', body: form });
        if (!resp.ok) throw new Error('后端返回错误');
        const j = await resp.json();
        this.denoisedFilePath = j.mat_path;
        this.denoisedImageUrl = j.file_url;
      } catch (e) {
        this.$message.error('去噪失败：' + e.message);
      } finally {
        this.isProcessing = false;
      }
    }
  }
}
</script>

<style scoped>
.img-denoising-page {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 24px;
}
.images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 24px;
}
.image-panel h3 {
  margin-bottom: 12px;
  font-size: 1.2rem;
}
.image-wrapper {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background-color: #f5f5f5;
  overflow: hidden;
  border: 1px solid #ddd;
}
.image-wrapper img {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100%;
  height: 100%;
  object-fit: contain;
  transform: translate(-50%, -50%);
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  margin-top: 24px;
}
.path {
  margin-top: 12px;
  word-break: break-all;
}
</style>
