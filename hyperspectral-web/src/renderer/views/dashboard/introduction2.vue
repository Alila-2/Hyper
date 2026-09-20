<template>
    <div class="introduction-root">
        <div class="introduction-container">
            <h2 class="page-title">高光谱图像可视化</h2>

            <!-- 数据加载与HDR选择 -->
            <div class="data-loader">
                <!-- 新增：选择HDR文件 + 显示拍摄时间 + 加载所选HDR -->
                <div class="picker-row">
                    <button class="pick-button" @click="triggerFilePicker" :disabled="isLoading">
                        选择文件
                    </button>
                    <input ref="hdrInput"
                           type="file"
                           accept=".hdr"
                           @change="onHdrFileChange"
                           class="hidden-file-input" />
                    <div v-if="selectedHdrFileName" class="selected-file-pill">
                        <span class="file-name">{{ selectedHdrFileName }}</span>
                        <span class="file-time" v-if="selectedHdrCaptureTime">
                            拍摄时间：{{ selectedHdrCaptureTime }}
                        </span>
                        <span class="file-time" v-else>无法解析拍摄时间</span>
                    </div>
                    <button v-if="selectedHdrFile"
                            class="load-selected-button"
                            @click="loadDataFromSelectedHdr"
                            :disabled="isLoading"
                            title="使用所选HDR文件加载数据">
                        加载所选文件
                    </button>
                </div>

                <!-- 保留：加载最新HDR -->
                <button @click="loadData" class="load-button" :disabled="isLoading">
                    <svg class="load-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" v-if="!isLoading">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    <svg class="spinner" viewBox="0 0 24 24" v-else>
                        <circle class="path" cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"
                                stroke-linecap="round" stroke-dasharray="32" stroke-dashoffset="32">
                            <animate attributeName="stroke-dasharray" dur="2s" values="0 32;16 16;0 32;0 32" repeatCount="indefinite" />
                            <animate attributeName="stroke-dashoffset" dur="2s" values="0;-16;-32;-32" repeatCount="indefinite" />
                        </circle>
                    </svg>
                    {{ isLoading ? '加载中...' : '加载最新数据' }}
                </button>

                <div class="load-info" v-if="loadStatus !== 'idle'">
                    <span class="load-status" :class="loadStatusClass">{{ loadStatusText }}</span>
                </div>
            </div>

            <div class="viewer">
                <!-- 第一个框：原始图像 -->
                <div class="asset-container left-panel">
                    <div class="image-wrapper">
                        <div v-if="!originalImageUrl" class="placeholder">
                            <svg class="placeholder-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                                <circle cx="8.5" cy="8.5" r="1.5" />
                                <polyline points="21,15 16,10 5,21" />
                            </svg>
                            <p>点击“加载最新HDR”或“加载所选HDR”获取原始图像</p>
                        </div>
                        <img v-else
                             :src="originalImageUrl"
                             alt="原始图像"
                             class="display-image"
                             @load="onOriginalImageLoad" />
                    </div>
                    <div>
                        <h3 class="asset-title">多光谱图像</h3>
                        <div class="image-info">
                            <p class="info-text">
                                {{ originalImageUrl ? '显示原始BMP图像（或由HDR生成的可视化图像）' : '等待加载原始图像数据' }}
                            </p>
                            <div class="image-params" v-if="originalImageUrl">
                                <div class="param-item">
                                    <span class="param-label">尺寸:</span>
                                    <span class="param-value">{{ originalWidth }} × {{ originalHeight }}</span>
                                </div>
                                <div class="param-item">
                                    <span class="param-label">格式:</span>
                                    <span class="param-value">BMP</span>
                                </div>
                                <!--div class="param-item" v-if="selectedHdrFileName">
                                    <span class="param-label">HDR文件:</span>
                                    <span class="param-value">{{ selectedHdrFileName }}</span>
                                </div-->
                                <div class="param-item" v-if="selectedHdrCaptureTime">
                                    <span class="param-label">拍摄时间:</span>
                                    <span class="param-value">{{ selectedHdrCaptureTime }}</span>
                                </div>
                                <div class="param-item">
                                    <span class="param-label">宽高比:</span>
                                    <span class="param-value">{{ aspectRatio.original.toFixed(2) }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 第二个框：伪彩图 -->
                <div class="asset-container middle-panel">
                    <div ref="imgWrapper"
                         class="image-wrapper"
                         @mousemove="onMouseMove"
                         @mouseleave="onMouseLeave">
                        <div v-if="!pseudoColorImageUrl" class="placeholder">
                            <svg class="placeholder-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                                <circle cx="8.5" cy="8.5" r="1.5" />
                                <polyline points="21,15 16,10 5,21" />
                            </svg>
                            <p>点击“加载最新HDR”或“加载所选HDR”获取伪彩图</p>
                        </div>
                        <img v-else
                             ref="pseudoImg"
                             :src="pseudoColorImageUrl"
                             alt="高光谱伪彩图"
                             class="pseudo-image"
                             @load="onPseudoImageLoad" />
                        <div class="crosshair" v-if="showCrosshair" :style="crosshairStyle"></div>
                    </div>
                    <div>
                        <h3 class="asset-title">高光谱图像</h3>
                        <div class="image-info">
                            <p class="info-text" v-if="!isDataReady">
                                {{ loadStatusText || '等待加载伪彩图数据' }}
                            </p>
                            <p class="info-text" v-else>
                                将鼠标悬停在图像上查看对应像素的光谱曲线
                            </p>
                            <div class="image-params" v-if="pseudoColorImageUrl">
                                <div class="param-item">
                                    <span class="param-label">尺寸:</span>
                                    <span class="param-value">{{ pseudoWidth }} × {{ pseudoHeight }}</span>
                                </div>
                                <div class="param-item">
                                    <span class="param-label">总波段数:</span>
                                    <span class="param-value">{{ totalBands }}</span>
                                </div>
                                <div class="param-item">
                                    <span class="param-label">波段范围:</span>
                                    <span class="param-value">0 - {{ totalBands - 1 }}</span>
                                </div>
                                <!--div class="param-item">
                                    <span class="param-label">数据源:</span>
                                    <span class="param-value">hyperspectral.mat</span>
                                </div-->
                                <div class="param-item">
                                    <span class="param-label">可视化波段:</span>
                                    <span class="param-value">{{ formattedVisualizationBands }}</span>
                                </div>
                                <div class="param-item">
                                    <span class="param-label">宽高比:</span>
                                    <span class="param-value">{{ aspectRatio.pseudo.toFixed(2) }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 第三个框：光谱曲线 -->
                <div class="asset-container right-panel">
                    <div class="spectrum-info" v-if="spectrumData.length > 0">
                        波段数: {{ spectrumData.length }}
                    </div>
                    <div ref="chartRef" class="spectrum-chart"></div>
                    <div>
                        <h3 class="asset-title">光谱曲线</h3>
                        <div class="chart-info">
                            <p class="info-text" v-if="!isDataReady">
                                请先点击加载按钮并等待数据加载完成
                            </p>
                            <p class="info-text" v-else-if="spectrumData.length === 0">
                                请将鼠标移动到伪彩图上以显示光谱数据
                            </p>
                            <p class="info-text" v-else>
                                当前显示像素 ({{ currentCoords.x }}, {{ currentCoords.y }}) 的光谱特征
                            </p>
                            <div class="image-params" v-if="spectrumData.length > 0">
                                <div class="param-item">
                                    <span class="param-label">像素坐标:</span>
                                    <span class="param-value">({{ currentCoords.x }}, {{ currentCoords.y }})</span>
                                </div>
                                <div class="param-item">
                                    <span class="param-label">显示波段:</span>
                                    <span class="param-value">{{ spectrumData.length }}</span>
                                </div>
                                <div class="param-item">
                                    <span class="param-label">数据范围:</span>
                                    <span class="param-value">{{ minSpectrumValue.toFixed(4) }} - {{ maxSpectrumValue.toFixed(4) }}</span>
                                </div>
                                <div class="param-item">
                                    <span class="param-label">平均值:</span>
                                    <span class="param-value">{{ avgSpectrumValue.toFixed(4) }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div> <!-- /viewer -->
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
                // 图像URLs
                originalImageUrl: null,
                pseudoColorImageUrl: null,

                // 图表相关
                chart: null,
                bands: [],
                spectrumData: [],
                currentCoords: null,
                showCrosshair: false,
                crosshairStyle: {},

                // 状态管理
                isLoading: false,
                isDataReady: false,
                loadStatus: 'idle', // idle, loading, ready, error
                errorMessage: '',
                originalImageLoaded: false,
                pseudoImageLoaded: false,

                // 图像尺寸信息
                originalWidth: 0,
                originalHeight: 0,
                pseudoWidth: 0,
                pseudoHeight: 0,
                totalBands: 0,

                // 光谱数据统计
                minSpectrumValue: 0,
                maxSpectrumValue: 0,
                avgSpectrumValue: 0,

                // 可视化波段
                visualizationBands: [],

                // 新增：HDR选择
                selectedHdrFile: null,
                selectedHdrFileName: '',
                selectedHdrCaptureTime: '' // 格式化后的“YYYY年M月D日H时m分s秒”
            }
        },
        computed: {
            loadStatusClass() {
                return {
                    'status-loading': this.loadStatus === 'loading',
                    'status-ready': this.loadStatus === 'ready',
                    'status-error': this.loadStatus === 'error'
                }
            },
            loadStatusText() {
                switch (this.loadStatus) {
                    case 'loading': return '正在加载数据...'
                    case 'ready': return '数据加载完成'
                    case 'error': return this.errorMessage || '加载失败'
                    default: return ''
                }
            },
            aspectRatio() {
                return {
                    original: this.originalWidth > 0 ? this.originalWidth / this.originalHeight : 0,
                    pseudo: this.pseudoWidth > 0 ? this.pseudoWidth / this.pseudoHeight : 0
                }
            },
            formattedVisualizationBands() {
                if (this.visualizationBands && this.visualizationBands.length > 0) {
                    return `[${this.visualizationBands.join(', ')}]`
                }
                return '未设置'
            }
        },
        mounted() {
            this.initChart()
            window.addEventListener('resize', this.handleResize)
        },
        activated() {
            this.$nextTick(() => this.handleResize())
        },
        beforeDestroy() {
            if (this.chart) this.chart.dispose()
            window.removeEventListener('resize', this.handleResize)
        },
        methods: {
            // ===== 新增：HDR选择相关 =====
            triggerFilePicker() {
                if (this.isLoading) return
                this.$refs.hdrInput && this.$refs.hdrInput.click()
            },
            onHdrFileChange(e) {
                const file = e.target.files && e.target.files[0]
                if (!file) return
                if (!/\.hdr$/i.test(file.name)) {
                    this.selectedHdrFile = null
                    this.selectedHdrFileName = ''
                    this.selectedHdrCaptureTime = ''
                    alert('请选择 .hdr 文件')
                    return
                }
                this.selectedHdrFile = file
                this.selectedHdrFileName = file.name
                this.selectedHdrCaptureTime = this.parseCaptureTimeFromFilename(file.name) || ''
            },
            parseCaptureTimeFromFilename(name) {
                // 形如：D_20251010_131632.hdr
                const m = /^D_(\d{8})_(\d{6})\.hdr$/i.exec(name.trim())
                if (!m) return ''
                const date = m[1] // YYYYMMDD
                const time = m[2] // HHMMSS
                const y = date.slice(0, 4)
                const mon = date.slice(4, 6)
                const d = date.slice(6, 8)
                const h = time.slice(0, 2)
                const mi = time.slice(2, 4)
                const s = time.slice(4, 6)
                return `${y}年${Number(mon)}月${Number(d)}日${Number(h)}时${Number(mi)}分${Number(s)}秒`
            },
            async loadDataFromSelectedHdr() {
                if (!this.selectedHdrFile || this.isLoading) return

                this.isLoading = true
                this.loadStatus = 'loading'
                this.isDataReady = false
                this.originalImageLoaded = false
                this.pseudoImageLoaded = false

                // 清空既有数据
                this.clearAllData()

                try {
                    // 如需复用原路由，可将 URL 改为 /api/load-hyperspectral-data
                    const form = new FormData()
                    form.append('hdr_file', this.selectedHdrFile, this.selectedHdrFile.name)

                    const response = await axios.post(
                        'http://localhost:8000/api/load-hyperspectral-data/upload',
                        form,
                        { headers: { 'Content-Type': 'multipart/form-data' } }
                    )

                    if (response.data && response.data.success) {
                        // 与原 loadData 完全一致的处理
                        this.originalImageUrl = `data:image/png;base64,${response.data.original_image}`
                        this.pseudoColorImageUrl = `data:image/png;base64,${response.data.pseudo_color_image}`

                        this.originalWidth = response.data.original_width || 0
                        this.originalHeight = response.data.original_height || 0
                        this.pseudoWidth = response.data.pseudo_width || response.data.image_width || 0
                        this.pseudoHeight = response.data.pseudo_height || response.data.image_height || 0
                        this.totalBands = response.data.total_bands || 0

                        this.visualizationBands = response.data.visualization_bands || [30, 60, 90]

                        const step = Math.round(1301 / this.totalBands)
                        this.bands = Array.from({ length: this.totalBands }, (_, i) => 400 + i * step)

                        this.loadStatus = 'ready'
                        this.isDataReady = true

                        console.log('所选HDR数据加载成功:', {
                            文件名: this.selectedHdrFileName,
                            拍摄时间: this.selectedHdrCaptureTime || '未解析',
                            原始图像尺寸: `${this.originalWidth} × ${this.originalHeight}`,
                            伪彩图尺寸: `${this.pseudoWidth} × ${this.pseudoHeight}`,
                            总波段数: this.totalBands,
                            可视化波段: this.visualizationBands,
                            波段范围: this.bands
                        })
                    } else {
                        throw new Error((response.data && response.data.message) || '加载所选HDR失败')
                    }
                } catch (error) {
                    console.error('加载所选HDR失败:', error)
                    this.loadStatus = 'error'
                    this.errorMessage =
                        (error.response && error.response.data && error.response.data.message) ||
                        '无法加载所选HDR'
                    this.isDataReady = false
                } finally {
                    this.isLoading = false
                }
            },

            initChart() {
                this.chart = echarts.init(this.$refs.chartRef)
                const option = {
                    title: {
                        text: '光谱强度',
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
                        formatter: function (params) {
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
                        name: '光谱范围',
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
                        name: '光强',
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
                        name: '光谱强度',
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

            async loadData() {
                if (this.isLoading) return

                this.isLoading = true
                this.loadStatus = 'loading'
                this.isDataReady = false
                this.originalImageLoaded = false
                this.pseudoImageLoaded = false

                // 清除之前的数据
                this.clearAllData()

                try {
                    const response = await axios.post('http://localhost:8000/api/load-hyperspectral-data')

                    if (response.data.success) {
                        // 设置图像URLs
                        this.originalImageUrl = `data:image/png;base64,${response.data.original_image}`
                        this.pseudoColorImageUrl = `data:image/png;base64,${response.data.pseudo_color_image}`

                        // 分别设置两个图像的尺寸
                        this.originalWidth = response.data.original_width || 0
                        this.originalHeight = response.data.original_height || 0
                        this.pseudoWidth = response.data.pseudo_width || response.data.image_width || 0
                        this.pseudoHeight = response.data.pseudo_height || response.data.image_height || 0
                        this.totalBands = response.data.total_bands || 0

                        // 设置可视化波段
                        this.visualizationBands = response.data.visualization_bands || [30, 60, 90]  // 默认值

                        // 生成波段数组
                        const step = Math.round(1301 / this.totalBands)
                        this.bands = Array.from({ length: this.totalBands }, (_, i) => 400 + i * step)

                        this.loadStatus = 'ready'
                        this.isDataReady = true
                    } else {
                        throw new Error(response.data.message || '加载数据失败')
                    }
                } catch (error) {
                    console.error('加载数据失败:', error)
                    this.loadStatus = 'error'
                    this.errorMessage = (error.response && error.response.data && error.response.data.message) || '无法加载高光谱数据'
                    this.isDataReady = false
                } finally {
                    this.isLoading = false
                }
            },

            clearAllData() {
                this.originalImageUrl = null
                this.pseudoColorImageUrl = null
                this.spectrumData = []
                this.currentCoords = null
                this.showCrosshair = false
                this.bands = []
                this.originalWidth = 0
                this.originalHeight = 0
                this.pseudoWidth = 0
                this.pseudoHeight = 0
                this.totalBands = 0
                this.minSpectrumValue = 0
                this.maxSpectrumValue = 0
                this.avgSpectrumValue = 0
                this.visualizationBands = []

                if (this.chart) {
                    this.chart.setOption({
                        xAxis: { data: [] },
                        series: [{ data: [] }]
                    })
                }
            },

            onOriginalImageLoad() {
                this.originalImageLoaded = true
                const img = this.$el.querySelector('.display-image')
                if (img) {
                    console.log('原始图像实际尺寸:', { naturalWidth: img.naturalWidth, naturalHeight: img.naturalHeight })
                }
            },

            onPseudoImageLoad() {
                this.pseudoImageLoaded = true
                const img = this.$refs.pseudoImg
                if (img) {
                    console.log('伪彩图实际尺寸:', { naturalWidth: img.naturalWidth, naturalHeight: img.naturalHeight })
                    if (this.pseudoWidth === 0) this.pseudoWidth = img.naturalWidth
                    if (this.pseudoHeight === 0) this.pseudoHeight = img.naturalHeight
                }
            },

            onMouseMove(event) {
                if (!this.pseudoColorImageUrl || !this.isDataReady || !this.pseudoImageLoaded) return

                const imgEl = this.$refs.pseudoImg
                const wrapperEl = this.$refs.imgWrapper

                if (!imgEl || !wrapperEl) return

                if (imgEl.naturalWidth === 0 || imgEl.naturalHeight === 0) {
                    console.log('Image not fully loaded')
                    return
                }

                const imgRect = imgEl.getBoundingClientRect()
                const wrapperRect = wrapperEl.getBoundingClientRect()

                const relativeX = event.clientX - imgRect.left
                const relativeY = event.clientY - imgRect.top

                if (relativeX < 0 || relativeY < 0 || relativeX > imgRect.width || relativeY > imgRect.height) {
                    return
                }

                // 转换为图像原始坐标 - 使用伪彩图尺寸
                const x = Math.floor((relativeX / imgRect.width) * this.pseudoWidth)
                const y = Math.floor((relativeY / imgRect.height) * this.pseudoHeight)

                const validX = Math.max(0, Math.min(x, this.pseudoWidth - 1))
                const validY = Math.max(0, Math.min(y, this.pseudoHeight - 1))

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
                this.spectrumData = []
                this.minSpectrumValue = 0
                this.maxSpectrumValue = 0
                this.avgSpectrumValue = 0
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
                    const res = await axios.get('http://localhost:8000/api/spectrum', {
                        params: { x, y },
                        timeout: 5000
                    })

                    if (res.data.spectrum && res.data.spectrum.length > 0) {
                        this.spectrumData = res.data.spectrum

                        // 计算光谱数据统计
                        this.minSpectrumValue = Math.min(...this.spectrumData)
                        this.maxSpectrumValue = Math.max(...this.spectrumData)
                        this.avgSpectrumValue = this.spectrumData.reduce((a, b) => a + b, 0) / this.spectrumData.length

                        this.chart.setOption({
                            xAxis: {
                                data: this.bands
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
                    if (err.code === 'ECONNABORTED') {
                        console.error('请求超时')
                    }
                }
            },

            handleResize() {
                if (this.chart) this.chart.resize()
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
        font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;
        min-height: 100vh;
    }

    .introduction-container {
        width: 100%;
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

    /* 数据加载区 */
    .data-loader {
        display: flex;
        flex-direction: column;
        align-items: center;
        margin-bottom: 30px;
        gap: 15px;
    }

    .load-button {
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

        .load-button:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
        }

        .load-button:disabled {
            opacity: 0.7;
            cursor: not-allowed;
            transform: none;
        }

    .load-icon, .spinner {
        width: 20px;
        height: 20px;
    }

    .spinner {
        animation: spin 2s linear infinite;
    }

    @keyframes spin {
        from {
            transform: rotate(0deg);
        }

        to {
            transform: rotate(360deg);
        }
    }

    .load-info {
        display: flex;
        align-items: center;
        gap: 12px;
        background: #f8fafc;
        padding: 8px 16px;
        border-radius: 20px;
        border: 1px solid #e2e8f0;
    }

    .load-status {
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

    /* 选择HDR样式（新增） */
    .hidden-file-input {
        display: none;
    }

    .picker-row {
        display: flex;
        gap: 12px;
        align-items: center;
        flex-wrap: wrap;
        justify-content: center;
    }

    .pick-button, .load-selected-button {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 10px 18px;
        border: none;
        border-radius: 25px;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;
    }

    .pick-button {
        background: linear-gradient(135deg, #10b981 0%, #22c55e 100%);
        color: #fff;
        box-shadow: 0 4px 12px rgba(16, 185, 129, 0.35);
    }

        .pick-button:hover {
            transform: translateY(-1px);
        }

    .load-selected-button {
        background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%);
        color: #fff;
        box-shadow: 0 4px 12px rgba(249, 115, 22, 0.35);
    }

        .load-selected-button:hover {
            transform: translateY(-1px);
        }

    .selected-file-pill {
        display: inline-flex;
        gap: 10px;
        align-items: center;
        background: #f8fafc;
        color: #1f2937;
        border: 1px solid #e5e7eb;
        border-radius: 999px;
        padding: 8px 14px;
        max-width: 100%;
    }

        .selected-file-pill .file-name {
            font-weight: 600;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .selected-file-pill .file-time {
            font-size: 12px;
            color: #6b7280;
            white-space: nowrap;
        }

    /* 三栏区域 */
    .viewer {
        display: grid;
        grid-template-columns: 1fr 1fr 1fr;
        gap: 30px;
        align-items: start;
    }

    .asset-container {
        height: 700px;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        border-radius: 16px;
        padding: 24px;
        align-items: center;
    }

    .asset-title {
        font-size: 18px;
        font-weight: 600;
        color: #2d3748;
        margin: 16px 0 8px 0;
        text-align: center;
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
        width: 80%;
        height: 400px;
        border-radius: 12px;
        overflow: visible;
        background: transparent;
        align-self: center;
        display: flex;
        align-items: center;
        justify-content: center;
        margin: 20px 0;
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
        padding: 20px;
    }

    .placeholder-icon {
        width: 48px;
        height: 48px;
        margin-bottom: 16px;
    }

    .placeholder p {
        font-size: 14px;
        margin: 0;
        line-height: 1.4;
    }

    .display-image, .pseudo-image {
        max-width: 100%;
        max-height: 400px;
        width: auto;
        height: auto;
        object-fit: contain;
        border-radius: 8px;
    }

    .pseudo-image {
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
        height: 500px;
        border-radius: 12px;
        background: #ffffff;
        box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.05);
    }

    .image-info, .chart-info {
        margin-top: 16px;
        text-align: center;
    }

    .info-text {
        font-size: 13px;
        color: #718096;
        margin: 0;
        line-height: 1.5;
    }

    .image-params {
        margin-top: 12px;
        padding: 12px;
        background: #f8fafc;
        border-radius: 8px;
        border: 1px solid #e2e8f0;
    }

    .param-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 6px;
        font-size: 13px;
    }

        .param-item:last-child {
            margin-bottom: 0;
        }

    .param-label {
        color: #64748b;
        font-weight: 500;
    }

    .param-value {
        color: #1e293b;
        font-weight: 600;
    }

    /* 响应式 */
    @media (max-width: 1400px) {
        .viewer {
            gap: 20px;
        }

        .asset-container {
            padding: 20px;
        }
    }

    @media (max-width: 1200px) {
        .introduction-container {
            padding: 20px;
        }

        .page-title {
            font-size: 28px;
        }

        .asset-container {
            height: 600px;
        }

        .image-wrapper {
            height: 350px;
        }

        .spectrum-chart {
            height: 400px;
        }
    }

    @media (max-width: 992px) {
        .viewer {
            grid-template-columns: 1fr;
            gap: 30px;
        }

        .asset-container {
            height: 500px;
        }

        .image-wrapper {
            height: 300px;
        }

        .spectrum-chart {
            height: 350px;
        }
    }

    @media (max-width: 768px) {
        .introduction-root {
            padding: 10px;
        }

        .introduction-container {
            padding: 15px;
        }

        .page-title {
            font-size: 24px;
            margin-bottom: 30px;
        }

        .asset-container {
            padding: 16px;
            height: 450px;
        }

        .image-wrapper {
            height: 250px;
            margin: 15px 0;
        }

        .spectrum-chart {
            height: 300px;
        }

        .image-params {
            padding: 8px;
        }

        .param-item {
            font-size: 12px;
        }
    }

    @media (max-width: 480px) {
        .asset-title {
            font-size: 16px;
        }

        .info-text {
            font-size: 12px;
        }

        .spectrum-info {
            font-size: 12px;
        }

        .image-params {
            padding: 6px;
        }

        .param-item {
            font-size: 11px;
        }
    }
</style>
