<template>
    <div class="spectrum-library-container">
        <h1 class="page-title">伪装材料光谱库</h1>

        <!-- 光谱详情模态框 -->
        <el-dialog :visible.sync="detailDialogVisible"
                   width="85%"
                   top="5vh"
                   :close-on-click-modal="false"
                   custom-class="spectrum-dialog"
                   @closed="resetDetail">
            <div class="spectrum-detail-wrapper">
                <h2 class="detail-title">{{ currentSpectrum.name }}</h2>

                <!-- 详细光谱图 -->
                <div class="spectrum-chart-container">
                    <canvas ref="detailChart" class="spectrum-canvas"></canvas>
                </div>

                <!-- 材料描述 -->
                <div class="spectrum-description">
                    <h3>材料描述</h3>
                    <div class="description-content">
                        <div class="description-item">
                            <label>材料编号：</label>
                            <span>{{ currentSpectrum.code }}</span>
                        </div>
                        <div class="description-item">
                            <label>波长范围：</label>
                            <span>{{ currentSpectrum.wavelengthRange }}</span>
                        </div>
                        <div class="description-item">
                            <label>测量条件：</label>
                            <span>{{ currentSpectrum.conditions }}</span>
                        </div>
                        <div class="description-item">
                            <label>采集日期：</label>
                            <span>{{ formatDate(currentSpectrum.date) }}</span>
                        </div>
                        <div class="description-item full-width">
                            <label>材料特性：</label>
                            <p>{{ currentSpectrum.description }}</p>
                        </div>
                        <div class="description-item full-width">
                            <label>应用场景：</label>
                            <p>{{ currentSpectrum.applications }}</p>
                        </div>
                    </div>
                </div>

                <!-- 数据统计 -->
                <div class="spectrum-stats">
                    <div class="stat-item main-spectrum">
                        <span class="stat-label">光谱1 - 峰值波长</span>
                        <span class="stat-value">{{ currentSpectrum.peakWavelength }} nm</span>
                    </div>
                    <div class="stat-item main-spectrum">
                        <span class="stat-label">光谱1 - 最大反射率</span>
                        <span class="stat-value">{{ currentSpectrum.maxReflectance }}%</span>
                    </div>
                    <div class="stat-item comparison-spectrum" v-if="currentSpectrum.spectrumData2">
                        <span class="stat-label">光谱2 - 峰值波长</span>
                        <span class="stat-value">{{ currentSpectrum.peakWavelength2 }} nm</span>
                    </div>
                    <div class="stat-item comparison-spectrum" v-if="currentSpectrum.spectrumData2">
                        <span class="stat-label">光谱2 - 最大反射率</span>
                        <span class="stat-value">{{ currentSpectrum.maxReflectance2 }}%</span>
                    </div>
                </div>
            </div>
        </el-dialog>

        <!-- 光谱列表 -->
        <div class="spectrum-list">
            <div v-for="spectrum in spectrums"
                 :key="spectrum.id"
                 class="spectrum-item"
                 @click="viewDetail(spectrum)">
                <div class="spectrum-thumbnail-wrapper">
                    <div class="spectrum-thumbnail">
                        <img v-if="spectrum.thumbnail"
                             :src="spectrum.thumbnail"
                             class="thumbnail-image"
                             alt="光谱预览图"
                             @error="onThumbnailError(spectrum)">
                        <div v-else class="thumbnail-placeholder">
                            <i class="el-icon-picture-outline"></i>
                        </div>
                        <div class="view-icon">
                            <i class="el-icon-zoom-in"></i>
                        </div>
                    </div>
                    <div class="spectrum-info">
                        <h3 class="spectrum-name">{{ spectrum.name }}</h3>
                        <div class="spectrum-code">编号: {{ spectrum.code }}</div>
                        <div class="spectrum-brief">{{ spectrum.brief }}</div>
                        <div v-if="spectrum.spectrumData2" class="spectrum-has-comparison">
                            <el-tag size="mini" type="success">双光谱对比</el-tag>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-container">
            <i class="el-icon-loading"></i>
            <span>加载光谱数据中...</span>
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

    export default {
        name: 'SpectrumLibrary',
        data() {
            return {
                spectrums: [],
                loading: true,
                errorMessage: '',
                detailDialogVisible: false,
                currentSpectrum: {},
                basePath: '',
                // 配置项：可以轻松修改的数据源
                config: {
                    // 光谱数据JSON文件路径
                    dataJsonPath: '/static/spectrum-data.json',
                    // 概述图文件夹路径
                    thumbnailFolder: '/static/spectrum-thumbnails/',
                    // 概述图文件扩展名
                    thumbnailExtension: '.png'
                },
                // 图表配置
                chartConfig: {
                    minWavelength: 400,  // 最小波长
                    maxWavelength: 1100, // 最大波长
                    wavelengthRange: 700 // 波长范围 (1100 - 400 = 700)
                }
            };
        },
        mounted() {
            this.initBasePath();
            this.loadSpectrumData();
        },
        methods: {
            // 初始化基础路径
            initBasePath() {
                if (process.env.NODE_ENV === 'production') {
                    if (typeof window !== 'undefined' && window.require) {
                        try {
                            const { remote, app } = window.require('electron');
                            const electronApp = remote ? remote.app : app;
                            if (electronApp) {
                                const appPath = electronApp.getAppPath();
                                this.basePath = appPath.replace(/\\/g, '/');
                                console.log('Electron环境，基础路径:', this.basePath);
                                return;
                            }
                        } catch (e) {
                            console.warn('获取Electron路径失败:', e);
                        }
                    }
                    this.basePath = process.env.BASE_URL || '.';
                } else {
                    this.basePath = '';
                }
                console.log('当前基础路径:', this.basePath);
            },

            // 加载光谱数据
            async loadSpectrumData() {
                try {
                    this.loading = true;
                    this.errorMessage = '';

                    // 尝试加载JSON配置文件
                    try {
                        const response = await axios.get(this.basePath + this.config.dataJsonPath);
                        if (response.data && Array.isArray(response.data)) {
                            this.spectrums = response.data.map(item => this.processSpectrumItem(item));
                            console.log(`从JSON加载了 ${this.spectrums.length} 个光谱数据`);
                        } else {
                            throw new Error('JSON格式错误');
                        }
                    } catch (e) {
                        console.warn('加载JSON失败，使用模拟数据:', e);
                        // 如果JSON加载失败，使用模拟数据
                        this.spectrums = this.generateMockData();
                    }

                    this.loading = false;
                } catch (error) {
                    console.error('加载光谱数据失败:', error);
                    this.loading = false;
                    this.errorMessage = '加载光谱数据失败: ' + error.message;
                }
            },

            // 处理单个光谱数据项
            processSpectrumItem(item) {
                // 生成缩略图路径（根据材料名称匹配）
                const thumbnailPath = this.getThumbnailPath(item.name || item.code);

                // 处理主光谱数据 - 过滤波长范围并转换反射率为百分数
                const filteredSpectrumData = this.filterAndConvertSpectrumData(item.spectrumData);
                const stats = this.calculateStats(filteredSpectrumData);

                // 处理第二条光谱数据（如果存在）- 同样过滤和转换
                let filteredSpectrumData2 = null;
                let spectrumData2Stats = {};
                if (item.spectrumData2) {
                    filteredSpectrumData2 = this.filterAndConvertSpectrumData(item.spectrumData2);
                    spectrumData2Stats = this.calculateStats(filteredSpectrumData2);
                }

                return {
                    id: item.id || Math.random().toString(36).substr(2, 9),
                    name: item.name || '材料',
                    code: item.code || 'MAT-000',
                    brief: item.brief || '伪装材料光谱特征数据',
                    description: item.description || '该材料具有独特的光谱反射特性，适用于伪装应用场景。',
                    applications: item.applications || '军事伪装、目标隐蔽、光学欺骗等领域',
                    wavelengthRange: '400-1100 nm', // 更新波长范围显示
                    conditions: item.conditions || '室温 25°C，标准光源',
                    date: item.date || new Date().toISOString(),
                    thumbnail: thumbnailPath,
                    spectrumData: filteredSpectrumData,
                    spectrumData2: filteredSpectrumData2, // 使用过滤和转换后的数据
                    peakWavelength: stats.peakWavelength,
                    maxReflectance: stats.maxReflectance,
                    avgReflectance: stats.avgReflectance,
                    dataPoints: filteredSpectrumData.length,
                    // 第二条光谱的统计数据
                    peakWavelength2: spectrumData2Stats.peakWavelength || 0,
                    maxReflectance2: spectrumData2Stats.maxReflectance || '0.00',
                    avgReflectance2: spectrumData2Stats.avgReflectance || '0.00'
                };
            },

            // 过滤波长范围并转换反射率为百分数
            filterAndConvertSpectrumData(spectrumData) {
                if (!spectrumData || !Array.isArray(spectrumData)) {
                    return [];
                }

                return spectrumData
                    .filter(point =>
                        point.wavelength >= this.chartConfig.minWavelength &&
                        point.wavelength <= this.chartConfig.maxWavelength
                    )
                    .map(point => ({
                        wavelength: point.wavelength,
                        reflectance: point.reflectance * 100 // 转换为百分数
                    }));
            },

            // 获取缩略图路径
            getThumbnailPath(materialName) {
                // 支持多种命名方式匹配
                // 例如：材料一.png, 材料1.png, MAT-001.png 等
                const possibleNames = [
                    materialName,
                    materialName.replace('材料', ''),
                    materialName.replace('材料', 'MAT-'),
                ];

                // 返回第一个可能的路径（实际使用时会通过error事件判断是否存在）
                return this.basePath + this.config.thumbnailFolder + materialName + this.config.thumbnailExtension;
            },

            // 生成模拟数据（用于演示）
            generateMockData() {
                const materials = [];
                for (let i = 1; i <= 12; i++) {
                    const spectrumData = this.generateSpectrumCurve(i);
                    const filteredSpectrumData = this.filterAndConvertSpectrumData(spectrumData);
                    const stats = this.calculateStats(filteredSpectrumData);

                    // 为部分材料生成第二条光谱数据
                    const hasSecondSpectrum = i <= 6; // 前6个材料有第二条光谱
                    let filteredSpectrumData2 = null;
                    if (hasSecondSpectrum) {
                        const spectrumData2 = this.generateSpectrumCurve(i + 0.5);
                        filteredSpectrumData2 = this.filterAndConvertSpectrumData(spectrumData2);
                    }

                    materials.push({
                        id: i,
                        name: `材料${this.numberToChinese(i)}`,
                        code: `MAT-${String(i).padStart(3, '0')}`,
                        brief: `第${this.numberToChinese(i)}号伪装材料光谱特征数据`,
                        description: `该伪装材料具有在可见光到近红外波段的独特反射特性，能够有效模拟自然背景的光谱特征。材料表面经过特殊处理，在不同波段表现出与目标背景相似的反射率曲线，可用于军事装备的光学伪装。`,
                        applications: `军事伪装网、装备涂层、目标隐蔽、反侦察系统、光学欺骗技术`,
                        wavelengthRange: '400-1100 nm',
                        conditions: '室温 25°C，标准光源 D65，入射角 45°',
                        date: new Date(Date.now() - Math.random() * 365 * 24 * 60 * 60 * 1000).toISOString(),
                        thumbnail: this.basePath + this.config.thumbnailFolder + `材料${this.numberToChinese(i)}` + this.config.thumbnailExtension,
                        spectrumData: filteredSpectrumData,
                        spectrumData2: filteredSpectrumData2,
                        peakWavelength: stats.peakWavelength,
                        maxReflectance: stats.maxReflectance,
                        avgReflectance: stats.avgReflectance,
                        dataPoints: filteredSpectrumData.length
                    });
                }
                return materials;
            },

            // 生成光谱曲线数据
            generateSpectrumCurve(seed = 1) {
                const data = [];
                const phase = seed * 0.5;

                for (let wavelength = 400; wavelength <= 1100; wavelength += 10) {
                    // 基础反射率（已经是0-1范围的小数）
                    let reflectance = 0.3 + Math.sin((wavelength - 400) / 200 + phase) * 0.15;

                    // 添加特征峰和谷
                    if (wavelength > 700 && wavelength < 900) {
                        reflectance += 0.15; // 近红外高原
                    }
                    if (wavelength > 900 && wavelength < 1000) {
                        reflectance -= 0.1; // 水吸收带
                    }

                    // 添加随机噪声
                    reflectance += (Math.random() - 0.5) * 0.03;

                    // 限制范围
                    reflectance = Math.max(0.05, Math.min(0.85, reflectance));

                    data.push({ wavelength, reflectance });
                }

                return data;
            },

            // 计算统计数据
            calculateStats(spectrumData) {
                let maxReflectance = 0;
                let peakWavelength = this.chartConfig.minWavelength;
                let totalReflectance = 0;

                spectrumData.forEach(point => {
                    totalReflectance += point.reflectance;
                    if (point.reflectance > maxReflectance) {
                        maxReflectance = point.reflectance;
                        peakWavelength = point.wavelength;
                    }
                });

                const avgReflectance = totalReflectance / spectrumData.length;

                return {
                    peakWavelength,
                    maxReflectance: maxReflectance.toFixed(2),
                    avgReflectance: avgReflectance.toFixed(2)
                };
            },

            // 数字转中文
            numberToChinese(num) {
                const chinese = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'];
                if (num <= 10) return chinese[num];
                if (num < 20) return '十' + chinese[num - 10];
                return chinese[Math.floor(num / 10)] + '十' + (num % 10 ? chinese[num % 10] : '');
            },

            // 渲染详细光谱图（支持两条曲线对比）
            renderDetailChart() {
                this.$nextTick(() => {
                    const canvas = this.$refs.detailChart;
                    if (!canvas) return;

                    const ctx = canvas.getContext('2d');
                    const width = canvas.width = canvas.offsetWidth;
                    const height = canvas.height = 500;

                    // 清空画布
                    ctx.clearRect(0, 0, width, height);

                    // 设置边距
                    const padding = { top: 50, right: 60, bottom: 70, left: 80 };
                    const chartWidth = width - padding.left - padding.right;
                    const chartHeight = height - padding.top - padding.bottom;

                    // 绘制背景
                    ctx.fillStyle = '#ffffff';
                    ctx.fillRect(0, 0, width, height);

                    // 绘制图表区域背景
                    ctx.fillStyle = '#fafafa';
                    ctx.fillRect(padding.left, padding.top, chartWidth, chartHeight);

                    // 绘制网格线和刻度
                    ctx.strokeStyle = '#e0e0e0';
                    ctx.lineWidth = 1;
                    ctx.fillStyle = '#666';
                    ctx.font = '12px Arial';

                    // Y轴（反射率）- 百分数
                    for (let i = 0; i <= 10; i++) {
                        const y = padding.top + (chartHeight / 10) * i;
                        const reflectance = 100 - i * 10;

                        ctx.beginPath();
                        ctx.moveTo(padding.left, y);
                        ctx.lineTo(padding.left + chartWidth, y);
                        ctx.stroke();

                        ctx.textAlign = 'right';
                        ctx.fillText(reflectance + '%', padding.left - 10, y + 4);
                    }

                    // X轴（波长）- 400-1100nm范围
                    const wavelengthStep = 100; // 每100nm一个刻度
                    for (let wl = 400; wl <= 1100; wl += wavelengthStep) {
                        const x = padding.left + ((wl - this.chartConfig.minWavelength) / this.chartConfig.wavelengthRange) * chartWidth;

                        ctx.beginPath();
                        ctx.moveTo(x, padding.top);
                        ctx.lineTo(x, padding.top + chartHeight);
                        ctx.stroke();

                        ctx.textAlign = 'center';
                        ctx.fillText(wl + ' nm', x, padding.top + chartHeight + 25);
                    }

                    // 绘制坐标轴
                    ctx.strokeStyle = '#333';
                    ctx.lineWidth = 2;
                    ctx.beginPath();
                    ctx.moveTo(padding.left, padding.top);
                    ctx.lineTo(padding.left, padding.top + chartHeight);
                    ctx.lineTo(padding.left + chartWidth, padding.top + chartHeight);
                    ctx.stroke();

                    // 绘制轴标签
                    ctx.fillStyle = '#333';
                    ctx.font = 'bold 14px Arial';
                    ctx.textAlign = 'center';
                    ctx.fillText('波长 (nm)', padding.left + chartWidth / 2, height - 20);

                    ctx.save();
                    ctx.translate(25, padding.top + chartHeight / 2);
                    ctx.rotate(-Math.PI / 2);
                    ctx.fillText('反射率 (%)', 0, 0);
                    ctx.restore();

                    // 绘制主光谱曲线（spectrumData）- 注意：数据已经是百分数
                    const mainData = this.currentSpectrum.spectrumData;
                    ctx.strokeStyle = '#1890ff';
                    ctx.lineWidth = 2.5;
                    ctx.beginPath();

                    mainData.forEach((point, index) => {
                        const x = padding.left + ((point.wavelength - this.chartConfig.minWavelength) / this.chartConfig.wavelengthRange) * chartWidth;
                        const y = padding.top + chartHeight - (point.reflectance / 100) * chartHeight;

                        if (index === 0) {
                            ctx.moveTo(x, y);
                        } else {
                            ctx.lineTo(x, y);
                        }
                    });

                    ctx.stroke();

                    // 绘制第二条光谱曲线（spectrumData2，如果存在）- 数据已经是百分数
                    if (this.currentSpectrum.spectrumData2) {
                        const comparisonData = this.currentSpectrum.spectrumData2;
                        ctx.strokeStyle = '#ff4d4f';
                        ctx.lineWidth = 2.5;
                        ctx.setLineDash([5, 3]); // 虚线样式
                        ctx.beginPath();

                        comparisonData.forEach((point, index) => {
                            const x = padding.left + ((point.wavelength - this.chartConfig.minWavelength) / this.chartConfig.wavelengthRange) * chartWidth;
                            const y = padding.top + chartHeight - (point.reflectance / 100) * chartHeight;

                            if (index === 0) {
                                ctx.moveTo(x, y);
                            } else {
                                ctx.lineTo(x, y);
                            }
                        });

                        ctx.stroke();
                        ctx.setLineDash([]); // 重置虚线样式
                    }

                    // 绘制图例
                    ctx.fillStyle = '#333';
                    ctx.font = '14px Arial';
                    ctx.textAlign = 'left';

                    // 主光谱图例
                    ctx.fillStyle = '#1890ff';
                    ctx.fillRect(width - 200, padding.top - 30, 20, 3);
                    ctx.fillStyle = '#333';
                    ctx.fillText('光谱1', width - 170, padding.top - 25);

                    // 第二条光谱图例（如果存在）
                    if (this.currentSpectrum.spectrumData2) {
                        ctx.fillStyle = '#ff4d4f';
                        ctx.setLineDash([5, 3]);
                        ctx.beginPath();
                        ctx.moveTo(width - 200, padding.top - 10);
                        ctx.lineTo(width - 180, padding.top - 10);
                        ctx.stroke();
                        ctx.setLineDash([]);
                        ctx.fillStyle = '#333';
                        ctx.fillText('光谱2', width - 170, padding.top - 5);
                    }

                    // 绘制标题
                    ctx.fillStyle = '#333';
                    ctx.font = 'bold 16px Arial';
                    ctx.textAlign = 'center';
                    let title = this.currentSpectrum.name + ' - 光谱反射率曲线';
                    if (this.currentSpectrum.spectrumData2) {
                        title += ' (双光谱对比)';
                    }
                    ctx.fillText(title, width / 2, 30);
                });
            },

            // 查看详情
            viewDetail(spectrum) {
                this.currentSpectrum = spectrum;
                this.detailDialogVisible = true;
                this.$nextTick(() => {
                    this.renderDetailChart();
                });
            },

            resetDetail() {
                this.currentSpectrum = {};
            },

            onThumbnailError(spectrum) {
                console.warn('缩略图加载失败:', spectrum.thumbnail);
                spectrum.thumbnail = null;
            },

            formatDate(dateString) {
                try {
                    const date = new Date(dateString);
                    return date.toLocaleDateString('zh-CN', {
                        year: 'numeric',
                        month: '2-digit',
                        day: '2-digit'
                    });
                } catch (e) {
                    return '未知日期';
                }
            }
        }
    };
