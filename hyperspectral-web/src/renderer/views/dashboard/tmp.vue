<template>
    <div class="introduction-root">
        <div class="introduction-container">
            <h2 class="page-title">高光谱图像可视化</h2>

            <!-- 数据加载与HDR选择 -->
            <div class="data-loader">
                <!-- 选择HDR文件 + 显示拍摄时间 + 加载所选HDR -->
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

                <!-- 加载最新HDR -->
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
                            <p>{{ autoLoadMessage || '点击"加载最新数据"或"加载所选文件"获取原始图像' }}</p>
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
                            <p>{{ autoLoadMessage || '点击"加载最新数据"或"加载所选文件"获取伪彩图' }}</p>
                        </div>
                        <img v-else
                             ref="pseudoImg"
                             :src="pseudoColorImageUrl"
                             alt="高光谱伪彩图"
                             class="pseudo-image"
                             @load="onPseudoImageLoad" />
                        <div class="crosshair" v-if="showCrosshair" :style="crosshairStyle"></div>
                        <div class="pixel-info" v-if="showCrosshair && currentCoords">
                            坐标: ({{ currentCoords.x }}, {{ currentCoords.y }})
                        </div>
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
                    <div class="spectrum-header">
                        <div class="spectrum-controls">
                        </div>
                    </div>
                    <div class="spectrum-info" v-if="spectrumData.length > 0">
                        波段数: {{ spectrumData.length }}
                    </div>
                    <div ref="chartRef" class="spectrum-chart"></div>
                    <div class="chart-info">
                        <p class="info-text" v-if="!isDataReady">
                            {{ autoLoadMessage || '请先点击加载按钮并等待数据加载完成' }}
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
                            <div class="param-item">
                                <span class="param-label">标准差:</span>
                                <span class="param-value">{{ stdSpectrumValue.toFixed(4) }}</span>
                            </div>
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
        name: 'HyperspectralVisualization',
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
                stdSpectrumValue: 0,

                // 可视化波段
                visualizationBands: [],

                // HDR选择
                selectedHdrFile: null,
                selectedHdrFileName: '',
                selectedHdrCaptureTime: '',

                // 新增：自动加载状态
                autoLoadMessage: '正在自动加载最新数据...',
                isAutoLoading: false
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

            // 新增：进入界面时自动加载最新数据
            this.autoLoadLatestData()
        },
        beforeDestroy() {
            if (this.chart) this.chart.dispose()
            window.removeEventListener('resize', this.handleResize)
        },
        methods: {
            // 新增：自动加载最新数据
            // async autoLoadLatestData() {
            //     // 防止重复加载
            //     if (this.isLoading) return

            //     this.isAutoLoading = true

            //     try {
            //         // 检查是否有最新处理的数据
            //         const latestData = localStorage.getItem('latestProcessedData')
            //         if (latestData) {
            //             const data = JSON.parse(latestData)
            //             const now = new Date().getTime()
            //             const tenMinutes = 10 * 60 * 1000 // 10分钟内处理的数据

            //             // 如果是10分钟内处理的数据，自动加载
            //             if (data.processed && (now - data.timestamp) < tenMinutes) {
            //                 console.log('检测到最新处理的数据，正在自动加载...')
            //                 this.autoLoadMessage = '检测到最新数据，正在自动加载...'
            //                 await this.loadData()
            //                 this.autoLoadMessage = ''
            //                 return
            //             }
            //         }

            //         // 如果没有最新处理的数据，尝试加载默认的最新数据
            //         console.log('正在自动加载最新数据...')
            //         this.autoLoadMessage = '正在加载最新数据...'
            //         await this.loadData()
            //         this.autoLoadMessage = ''

            //     } catch (error) {
            //         console.log('自动加载失败，用户可手动加载:', error)
            //         this.autoLoadMessage = '自动加载失败，请手动加载数据'
            //         // 3秒后清除提示信息
            //         setTimeout(() => {
            //             this.autoLoadMessage = ''
            //         }, 3000)
            //     } finally {
            //         this.isAutoLoading = false
            //     }
            // },
            // 新增：自动加载最新数据
            async autoLoadLatestData() {
                // 防止重复加载
                if (this.isLoading) return

                this.isAutoLoading = true

                try {
                    // 首先检查本地存储是否有预处理结果
                    const savedResult = localStorage.getItem('latestProcessingResult')
                    if (savedResult) {
                        const result = JSON.parse(savedResult)
                        if (result.success) {
                            console.log('检测到最新处理的数据，正在自动加载...')
                            this.autoLoadMessage = '检测到最新数据，正在自动加载...'

                            // 直接使用保存的数据，避免重复请求
                            await this.loadDataFromResult(result)
                            this.autoLoadMessage = ''
                            return
                        }
                    }

                    // 如果没有本地数据，尝试从服务器获取最新处理结果
                    console.log('正在从服务器获取最新处理结果...')
                    this.autoLoadMessage = '正在获取最新处理结果...'

                    try {
                        const response = await axios.get('http://localhost:8000/api/get-latest-processing-result')
                        if (response.data.success && response.data.data) {
                            console.log('从服务器获取到最新处理结果，正在加载...')
                            this.autoLoadMessage = '获取到最新数据，正在加载...'
                            await this.loadDataFromResult(response.data.data)

                            // 保存到本地存储
                            localStorage.setItem('latestProcessingResult', JSON.stringify(response.data.data))
                        } else {
                            throw new Error('没有可用的最新处理结果')
                        }
                    } catch (error) {
                        console.log('获取最新处理结果失败:', error)
                        // 如果获取最新处理结果失败，尝试加载最新数据
                        console.log('尝试加载最新数据...')
                        this.autoLoadMessage = '正在加载最新数据...'
                        await this.loadData(false)
                    }

                    this.autoLoadMessage = ''

                } catch (error) {
                    console.log('自动加载失败，用户可手动加载:', error)
                    this.autoLoadMessage = '自动加载失败，请手动加载数据'
                    // 3秒后清除提示信息
                    setTimeout(() => {
                        this.autoLoadMessage = ''
                    }, 3000)
                } finally {
                    this.isAutoLoading = false
                }
            },

            // ===== HDR选择相关方法 =====
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
                    this.$message.error('请选择 .hdr 文件')
                    return
                }

                this.selectedHdrFile = file
                this.selectedHdrFileName = file.name
                this.selectedHdrCaptureTime = this.parseCaptureTimeFromFilename(file.name) || ''
            },

            parseCaptureTimeFromFilename(name) {
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
                return `${y}年${Number(mon)}月${Number(d)}日 ${Number(h)}:${mi}:${s}`
            },

            async loadDataFromSelectedHdr() {
                if (!this.selectedHdrFile || this.isLoading) return
                await this.loadData(true)
            },

            // ===== 数据加载方法 =====
            // async loadData(useSelectedFile = false) {
            //     if (this.isLoading) return

            //     this.isLoading = true
            //     this.loadStatus = 'loading'
            //     this.isDataReady = false
            //     this.originalImageLoaded = false
            //     this.pseudoImageLoaded = false

            //     // 清除之前的数据
            //     this.clearAllData()

            //     try {
            //         let response

            //         if (useSelectedFile && this.selectedHdrFile) {
            //             // 使用选中的HDR文件
            //             const formData = new FormData()
            //             formData.append('hdr_file', this.selectedHdrFile, this.selectedHdrFile.name)

            //             response = await axios.post(
            //                 'http://localhost:8000/api/load-hyperspectral-data/upload',
            //                 formData,
            //                 {
            //                     headers: { 'Content-Type': 'multipart/form-data' },
            //                     timeout: 30000
            //                 }
            //             )
            //         } else {
            //             // 加载最新数据
            //             response = await axios.post(
            //                 'http://localhost:8000/api/get-latest-processing-result',
            //                 {},
            //                 { timeout: 30000 }
            //             )
            //         }

            //         if (response.data && response.data.success) {
            //             // 设置图像URLs
            //             this.originalImageUrl = `data:image/png;base64,${response.data.original_image}`
            //             this.pseudoColorImageUrl = `data:image/png;base64,${response.data.pseudo_color_image}`

            //             // 设置图像尺寸
            //             this.originalWidth = response.data.original_width || 0
            //             this.originalHeight = response.data.original_height || 0
            //             this.pseudoWidth = response.data.pseudo_width || response.data.image_width || 0
            //             this.pseudoHeight = response.data.pseudo_height || response.data.image_height || 0
            //             this.totalBands = response.data.total_bands || 0

            //             // 设置可视化波段
            //             this.visualizationBands = response.data.visualization_bands || [30, 60, 90]

            //             // 生成波段数组
            //             const step = Math.round(1301 / this.totalBands)
            //             this.bands = Array.from({ length: this.totalBands }, (_, i) => 400 + i * step)

            //             this.loadStatus = 'ready'
            //             this.isDataReady = true

            //             // 清除处理状态标记
            //             localStorage.removeItem('latestProcessedData')

            //             this.$message.success('数据加载成功')

            //             console.log('数据加载成功:', {
            //                 原始图像尺寸: `${this.originalWidth} × ${this.originalHeight}`,
            //                 伪彩图尺寸: `${this.pseudoWidth} × ${this.pseudoHeight}`,
            //                 总波段数: this.totalBands,
            //                 可视化波段: this.visualizationBands
            //             })
            //         } else {
            //             throw new Error((response.data && response.data.message) || '加载数据失败')
            //         }
            //     } catch (error) {
            //         console.error('加载数据失败:', error)
            //         this.loadStatus = 'error'
            //         this.errorMessage =
            //             (error.response && error.response.data && error.response.data.message) ||
            //             (error.code === 'ECONNABORTED' ? '请求超时，请检查服务器连接' : '无法加载高光谱数据')
            //         this.isDataReady = false
            //         this.$message.error(this.errorMessage)
            //     } finally {
            //         this.isLoading = false
            //     }
            // },
            // ===== 数据加载方法 =====
            // ===== 数据加载方法 =====
            async loadData(useSelectedFile = false) {
                if (this.isLoading) return

                this.isLoading = true
                this.loadStatus = 'loading'
                this.isDataReady = false
                this.originalImageLoaded = false
                this.pseudoImageLoaded = false

                // 清除之前的数据
                this.clearAllData()

                try {
                    let response
                    let resultData

                    if (useSelectedFile && this.selectedHdrFile) {
                        // 使用选中的HDR文件
                        const formData = new FormData()
                        formData.append('hdr_file', this.selectedHdrFile, this.selectedHdrFile.name)

                        response = await axios.post(
                            'http://localhost:8000/api/load-hyperspectral-data/upload',
                            formData,
                            {
                                headers: { 'Content-Type': 'multipart/form-data' },
                                timeout: 30000
                            }
                        )
                        resultData = response.data
                    } else {
                        // 加载最新数据 - 先尝试从本地存储获取
                        const savedResult = localStorage.getItem('latestProcessingResult')
                        if (savedResult) {
                            const parsedResult = JSON.parse(savedResult)
                            if (parsedResult.success) {
                                resultData = parsedResult
                                console.log('从本地存储加载预处理结果')
                            } else {
                                // 如果本地存储没有，调用API获取最新处理结果
                                response = await axios.get('http://localhost:8000/api/get-latest-processing-result')
                                if (response.data.success && response.data.data) {
                                    resultData = response.data.data
                                    console.log('从服务器获取最新处理结果')
                                } else {
                                    // 如果没有预处理结果，调用普通预处理接口
                                    response = await axios.post(
                                        'http://localhost:8000/api/load-hyperspectral-data',
                                        {},
                                        { timeout: 30000 }
                                    )
                                    resultData = response.data
                                }
                            }
                        } else {
                            // 先尝试获取最新处理结果
                            try {
                                response = await axios.get('http://localhost:8000/api/get-latest-processing-result')
                                if (response.data.success && response.data.data) {
                                    resultData = response.data.data
                                    console.log('从服务器获取最新处理结果')
                                } else {
                                    throw new Error('没有最新处理结果')
                                }
                            } catch (error) {
                                console.log('获取最新处理结果失败，调用普通预处理接口:', error)
                                // 调用普通预处理接口
                                response = await axios.post(
                                    'http://localhost:8000/api/load-hyperspectral-data',
                                    {},
                                    { timeout: 30000 }
                                )
                                resultData = response.data
                            }
                        }
                    }

                    // 统一处理返回数据
                    if (resultData && resultData.success) {
                        // 设置图像URLs
                        this.originalImageUrl = `data:image/png;base64,${resultData.original_image}`
                        this.pseudoColorImageUrl = `data:image/png;base64,${resultData.pseudo_color_image}`

                        // 设置图像尺寸
                        this.originalWidth = resultData.original_width || 0
                        this.originalHeight = resultData.original_height || 0
                        this.pseudoWidth = resultData.pseudo_width || resultData.image_width || 0
                        this.pseudoHeight = resultData.pseudo_height || resultData.image_height || 0
                        this.totalBands = resultData.total_bands || 0

                        // 设置可视化波段
                        this.visualizationBands = resultData.visualization_bands || [30, 60, 90]

                        // 生成波段数组
                        const step = Math.round(1301 / this.totalBands)
                        this.bands = Array.from({ length: this.totalBands }, (_, i) => 400 + i * step)

                        this.loadStatus = 'ready'
                        this.isDataReady = true

                        // 保存到本地存储供下次使用
                        localStorage.setItem('latestProcessingResult', JSON.stringify(resultData))

                        this.$message.success('数据加载成功')

                        console.log('数据加载成功:', {
                            原始图像尺寸: `${this.originalWidth} × ${this.originalHeight}`,
                            伪彩图尺寸: `${this.pseudoWidth} × ${this.pseudoHeight}`,
                            总波段数: this.totalBands,
                            可视化波段: this.visualizationBands
                        })
                    } else {
                        throw new Error((resultData && resultData.message) || '加载数据失败')
                    }
                } catch (error) {
                    console.error('加载数据失败:', error)
                    this.loadStatus = 'error'
                    this.errorMessage =
                        (error.response && error.response.data && error.response.data.message) ||
                        (error.code === 'ECONNABORTED' ? '请求超时，请检查服务器连接' : '无法加载高光谱数据')
                    this.isDataReady = false
                    this.$message.error(this.errorMessage)
                } finally {
                    this.isLoading = false
                }
            },
            // ===== 图表初始化 =====
            initChart() {
                this.chart = echarts.init(this.$refs.chartRef)
                const option = {
                    title: {
                        text: '光谱强度曲线',
                        left: 'center',
                        textStyle: {
                            fontSize: 16,
                            fontWeight: '600',
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
                        formatter: (params) => {
                            if (params && params.length > 0) {
                                const data = params[0]
                                return `波段 ${data.dataIndex}<br/>波长: ${this.bands[data.dataIndex]}nm<br/>强度: ${data.value.toFixed(4)}`
                            }
                            return ''
                        }
                    },
                    grid: {
                        left: '60px',
                        right: '30px',
                        top: '60px',
                        bottom: '60px',
                        backgroundColor: '#fafafa'
                    },
                    xAxis: {
                        name: '波段 (nm)',
                        nameLocation: 'middle',
                        nameGap: 30,
                        nameTextStyle: {
                            fontSize: 14,
                            fontWeight: '500',
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
                            fontSize: 12,
                            rotate: 45
                        }
                    },
                    yAxis: {
                        name: '反射率',
                        nameLocation: 'middle',
                        nameGap: 40,
                        nameTextStyle: {
                            fontSize: 14,
                            fontWeight: '500',
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
                        smooth: true,
                        lineStyle: {
                            width: 3,
                            color: '#3b82f6'
                        },
                        itemStyle: {
                            color: '#3b82f6'
                        },
                        symbol: 'circle',
                        symbolSize: 6,
                        showSymbol: true,
                        areaStyle: {
                            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                                { offset: 0, color: 'rgba(59, 130, 246, 0.3)' },
                                { offset: 1, color: 'rgba(59, 130, 246, 0.1)' }
                            ])
                        },
                        emphasis: {
                            focus: 'series',
                            itemStyle: {
                                borderColor: '#1d4ed8',
                                borderWidth: 2
                            }
                        }
                    }],
                    animation: true,
                    animationDuration: 1000,
                    animationEasing: 'cubicOut'
                }
                this.chart.setOption(option)
            },

            // ===== 数据清除 =====
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
                this.stdSpectrumValue = 0
                this.visualizationBands = []

                if (this.chart) {
                    this.chart.setOption({
                        xAxis: { data: [] },
                        series: [{ data: [] }]
                    })
                }
            },

            // ===== 图像加载回调 =====
            onOriginalImageLoad() {
                this.originalImageLoaded = true
                const img = this.$el.querySelector('.display-image')
                if (img) {
                    console.log('原始图像实际尺寸:', {
                        naturalWidth: img.naturalWidth,
                        naturalHeight: img.naturalHeight
                    })
                }
            },

            onPseudoImageLoad() {
                this.pseudoImageLoaded = true
                const img = this.$refs.pseudoImg
                if (img) {
                    console.log('伪彩图实际尺寸:', {
                        naturalWidth: img.naturalWidth,
                        naturalHeight: img.naturalHeight
                    })
                    if (this.pseudoWidth === 0) this.pseudoWidth = img.naturalWidth
                    if (this.pseudoHeight === 0) this.pseudoHeight = img.naturalHeight
                }
            },

            // ===== 鼠标交互 =====
            onMouseMove(event) {
                if (!this.pseudoColorImageUrl || !this.isDataReady || !this.pseudoImageLoaded) return

                const imgEl = this.$refs.pseudoImg
                const wrapperEl = this.$refs.imgWrapper

                if (!imgEl || !wrapperEl) return

                if (imgEl.naturalWidth === 0 || imgEl.naturalHeight === 0) {
                    return
                }

                const imgRect = imgEl.getBoundingClientRect()
                const wrapperRect = wrapperEl.getBoundingClientRect()

                const relativeX = event.clientX - imgRect.left
                const relativeY = event.clientY - imgRect.top

                if (relativeX < 0 || relativeY < 0 || relativeX > imgRect.width || relativeY > imgRect.height) {
                    return
                }

                // 转换为图像原始坐标
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
                this.stdSpectrumValue = 0

                if (this.chart) {
                    this.chart.setOption({
                        series: [{
                            data: []
                        }]
                    })
                }
            },

            // ===== 光谱数据获取 =====
            async fetchSpectrum(x, y) {
                if (!this.isDataReady) return

                try {
                    const res = await axios.get('http://localhost:8000/api/spectrum', {
                        params: { x, y },
                        timeout: 10000
                    })

                    if (res.data.spectrum && res.data.spectrum.length > 0) {
                        this.spectrumData = res.data.spectrum

                        // 计算光谱数据统计
                        this.minSpectrumValue = Math.min(...this.spectrumData)
                        this.maxSpectrumValue = Math.max(...this.spectrumData)
                        this.avgSpectrumValue = this.spectrumData.reduce((a, b) => a + b, 0) / this.spectrumData.length

                        // 计算标准差
                        const squareDiffs = this.spectrumData.map(value => {
                            const diff = value - this.avgSpectrumValue
                            return diff * diff
                        })
                        this.stdSpectrumValue = Math.sqrt(squareDiffs.reduce((a, b) => a + b, 0) / this.spectrumData.length)

                        // 更新图表
                        this.chart.setOption({
                            xAxis: {
                                data: this.bands
                            },
                            series: [{
                                data: this.spectrumData
                            }]
                        })
                    } else {
                        console.warn('未接收到光谱数据:', { x, y })
                    }
                } catch (err) {
                    console.error('获取光谱数据失败:', err)
                    if (err.code === 'ECONNABORTED') {
                        this.$message.warning('光谱数据请求超时')
                    }
                }
            },

            // ===== 数据导出 =====
            exportSpectrumData() {
                if (this.spectrumData.length === 0) return

                const data = {
                    coordinates: this.currentCoords,
                    bands: this.bands,
                    spectrum: this.spectrumData,
                    statistics: {
                        min: this.minSpectrumValue,
                        max: this.maxSpectrumValue,
                        mean: this.avgSpectrumValue,
                        std: this.stdSpectrumValue
                    },
                    timestamp: new Date().toISOString()
                }

                const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
                const url = URL.createObjectURL(blob)
                const a = document.createElement('a')
                a.href = url
                a.download = `spectrum_data_${this.currentCoords.x}_${this.currentCoords.y}_${Date.now()}.json`
                document.body.appendChild(a)
                a.click()
                document.body.removeChild(a)
                URL.revokeObjectURL(url)

                this.$message.success('光谱数据导出成功')
            },

            // ===== 工具方法 =====
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
        font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;
        min-height: 100vh;
        background: #ffffff;
    }

    .introduction-container {
        width: 100%;
        max-width: 1800px;
        background: white;
        border-radius: 20px;
        padding: 30px;
        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
    }

    .page-title {
        font-size: 36px;
        color: #1a202c;
        text-align: center;
        margin-bottom: 40px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
        font-weight: 700;
    }

    /* 数据加载区 */
    .data-loader {
        display: flex;
        flex-direction: column;
        align-items: center;
        margin-bottom: 40px;
        gap: 20px;
    }

    .load-button {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 14px 32px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white;
        border: none;
        border-radius: 25px;
        font-size: 16px;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.3s ease;
        box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
    }

        .load-button:hover:not(:disabled) {
            transform: translateY(-3px);
            box-shadow: 0 10px 25px rgba(102, 126, 234, 0.6);
        }

        .load-button:disabled {
            opacity: 0.7;
            cursor: not-allowed;
            transform: none;
        }

    .load-icon, .spinner {
        width: 22px;
        height: 22px;
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
        padding: 10px 20px;
        border-radius: 20px;
        border: 1px solid #e2e8f0;
    }

    .load-status {
        font-size: 14px;
        padding: 6px 12px;
        border-radius: 12px;
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

    /* 文件选择样式 */
    .hidden-file-input {
        display: none;
    }

    .picker-row {
        display: flex;
        gap: 15px;
        align-items: center;
        flex-wrap: wrap;
        justify-content: center;
    }

    .pick-button, .load-selected-button {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 12px 24px;
        border: none;
        border-radius: 25px;
        font-size: 14px;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.3s ease;
    }

    .pick-button {
        background: linear-gradient(135deg, #10b981 0%, #22c55e 100%);
        color: #fff;
        box-shadow: 0 4px 15px rgba(16, 185, 129, 0.4);
    }

        .pick-button:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(16, 185, 129, 0.6);
        }

    .load-selected-button {
        background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%);
        color: #fff;
        box-shadow: 0 4px 15px rgba(249, 115, 22, 0.4);
    }

        .load-selected-button:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(249, 115, 22, 0.6);
        }

    .selected-file-pill {
        display: inline-flex;
        gap: 12px;
        align-items: center;
        background: #f8fafc;
        color: #1f2937;
        border: 1px solid #e5e7eb;
        border-radius: 999px;
        padding: 10px 18px;
        max-width: 400px;
    }

        .selected-file-pill .file-name {
            font-weight: 600;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            max-width: 200px;
        }

        .selected-file-pill .file-time {
            font-size: 13px;
            color: #6b7280;
            white-space: nowrap;
        }

    /* 处理弹窗 */
    .processing-dialog {
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.8);
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 10000;
    }

    .dialog-content {
        background: white;
        padding: 50px;
        border-radius: 20px;
        text-align: center;
        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
        min-width: 400px;
        max-width: 500px;
    }

    .spinner-large {
        width: 60px;
        height: 60px;
        border: 5px solid #f3f4f6;
        border-top: 5px solid #667eea;
        border-radius: 50%;
        animation: spin 1s linear infinite;
        margin: 0 auto 25px;
    }

    .dialog-content h3 {
        margin: 0 0 15px 0;
        color: #1f2937;
        font-size: 20px;
        font-weight: 600;
    }

    .dialog-content p {
        margin: 0 0 20px 0;
        color: #6b7280;
        font-size: 16px;
        line-height: 1.5;
    }

    .progress-bar {
        width: 100%;
        height: 8px;
        background: #f3f4f6;
        border-radius: 4px;
        overflow: hidden;
    }

    .progress-fill {
        height: 100%;
        background: linear-gradient(90deg, #10b981, #22c55e);
        transition: width 0.3s ease;
    }

    /* 三栏区域 */
    .viewer {
        display: grid;
        grid-template-columns: 1fr 1fr 1fr;
        gap: 30px;
        align-items: start;
    }

    .asset-container {
        height: 750px;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        border-radius: 16px;
        padding: 25px;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        transition: all 0.3s ease;
    }

        .asset-container:hover {
            transform: translateY(-5px);
            box-shadow: 0 15px 40px rgba(0, 0, 0, 0.1);
        }

    .asset-title {
        font-size: 20px;
        font-weight: 700;
        color: #2d3748;
        margin: 0 0 15px 0;
        text-align: center;
    }

    .spectrum-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 15px;
    }

    .spectrum-controls {
        display: flex;
        gap: 10px;
    }

    .control-btn {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 8px 16px;
        background: #3b82f6;
        color: white;
        border: none;
        border-radius: 8px;
        font-size: 12px;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;
    }

        .control-btn:hover:not(:disabled) {
            background: #2563eb;
            transform: translateY(-1px);
        }

        .control-btn:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }

        .control-btn svg {
            width: 14px;
            height: 14px;
        }

    .spectrum-info {
        font-size: 14px;
        color: #718096;
        padding: 6px 12px;
        background: #e2e8f0;
        border-radius: 12px;
        font-weight: 600;
        align-self: flex-end;
        margin-bottom: 15px;
    }

    .image-wrapper {
        position: relative;
        width: 100%;
        height: 400px;
        border-radius: 12px;
        overflow: visible;
        background: transparent;
        display: flex;
        align-items: center;
        justify-content: center;
        margin: 15px 0;
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
        padding: 30px;
    }

    .placeholder-icon {
        width: 60px;
        height: 60px;
        margin-bottom: 20px;
    }

    .placeholder p {
        font-size: 15px;
        margin: 0;
        line-height: 1.5;
        color: #64748b;
    }

    .display-image, .pseudo-image {
        max-width: 100%;
        max-height: 400px;
        width: auto;
        height: auto;
        object-fit: contain;
        border-radius: 8px;
        box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
    }

    .pseudo-image {
        cursor: crosshair;
        transition: transform 0.3s ease;
    }

        .pseudo-image:hover {
            transform: scale(1.03);
        }

    .crosshair {
        position: absolute;
        width: 24px;
        height: 24px;
        border: 3px solid #ef4444;
        border-radius: 50%;
        transform: translate(-50%, -50%);
        pointer-events: none;
        background: rgba(239, 68, 68, 0.1);
        animation: pulse 1.5s infinite;
        z-index: 10;
    }

    .pixel-info {
        position: absolute;
        top: -35px;
        left: 50%;
        transform: translateX(-50%);
        background: rgba(0, 0, 0, 0.8);
        color: white;
        padding: 5px 12px;
        border-radius: 6px;
        font-size: 12px;
        font-weight: 500;
        white-space: nowrap;
        z-index: 10;
    }

    @keyframes pulse {
        0% {
            box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7);
        }

        70% {
            box-shadow: 0 0 0 12px rgba(239, 68, 68, 0);
        }

        100% {
            box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
        }
    }

    .spectrum-chart {
        width: 100%;
        height: 450px;
        border-radius: 12px;
        background: #ffffff;
        box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.1);
        border: 1px solid #e2e8f0;
    }

    .image-info, .chart-info {
        margin-top: 20px;
        text-align: center;
    }

    .info-text {
        font-size: 14px;
        color: #718096;
        margin: 0 0 15px 0;
        line-height: 1.5;
    }

    .image-params {
        margin-top: 15px;
        padding: 15px;
        background: #ffffff;
        border-radius: 10px;
        border: 1px solid #e2e8f0;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
    }

    .param-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
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

    /* 响应式设计 */
    @media (max-width: 1600px) {
        .viewer {
            gap: 25px;
        }

        .asset-container {
            height: 700px;
            padding: 20px;
        }

        .image-wrapper {
            height: 350px;
        }

        .spectrum-chart {
            height: 400px;
        }
    }

    @media (max-width: 1400px) {
        .introduction-container {
            padding: 25px;
        }

        .page-title {
            font-size: 32px;
        }

        .viewer {
            gap: 20px;
        }
    }

    @media (max-width: 1200px) {
        .viewer {
            grid-template-columns: 1fr 1fr;
            gap: 25px;
        }

        .asset-container:last-child {
            grid-column: 1 / -1;
            height: 600px;
        }

        .spectrum-chart {
            height: 350px;
        }
    }

    @media (max-width: 992px) {
        .introduction-root {
            padding: 15px;
        }

        .introduction-container {
            padding: 20px;
        }

        .page-title {
            font-size: 28px;
            margin-bottom: 30px;
        }

        .viewer {
            grid-template-columns: 1fr;
            gap: 25px;
        }

        .asset-container {
            height: 650px;
        }

            .asset-container:last-child {
                grid-column: auto;
            }

        .picker-row {
            flex-direction: column;
            gap: 12px;
        }

        .selected-file-pill {
            max-width: 300px;
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
            margin-bottom: 25px;
        }

        .asset-container {
            height: 550px;
            padding: 15px;
        }

        .image-wrapper {
            height: 280px;
            margin: 10px 0;
        }

        .spectrum-chart {
            height: 300px;
        }

        .load-button {
            padding: 12px 24px;
            font-size: 14px;
        }

        .pick-button, .load-selected-button {
            padding: 10px 20px;
            font-size: 13px;
        }

        .dialog-content {
            min-width: 300px;
            padding: 30px;
        }
    }

    @media (max-width: 480px) {
        .page-title {
            font-size: 22px;
        }

        .asset-title {
            font-size: 18px;
        }

        .asset-container {
            height: 500px;
        }

        .image-wrapper {
            height: 220px;
        }

        .spectrum-chart {
            height: 250px;
        }

        .info-text {
            font-size: 13px;
        }

        .param-item {
            font-size: 12px;
        }

        .spectrum-header {
            flex-direction: column;
            gap: 10px;
            align-items: flex-start;
        }
    }
</style>