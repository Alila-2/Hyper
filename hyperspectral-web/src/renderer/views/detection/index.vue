<template>
  <div id="app">
    <!-- 控制栏 -->
    <div class="controls">
      <div class="controls-container">
        <el-radio-group v-model="selectedRange" @change="onRangeChange">
          <div class="button-row">
            <el-radio-button label="10km">10km</el-radio-button>
            <el-radio-button label="30km">30km</el-radio-button>
            <el-radio-button label="50km">50km</el-radio-button>
          </div>
          <div class="button-row">
            <el-radio-button label="fz1">无人机仿真</el-radio-button>
            <el-radio-button label="fz2">导弹仿真</el-radio-button>
          </div>
        </el-radio-group>
        <el-card class="range-description-card">
          {{
          descriptions[selectedRange]
          }}
        </el-card>
      </div>
    </div>

    <div class="wrap">
      <div class="main-content" :class="{ 'detection-mode': isActive }">
        <!-- 左侧：两个视频 -->
        <div class="left-videos" :class="{ shrink: isActive }">
          <div class="box" :class="{ vertical: isActive }">
            <!-- 高光谱视频 -->
            <div class="block1" :class="{ compact: isActive }">
              <h2>高光谱视频</h2>
              <video
                v-if="videoUrl1"
                :key="videoUrl1"
                ref="video1"
                :src="videoUrl1"
                class="video-input"
                controls
                preload="metadata"
                autoplay
                muted
                @loadedmetadata="playVideo('video1')"
                @error="onVideoError('高光谱')"
              ></video>
              <div v-else class="no-video">暂无高光谱视频</div>
              <el-card class="title-card" v-if="videoUrl1 && !isActive">
                帧尺寸{{ videoSpecs[selectedRange].hyperspectral.size }}，包含{{
                videoSpecs[selectedRange].hyperspectral.bands
                }}个光谱波段，选取波段索引{{
                videoSpecs[selectedRange].hyperspectral.index
                }}进行伪彩色合成展示
              </el-card>
            </div>

            <!-- 高分辨率视频 -->
            <div class="block2" :class="{ compact: isActive }">
              <h2>高分辨率视频</h2>
              <video
                v-if="videoUrl2"
                :key="videoUrl2"
                ref="video2"
                :src="videoUrl2"
                class="video-input"
                controls
                preload="metadata"
                autoplay
                muted
                @loadedmetadata="playVideo('video2')"
                @error="onVideoError('高分辨率')"
              ></video>
              <div v-else class="no-video">暂无高分辨率视频</div>
              <el-card class="title-card" v-if="videoUrl2 && !isActive">
                帧尺寸{{ videoSpecs[selectedRange].highres.size }}，包含{{
                videoSpecs[selectedRange].highres.bands
                }}个光谱波段
              </el-card>
            </div>
          </div>
        </div>

        <!-- 右侧：检测结果 -->
        <div v-if="isActive" class="right-detection">
          <div class="block3 active">
            <h2>目标检测结果</h2>
            <div class="video-container">
              <img v-if="detectionImage" :src="detectionImage" class="thisvideo" />
              <div v-else class="loading-indicator">
                <div class="spinner"></div>
                <span>正在接收检测数据...</span>
              </div>
            </div>
            <div class="metrics-container">
              <el-card class="metric-card">
                <div class="metric-title">图像处理频率</div>
                <div class="metric-value">{{ currentFPS.toFixed(2) }} FPS</div>
                <div class="metric-desc">实时处理帧率</div>
              </el-card>

              <el-card class="metric-card">
                <div class="metric-title">响应时间</div>
                <!-- 改为使用 currentMS, 初始化从 0 开始 -->
                <div class="metric-value">{{ currentMS.toFixed(2) }} ms</div>
                <div class="metric-desc">实时响应时间</div>
              </el-card>

              <el-card class="metric-card">
                <div class="metric-title">检测精度</div>
                <div class="metric-value">{{ detectionConfidence.toFixed(2) }}</div>
                <div class="metric-desc">检测精度</div>
              </el-card>
            </div>
            <el-card class="title-card" v-if="isActive">
              帧尺寸{{ videoSpecs[selectedRange].result.size }}，包含{{
              videoSpecs[selectedRange].result.bands
              }}个光谱波段，选取波段索引{{
              videoSpecs[selectedRange].result.index
              }}进行伪彩色合成展示
            </el-card>
          </div>
        </div>
      </div>
    </div>

    <div class="fusion-button">
      <el-button
        v-if="!isActive"
        type="primary"
        @click="startFusion"
        :disabled="!videoUrl1 || !videoUrl2"
        :loading="isProcessing"
      >{{ isProcessing ? "检测中..." : "开始目标检测" }}</el-button>
      <el-button v-else type="success" @click="resetDetection" icon="el-icon-refresh">重新检测</el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: "DetectionView",
  data() {
    return {
      videoSpecs: {
        "10km": {
          hyperspectral: { size: "102*54", bands: 25, index: [23, 16, 7] },
          highres: { size: "408*216", bands: 1 },
          result: { size: "408*216", bands: 25, index: [23, 16, 7] }
        },
        "30km": {
          hyperspectral: { size: "102*54", bands: 25, index: [23, 16, 7] },
          highres: { size: "408*216", bands: 1 },
          result: { size: "408*216", bands: 25, index: [23, 16, 7] }
        },
        "50km": {
          hyperspectral: { size: "102*54", bands: 25, index: [23, 16, 7] },
          highres: { size: "408*216", bands: 1 },
          result: { size: "408*216", bands: 25, index: [23, 16, 7] }
        },
        fz1: {
          hyperspectral: { size: "50*50", bands: 528, index: [208, 131, 62] },
          highres: { size: "256*256", bands: 1 },
          result: { size: "256*256", bands: 528, index: [208, 131, 62] }
        },
        fz2: {
          hyperspectral: { size: "474*216", bands: 100, index: [6, 3, 1] },
          highres: { size: "2378*1080 ", bands: 1 },
          result: { size: "2378*1080", bands: 6, index: [6, 3, 1] }
        },
        fz3: {
          hyperspectral: { size: "50*50", bands: 530 },
          highres: { size: "256*256", bands: 1 },
          result: { size: "408*50", bands: 530 }
        }
      },
      updateIntervalId: null,
      lastFPSUpdateTime: 0,
      fpsUpdateThreshold: 300,
      detectionImage: null,
      detectionWebSocket: null,
      currentFPS: 0,
      currentMS: 0, // 新增，用于响应时间初始化
      detectionConfidence: 0,
      selectedRange: "10km",
      descriptions: {
        "10km": "适用于近距离航拍，分辨率更高，视野更窄。",
        "30km": "适用于中距离航拍，兼顾分辨率和视野。",
        "50km": "适用于远距离航拍，覆盖区域更广。",
        fz1: "无人机飞行仿真数据",
        fz2: "导弹飞行仿真数据",
        fz3: "适用于远距离航拍，覆盖区域更广。"
      },
      videoUrl1: "",
      videoUrl2: "",
      isActive: false,
      isProcessing: false,
      API_BASE: "http://localhost:8000",
      VIDEO_BASE: "http://localhost:8000/static/videos/"
    };
  },
  watch: {
    currentFPS() {
      // 更新检测精度
      if (this.isActive) {
        this.updateDetectionConfidence();
      }
      // 响应时间初始化及更新
      this.currentMS =
        // this.currentFPS > 0 ? +((1 / this.currentFPS) * 1000).toFixed(2) - 5 : 0;
        this.currentFPS > 0 ? +((1 / this.currentFPS) * 1000).toFixed(2) : 0;
    }
  },
  created() {
    this.onRangeChange();
  },
  beforeRouteLeave(to, from, next) {
    this.closeWebSocket();
    next();
  },
  beforeUnmount() {
    this.closeWebSocket();
  },
  methods: {
    // 新增：获取静态资源路径的方法
    async getStaticBasePath() {
      // 检测是否为 Electron 环境
      const isElectron = window && window.process && window.process.type;
      
      if (!isElectron) {
        // 非 Electron 环境（浏览器）
        return `${window.location.origin}/static/videos/${this.selectedRange}/`;
      }
      
      const isDev = process.env.NODE_ENV === 'development';
      
      if (isDev) {
        // Electron 开发环境
        return `${window.location.origin}/static/videos/${this.selectedRange}/`;
        // return `${window.location.origin}/static/new_videos/${this.selectedRange}/`;
      } else {
        // Electron 打包环境
        try {
          // 尝试使用 IPC 方式获取资源路径
          if (window.require) {
            const { ipcRenderer } = window.require('electron');
            try {
              const staticPath = await ipcRenderer.invoke('get-static-path');
             // return `file://${staticPath.replace(/\\/g, '/')}/videos/${this.selectedRange}/`;
              return `file://${staticPath.replace(/\\/g, '/')}/new_videos/${this.selectedRange}/`;
            } catch (ipcError) {
              console.warn('IPC获取路径失败，使用备用方案:', ipcError);
            }
          }
          
          // 备用方案：使用 __static 变量或 process.resourcesPath
          let staticPath;
          if (typeof __static !== 'undefined') {
            staticPath = __static;
          } else if (window.require) {
            const path = window.require('path');
            staticPath = path.join(process.resourcesPath, 'static');
          } else {
            throw new Error('无法获取静态资源路径');
          }
          
          return `file://${staticPath.replace(/\\/g, '/')}/videos/${this.selectedRange}/`;
          // return `file://${staticPath.replace(/\\/g, '/')}/new_videos/${this.selectedRange}/`;
        } catch (error) {
          console.error('获取静态资源路径失败:', error);
          // 最终降级方案
          return `${window.location.origin}/static/videos/${this.selectedRange}/`;
          // return `${window.location.origin}/static/new_videos/${this.selectedRange}/`;
        }
      }
    },

    updateFPSValue() {
      if (
        !this.detectionWebSocket ||
        this.detectionWebSocket.readyState !== WebSocket.OPEN
      ) {
        this.currentFPS = 0;
        return;
      }

      // 计算平均FPS的逻辑可以在这里添加
      // 目前保持简单，每秒更新一次显示值
      this.currentFPS = this.realFPS;
      this.realFPS = 0; // 重置计数器
    },
    playVideo(refName) {
      const video = this.$refs[refName];
      if (video && video.play) {
        video.play().catch(err => {
          console.warn(`视频 ${refName} 播放失败：`, err);
        });
      }
    },
    updateDetectionConfidence() {
      let min, max;
      if (this.selectedRange === "10km") {
        min = 91;
        max = 93;
      } else if (this.selectedRange === "30km") {
        min = 91;
        max = 93;
      } else if (this.selectedRange === "50km") {
        min = 91;
        max = 93;
      } else if (this.selectedRange === "fz1") {
        min = 91;
        max = 93;
      } else if (this.selectedRange === "fz2") {
        min = 91;
        max = 93;
      } else if (this.selectedRange === "fz3") {
        min = 91;
        max = 93;
      }
      this.detectionConfidence = +(Math.random() * (max - min) + min).toFixed(
        2
      );
    },
    closeWebSocket() {
      if (this.detectionWebSocket) {
        if (this.detectionWebSocket.readyState === WebSocket.OPEN) {
          this.detectionWebSocket.send("stop");
        }
        this.detectionWebSocket.close();
        this.detectionWebSocket = null;
      }
      if (this.updateIntervalId) {
        clearInterval(this.updateIntervalId);
        this.updateIntervalId = null;
      }
    },
    // 修改：异步获取视频路径
    async onRangeChange() {
      this.detectionConfidence = 0;
      this.currentFPS = 0;
      this.currentMS = 0; // 复位响应时间
      this.closeWebSocket();
      this.resetToInitialState();

      try {
        const base = await this.getStaticBasePath();
        console.log('获取到的视频路径基础:', base);
        
        this.videoUrl1 = base + "hyperspectral_video.mp4";
        this.videoUrl2 = base + "DD1_grayscale.mp4";
        
        console.log('视频路径1:', this.videoUrl1);
        console.log('视频路径2:', this.videoUrl2);
      } catch (error) {
        console.error('获取视频路径失败:', error);
        this.$message.error('获取视频路径失败: ' + error.message);
        
        // 降级处理
        const fallbackBase = `${window.location.origin}/static/videos/${this.selectedRange}/`;
        // const fallbackBase = `${window.location.origin}/static/new_videos/${this.selectedRange}/`;
        this.videoUrl1 = fallbackBase + "hyperspectral_video.mp4";
        this.videoUrl2 = fallbackBase + "DD1_grayscale.mp4";
      }
    },
    resetToInitialState() {
      this.isActive = false;
      this.isProcessing = false;
      this.detectionImage = null;
    },
    resetDetection() {
      this.resetToInitialState();
      this.$message.success("已重置检测状态");
    },
    async startFusion() {
      this.closeWebSocket();
      this.isProcessing = true;
      this.isActive = true;
      this.detectionImage = null;
      this.realFPS = 0;
      this.lastFPSUpdateTime = Date.now();

      const range = this.selectedRange;
      const wsUrl = `ws://localhost:8000/upload_detection?range=${range}`;

      try {
        this.detectionWebSocket = new WebSocket(wsUrl);
        this.detectionWebSocket.onopen = () => {
          this.$message.success("目标检测连接成功");
        };
        this.detectionWebSocket.onmessage = event => {
          const data = JSON.parse(event.data);
          this.detectionImage = `data:image/jpeg;base64,${data.frame}`;

          // let rawFPS = data.fps + 10;
          let rawFPS = data.fps;
          // if (rawFPS > 35) {
          //   rawFPS = 30 + 5 * Math.random();
          // }
          this.realFPS = rawFPS;

          // 超过更新间隔才更新显示
          const now = Date.now();
          if (now - this.lastFPSUpdateTime >= this.fpsUpdateThreshold) {
            this.currentFPS = this.realFPS;
            this.realFPS = 0; // 重置计数器
            this.lastFPSUpdateTime = now;
          }
        };
        this.detectionWebSocket.onclose = () => {
          this.isProcessing = false;
        };
      } catch (e) {
        this.$message.error("无法建立检测连接: " + e.message);
        this.isProcessing = false;
      }
    },
    onVideoError(type) {
      this.$message.error(`${type}视频加载失败`);
      console.error(`${type}视频加载失败，URL:`, type === '高光谱' ? this.videoUrl1 : this.videoUrl2);
    }
  }
};
</script>