</script>

<style scoped>
    /* 样式部分保持不变，与之前相同 */
    .spectrum-library-container {
        padding: 40px;
        max-width: 1600px;
        margin: 0 auto;
        background: #f8f9fa;
        min-height: 100vh;
    }

    .page-title {
        text-align: center;
        margin-bottom: 50px;
        font-size: 32px;
        color: #2c3e50;
        font-weight: 600;
        letter-spacing: 0.5px;
    }

    .spectrum-list {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
        gap: 50px 40px;
        margin-top: 40px;
        padding: 20px;
    }

    .spectrum-item {
        border-radius: 16px;
        overflow: hidden;
        box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
        cursor: pointer;
        transition: all 0.35s ease;
        background: #fff;
        margin: 15px;
        border: 1px solid rgba(0, 0, 0, 0.05);
    }

        .spectrum-item:hover {
            transform: translateY(-10px);
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.18);
        }

    .spectrum-thumbnail-wrapper {
        display: flex;
        flex-direction: column;
        height: 100%;
    }

    .spectrum-thumbnail {
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

    .spectrum-item:hover .thumbnail-image {
        transform: scale(1.05);
    }

    .thumbnail-placeholder {
        font-size: 80px;
        color: rgba(108, 117, 125, 0.3);
    }

    .view-icon {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        font-size: 70px;
        color: rgba(255, 255, 255, 0.9);
        text-shadow: 0 0 15px rgba(0, 0, 0, 0.6);
        transition: all 0.3s ease;
        z-index: 2;
        opacity: 0;
    }

    .spectrum-item:hover .view-icon {
        opacity: 1;
        font-size: 75px;
        text-shadow: 0 0 20px rgba(0, 0, 0, 0.8);
    }

    .spectrum-info {
        padding: 25px;
        flex-grow: 1;
        display: flex;
        flex-direction: column;
    }

    .spectrum-name {
        margin: 0 0 10px 0;
        font-size: 20px;
        color: #343a40;
        line-height: 1.4;
        font-weight: 600;
    }

    .spectrum-code {
        font-size: 13px;
        color: #6c757d;
        font-family: 'Courier New', monospace;
        margin-bottom: 12px;
        padding-bottom: 12px;
        border-bottom: 1px solid #f1f3f4;
    }

    .spectrum-brief {
        font-size: 14px;
        color: #6c757d;
        line-height: 1.6;
        margin-top: auto;
    }

    .spectrum-has-comparison {
        margin-top: 10px;
    }

    .spectrum-detail-wrapper {
        padding: 20px;
    }

    .detail-title {
        text-align: center;
        font-size: 28px;
        color: #2c3e50;
        margin-bottom: 30px;
        font-weight: 600;
    }

    .spectrum-chart-container {
        background: #fff;
        border-radius: 12px;
        padding: 20px;
        margin-bottom: 30px;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    }

    .spectrum-canvas {
        width: 100%;
        height: 500px;
        display: block;
    }

    .spectrum-description {
        background: #fff;
        border-radius: 12px;
        padding: 25px;
        margin-bottom: 25px;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    }

        .spectrum-description h3 {
            font-size: 20px;
            color: #2c3e50;
            margin-bottom: 20px;
            padding-bottom: 12px;
            border-bottom: 2px solid #e9ecef;
        }

    .description-content {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 18px;
    }

    .description-item {
        display: flex;
        flex-direction: column;
    }

        .description-item.full-width {
            grid-column: 1 / -1;
        }

        .description-item label {
            font-weight: 600;
            color: #495057;
            margin-bottom: 6px;
            font-size: 14px;
        }

        .description-item span,
        .description-item p {
            color: #6c757d;
            line-height: 1.6;
            font-size: 14px;
            margin: 0;
        }

    .spectrum-stats {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 20px;
    }

    .stat-item {
        padding: 20px;
        border-radius: 12px;
        text-align: center;
        box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
    }

        .stat-item.main-spectrum {
            background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
        }

        .stat-item.comparison-spectrum {
            background: linear-gradient(135deg, #ff4d4f 0%, #cf1322 100%);
        }

    .stat-label {
        display: block;
        color: rgba(255, 255, 255, 0.9);
        font-size: 13px;
        margin-bottom: 8px;
    }

    .stat-value {
        display: block;
        color: #fff;
        font-size: 20px;
        font-weight: 600;
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
    @media (max-width: 1200px) {
        .spectrum-list {
            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
        }

        .spectrum-stats {
            grid-template-columns: repeat(2, 1fr);
        }
    }

    @media (max-width: 768px) {
        .spectrum-library-container {
            padding: 25px;
        }

        .spectrum-list {
            grid-template-columns: 1fr;
            gap: 35px 25px;
        }

        .page-title {
            font-size: 26px;
        }

        .description-content {
            grid-template-columns: 1fr;
        }

        .spectrum-stats {
            grid-template-columns: 1fr;
        }
    }

    @media (max-width: 480px) {
        .spectrum-library-container {
            padding: 20px;
        }

        .spectrum-thumbnail {
            height: 180px;
        }

        .spectrum-info {
            padding: 20px;
        }
    }
</style>

<style>
    /* 全局样式调整对话框 */
    .spectrum-dialog .el-dialog__body {
        padding: 25px;
        background: #f8f9fa;
    }

    .spectrum-dialog .el-dialog__header {
        padding: 20px 25px 10px;
    }

    .spectrum-dialog .el-dialog {
        border-radius: 12px;
        overflow: hidden;
    }
</style>