<template>
  <div class="video-library-container">
    <h1 class="page-title">高光谱视频库</h1>
    
    <!-- 视频播放模态框 -->
    <el-dialog 
      :visible.sync="videoDialogVisible" 
      width="80%"
      top="5vh"
      :close-on-click-modal="false"
      custom-class="video-dialog"
      @closed="resetVideoPlayer">
      <div class="video-player-wrapper">
        <video 
          ref="videoPlayer" 
          controls 
          :src="currentVideoUrl" 
          class="video-player"
          preload="metadata"
          @ended="onVideoEnded"
          @error="onVideoError"
          @loadstart="onLoadStart"
          @canplay="onCanPlay">
          您的浏览器不支持视频播放，请升级浏览器或使用Chrome/Firefox等现代浏览器。
        </video>
        <h3 class="video-title">{{ currentVideoTitle }}</h3>
        <div v-if="videoLoadingError" class="video-error-info">
          <p>视频加载失败，请检查:</p>
          <ul>
            <li>视频文件是否存在: {{ currentVideoUrl }}</li>
            <li>文件格式是否为 MP4/WebM/OGG</li>
            <li>服务器是否正确配置静态文件服务</li>
          </ul>
        </div>
      </div>
    </el-dialog>

    <!-- 视频列表 -->
    <div class="video-list">
      <div 
        v-for="video in videos" 
        :key="video.id" 
        class="video-item"
        @click="playVideo(video)">
        <div class="video-thumbnail-wrapper">
          <div class="video-thumbnail">
            <img 
              v-if="video.thumbnail" 
              :src="video.thumbnail" 
              class="thumbnail-image"
              alt="视频封面"
              @error="onThumbnailError(video)">
            <div v-else class="thumbnail-placeholder">
              <i class="el-icon-video-camera"></i>
            </div>
            <div class="play-icon">
              <i class="el-icon-video-play"></i>
            </div>
            <div class="video-duration" v-if="video.duration">
              {{ formatDuration(video.duration) }}
            </div>
          </div>
          <div class="video-info">
            <h3 class="video-name">{{ video.title }}</h3>
            <div class="video-meta">
              <span class="video-date">{{ formatDate(video.date) }}</span>
              <span class="video-size" v-if="video.size">{{ formatFileSize(video.size) }}</span>
            </div>
            <!-- <div class="video-path-debug" v-if="isDevelopment">
              视频路径: {{ getVideoUrl(video.fileName) }}
            </div> -->
          </div>
        </div>
      </div>
    </div>
    
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <i class="el-icon-loading"></i>
      <span>加载视频中...</span>
    </div>
    
    <!-- 错误提示 -->
    <div v-if="errorMessage" class="error-message">
      <i class="el-icon-error"></i>
      {{ errorMessage }}
    </div>
  </div>
</template>

<script>
import axios from 'axios';
import path from 'path';

