<template>
  <div class="introduction-root">
    <div class="introduction-container">
      <h2 class="page-title">高光谱图像展示</h2>
      
      <!-- 文件选择区域 -->
      <div class="file-selector">
        <div class="file-input-wrapper">
          <input
            ref="fileInput"
            type="file"
            accept=".png,.jpg,.jpeg"
            @change="onFileSelect"
            class="file-input"
            id="file-input"
          />
          <label for="file-input" class="file-button">
            <svg class="file-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/>
            </svg>
            选择文件
          </label>
        </div>
        <div class="file-info" v-if="selectedFileName">
          <!-- <span class="selected-file">已选择: {{ selectedFileName }}</span> -->
          <span class="file-status" :class="fileStatusClass">{{ fileStatusText }}</span>
          <button @click="clearFile" class="clear-button">清除</button>
        </div>
      </div>

      <div class="viewer">
        <!-- 左侧：伪彩图 -->
        <div class="asset-container left-panel">
          <!-- <div class="coordinates-display" v-if="currentCoords">
            坐标: ({{ currentCoords.x }}, {{ currentCoords.y }})
          </div> -->
          <div
            ref="imgWrapper"
            class="image-wrapper"
            @mousemove="onMouseMove"
            @mouseleave="onMouseLeave"
          >
            <div v-if="!imageUrl" class="placeholder">
              <svg class="placeholder-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <polyline points="21,15 16,10 5,21"/>
              </svg>
              <p>请选择一个伪彩图文件</p>
            </div>
            <img
              v-else
              ref="pseudoImg"
              :src="imageUrl"
              alt="高光谱伪彩图"
              class="pseudo-image"
              @load="onImageLoad"
            />
            <div class="crosshair" v-if="showCrosshair" :style="crosshairStyle"></div>
          </div>
          <div>
            <h3 class="asset-title">高光谱图像</h3>
            <div class="image-info">
              <p class="info-text" v-if="!isDataReady">
                {{ fileStatusText }}
              </p>
              <p class="info-text" v-else>
                将鼠标悬停在图像上查看对应像素的光谱曲线
              </p>
            </div>
          </div>
        </div>

        <!-- 右侧：光谱曲线 -->
        <div class="asset-container right-panel">
          <div class="spectrum-info" v-if="spectrumData.length > 0">
            波段数: {{ spectrumData.length }}
          </div>
          <div ref="chartRef" class="spectrum-chart"></div>
          <div>
            <h3 class="asset-title">光谱曲线</h3>
            <div class="chart-info">
              <p class="info-text" v-if="!isDataReady">
                请先选择文件并等待数据加载完成
              </p>
              <p class="info-text" v-else-if="spectrumData.length === 0">
                请将鼠标移动到左侧图像上以显示光谱数据
              </p>
              <p class="info-text" v-else>
                当前显示像素 ({{ currentCoords.x }}, {{ currentCoords.y }}) 的光谱特征
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import axios from 'axios'