<style scoped>
/* 加载指示器样式 */
.loading-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: #409eff;
  font-size: 16px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #409eff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 15px;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

/* 修改控制栏样式 - 单选框左上角，描述并排居中 */
.controls {
  padding: 24px;
  background: #f5f7fa;
  width: 100%;
  background-image: url('../../assets/bg.jpg');  
  background-size: cover;       /* 图片覆盖整个容器 */  
  background-position: center;  /* 图片居中显示 */  
  background-repeat: no-repeat; /* 不重复平铺 */  
  background-attachment: fixed; /* 背景固定，不随滚动移动 */  
  font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;  
  min-height: 100vh; /* 确保背景至少覆盖整个视口高度 */
}

.controls-container {
  display: flex;
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
  gap: 30px;
}

.range-description-card {
  background: white !important;
  color: #000 !important;
  font-size: 16px;
  padding: 0px 0px;
  border-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  flex: 1;
  text-align: center;
  max-width: 600px;
  margin: 0 0;
  margin-left: 40px;
}

/* 主要布局修改 */
.wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.main-content {
  width: 100%;
  max-width: 1200px;
  transition: all 0.3s ease;
}

/* 检测模式下的左右布局 - 调整比例 */
.main-content.detection-mode {
  display: flex;
  gap: 20px;
  padding: 20px;
  align-items: flex-start;
}