export default {
  name: 'VideoALibrary',
  data() {
    return {
      videos: [],
      loading: true,
      errorMessage: '',
      videoDialogVisible: false,
      currentVideoUrl: '',
      currentVideoTitle: '',
      videoLoadingError: false,
      isDevelopment: process.env.NODE_ENV === 'development',
      basePath: ''
    };
  },
  mounted() {
    this.initBasePath();
    this.loadVideos();
  },
  methods: {
    // 初始化基础路径
    initBasePath() {
      if (process.env.NODE_ENV === 'production') {
        // 打包环境：检测是否为Electron环境
        if (typeof window !== 'undefined' && window.require) {
          try {
            const { remote, app } = window.require('electron');
            const electronApp = remote ? remote.app : app;
            if (electronApp) {
              const appPath = electronApp.getAppPath();
              this.basePath = path.join(appPath, '../static').replace(/\\/g, '/');
              console.log('Electron环境，基础路径:', this.basePath);
              return;
            }
          } catch (e) {
            console.warn('获取Electron路径失败:', e);
          }
        }
        
        // Web打包环境
        this.basePath = process.env.BASE_URL ? process.env.BASE_URL + '/static' : './static';
      } else {
        // 开发环境
        this.basePath = '/static';
      }
      console.log('当前基础路径:', this.basePath);
    },

    async loadVideos() {
      try {
        this.loading = true;
        this.errorMessage = '';
        
        // 尝试多个可能的JSON文件路径
        const possiblePaths = [
          `${this.basePath}/video-listA.json`,
          './static/video-listA.json',
          '/static/video-listA.json',
          'static/video-listA.json'
        ];
        
        let response = null;
        let loadedPath = '';
        
        // 依次尝试加载JSON文件
        for (const jsonPath of possiblePaths) {
          try {
            console.log('尝试加载路径:', jsonPath);
            response = await axios.get(jsonPath);
            loadedPath = jsonPath;
            console.log('成功加载JSON:', jsonPath);
            break;
          } catch (e) {
            console.warn(`加载失败 ${jsonPath}:`, e.message);
            continue;
          }
        }
        
        if (!response) {
          throw new Error('无法找到视频列表文件，请检查 video-listA.json 是否存在');
        }
        
        if (!response.data || !Array.isArray(response.data)) {
          throw new Error('视频列表格式错误');
        }
        
        this.videos = response.data.map(video => ({
          ...video,
          id: video.id || Math.random().toString(36).substr(2, 9),
          date: video.date || new Date().toISOString(),
          size: video.size || 0,
          duration: video.duration || 0,
          thumbnail: null
        }));

        console.log(`加载了 ${this.videos.length} 个视频`);

        // 生成每个视频的第一帧封面
        await Promise.all(this.videos.map(v => this.generateThumbnail(v)));

        this.loading = false;
      } catch (error) {
        console.error('加载视频失败:', error);
        this.loading = false;
        this.errorMessage = '加载视频列表失败: ' + (error.message || '请检查文件路径和服务器配置');
      }
    },

    getVideoUrl(fileName) {
      // 构建视频文件的完整路径
      let videoPath;
      
      if (process.env.NODE_ENV === 'production') {
        // 打包环境
        if (typeof window !== 'undefined' && window.require) {
          try {
            const { remote, app } = window.require('electron');
            const electronApp = remote ? remote.app : app;
            if (electronApp) {
              // Electron环境
              const appPath = electronApp.getAppPath();
              videoPath = `file://${path.join(appPath, '../static/videosLib/A', fileName).replace(/\\/g, '/')}`;
              return videoPath;
            }
          } catch (e) {
            console.warn('Electron路径构建失败:', e);
          }
        }
        
        // Web打包环境
        const baseUrl = process.env.BASE_URL || '.';
        videoPath = `${baseUrl}/static/videosLib/A/${fileName}`;
      } else {
        // 开发环境
        const baseUrl = process.env.BASE_URL || '';
        videoPath = `${baseUrl}/static/videosLib/A/${fileName}`;
      }
      
      console.log('视频URL:', videoPath);
      return videoPath;
    },

    async generateThumbnail(video) {
      return new Promise(resolve => {
        const url = this.getVideoUrl(video.fileName);
        const vid = document.createElement('video');
        vid.src = url;
        vid.crossOrigin = 'anonymous';
        vid.muted = true;
        
        const cleanup = () => {
          vid.removeEventListener('loadeddata', onLoadedData);
          vid.removeEventListener('seeked', onSeeked);
          vid.removeEventListener('error', onError);
          vid.src = '';
        };
        
        const onLoadedData = async () => {
          try {
            vid.currentTime = Math.min(1, vid.duration * 0.1);
          } catch (e) {
            console.warn('设置时间失败:', e);
            cleanup();
            resolve();
          }
        };
        
        const onSeeked = () => {
          try {
            const canvas = document.createElement('canvas');
            canvas.width = vid.videoWidth || 320;
            canvas.height = vid.videoHeight || 240;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(vid, 0, 0, canvas.width, canvas.height);
            video.thumbnail = canvas.toDataURL('image/jpeg', 0.7);
          } catch (e) {
            console.warn('生成缩略图失败:', e);
          }
          cleanup();
          resolve();
        };
        
        const onError = (e) => {
          console.warn('生成封面失败:', url, e);
          cleanup();
          resolve();
        };
        
        vid.addEventListener('loadeddata', onLoadedData);
        vid.addEventListener('seeked', onSeeked);
        vid.addEventListener('error', onError);
        
        // 设置超时
        setTimeout(() => {
          cleanup();
          resolve();
        }, 10000);
      });
    },

    playVideo(video) {
      this.currentVideoUrl = this.getVideoUrl(video.fileName);
      this.currentVideoTitle = video.title;
      this.videoLoadingError = false;
      this.videoDialogVisible = true;
      this.$nextTick(() => {
        try {
          const player = this.$refs.videoPlayer;
          if (player) {
            player.load();
            const onCanPlay = () => {
              const playPromise = player.play();
              if (playPromise !== undefined) {
                playPromise.catch(error => {
                  console.log('自动播放被浏览器阻止:', error);
                });
              }
              player.removeEventListener('canplay', onCanPlay);
            };
            player.addEventListener('canplay', onCanPlay);
          }
        } catch (e) {
          console.error('播放视频出错:', e);
          this.$message.error(`播放失败: ${e.message}`);
        }
      });
    },

    onLoadStart() {
      console.log('开始加载视频:', this.currentVideoUrl);
      this.videoLoadingError = false;
    },
    onCanPlay() {
      console.log('视频可以播放');
      this.videoLoadingError = false;
    },
    onVideoEnded() {
      console.log('视频播放结束');
    },
    resetVideoPlayer() {
      const player = this.$refs.videoPlayer;
      if (player) {
        player.pause();
        player.currentTime = 0;
      }
      this.videoLoadingError = false;
    },
    onVideoError(event) {
      console.error('视频播放错误:', event);
      this.videoLoadingError = true;
      const errorMsg = this.getVideoError(event);
      this.$message.error(`视频播放失败: ${errorMsg}`);
    },
    onThumbnailError(video) {
      video.thumbnail = null;
    },
    getVideoError(event) {
      const mediaError = event.target.error;
      if (!mediaError) return '未知错误';
      switch (mediaError.code) {
        case mediaError.MEDIA_ERR_ABORTED: return '播放被中止';
        case mediaError.MEDIA_ERR_NETWORK: return '网络错误';
        case mediaError.MEDIA_ERR_DECODE: return '视频解码错误';
        case mediaError.MEDIA_ERR_SRC_NOT_SUPPORTED: return '格式不支持或文件不存在';
        default: return `错误代码: ${mediaError.code}`;
      }
    },
    formatDate(dateString) {
      try {
        const date = new Date(dateString);
        return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });
      } catch (e) {
        return '未知日期';
      }
    },
    formatDuration(seconds) {
      const mins = Math.floor(seconds / 60);
      const secs = Math.floor(seconds % 60);
      return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
    },
    formatFileSize(bytes) {
      if (!bytes) return '0 Bytes';
      const k = 1024;
      const sizes = ['Bytes','KB','MB','GB'];
      const i = Math.floor(Math.log(bytes)/Math.log(k));
      return parseFloat((bytes/Math.pow(k,i)).toFixed(2)) + ' ' + sizes[i];
    }
  }
};
</script>