export default {
  name: 'Introduction',
  data() {
    return {
      imageUrl: null,
      selectedFileName: null,
      selectedFile: null,
      chart: null,
      bands: [],
      spectrumData: [],
      currentCoords: null,
      showCrosshair: false,
      crosshairStyle: {},
      // 新增状态管理
      isDataReady: false,
      fileStatus: 'idle', // idle, loading, ready, error
      errorMessage: '',
      imageLoaded: false // 新增图片加载状态
    }
  },
  computed: {
    fileStatusClass() {
      return {
        'status-loading': this.fileStatus === 'loading',
        'status-ready': this.fileStatus === 'ready',
        'status-error': this.fileStatus === 'error'
      }
    },
    fileStatusText() {
      switch (this.fileStatus) {
        case 'loading':
          return '加载中...'
        case 'ready':
          return '数据已就绪'
        case 'error':
          return this.errorMessage || '加载失败'
        default:
          return ''
      }
    }
  },
  mounted() {
    this.initChart()
    window.addEventListener('resize', this.handleResize)
  },
  beforeDestroy() {
    if (this.chart) {
      this.chart.dispose()
    }
    window.removeEventListener('resize', this.handleResize)
  },
  methods: {
    initChart() {
      this.chart = echarts.init(this.$refs.chartRef)
      const option = {
        title: {
          text: '光谱反射率',
          left: 'center',
          textStyle: {
            fontSize: 16,
            fontWeight: '500',
            color: '#2c3e50'
          }
        },
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(255, 255, 255, 0.95)',
          borderColor: '#e1e8ed',
          borderWidth: 1,
          textStyle: {
            color: '#2c3e50'
          },
          formatter: function(params) {
            if (params && params.length > 0) {
              return `波段 ${params[0].axisValue}: ${params[0].value.toFixed(4)}`
            }
            return ''
          }
        },
        grid: {
          left: '60px',
          right: '30px',
          top: '60px',
          bottom: '60px'
        },
        xAxis: {
          name: '波段编号',
          nameLocation: 'middle',
          nameGap: 30,
          nameTextStyle: {
            fontSize: 14,
            color: '#666'
          },
          type: 'category',
          data: [],
          axisLine: {
            lineStyle: { color: '#d1d5db' }
          },
          axisTick: {
            lineStyle: { color: '#d1d5db' }
          },
          axisLabel: {
            color: '#666',
            fontSize: 12
          }
        },
        yAxis: {
          name: '反射率',
          nameLocation: 'middle',
          nameGap: 40,
          nameTextStyle: {
            fontSize: 14,
            color: '#666'
          },
          type: 'value',
          axisLine: {
            lineStyle: { color: '#d1d5db' }
          },
          axisTick: {
            lineStyle: { color: '#d1d5db' }
          },
          axisLabel: {
            color: '#666',
            fontSize: 12,
            formatter: '{value}'
          },
          splitLine: {
            lineStyle: {
              color: '#f3f4f6',
              type: 'dashed'
            }
          }
        },
        series: [{
          name: '光谱反射率',
          type: 'line',
          data: [],
          smooth: false,
          lineStyle: {
            width: 2,
            color: '#3b82f6'
          },
          itemStyle: {
            color: '#3b82f6'
          },
          symbol: 'circle',
          symbolSize: 3,
          showSymbol: true,
          emphasis: {
            focus: 'series',
            itemStyle: {
              borderColor: '#3b82f6',
              borderWidth: 2
            }
          }
        }]
      }
      this.chart.setOption(option)
    },
    
    async onFileSelect(event) {
      const file = event.target.files[0]
      if (!file) return
      
      this.selectedFile = file
      this.selectedFileName = file.name
      this.fileStatus = 'loading'
      this.isDataReady = false
      this.imageLoaded = false
      
      // 创建预览URL
      const reader = new FileReader()
      reader.onload = (e) => {
        this.imageUrl = e.target.result
      }
      reader.readAsDataURL(file)
      
      // 清除之前的光谱数据
      this.clearSpectrum()
      
      // 初始化后端数据
      await this.initializeBackendData()
    },
    
    // 新增图片加载完成处理
    onImageLoad() {
      this.imageLoaded = true
      console.log('Image loaded:', {
        naturalWidth: this.$refs.pseudoImg.naturalWidth,
        naturalHeight: this.$refs.pseudoImg.naturalHeight
      })
    },
    
    async initializeBackendData() {
      try {
        // 调用后端初始化接口，只发送文件名
        const response = await axios.post('http://localhost:8000/api/initialize', {
          filename: this.selectedFileName
        })
        
        if (response.data.success) {
          this.fileStatus = 'ready'
          this.isDataReady = true
          this.bands = response.data.bands || [] // 如果后端返回波段信息
          console.log('数据初始化成功:', this.selectedFileName)
        } else {
          throw new Error(response.data.message || '初始化失败')
        }
      } catch (error) {
        console.error('初始化数据失败:', error)
        this.fileStatus = 'error'
        this.errorMessage = (error.response && error.response.data && error.response.data.message) || '无法找到对应的MAT文件'
        this.isDataReady = false
      }
    },
    
    clearFile() {
      this.selectedFile = null
      this.selectedFileName = null
      this.imageUrl = null
      this.fileStatus = 'idle'
      this.isDataReady = false
      this.imageLoaded = false
      this.errorMessage = ''
      this.clearSpectrum()
      this.$refs.fileInput.value = ''
    },
    
    clearSpectrum() {
      this.spectrumData = []
      this.currentCoords = null
      this.showCrosshair = false
      if (this.chart) {
        this.chart.setOption({
          series: [{
            data: []
          }]
        })
      }
    },
    
    onMouseMove(event) {
      if (!this.imageUrl || !this.isDataReady || !this.imageLoaded) return
      
      const imgEl = this.$refs.pseudoImg
      const wrapperEl = this.$refs.imgWrapper
      
      // 确保元素存在
      if (!imgEl || !wrapperEl) return
      
      // 确保图片已完全加载
      if (imgEl.naturalWidth === 0 || imgEl.naturalHeight === 0) {
        console.log('Image not fully loaded')
        return
      }
      
      const imgRect = imgEl.getBoundingClientRect()
      const wrapperRect = wrapperEl.getBoundingClientRect()
      
      // 计算相对于图片的坐标
      const relativeX = event.clientX - imgRect.left
      const relativeY = event.clientY - imgRect.top
      
      // 检查是否在图片范围内，使用更宽松的边界检查
      if (relativeX < 0 || relativeY < 0 || relativeX > imgRect.width || relativeY > imgRect.height) {
        return
      }
      
      // 转换为图片原始坐标
      const x = Math.floor((relativeX / imgRect.width) * imgEl.naturalWidth)
      const y = Math.floor((relativeY / imgRect.height) * imgEl.naturalHeight)
      
      // 确保坐标在有效范围内，使用更宽松的边界处理
      const validX = Math.max(0, Math.min(x, imgEl.naturalWidth - 1))
      const validY = Math.max(0, Math.min(y, imgEl.naturalHeight - 1))
      
      // 更新十字线位置
      this.showCrosshair = true
      this.crosshairStyle = {
        left: `${event.clientX - wrapperRect.left}px`,
        top: `${event.clientY - wrapperRect.top}px`
      }
      
      this.currentCoords = { x: validX, y: validY }
      this.fetchSpectrum(validX, validY)
    },
    
    onMouseLeave() {
      this.showCrosshair = false
      this.currentCoords = null
      if (this.chart) {
        this.chart.setOption({
          series: [{
            data: []
          }]
        })
      }
    },
    
    async fetchSpectrum(x, y) {
      if (!this.isDataReady) return
      
      try {
        // 现在只发送坐标，不再发送文件名
        const res = await axios.get('http://localhost:8000/api/spectrum', {
          params: { x, y },
          timeout: 5000 // 添加超时设置
        })
        
        if (res.data.spectrum && res.data.spectrum.length > 0) {
          this.spectrumData = res.data.spectrum
          
          // 如果初始化时没有获取到bands，这里可以获取
          if (this.bands.length === 0 && res.data.bands) {
            this.bands = res.data.bands
          }
          
          this.chart.setOption({
            xAxis: {
              data: this.bands.length > 0 ? this.bands : res.data.bands || []
            },
            series: [{
              data: this.spectrumData
            }]
          })
        } else {
          console.warn('No spectrum data received for coordinates:', { x, y })
        }
      } catch (err) {
        console.error('获取光谱数据失败:', err)
        // 可以在这里添加用户友好的错误提示
        if (err.code === 'ECONNABORTED') {
          console.error('请求超时')
        }
      }
    },
    
    handleResize() {
      if (this.chart) {
        this.chart.resize()
      }
    }
  }
}
</script>