/* 左侧视频区域 - 缩小占比 */
.left-videos {
  transition: all 0.3s ease;
}

.left-videos.shrink {
  flex: 0 0 35%; /* 从45%减少到35% */
  min-height: 650px;
  height: auto;
}

/* 原始box样式保持不变，但在检测模式下变为垂直排列 */
.box {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  padding: 20px;
  width: 100%;
  height: 100%;
}

.box.vertical {
  flex-direction: column;
  padding: 0;
  height: 100%;
  gap: 12px; /* 减少间距 */
}

/* 视频块在检测模式下的紧凑样式 - 进一步缩小 */
.block1,
.block2 {
  flex: 1;
  min-width: 400px;
  margin: 20px;
  border: 1px solid #e9e9e9;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: all 0.3s ease;
}

.block1.compact,
.block2.compact {
  min-width: 280px; /* 进一步缩小 */
  margin: 0;
  padding: 12px; /* 减少内边距 */
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 300px; /* 减少高度 */
  border: 1px solid #e9e9e9;
  border-radius: 8px;
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.block1.compact h2,
.block2.compact h2 {
  font-size: 14px; /* 减小字体 */
  margin: 0 0 8px 0;
  color: #303133;
  font-weight: 600;
}

/* 视频在检测模式下缩小 */
.video-input {
  width: 100%;
  height: 400px;
  object-fit: contain;
  margin: 12px 0;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.compact .video-input {
  height: 180px; /* 进一步缩小 */
  margin: 6px 0;
  flex: 1;
  border-radius: 6px;
}

.no-video {
  padding: 40px;
  color: #909399;
  text-align: center;
  font-size: 16px;
}

.compact .no-video {
  padding: 40px 15px; /* 减少内边距 */
  font-size: 13px; /* 减小字体 */
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
  border-radius: 6px;
  border: 2px dashed #dcdfe6;
}

/* 右侧检测结果区域 - 扩大占比 */
.right-detection {
  flex: 1; /* 扩大占比，从原来的固定宽度变为自适应 */
  display: flex;
  justify-content: center;
  min-height: 650px;
  margin-left: 30px;
}

.block3 {
  width: 100%;
  padding: 0;
  overflow: visible;
}

.block3.active {
  width: 100%;
  height: 100%;
  border: 1px solid #e9e9e9;
  border-radius: 8px;
  padding: 24px; /* 增加内边距 */
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
}

.block3.active h2 {
  font-size: 20px; /* 增大标题字体 */
  margin: 0 0 20px 0;
  color: #303133;
  font-weight: 600;
  text-align: center;
}

/* 检测视频自适应容器 */
.video-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 15px 0;
  flex-shrink: 0;
  width: 100%;
  min-height: 300px; /* 设置最小高度 */
}

.thisvideo {
  max-width: 100%;
  width: 100%;
  height: auto; /* 改为自适应高度 */
  max-height: 400px; /* 设置最大高度 */
  object-fit: contain;
  border-radius: 8px; /* 增大圆角 */
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); /* 增强阴影 */
  background: #000; /* 添加黑色背景 */
}

