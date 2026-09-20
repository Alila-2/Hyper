<template>
  <div class="img-denoising-page">
    <!-- 顶部选择文件 -->
    <div class="toolbar">
      <el-button type="primary" @click="onSelectClick">选择文件</el-button>
      <input
        ref="fileInput"
        type="file"
        accept="image/*,.mat"
        style="display: none"
        @change="onFileChange"
      />
    </div>

    <!-- 算法选择 -->
    <div class="algo-selection">
      <h3>算法选择</h3>
      <el-radio-group v-model="selectedAlgo" class="algo-radio-group">
        <el-radio
          v-for="algo in algos"
          :key="algo.value"
          :label="algo.value"
        >
          {{ algo.label }}
        </el-radio>
      </el-radio-group>
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
      fileToProcess: null,
      originalPreviewUrl: '',
      denoisedImageUrl: '',
      denoisedFilePath: '',
      isProcessing: false,
      selectedAlgo: '',
      algos: [
        { label: '均值滤波', value: 'mean' },
        { label: '中值滤波', value: 'median' },
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
        const form = new FormData();
        form.append('file', file);
        try {
          const resp = await fetch('http://127.0.0.1:8000/preview', { method: 'POST', body: form });
          if (!resp.ok) throw new Error('预览接口返回错误');
          const j = await resp.json();
          this.originalPreviewUrl = j.preview_url;
        } catch (err) {
          this.$message.error('预览失败：' + err.message);
        }
      } else {
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
        this.denoisedFilePath = j.mat_path || j.file_path;
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
  background-image: url('../../assets/bg.jpg');  
  background-size: cover;       /* 图片覆盖整个容器 */  
  background-position: center;  /* 图片居中显示 */  
  background-repeat: no-repeat; /* 不重复平铺 */  
  background-attachment: fixed; /* 背景固定，不随滚动移动 */  
  font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;  
  min-height: 100vh; /* 确保背景至少覆盖整个视口高度 */
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  margin-bottom: 12px;
}
.algo-selection {
  margin-bottom: 24px;
}
.algo-selection h3 {
  margin-bottom: 8px;
  font-size: 1.1rem;
}
.algo-radio-group {
  display: flex;
  gap: 16px;
}
.images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(450px, 1fr));
  gap: 40px;
  margin-bottom: 30px;
}
.image-panel h3 {
  margin-bottom: 12px;
  font-size: 1.2rem;
}
.image-wrapper {
  position: relative;
  width: 95%;
  padding-top: 56.25%;
  background-color: #fafafa;
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
  gap: 16px;
  justify-content: center;
  margin-top: 24px;
}
.path {
  margin-top: 12px;
  word-break: break-all;
}
</style>