<style scoped>
.introduction-root {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;
}

.introduction-container {
  width: 100%;
  background: #ffffff;
  border-radius: 20px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  padding: 30px;
}

.page-title {
  font-size: 32px;
  color: #1a202c;
  text-align: center;
  margin-bottom: 40px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 文件选择器样式 */
.file-selector {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 30px;
  gap: 15px;
}

.file-input-wrapper {
  position: relative;
}

.file-input {
  position: absolute;
  width: 0.1px;
  height: 0.1px;
  opacity: 0;
  overflow: hidden;
  z-index: -1;
}

.file-button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 25px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.file-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
}

.file-icon {
  width: 20px;
  height: 20px;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f8fafc;
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
}

.selected-file {
  font-size: 14px;
  color: #2d3748;
  font-weight: 500;
}

.file-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.status-loading {
  background: #fef3c7;
  color: #d97706;
}

.status-ready {
  background: #d1fae5;
  color: #065f46;
}

.status-error {
  background: #fee2e2;
  color: #dc2626;
}

.clear-button {
  background: #ef4444;
  color: white;
  border: none;
  padding: 4px 12px;
  border-radius: 15px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.clear-button:hover {
  background: #dc2626;
}

.viewer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  align-items: start;
}

.asset-container {
  height: 800px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-radius: 16px;
  padding: 24px;
  align-items: center;
}

