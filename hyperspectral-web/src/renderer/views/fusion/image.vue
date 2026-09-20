<template>
    <div class="fusion-page">
        <div class="fusion-container">
            <!-- 标题 -->
            <h2 class="page-title">融合计算成像</h2>

            <!-- 主要内容区域 -->
            <div class="fusion-content">
                <!-- 左侧：输入图像 -->
                <div class="input-section">
                    <!-- 模态一：高光谱图像 -->
                    <div class="input-card">
                        <div class="card-header">
                            <h3 class="card-title">模态一数据：高光谱图像</h3>
                        </div>
                        <div class="card-content">
                            <el-upload class="image-upload"
                                       :show-file-list="false"
                                       :before-upload="beforeUploadFile"
                                       :on-change="handleFileUpload"
                                       accept=".hdr,.img"
                                       :disabled="loading">
                                <el-button size="medium"
                                           type="primary"
                                           class="upload-btn"
                                           :loading="matLoading">
                                    <i class="el-icon-upload"></i>
                                    {{ matLoading ? '加载中...' : '选择HDR/IMG文件' }}
                                </el-button>
                            </el-upload>

                            <div class="image-preview" v-if="previewUrl1">
                                <img :src="previewUrl1" class="preview-image" alt="高光谱图像预览" />
                            </div>

                            <div class="file-info" v-if="imageName1">
                                <el-tag type="info" class="file-tag">{{ imageName1 }}</el-tag>
                            </div>

                            <div class="image-info" v-if="matInfo1">
                                <el-card class="info-card">
                                    <div class="info-content">
                                        <div class="info-item">
                                            <span class="info-label">图像尺寸:</span>
                                            <span class="info-value">{{ matInfo1.resolution }}</span>
                                        </div>
                                        <div class="info-item">
                                            <span class="info-label">光谱波段数:</span>
                                            <span class="info-value">{{ matInfo1.total_bands || totalBands || '-' }}</span>
                                        </div>
                                        <div class="info-item" v-if="matInfo1.wavelength_range">
                                            <span class="info-label">波段范围:</span>
                                            <span class="info-value">{{ matInfo1.wavelength_range }}</span>
                                        </div>
                                    </div>
                                </el-card>
                            </div>
                        </div>
                    </div>

                    <!-- 模态二：多光谱图像（通过MAT文件获取） -->
                    <div class="input-card">
                        <div class="card-header">
                            <h3 class="card-title">模态二数据：多光谱图像</h3>
                            <div class="card-subtitle">根据文件自动匹配</div>
                        </div>
                        <div class="card-content">
                            <!-- 修改处：为模态二预览添加专属类名 -->
                            <div class="image-preview msi-preview" v-if="imageUrl2">
                                <img :src="imageUrl2" class="preview-image preview-image--msi" alt="多光谱图像" />
                            </div>

                            <div class="empty-state" v-else>
                                <div class="empty-icon">📊</div>
                                <h4>等待文件</h4>
                                <p>上传文件后将自动加载对应BMP图像</p>
                            </div>

                            <div class="file-info" v-if="imageUrl2">
                                <el-tag type="success" class="file-tag">{{ pngFileName }}</el-tag>
                            </div>

                            <div class="image-info" v-if="imageInfo2">
                                <el-card class="info-card">
                                    <div class="info-content">
                                        <div class="info-item">
                                            <span class="info-label">图像尺寸:</span>
                                            <span class="info-value">{{ imageInfo2.resolution }}</span>
                                        </div>
                                        <!--div class="info-item">
                                            <span class="info-label">文件格式:</span>
                                            <span class="info-value">{{ imageInfo2.format }}</span>
                                        </!div>
                                        <div class="info-item">
                                            <span class="info-label">文件大小:</span>
                                            <span class="info-value">{{ imageInfo2.file_size }}</span>
                                        </div-->
                                        <div class="info-item" v-if="imageInfo2.mode">
                                            <span class="info-label">色彩模式:</span>
                                            <span class="info-value">{{ imageInfo2.mode }}</span>
                                        </div>
                                    </div>
                                </el-card>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 右侧：融合结果和光谱可视化 -->
                <div class="result-section">
                    <!-- 融合结果图像 -->
                    <div class="result-card">
                        <div class="card-header">
                            <h3 class="card-title">融合结果</h3>
                        </div>
                        <div class="card-content">
                            <div class="result-preview" v-if="imageUrl3">
                                <div class="image-wrapper" ref="imgWrapper">
                                    <img :src="imageUrl3"
                                         class="result-image"
                                         alt="融合结果"
                                         ref="fusionImg"
                                         @mousemove="onMouseMove"
                                         @mouseleave="onMouseLeave" />
                                    <div v-if="showCrosshair"
                                         class="crosshair"
                                         :style="crosshairStyle"></div>
                                </div>
                                <div class="coordinate-info">
                                    <el-tag type="info">
                                        坐标: ({{ currentCoords ? currentCoords.x : '-' }}, {{ currentCoords ? currentCoords.y : '-' }})
                                    </el-tag>
                                </div>
                            </div>

                            <div class="empty-state" v-else>
                                <div class="empty-icon">🖼️</div>
                                <h4>等待融合结果</h4>
                                <p>上传文件后点击开始融合</p>
                            </div>

                            <!-- 光谱曲线区域 -->
                            <div class="spectrum-section" v-if="imageUrl3">
                                <div class="spectrum-info" v-if="spectrumData.length > 0">
                                波段数: {{ totalBands }}
                                </div>
                                <div ref="chart" class="spectrum-chart"></div>
                                <div class="spectrum-footer">
                                    <div class="spectrum-stats">
                                        <div class="stat-item">
                                            <span class="stat-label">最小值:</span>
                                            <span class="stat-value">{{ minSpectrumValue.toFixed(4) }}</span>
                                        </div>
                                        <div class="stat-item">
                                            <span class="stat-label">最大值:</span>
                                            <span class="stat-value">{{ maxSpectrumValue.toFixed(4) }}</span>
                                        </div>
                                        <div class="stat-item">
                                            <span class="stat-label">平均值:</span>
                                            <span class="stat-value">{{ avgSpectrumValue.toFixed(4) }}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="result-info" v-if="fusionResultInfo">
                                <el-card class="info-card">
                                    <div class="info-content">
                                        <div class="info-item">
                                            <span class="info-label">图像尺寸:</span>
                                            <span class="info-value">{{ fusionResultInfo.resolution }}</span>
                                        </div>
                                        <div class="info-item">
                                            <span class="info-label">光谱波段数:</span>
                                            <span class="info-value">{{ fusionResultInfo.bands }}</span>
                                        </div>
                                        <!--div class="info-item">
                                            <span class="info-label">文件大小:</span>
                                            <span class="info-value">{{ fusionResultInfo.file_size }}</span>
                                        </!div-->
                                    </div>
                                </el-card>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 融合按钮 -->
            <div class="action-section">
                <el-button type="primary"
                           @click="startFusion"
                           class="fusion-button"
                           :disabled="!selectedFile1 || (!isRawSelected && !selectedFile2) || loading"
                           :loading="loading">
                    <i class="el-icon-magic-stick"></i>
                    {{ loading ? '融合中...' : '开始融合' }}
                </el-button>
            </div>
        </div>
    </div>
