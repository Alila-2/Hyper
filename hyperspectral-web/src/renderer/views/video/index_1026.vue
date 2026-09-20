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
                    <div v-if="currentSpectrum.loadingData" class="data-loading">
                        <i class="el-icon-loading"></i>
                        <span>加载光谱数据中...</span>
                    </div>
                    <div v-else-if="!currentSpectrum.spectrumData" class="data-error">
                        <i class="el-icon-warning"></i>
                        <span>无法加载光谱数据</span>
                    </div>
                    <canvas v-else ref="detailChart" class="spectrum-canvas"></canvas>
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
                        <div class="description-item">
                            <label>数据类型：</label>
                            <span>{{ currentSpectrum.dataType === 'intensity' ? '光强' : '反射率' }}</span>
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
                <div class="spectrum-stats" v-if="currentSpectrum.spectrumData">
                    <div class="stat-item main-spectrum">
                        <span class="stat-label">峰值波长</span>
                        <span class="stat-value">{{ currentSpectrum.peakWavelength }} nm</span>
                    </div>
                    <div class="stat-item main-spectrum">
                        <span class="stat-label">最大值</span>
                        <span class="stat-value">{{ currentSpectrum.maxReflectance }}{{ currentSpectrum.dataUnit }}</span>
                    </div>
                    <div class="stat-item main-spectrum">
                        <span class="stat-label">平均值</span>
                        <span class="stat-value">{{ currentSpectrum.avgReflectance }}{{ currentSpectrum.dataUnit }}</span>
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
                        <img v-if="spectrum.thumbnail && spectrum.thumbnail !== 'error'"
                             :src="spectrum.thumbnail"
                             class="thumbnail-image"
                             alt="光谱预览图"
                             @error="onThumbnailError(spectrum)">
                        <div v-else class="thumbnail-placeholder">
                            <i class="el-icon-picture-outline"></i>
                            <span>暂无图片</span>
                        </div>
                        <div class="view-icon">
                            <i class="el-icon-zoom-in"></i>
                        </div>
                    </div>
                    <div class="spectrum-info">
                        <h3 class="spectrum-name">{{ spectrum.name }}</h3>
                        <div class="spectrum-code">编号: {{ spectrum.code }}</div>
                        <div class="spectrum-brief">{{ spectrum.brief }}</div>
                        <div class="spectrum-meta">
                            <el-tag size="mini" :type="spectrum.dataType === 'intensity' ? 'warning' : 'success'">
                                {{ spectrum.dataType === 'intensity' ? '光谱数据' : '光谱数据' }}
                            </el-tag>
                            <div class="data-status">
                                <el-tag v-if="!spectrum.dataLoaded && !spectrum.loadingData" size="mini" type="info">
                                    点击查看数据
                                </el-tag>
                                <el-tag v-else-if="spectrum.loadingData" size="mini" type="warning">
                                    加载中...
                                </el-tag>
                                <el-tag v-else size="mini" type="success">
                                    数据已加载
                                </el-tag>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-container">
            <i class="el-icon-loading"></i>
            <span>加载材料信息中...</span>
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
                    // 主JSON文件路径
                    dataJsonPath: '/static/spectrum-data.json',
                    // 光谱数据文件夹路径
                    spectrumDataFolder: '/static/spectrum-data/',
                    // 概述图文件夹路径
                    thumbnailFolder: '/static/spectrum-thumbnails/',
                    // 概述图文件扩展名
                    thumbnailExtension: '.png'
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
                    this.basePath = process.env.BASE_URL || '';
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

                    console.log('开始加载主JSON文件:', this.basePath + this.config.dataJsonPath);
                    
                    // 尝试加载主JSON配置文件
                    try {
                        const response = await axios.get(this.basePath + this.config.dataJsonPath, {
                            timeout: 10000
                        });
                        console.log('主JSON加载成功:', response.data);
                        
                        if (response.data && Array.isArray(response.data)) {
                            // 先加载基本信息，光谱数据按需加载
                            this.spectrums = response.data.map(item => this.createBasicSpectrumItem(item));
                            console.log(`从JSON加载了 ${this.spectrums.length} 个光谱数据基本信息`);
                        } else {
                            throw new Error('JSON格式错误：数据不是数组');
                        }
                    } catch (e) {
                        console.warn('加载JSON失败:', e);
                        this.errorMessage = `加载材料信息失败: ${e.message}`;
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

            // 创建基本光谱项（不包含光谱数据）
            createBasicSpectrumItem(item) {
                const thumbnailPath = this.getThumbnailPath(item.thumbnail || item.name || item.code);
                console.log('创建材料项:', item.name, '缩略图路径:', thumbnailPath);
                
                return {
                    id: item.id || Math.random().toString(36).substr(2, 9),
                    name: item.name || '材料',
                    code: item.code || 'MAT-000',
                    brief: item.brief || '伪装材料光谱特征数据',
                    description: item.description || '该材料具有独特的光谱反射特性，适用于伪装应用场景。',
                    applications: item.applications || '军事伪装、目标隐蔽、光学欺骗等领域',
                    wavelengthRange: item.wavelengthRange || '400-1100 nm',
                    conditions: item.conditions || '室温 25°C，标准光源',
                    date: item.date || new Date().toISOString(),
                    thumbnail: thumbnailPath,
                    // 光谱数据文件路径
                    spectrumDataFile: item.spectrumDataFile,
                    spectrumData2File: item.spectrumData2File,
                    // 光谱数据（初始为空，按需加载）
                    spectrumData: null,
                    spectrumData2: null,
                    // 统计数据（初始为空）
                    peakWavelength: 0,
                    maxReflectance: '0.00',
                    avgReflectance: '0.00',
                    peakWavelength2: 0,
                    maxReflectance2: '0.00',
                    avgReflectance2: '0.00',
                    dataPoints: 0,
                    dataRange: null,
                    dataType: 'reflectance',
                    dataUnit: '%',
                    // 加载状态
                    dataLoaded: false,
                    loadingData: false
                };
            },

            // 按需加载光谱数据
            async loadSpectrumDataForItem(spectrum) {
                if (spectrum.dataLoaded || spectrum.loadingData) {
                    return;
                }

                try {
                    spectrum.loadingData = true;
                    console.log(`开始加载光谱数据: ${spectrum.name}`, spectrum.spectrumDataFile);
                    
                    let spectrumData = null;
                    let spectrumData2 = null;
                    
                    // 加载主光谱数据
                    if (spectrum.spectrumDataFile) {
                        const filePath = this.basePath + spectrum.spectrumDataFile;
                        console.log('加载光谱数据文件:', filePath);
                        
                        const response = await axios.get(filePath, {
                            timeout: 15000
                        });
                        console.log('光谱数据文件加载成功:', response.data);
                        
                        // 支持两种格式：{data: [...]} 或直接是数组
                        if (response.data && response.data.data) {
                            spectrumData = response.data.data;
                        } else if (Array.isArray(response.data)) {
                            spectrumData = response.data;
                        } else {
                            console.warn('光谱数据格式不正确:', response.data);
                        }
                    }
                    
                    // 加载对比光谱数据
                    if (spectrum.spectrumData2File) {
                        const filePath = this.basePath + spectrum.spectrumData2File;
                        const response = await axios.get(filePath, {
                            timeout: 15000
                        });
                        
                        if (response.data && response.data.data) {
                            spectrumData2 = response.data.data;
                        } else if (Array.isArray(response.data)) {
                            spectrumData2 = response.data;
                        }
                    }
                    
                    // 处理加载的光谱数据
                    if (spectrumData && spectrumData.length > 0) {
                        console.log(`处理光谱数据: ${spectrumData.length} 个数据点`);
                        const processedData = this.processSpectrumData(spectrumData, spectrumData2);
                        Object.assign(spectrum, processedData);
                        spectrum.dataLoaded = true;
                        console.log('光谱数据处理完成');
                    } else {
                        console.warn('没有可用的光谱数据');
                        spectrum.dataLoaded = false;
                    }
                    
                    spectrum.loadingData = false;
                    
                } catch (error) {
                    console.error(`加载光谱数据失败 ${spectrum.name}:`, error);
                    spectrum.loadingData = false;
                    spectrum.dataLoaded = false;
                    this.$message.error(`加载 ${spectrum.name} 的光谱数据失败: ${error.message}`);
                }
            },

            // 处理光谱数据
            processSpectrumData(spectrumData, spectrumData2) {
                // 检测数据类型并标准化数据
                const normalizedSpectrumData = this.normalizeSpectrumData(spectrumData);
                const normalizedSpectrumData2 = spectrumData2 ? this.normalizeSpectrumData(spectrumData2) : null;

                // 计算实际的数据范围
                const dataRange = this.calculateDataRange(normalizedSpectrumData, normalizedSpectrumData2);

                // 处理主光谱数据
                const filteredSpectrumData = this.filterSpectrumData(normalizedSpectrumData, dataRange);
                const stats = this.calculateStats(filteredSpectrumData);

                // 处理第二条光谱数据（如果存在）
                let filteredSpectrumData2 = null;
                let spectrumData2Stats = {};
                if (normalizedSpectrumData2) {
                    filteredSpectrumData2 = this.filterSpectrumData(normalizedSpectrumData2, dataRange);
                    spectrumData2Stats = this.calculateStats(filteredSpectrumData2);
                }

                // 检测数据类型
                const dataType = this.detectDataType(spectrumData);

                return {
                    spectrumData: filteredSpectrumData,
                    spectrumData2: filteredSpectrumData2,
                    peakWavelength: stats.peakWavelength,
                    maxReflectance: stats.maxReflectance,
                    avgReflectance: stats.avgReflectance,
                    dataPoints: filteredSpectrumData.length,
                    // 第二条光谱的统计数据
                    peakWavelength2: spectrumData2Stats.peakWavelength || 0,
                    maxReflectance2: spectrumData2Stats.maxReflectance || '0.00',
                    avgReflectance2: spectrumData2Stats.avgReflectance || '0.00',
                    // 数据范围信息，用于坐标轴自适应
                    dataRange: dataRange,
                    // 数据类型信息
                    dataType: dataType,
                    dataUnit: dataType === 'reflectance' ? '%' : 'a.u.'
                };
            },

            // 检测数据类型（反射率或光强）
            detectDataType(spectrumData) {
                if (!spectrumData || spectrumData.length === 0) return 'reflectance';
                
                // 检查数据的典型范围来判断类型
                const sampleValues = spectrumData.slice(0, 10).map(point => point.reflectance);
                const maxValue = Math.max(...sampleValues);
                const minValue = Math.min(...sampleValues);
                
                console.log(`数据类型检测 - 最大值: ${maxValue}, 最小值: ${minValue}`);
                
                // 如果数值明显大于100，很可能是光强数据
                if (maxValue > 1000) {
                    return 'intensity';
                }
                
                // 如果数值在0-100范围内，很可能是反射率
                if (maxValue <= 100 && minValue >= 0) {
                    return 'reflectance';
                }
                
                // 默认认为是反射率
                return 'reflectance';
            },

            // 标准化光谱数据
            normalizeSpectrumData(spectrumData) {
                if (!spectrumData || !Array.isArray(spectrumData)) {
                    return [];
                }

                // 检测数据类型
                const dataType = this.detectDataType(spectrumData);
                console.log(`标准化光谱数据 - 类型: ${dataType}, 数据点数量: ${spectrumData.length}`);
                
                if (dataType === 'intensity') {
                    // 如果是光强数据，直接返回原始数据，不做归一化
                    return spectrumData.map(point => ({
                        wavelength: point.wavelength,
                        reflectance: point.reflectance
                    }));
                }
                
                // 如果是反射率数据，直接返回
                return spectrumData;
            },

            // 计算数据范围（用于坐标轴自适应）
            calculateDataRange(spectrumData1, spectrumData2) {
                const allData = [...spectrumData1];
                if (spectrumData2) {
                    allData.push(...spectrumData2);
                }

                let minWavelength = Infinity;
                let maxWavelength = -Infinity;
                let minReflectance = Infinity;
                let maxReflectance = -Infinity;

                allData.forEach(point => {
                    minWavelength = Math.min(minWavelength, point.wavelength);
                    maxWavelength = Math.max(maxWavelength, point.wavelength);
                    minReflectance = Math.min(minReflectance, point.reflectance);
                    maxReflectance = Math.max(maxReflectance, point.reflectance);
                });

                // 添加一些边距
                const wavelengthMargin = (maxWavelength - minWavelength) * 0.05;
                const reflectanceMargin = (maxReflectance - minReflectance) * 0.1;

                const range = {
                    minWavelength: Math.max(0, minWavelength - wavelengthMargin),
                    maxWavelength: maxWavelength + wavelengthMargin,
                    minReflectance: Math.max(0, minReflectance - reflectanceMargin),
                    maxReflectance: maxReflectance + reflectanceMargin,
                    wavelengthRange: maxWavelength - minWavelength
                };

                console.log('数据范围计算:', range);
                return range;
            },

            // 过滤光谱数据
            filterSpectrumData(spectrumData, dataRange) {
                if (!spectrumData || !Array.isArray(spectrumData)) {
                    return [];
                }

                return spectrumData.filter(point =>
                    point.wavelength >= dataRange.minWavelength &&
                    point.wavelength <= dataRange.maxWavelength
                );
            },

            // 获取缩略图路径
            getThumbnailPath(materialName) {
                // 如果已经是完整路径，直接返回
                if (materialName && materialName.startsWith('/')) {
                    return this.basePath + materialName;
                }
                
                // 否则构建路径
                let fileName = materialName;
                if (fileName.includes('/')) {
                    fileName = fileName.split('/').pop();
                }
                if (fileName.endsWith('.png') || fileName.endsWith('.jpg')) {
                    fileName = fileName.replace(/\.(png|jpg)$/, '');
                }
                
                const path = this.basePath + this.config.thumbnailFolder + fileName + this.config.thumbnailExtension;
                console.log('构建缩略图路径:', path);
                return path;
            },

            // 生成模拟数据（用于演示）
            generateMockData() {
                console.log('使用模拟数据');
                const mockData = [
                    {
                        id: "mat-mock-001",
                        name: "模拟材料",
                        code: "MAT-MOCK-001",
                        brief: "模拟伪装材料光谱数据",
                        description: "这是一个模拟材料的光谱数据示例",
                        applications: "测试、演示",
                        wavelengthRange: "400-1000 nm",
                        conditions: "标准测试条件",
                        date: new Date().toISOString(),
                        thumbnail: "/static/spectrum-thumbnails/mock.png",
                        spectrumDataFile: null
                    }
                ];
                return mockData.map(item => this.createBasicSpectrumItem(item));
            },

            // 计算统计数据
            calculateStats(spectrumData) {
                if (!spectrumData || spectrumData.length === 0) {
                    return {
                        peakWavelength: 0,
                        maxReflectance: '0.00',
                        avgReflectance: '0.00'
                    };
                }

                let maxReflectance = 0;
                let peakWavelength = spectrumData[0].wavelength;
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

            // 渲染详细光谱图（支持坐标轴自适应和真假材料对比）
            renderDetailChart() {
                this.$nextTick(() => {
                    const canvas = this.$refs.detailChart;
                    if (!canvas || !this.currentSpectrum.spectrumData) {
                        console.log('无法渲染图表: canvas或数据不存在');
                        return;
                    }

                    console.log('开始渲染图表，数据点数量:', this.currentSpectrum.spectrumData.length);
                    
                    const ctx = canvas.getContext('2d');
                    const width = canvas.width = canvas.offsetWidth;
                    const height = canvas.height = 500;

                    // 清空画布
                    ctx.clearRect(0, 0, width, height);

                    // 设置边距
                    const padding = { top: 50, right: 60, bottom: 70, left: 80 };
                    const chartWidth = width - padding.left - padding.right;
                    const chartHeight = height - padding.top - padding.bottom;

                    // 获取数据范围
                    const dataRange = this.currentSpectrum.dataRange;

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

                    // Y轴（反射率/光强）- 自适应刻度
                    const ySteps = 8;
                    for (let i = 0; i <= ySteps; i++) {
                        const y = padding.top + (chartHeight / ySteps) * i;
                        const value = dataRange.maxReflectance - (dataRange.maxReflectance - dataRange.minReflectance) * (i / ySteps);

                        ctx.beginPath();
                        ctx.moveTo(padding.left, y);
                        ctx.lineTo(padding.left + chartWidth, y);
                        ctx.stroke();

                        ctx.textAlign = 'right';
                        // 根据数据类型显示不同的单位和格式
                        let displayValue;
                        if (this.currentSpectrum.dataType === 'intensity') {
                            displayValue = Math.round(value).toString();
                        } else {
                            displayValue = value.toFixed(1) + '%';
                        }
                        ctx.fillText(displayValue, padding.left - 10, y + 4);
                    }

                    // X轴（波长）- 自适应刻度
                    const xSteps = 10;
                    for (let i = 0; i <= xSteps; i++) {
                        const wavelength = dataRange.minWavelength + (dataRange.maxWavelength - dataRange.minWavelength) * (i / xSteps);
                        const x = padding.left + (chartWidth / xSteps) * i;

                        ctx.beginPath();
                        ctx.moveTo(x, padding.top);
                        ctx.lineTo(x, padding.top + chartHeight);
                        ctx.stroke();

                        ctx.textAlign = 'center';
                        ctx.fillText(wavelength.toFixed(0) + ' nm', x, padding.top + chartHeight + 25);
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
                    // 根据数据类型显示不同的Y轴标签
                    const yLabel = this.currentSpectrum.dataType === 'intensity' ? '光强' : '反射率';
                    const yUnit = this.currentSpectrum.dataType === 'intensity' ? 'a.u.' : '%';
                    ctx.fillText(`${yLabel} (${yUnit})`, 0, 0);
                    ctx.restore();

                    // 绘制真实材料光谱曲线（实线，蓝色）
                    const mainData = this.currentSpectrum.spectrumData;
                    ctx.strokeStyle = '#1890ff';
                    ctx.lineWidth = 2.5;
                    ctx.beginPath();

                    mainData.forEach((point, index) => {
                        const x = padding.left + ((point.wavelength - dataRange.minWavelength) / dataRange.wavelengthRange) * chartWidth;
                        const y = padding.top + chartHeight - ((point.reflectance - dataRange.minReflectance) / (dataRange.maxReflectance - dataRange.minReflectance)) * chartHeight;

                        if (index === 0) {
                            ctx.moveTo(x, y);
                        } else {
                            ctx.lineTo(x, y);
                        }
                    });

                    ctx.stroke();

                    // 绘制仿真材料光谱曲线（虚线，红色）
                    if (this.currentSpectrum.spectrumData2) {
                        const comparisonData = this.currentSpectrum.spectrumData2;
                        ctx.strokeStyle = '#ff4d4f';
                        ctx.lineWidth = 2.5;
                        ctx.setLineDash([5, 3]); // 虚线样式
                        ctx.beginPath();

                        comparisonData.forEach((point, index) => {
                            const x = padding.left + ((point.wavelength - dataRange.minWavelength) / dataRange.wavelengthRange) * chartWidth;
                            const y = padding.top + chartHeight - ((point.reflectance - dataRange.minReflectance) / (dataRange.maxReflectance - dataRange.minReflectance)) * chartHeight;

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

                    // 真实材料图例
                    ctx.fillStyle = '#1890ff';
                    ctx.fillRect(width - 200, padding.top - 30, 20, 3);
                    ctx.fillStyle = '#333';
                    ctx.fillText('当前材料', width - 170, padding.top - 25);

                    // 仿真材料图例（如果存在）
                    if (this.currentSpectrum.spectrumData2) {
                        ctx.fillStyle = '#ff4d4f';
                        ctx.setLineDash([5, 3]);
                        ctx.beginPath();
                        ctx.moveTo(width - 200, padding.top - 10);
                        ctx.lineTo(width - 180, padding.top - 10);
                        ctx.stroke();
                        ctx.setLineDash([]);
                        ctx.fillStyle = '#333';
                        ctx.fillText('真实材料', width - 170, padding.top - 5);
                    }

                    // 绘制标题
                    ctx.fillStyle = '#333';
                    ctx.font = 'bold 16px Arial';
                    ctx.textAlign = 'center';
                    let title = this.currentSpectrum.name + ' - 光谱曲线';
                    if (this.currentSpectrum.spectrumData2) {
                        title += ' (真实vs仿真对比)';
                    }
                    // 添加数据类型信息到标题
                    const dataTypeText = this.currentSpectrum.dataType === 'intensity' ? '光强' : '反射率';
                    title += ` - ${dataTypeText}`;
                    
                    ctx.fillText(title, width / 2, 30);
                    
                    console.log('图表渲染完成');
                });
            },

            // 查看详情
            async viewDetail(spectrum) {
                console.log('查看详情:', spectrum.name);
                // 如果数据未加载，先加载数据
                if (!spectrum.dataLoaded && !spectrum.loadingData) {
                    console.log('开始加载光谱数据...');
                    await this.loadSpectrumDataForItem(spectrum);
                }
                
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
                spectrum.thumbnail = 'error';
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
    /* 样式保持不变，与之前相同 */
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
        display: flex;
        flex-direction: column;
        align-items: center;
    }

    .thumbnail-placeholder span {
        font-size: 14px;
        margin-top: 10px;
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
        margin-bottom: 15px;
        flex-grow: 1;
    }

    .spectrum-meta {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: auto;
    }

    .data-status {
        margin-left: 8px;
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
        position: relative;
        min-height: 500px;
    }

    .data-loading, .data-error {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        height: 500px;
        font-size: 16px;
        color: #666;
    }

    .data-loading i, .data-error i {
        font-size: 32px;
        margin-bottom: 15px;
    }

    .data-loading i {
        animation: rotating 2s linear infinite;
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
        grid-template-columns: repeat(3, 1fr);
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

        .spectrum-meta {
            flex-direction: column;
            align-items: flex-start;
            gap: 8px;
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