.asset-title {
  font-size: 20px;
  font-weight: 600;
  color: #2d3748;
  margin: 16px 0 8px 0;
  text-align: center;
}

.coordinates-display {
  font-size: 14px;
  color: #718096;
  padding: 4px 12px;
  border-radius: 20px;
  font-weight: 500;
  align-self: flex-end;
  margin-bottom: 16px;
}

.spectrum-info {
  font-size: 14px;
  color: #718096;
  padding: 4px 12px;
  border-radius: 20px;
  font-weight: 500;
  align-self: flex-end;
  margin-bottom: 16px;
}

.image-wrapper {
  position: relative;
  width: 100%;
  height: 500px;
  border-radius: 12px;
  overflow: visible;
  background: transparent;
  align-self: center;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 30px;
}

.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #a0aec0;
  text-align: center;
  width: 100%;
  height: 100%;
  border: 2px dashed #e2e8f0;
  border-radius: 12px;
}

.placeholder-icon {
  width: 48px;
  height: 48px;
  margin-bottom: 16px;
}

.placeholder p {
  font-size: 16px;
  margin: 0;
}

.pseudo-image {
  max-width: 100%;
  max-height: 500px;
  width: auto;
  height: auto;
  object-fit: contain;
  cursor: crosshair;
  transition: transform 0.2s ease;
}

.pseudo-image:hover {
  transform: scale(1.02);
}

.crosshair {
  position: absolute;
  width: 20px;
  height: 20px;
  border: 2px solid #ef4444;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  background: rgba(239, 68, 68, 0.1);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(239, 68, 68, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
  }
}

.spectrum-chart {
  width: 100%;
  height: 600px;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.05);
}

.image-info,
.chart-info {
  margin-top: 16px;
  text-align: center;
}

.info-text {
  font-size: 14px;
  color: #718096;
  margin: 0;
  line-height: 1.5;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .introduction-container {
    max-width: 1000px;
    padding: 30px;
  }
  
  .viewer {
    gap: 30px;
  }
  
  .page-title {
    font-size: 28px;
  }
}

@media (max-width: 768px) {
  .introduction-root {
    padding: 10px;
  }
  
  .introduction-container {
    padding: 20px;
  }
  
  .viewer {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  
  .page-title {
    font-size: 24px;
    margin-bottom: 30px;
  }
  
  .asset-container {
    padding: 20px;
  }
  
  .spectrum-chart,
  .image-wrapper {
    height: 300px;
  }
  
  .pseudo-image {
    max-height: 300px;
  }
}

@media (max-width: 480px) {
  .coordinates-display,
  .spectrum-info {
    font-size: 12px;
  }
  
  .spectrum-chart,
  .image-wrapper {
    height: 250px;
  }
  
  .pseudo-image {
    max-height: 250px;
  }
}
</style>