</template>

<script>
    export default {
        name: "ImageFusion",
        data() {
            return {
                loading: false,
                matLoading: false,
                isRawSelected: false,
                previewUrl1: "",
                imageUrl2: "", // 模态二PNG图像（通过base64获取）
                imageUrl3: "", // 融合结果图像
                imageName1: "",
                pngFileName: "", // PNG文件名
                selectedFile1: null, // MAT文件
                selectedFile2: null, // PNG文件（通过base64转换）
                matInfo1: null,
                imageInfo2: null, // PNG图像信息
                fusionResultInfo: null,

                // 光谱可视化相关
                showCrosshair: false,
                crosshairStyle: {},
                currentCoords: null,
                spectrumData: [],
                minSpectrumValue: 0,
                maxSpectrumValue: 0,
                avgSpectrumValue: 0,
                chart: null,
                bands: [],
                fusionWidth: 0,
                fusionHeight: 0,
                echarts: null,
                totalBands: 0,
                pendingSpectrumCoords: null,
                spectrumRequestTimer: null,
                spectrumRequestInFlight: false,
                spectrumAbortController: null,
                lastSpectrumKey: ""
            };
        },
        methods: {
            beforeUploadFile(file) {
                const isHdr = file.name.toLowerCase().endsWith('.hdr');
                const isImg = file.name.toLowerCase().endsWith('.img');
                if (!isHdr && !isImg) {
                    this.$message.error('只支持选择 .hdr 或 .img 文件');
                    return false;
                }
                return true;
            },

            async handleFileUpload(file) {
                if (!file) return;

                this.isRawSelected = /\.(hdr|img)$/i.test(file.name);
                this.imageName1 = file.name;
                this.selectedFile1 = file.raw;
                this.selectedFile2 = null;
                this.matInfo1 = null;
                this.previewUrl1 = "";
                this.imageUrl2 = "";
                this.matLoading = true;

                try {
                    await this.loadRawData(file);
                } catch (err) {
                    console.error(err);
                    this.$message.error("文件处理失败");
                    this.previewUrl1 = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='200' viewBox='0 0 200 200'%3E%3Crect width='200' height='200' fill='%23f0f0f0'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' font-family='Arial' font-size='14' fill='%23999'%3E预览加载失败%3C/text%3E%3C/svg%3E";
                } finally {
                    this.matLoading = false;
                }
            },

            async loadRawData(file) {
                const formData = new FormData();
                const hdrName = file.name.replace(/\.(hdr|img)$/i, '.hdr');
                formData.append("hdr_name", hdrName);
                formData.append("hdr_file", file.raw, file.name);

                const response = await fetch("http://localhost:8000/api/load-hyperspectral-data/upload", {
                    method: "POST",
                    body: formData,
                });

                if (!response.ok) throw new Error("原始高光谱数据加载失败");

                const data = await response.json();
                if (!data.success) throw new Error(data.message || "原始高光谱数据加载失败");

                this.previewUrl1 = `data:image/png;base64,${data.pseudo_color_image}`;
                this.imageUrl2 = `data:image/png;base64,${data.original_image}`;
                this.matInfo1 = {
                    resolution: `${data.original_width}\u00d7${data.original_height}`,
                    total_bands: data.total_bands,
                    wavelength_range: data.total_bands > 0 ? `${data.bands[0]}nm - ${data.bands[data.bands.length-1]}nm` : ""
                };
                this.totalBands = data.total_bands;

                const bandsText = data.total_bands > 0 ? ` (${data.total_bands}波段)` : "";
                this.$message.success(`原始数据加载成功${bandsText}，已自动匹配 HDR、IMG 和可见光图像`);
            },

            async loadMatData(file) {
                // 1. 获取MAT文件预览和信息
                const previewFormData = new FormData();
                previewFormData.append("target_var", 'hyperspectral_data');
                previewFormData.append("mat_file", file.raw);

                const previewResponse = await fetch("http://localhost:8000/get_mat_preview", {
                    method: "POST",
                    body: previewFormData,
                });

                if (!previewResponse.ok) throw new Error("预览图生成失败");

                const previewData = await previewResponse.json();
                this.previewUrl1 = previewData.preview_url;
                this.matInfo1 = previewData.mat_info;

                // 2. 根据MAT文件获取对应的PNG文件（base64格式）
                await this.getPngFromMat(file.raw);

                this.$message.success("MAT文件加载成功，已找到对应BMP图像");
            },

            // 根据MAT文件获取PNG文件（base64格式）
            async getPngFromMat(matFile) {
                try {
                    const formData = new FormData();
                    formData.append("file", matFile);

                    // 获取PNG文件（base64格式）
                    const response = await fetch("http://localhost:8000/get_png_by_mat", {
                        method: "POST",
                        body: formData,
                    });

                    if (!response.ok) {
                        throw new Error(`获取BMP文件失败: ${response.status}`);
                    }

                    const data = await response.json();
                    console.log("获取到的BMP数据:", data);

                    // 处理base64数据
                    if (data.png_base64) {
                        // 创建base64 URL用于显示
                        const base64Url = `data:image/png;base64,${data.png_base64}`;
                        this.imageUrl2 = base64Url;
                        this.pngFileName = data.filename || 'matched_image.png';
                        this.imageInfo2 = data.png_info;

                        // 将base64转换为File对象，用于后续发送到融合接口
                        await this.convertBase64ToFile(data.png_base64, data.filename || 'matched_image.png');
                    } else {
                        throw new Error("未获取到BMP数据");
                    }

                } catch (error) {
                    console.error("获取BMP文件失败:", error);
                    this.$message.error("获取对应BMP图像失败: " + error.message);
                }
            },

            // 将base64转换为File对象
            async convertBase64ToFile(base64String, filename) {
                try {
                    // 将base64字符串转换为blob
                    const response = await fetch(`data:image/png;base64,${base64String}`);
                    const blob = await response.blob();

                    // 将blob转换为File对象
                    this.selectedFile2 = new File([blob], filename, { type: 'image/png' });
                    console.log("转换后的File对象:", this.selectedFile2);

                } catch (error) {
                    console.error("转换base64到File失败:", error);
                    throw error;
                }
            },

            async startFusion() {
                if (!this.selectedFile1) {
                    this.$message.warning("请先选择文件");
                    return;
                }
                if (!this.isRawSelected && !this.selectedFile2) {
                    this.$message.warning("请先上传MAT文件并确保找到对应BMP图像");
                    return;
                }

                this.loading = true;
                this.fusionResultInfo = null;
                this.imageUrl3 = "";
                this.spectrumData = [];
                this.resetSpectrumQueryState();

                const formData = new FormData();
                formData.append("file1", this.selectedFile1);
                if (this.selectedFile2) {
                    formData.append("file2", this.selectedFile2);
                }

                try {
                    this.$message.info("开始图像融合处理...");

                    const response = await fetch("http://localhost:8000/start_fusion_1", {
                        method: "POST",
                        body: formData,
                    });

                    if (!response.ok) {
                        const errorData = await response.json().catch(() => null);
                        const detail = errorData && errorData.detail ? errorData.detail : `HTTP ${response.status}`;
                        throw new Error(detail);
                    }

                    const data = await response.json();
                    this.imageUrl3 = data.image_url;
                    this.fusionResultInfo = data.fusion_info;

                    // 获取融合图像尺寸信息
                    await this.loadFusionInfo();

                    // 初始化图表
                    this.$nextTick(() => {
                        this.initChart();
                    });

                    this.$message.success("图像融合成功！");
                } catch (err) {
                    console.error(err);
                    this.$message.error(`融合失败：${err.message || '请检查文件格式或后端日志'}`);
                } finally {
                    this.loading = false;
                }
            },

            async loadFusionInfo() {
                try {
                    const response = await fetch('http://localhost:8000/api/fusion-info');
                    if (response.ok) {
                        const data = await response.json();
                        this.fusionWidth = data.width;
                        this.fusionHeight = data.height;
                        this.totalBands = data.bands;
                        // 生成波段数组
                        const step = Math.round(1301 / this.totalBands);
                        this.bands = Array.from({ length: this.totalBands }, (_, i) => 400 + i * step);
                    }
                } catch (err) {
                    console.error('获取融合信息失败:', err);
                }
            },

            async initChart() {
                // 动态导入echarts
                if (!this.echarts) {
                    try {
                        this.echarts = await import('echarts');
                    } catch (error) {
                        console.error('Failed to load echarts:', error);
                        return;
                    }
                }

                if (this.chart) {
                    this.chart.dispose();
                }

                this.chart = this.echarts.init(this.$refs.chart);
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
                                return `波段 ${params[0].axisValue}: ${params[0].value.toFixed(4)}`;
                            }
                            return '';
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
                };
                this.chart.setOption(option);
            },

            onMouseMove(event) {
                if (!this.imageUrl3) return;

                const imgEl = this.$refs.fusionImg;
                const wrapperEl = this.$refs.imgWrapper;

                if (!imgEl || !wrapperEl) return;

                const imgRect = imgEl.getBoundingClientRect();
                const wrapperRect = wrapperEl.getBoundingClientRect();

                const relativeX = event.clientX - imgRect.left;
                const relativeY = event.clientY - imgRect.top;

                if (relativeX < 0 || relativeY < 0 || relativeX > imgRect.width || relativeY > imgRect.height) {
                    return;
                }

                // 转换为融合图像原始坐标
                const x = Math.floor((relativeX / imgRect.width) * this.fusionWidth);
                const y = Math.floor((relativeY / imgRect.height) * this.fusionHeight);

                const validX = Math.max(0, Math.min(x, this.fusionWidth - 1));
                const validY = Math.max(0, Math.min(y, this.fusionHeight - 1));

                // 更新十字线位置
                this.showCrosshair = true;
                this.crosshairStyle = {
                    left: `${event.clientX - wrapperRect.left}px`,
                    top: `${event.clientY - wrapperRect.top}px`
                };

                this.currentCoords = { x: validX, y: validY };
                this.queueFusionSpectrum(validX, validY);
            },

            onMouseLeave() {
                this.showCrosshair = false;
                this.currentCoords = null;
                this.pendingSpectrumCoords = null;
            },

            queueFusionSpectrum(x, y) {
                const key = `${x}:${y}`;
                if (key === this.lastSpectrumKey && !this.pendingSpectrumCoords) {
                    return;
                }
                this.pendingSpectrumCoords = { x, y };
                if (this.spectrumRequestTimer !== null || this.spectrumRequestInFlight) {
                    return;
                }
                this.spectrumRequestTimer = window.setTimeout(async () => {
                    this.spectrumRequestTimer = null;
                    const coords = this.pendingSpectrumCoords;
                    this.pendingSpectrumCoords = null;
                    if (!coords) return;

                    this.spectrumRequestInFlight = true;
                    try {
                        await this.fetchFusionSpectrum(coords.x, coords.y);
                    } finally {
                        this.spectrumRequestInFlight = false;
                        if (this.pendingSpectrumCoords) {
                            this.queueFusionSpectrum(
                                this.pendingSpectrumCoords.x,
                                this.pendingSpectrumCoords.y
                            );
                        }
                    }
                }, 75);
            },

            async fetchFusionSpectrum(x, y) {
                const requestKey = `${x}:${y}`;
                this.spectrumAbortController = new AbortController();
                try {
                    const response = await fetch(
                        `http://localhost:8000/api/fusion-spectrum?x=${x}&y=${y}`,
                        { signal: this.spectrumAbortController.signal }
                    );
                    if (response.ok) {
                        const data = await response.json();
                        const currentKey = this.currentCoords
                            ? `${this.currentCoords.x}:${this.currentCoords.y}`
                            : "";
                        if (requestKey !== currentKey) return;
                        if (data.spectrum && data.spectrum.length > 0) {
                            this.lastSpectrumKey = requestKey;
                            this.spectrumData = data.spectrum;

                            // 计算光谱数据统计
                            this.minSpectrumValue = Math.min(...this.spectrumData);
                            this.maxSpectrumValue = Math.max(...this.spectrumData);
                            this.avgSpectrumValue = this.spectrumData.reduce((a, b) => a + b, 0) / this.spectrumData.length;

                            if (this.chart) {
                                this.chart.setOption({
                                    xAxis: {
                                        data: this.bands
                                    },
                                    series: [{
                                        data: this.spectrumData
                                    }]
                                });
                            }
                        }
                    }
                } catch (err) {
                    if (err.name !== 'AbortError') {
                        console.error('获取融合光谱数据失败:', err);
                    }
                } finally {
                    this.spectrumAbortController = null;
                }
            },

            resetSpectrumQueryState() {
                this.pendingSpectrumCoords = null;
                this.lastSpectrumKey = "";
                if (this.spectrumRequestTimer !== null) {
                    window.clearTimeout(this.spectrumRequestTimer);
                    this.spectrumRequestTimer = null;
                }
                if (this.spectrumAbortController) {
                    this.spectrumAbortController.abort();
                    this.spectrumAbortController = null;
                }
            },

            handleResize() {
                if (this.chart) {
                    this.chart.resize();
                }
            }
        },

        activated() {
            this.$nextTick(() => {
                if (!this.imageUrl3) return;
                if (this.chart) {
                    this.chart.resize();
                } else {
                    this.initChart();
                }
            });
        },

        beforeDestroy() {
            this.resetSpectrumQueryState();
            if (this.chart) {
                this.chart.dispose();
            }
        }
    };