<style scoped>
.video-library-container {
  padding: 40px;
  max-width: 1600px;
  margin: 0 auto;
  background: #f8f9fa;
  min-height: 100vh;
  background-image: url('../../assets/bg.jpg');  
  background-size: cover;       /* 图片覆盖整个容器 */  
  background-position: center;  /* 图片居中显示 */  
  background-repeat: no-repeat; /* 不重复平铺 */  
  background-attachment: fixed; /* 背景固定，不随滚动移动 */  
  font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;  
  min-height: 100vh; /* 确保背景至少覆盖整个视口高度 */
}

.page-title {
  text-align: center;
  margin-bottom: 50px;
  font-size: 32px;
  color: #2c3e50;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.video-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 50px 40px; /* 增加垂直和水平间距 */
  margin-top: 40px;
  padding: 20px; /* 给整个列表增加内边距 */
}

.video-item {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.35s ease;
  background: #fff;
  margin: 15px; /* 给每个视频项增加外边距 */
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.video-item:hover {
  transform: translateY(-10px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.18);
}

.video-thumbnail-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.video-thumbnail {
  position: relative;
  height: 240px;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.thumbnail-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.video-item:hover .thumbnail-image {
  transform: scale(1.05);
}

.thumbnail-placeholder {
  font-size: 80px;
  color: rgba(108, 117, 125, 0.4);
}

.play-icon {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 70px;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 0 15px rgba(0, 0, 0, 0.6);
  transition: all 0.3s ease;
  z-index: 2;
}

.video-item:hover .play-icon {
  color: #fff;
  font-size: 75px;
  text-shadow: 0 0 20px rgba(0, 0, 0, 0.8);
}

.video-duration {
  position: absolute;
  bottom: 12px;
  right: 12px;
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  z-index: 2;
}

.video-info {
  padding: 25px;
  flex-grow: 1;
  display: flex;
  flex-direction: column;
}

.video-name {
  margin: 0 0 18px 0;
  font-size: 19px;
  color: #343a40;
  line-height: 1.4;
  font-weight: 600;
  flex-grow: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.video-meta {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: #6c757d;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #f1f3f4;
}

.video-date, .video-size {
  display: flex;
  align-items: center;
  font-weight: 500;
}

.video-size::before {
  content: '📁';
  margin-right: 8px;
}

.video-path-debug {
  margin-top: 12px;
  font-size: 12px;
  color: #dc3545;
  word-break: break-all;
  background: #fff5f5;
  padding: 8px;
  border-radius: 6px;
  font-family: 'Courier New', monospace;
  border: 1px solid #fed7d7;
}

.video-player-wrapper {
  padding: 15px;
  background: #000;
  border-radius: 12px;
}

.video-player {
  width: 100%;
  max-height: 70vh;
  border-radius: 8px;
  outline: none;
}

.video-title {
  text-align: center;
  margin-top: 20px;
  color: #fff;
  font-size: 20px;
  font-weight: 500;
}

.video-error-info {
  margin-top: 15px;
  padding: 15px;
  background: #fff3cd;
  border: 1px solid #ffeaa7;
  border-radius: 8px;
  color: #856404;
}

.video-error-info ul {
  margin: 10px 0 0 20px;
  list-style-type: disc;
}

.video-error-info li {
  margin: 5px 0;
  font-size: 14px;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
  font-size: 18px;
  color: #666;
}

.loading-container i {
  font-size: 40px;
  margin-bottom: 20px;
  animation: rotating 2s linear infinite;
}

.error-message {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 35px;
  background: #fef0f0;
  color: #f56c6c;
  border-radius: 12px;
  font-size: 16px;
  border: 1px solid #fecaca;
  margin: 20px;
}

.error-message i {
  font-size: 28px;
  margin-right: 12px;
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 响应式调整 */
@media (max-width: 1400px) {
  .video-list {
    gap: 45px 35px;
  }
}

@media (max-width: 1200px) {
  .video-list {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 40px 30px;
  }
}

@media (max-width: 768px) {
  .video-library-container {
    padding: 25px;
  }
  
  .video-list {
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 35px 25px;
    padding: 15px;
  }
  
  .video-item {
    margin: 10px;
  }
  
  .video-thumbnail {
    height: 200px;
  }
  
  .page-title {
    font-size: 26px;
    margin-bottom: 35px;
  }
}

@media (max-width: 480px) {
  .video-library-container {
    padding: 20px;
  }
  
  .video-list {
    grid-template-columns: 1fr;
    gap: 30px;
    padding: 10px;
  }
  
  .video-item {
    margin: 8px;
  }
  
  .video-thumbnail {
    height: 180px;
  }
  
  .video-info {
    padding: 20px;
  }
}
</style>

<style>
/* 全局样式调整对话框 */
.video-dialog .el-dialog__body {
  padding: 25px;
  background: #f8f9fa;
}

.video-dialog .el-dialog__header {
  padding: 20px 25px 10px;
}

.video-dialog .el-dialog {
  border-radius: 12px;
  overflow: hidden;
}
</style>