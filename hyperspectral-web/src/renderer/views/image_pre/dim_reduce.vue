<template>
  <div class="pca-container img-denoising-page">
    <!-- 顶部选择按钮和主成分输入 -->
    <div class="toolbar">
      <!-- <el-form ref="form" :model="form" label-width="80px">
       
        <el-form-item label="活动形式">
          <el-input type="textarea" v-model="form.desc"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSubmit">立即创建</el-button>
          <el-button>取消</el-button>
        </el-form-item>
      </el-form> -->




      <el-button type="primary" @click="onSelectClick">选择文件</el-button>
      <p></p> 
      <el-input-number
        v-model="components"
        :min="1"
        :max="50"
        label="主成分数量"
      />
      <input
        ref="fileInput"
        type="file"
        accept=".mat"
        style="display: none"
        @change="onFileChange"
      />
    </div>

    <!-- 文件预览区 -->
    <div class="images-container">
      <div class="image-panel">
        <h3>原始预览</h3>
        <div class="image-wrapper">
          <img v-if="originalPreviewUrl" :src="originalPreviewUrl" />
          <div v-else class="placeholder">未选择文件</div>
        </div>
      </div>
      <div class="image-panel">
        <h3>降维结果</h3>
        <div class="image-wrapper">
          <img v-if="resultImage" :src="resultImage" />
          <div v-else class="placeholder">等待处理</div>
        </div>
        
        <!-- 结果信息展示 -->
        <div v-if="resultMat" class="result-info">
          <div class="path">
            MAT文件路径：<code>{{ resultMat }}</code>
          </div>
          <div class="download">
            <el-button 
              type="success" 
              @click="downloadResult"
            >
              下载结果
            </el-button>
          </div>
        </div>
        
        <div v-if="explainedVariance.length" class="variance-list">
          <h4>主成分贡献率</h4>
          <div class="variance-item" v-for="(v, i) in explainedVariance" :key="i">
            <span>PC{{ i + 1 }}: </span>
            <el-progress 
              :percentage="(v * 100).toFixed(2)" 
              :show-text="false"
              :stroke-width="14"
            />
            <span class="percentage">{{ (v * 100).toFixed(2) }}%</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部按钮 -->
    <div class="actions">
      <el-button
        type="success"
        :disabled="!file || !components || loading"
        :loading="loading"
        @click="submit"
      >
        {{ loading ? '处理中...' : '开始降维' }}
      </el-button>
      <el-button type="warning" @click="reset">重新选择</el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PcaProcessor',
  data() {
    return {
      file: null,
      components: 3,
      loading: false,
      originalPreviewUrl: '',
      resultImage: '',
      resultMat: '',
      explainedVariance: [],
    };
  },
  methods: {
    onSelectClick() {
      this.$refs.fileInput.click();
    },
    async onFileChange(e) {
      const file = e.target.files[0];
      if (!file) return;
      
      this.resetAll();
      this.file = file;
      
      // 生成原始预览
      try {
        const form = new FormData();
        form.append('file', file);
        
        const resp = await fetch('http://127.0.0.1:8000/preview', {
          method: 'POST',
          body: form
        });
        
        if (!resp.ok) throw new Error('预览失败');
        const data = await resp.json();
        this.originalPreviewUrl = data.preview_url;
      } catch (err) {
        this.$message.error('预览生成失败: ' + err.message);
      }
    },
    async submit() {
      if (!this.file || this.components < 1) {
        this.$message.warning('请选择文件并输入有效的主成分数量');
        return;
      }

      this.loading = true;
      // 只重置处理结果，保留原始预览
      this.resetProcessingResults();

      const formData = new FormData();
      formData.append("file", this.file);
      formData.append("components", this.components);

      try {
        const res = await fetch("http://127.0.0.1:8000/pca", {
          method: "POST",
          body: formData
        });

        if (!res.ok) {
          const error = await res.json();
          throw new Error(error.detail || '处理失败');
        }

        const data = await res.json();
        this.resultImage = data.file_url;
        this.resultMat = data.mat_path;
        this.explainedVariance = data.explained_variance;
        
        this.$message.success('降维处理完成');
      } catch (err) {
        this.$message.error('处理失败: ' + err.message);
      } finally {
        this.loading = false;
      }
    },
    reset() {
      this.file = null;
      this.components = 3;
      this.resetAll();
      if (this.$refs.fileInput) {
        this.$refs.fileInput.value = null;
      }
    },
    // 重置所有数据（包括原始预览）
    resetAll() {
      this.originalPreviewUrl = '';
      this.resultImage = '';
      this.resultMat = '';
      this.explainedVariance = [];
    },
    // 只重置处理结果（保留原始预览）
    resetProcessingResults() {
      this.resultImage = '';
      this.resultMat = '';
      this.explainedVariance = [];
    },
    downloadResult() {
      if (!this.resultMat) return;
      // 从完整路径中提取文件名
      const filename = this.resultMat.split('/').pop();
      
      const link = document.createElement('a');
      link.href = `http://127.0.0.1:8000/static/pca_mats/${filename}`;
      link.download = `pca_result_${this.components}pc.mat`;
      link.click();
    }
  }
};
</script>

<style scoped>
.pca-container {
  padding: 20px;
  /* max-width: 1400px; */
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  /* min-height: 100vh; */

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
  gap: 12px;
  align-items: center;
  margin-bottom: 24px;
}

.images-container {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-bottom: 24px;
 
}

.image-panel {
  flex: 1;
  min-width: 500px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  padding: 16px;
  display: flex;
  flex-direction: column;
   margin: 24px;
}

.image-panel h3 {
  margin-bottom: 12px;
  font-size: 1.2rem;
  color: #333;
  border-bottom: 1px solid #eee;
  padding-bottom: 8px;
}

.image-wrapper {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background-color: #f5f5f5;
  overflow: hidden;
  border: 1px solid #ddd;
  border-radius: 4px;
  margin-bottom: 16px;
  
}

.image-wrapper img {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 95%;
  height: 100%;
  object-fit: contain;
  transform: translate(-50%, -50%);
}

.placeholder {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 1rem;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  /* margin-top: auto; */
  padding-top: 24px;
}

.result-info {
  margin-top: auto;
  padding: 12px;
  background: #f9f9f9;
  border-radius: 4px;
}

.path {
  margin-bottom: 12px;
  word-break: break-all;
  font-size: 0.9rem;
}

.download {
  text-align: center;
}

.variance-list {
  margin-top: 20px;
}

.variance-list h4 {
  margin-bottom: 10px;
  font-size: 1rem;
  color: #666;
}

.variance-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.variance-item span {
  width: 60px;
  font-size: 0.9rem;
}

.variance-item .el-progress {
  flex: 1;
  margin: 0 10px;
}

.percentage {
  width: 70px;
  text-align: right;
  font-size: 0.9rem;
  color: #409EFF;
}
</style>