</script>

<style scoped>
    /* 样式保持不变，与之前相同 */
    .fusion-page {
        display: flex;
        justify-content: center;
        padding: 20px;
        background: #f5f7fa;
        min-height: 100vh;
        font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;
    }

    .fusion-container {
        width: 100%;
        max-width: 1400px;
        background: #fff;
        border-radius: 16px;
        padding: 30px;
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
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

    .fusion-content {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 30px;
        margin-bottom: 30px;
        align-items: start;
    }

    .input-section {
        display: flex;
        flex-direction: column;
        gap: 25px;
    }

    .input-card {
        background: #fafafa;
        border: 2px dashed #e2e8f0;
        border-radius: 12px;
        padding: 25px;
        transition: all 0.3s ease;
        position: relative;
    }

    .result-section {
        height: 100%;
    }

    .result-card {
        background: #fafafa;
        border: 2px dashed #e2e8f0;
        border-radius: 12px;
        padding: 25px;
        transition: all 0.3s ease;
        height: calc(100% - 0px);
        display: flex;
        flex-direction: column;
        box-sizing: border-box;
    }

        .input-card:hover, .result-card:hover {
            border-color: #667eea;
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.1);
        }

    .card-header {
        margin-bottom: 20px;
        text-align: center;
    }

    .card-title {
        font-size: 18px;
        color: #2d3748;
        font-weight: 600;
        margin: 0;
    }

    .card-subtitle {
        font-size: 14px;
        color: #718096;
        margin-top: 5px;
        font-style: italic;
    }

    .card-content {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 20px;
        flex: 1;
    }

    .image-upload {
        width: 100%;
        text-align: center;
    }

    .upload-btn {
        padding: 12px 24px;
        border-radius: 8px;
        font-weight: 600;
        width: 100%;
        max-width: 200px;
    }

    .image-preview {
        width: 100%;
        max-width: 300px;
        display: flex;
        justify-content: center;
    }

    .preview-image {
        max-width: 100%;
        max-height: 250px;
        border-radius: 8px;
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
        border: 1px solid #e9ecef;
        transition: transform 0.3s ease;
    }

        .preview-image:hover {
            transform: scale(1.02);
        }

    /* -------- 修改新增：仅放大模态二（MSI）的展示 -------- */
    .msi-preview {
        max-width: 480px; /* 原300px，放大容器宽度 */
    }

    .preview-image--msi {
        max-width: 100%;
        max-height: 400px; /* 原250px，增大纵向 */
        border-radius: 10px;
    }

    @media (max-width: 1024px) {
        .msi-preview {
            max-width: 420px;
        }

        .preview-image--msi {
            max-height: 320px;
        }
    }

    @media (max-width: 768px) {
        .msi-preview {
            max-width: 100%;
        }

        .preview-image--msi {
            max-height: 280px;
        }
    }
    /* ---------------------------------------------------- */

    .result-preview {
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 10px;
        flex: 1;
    }

    .image-wrapper {
        position: relative;
        display: flex;
        justify-content: center;
        width: 100%;
    }

    .result-image {
        max-width: 100%;
        max-height: 400px;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        border: 2px solid #e9ecef;
        cursor: crosshair;
        transition: transform 0.2s ease;
    }

        .result-image:hover {
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

    .coordinate-info {
        text-align: center;
    }

    .file-info {
        width: 100%;
        text-align: center;
    }

    .file-tag {
        padding: 8px 16px;
        font-size: 14px;
        max-width: 80%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .empty-state {
        text-align: center;
        padding: 40px 20px;
        color: #6c757d;
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        height: 100%;
    }

    .empty-icon {
        font-size: 3em;
        margin-bottom: 15px;
        opacity: 0.7;
    }

    .empty-state h4 {
        margin: 0 0 10px 0;
        color: #495057;
        font-size: 1.2em;
    }

    .empty-state p {
        margin: 0;
        font-size: 0.9em;
    }

    .spectrum-section {
        width: 100%;
        margin-top: 20px;
        padding: 20px;
        background: #f8fafc;
        border-radius: 12px;
        border: 1px solid #e2e8f0;
    }

    .spectrum-info {
        font-size: 14px;
        color: #718096;
        padding: 4px 12px;
        border-radius: 20px;
        font-weight: 500;
        align-self: flex-end;
        margin-bottom: 16px;
        background: #e2e8f0;
    }

    .spectrum-chart {
        width: 100%;
        height: 400px;
        border-radius: 12px;
        background: #ffffff;
        box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.05);
    }

    .spectrum-footer {
        display: flex;
        justify-content: center;
        align-items: center;
        margin-top: 15px;
        padding-top: 15px;
        border-top: 1px solid #e2e8f0;
        width: 100%;
    }

    .spectrum-stats {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 30px;
        width: 100%;
        max-width: 500px;
    }

    .stat-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        flex: 1;
    }

    .stat-label {
        font-size: 11px;
        color: #6c757d;
        margin-bottom: 2px;
    }

    .stat-value {
        font-size: 12px;
        font-weight: 600;
        color: #495057;
    }

    .image-info, .result-info {
        width: 100%;
    }

    .info-card {
        background: #f8f9fa;
        border: 1px solid #e9ecef;
        border-radius: 8px;
    }

    .info-content {
        padding: 15px;
    }

    .info-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
        padding: 5px 0;
        border-bottom: 1px solid #f1f3f4;
    }

        .info-item:last-child {
            margin-bottom: 0;
            border-bottom: none;
        }

    .info-label {
        color: #6c757d;
        font-weight: 500;
        font-size: 14px;
    }

    .info-value {
        color: #495057;
        font-weight: 600;
        font-size: 14px;
    }

    .action-section {
        display: flex;
        justify-content: center;
        margin-top: 20px;
    }

    .fusion-button {
        padding: 14px 40px;
        font-size: 16px;
        font-weight: 600;
        border-radius: 10px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border: none;
        box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
        transition: all 0.3s ease;
        color: white;
    }

        .fusion-button:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
        }

        .fusion-button:disabled {
            opacity: 0.6;
            cursor: not-allowed;
            transform: none;
        }

    /* 响应式设计 */
    @media (max-width: 1024px) {
        .fusion-content {
            grid-template-columns: 1fr;
            gap: 20px;
        }

        .fusion-container {
            padding: 20px;
        }

        .result-card {
            height: auto;
        }

        .spectrum-stats {
            flex-wrap: wrap;
            justify-content: space-around;
            gap: 20px;
        }

        .spectrum-chart {
            height: 350px;
        }
    }

    @media (max-width: 768px) {
        .fusion-page {
            padding: 10px;
        }

        .page-title {
            font-size: 24px;
        }

        .input-card, .result-card {
            padding: 20px;
        }

        .preview-image {
            max-height: 200px;
        }

        .result-image {
            max-height: 300px;
        }

        .spectrum-section {
            padding: 15px;
        }

        .spectrum-footer {
            flex-direction: column;
            gap: 10px;
        }

        .spectrum-stats {
            width: 100%;
            justify-content: space-between;
        }

        .spectrum-chart {
            height: 300px;
        }
    }

    @media (max-width: 480px) {
        .card-title {
            font-size: 16px;
        }

        .spectrum-info {
            font-size: 12px;
        }

        .stat-label {
            font-size: 10px;
        }

        .stat-value {
            font-size: 11px;
        }

        .spectrum-stats {
            flex-direction: column;
            gap: 10px;
        }

        .file-tag {
            max-width: 95%;
        }
    }
</style>