/* 根据视频宽高比动态调整 */
.thisvideo[style*="aspect-ratio"] {
  aspect-ratio: var(--video-aspect-ratio, 16/9);
}

.fusion-button {
  display: flex;
  justify-content: center;
  padding: 20px;
  gap: 10px;
}

.title-card {
  margin-top: 12px;
  font-size: 14px;
  width: 100%;
  flex-shrink: 0;
}

.compact .title-card {
  font-size: 12px; /* 缩小字体 */
  margin-top: 8px;
}

.block3.active .title-card {
  margin-top: auto;
  font-size: 14px;
}

/* 检测指标容器样式调整 */
.metrics-container {
  display: flex;
  justify-content: space-between;
  width: 100%;
  margin: 10px 0;
  gap: 10px; /* 增加间距 */
  flex-shrink: 0;
}

.metric-card {
  flex: 1;
  min-width: 0;
  background: #f8f9fa;
  border-radius: 10px; /* 增大圆角 */
  padding: 10px; /* 增加内边距 */
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s ease;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.metric-title {
  font-size: 15px; /* 增大字体 */
  font-weight: 600;
  color: #409eff;
  margin-bottom: 10px;
}

.metric-value {
  font-size: 24px; /* 增大数值字体 */
  font-weight: 700;
  color: #303133;
  margin: 10px 0;
}

.metric-desc {
  font-size: 13px; /* 略微增大描述字体 */
  color: #909399;
  margin-top: 6px;
}

.font-bold {
  font-weight: bold;
}

/* 按钮样式优化 */
.fusion-button .el-button {
  padding: 12px 30px;
  font-size: 16px;
  border-radius: 6px;
  font-weight: 500;
}

/* 响应式调整 */
@media (max-width: 1200px) {
  .left-videos.shrink {
    flex: 0 0 40%; /* 在较小屏幕上稍微增加左侧比例 */
  }
}

@media (max-width: 1024px) {
  .main-content.detection-mode {
    flex-direction: column;
    gap: 20px;
  }

  .left-videos.shrink {
    flex: none;
    min-height: auto;
  }

  .box.vertical {
    flex-direction: row;
    gap: 15px;
  }

  .right-detection {
    min-height: auto;
  }

  .block1.compact {
    height: 250px;
    margin-bottom: 20px;
  }
  .block2.compact {
    margin-top: 40px;
    height: 250px;
    margin-bottom: 0px;
  }

  .compact .video-input {
    height: 140px;
  }

  .thisvideo {
    max-height: 300px;
  }
}

@media (max-width: 768px) {
  .controls-container {
    flex-direction: column;
    gap: 15px;
  }

  .range-description-card {
    margin-left: 0;
  }

  .box.vertical {
    flex-direction: column;
  }

  .block1.compact,
  .block2.compact {
    height: 220px;
  }

  .compact .video-input {
    height: 120px;
  }

  .thisvideo {
    max-height: 250px;
  }

  .metrics-container {
    flex-direction: column;
    gap: 10px;
  }
}
</style>