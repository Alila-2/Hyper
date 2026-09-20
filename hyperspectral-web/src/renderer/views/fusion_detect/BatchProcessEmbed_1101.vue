<template>
    <div class="batch-process-root">
        <div class="batch-process-container">
            <!-- 页面标题优化 -->
            <div class="page-header">
                <h2 class="page-title">批量目标检测系统</h2>
                <div class="page-subtitle">军事目标与伪装材料智能识别分析平台</div>
            </div>

            <!-- 控制面板优化 -->
            <div class="control-panel">
                <div class="control-row">
                    <!-- 按钮组 -->
                    <div class="button-group">
                        <button @click="startBatchProcess"
                                :disabled="isProcessing || !selectedFolder"
                                class="start-button">
                            <span class="button-icon">🚀</span>
                            {{ isProcessing ? '处理中...' : '开始批量处理' }}
                        </button>
                        <button @click="resetProcess"
                                :disabled="isProcessing"
                                class="reset-button">
                            <span class="button-icon">🔄</span>
                            重置
                        </button>

                        <!-- 上传区域 - 缩小并放在重置按钮右侧 -->
                        <div class="upload-section" v-if="!isProcessing">
                            <div class="upload-area"
                                 @click="triggerFolderUpload"
                                 @drop="handleDrop"
                                 @dragover="handleDragOver"
                                 @dragleave="handleDragLeave"
                                 :class="{ 'drag-over': isDragOver, 'uploading': isUploading }">
                                <div class="upload-icon">{{ isUploading ? '⏳' : '📁' }}</div>
                                <div class="upload-text">
                                    <div class="upload-title">{{ isUploading ? '上传中...' : '选择文件夹' }}</div>
                                </div>
                                <input type="file"
                                       ref="folderInput"
                                       @change="handleFolderSelect"
                                       webkitdirectory
                                       directory
                                       multiple
                                       style="display: none">
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 上传状态提示 -->
                <div v-if="uploadMessage" class="upload-message" :class="{ 'success': uploadSuccess, 'error': !uploadSuccess }">
                    <span class="message-icon">{{ uploadSuccess ? '✅' : '⚠️' }}</span>
                    {{ uploadMessage }}
                </div>

                <!-- 处理状态优化 -->
                <div class="status-info" v-if="isProcessing">
                    <div class="progress-header">
                        <div class="progress-text">
                            <span class="progress-label">处理进度</span>
                            <span class="progress-numbers">{{ currentProgress }}/{{ totalFiles }} ({{ progressPercentage }}%)</span>
                        </div>
                        <div class="current-file-info">
                            <span class="file-label">当前文件:</span>
                            <span class="file-name">{{ currentFile }}</span>
                        </div>
                    </div>
                    <div class="progress-bar-container">
                        <div class="progress-fill"
                             :style="{ width: progressPercentage + '%' }"></div>
                    </div>
                </div>
            </div>

            <!-- 总体统计指标优化 - 只在有数据时显示 -->
            <div class="overall-metrics" v-if="overallStatistics && overallStatistics.total_files > 0">
                <div class="metrics-header">
                    <h3 class="metrics-title">总体检测指标</h3>
                    <div class="metrics-summary">
                        共处理 {{ overallStatistics.total_files }} 个数据集
                    </div>
                </div>

                <!-- 基本信息卡片优化 -->
                <div class="basic-info-cards">
                    <div class="info-card file-count">
                        <div class="info-icon">📊</div>
                        <div class="info-content">
                            <div class="info-value">{{ overallStatistics.total_files }}</div>
                            <div class="info-label">处理文件总数</div>
                        </div>
                    </div>
                    <div class="info-card avg-time">
                        <div class="info-icon">⚡</div>
                        <div class="info-content">
                            <div class="info-value">{{ systemAverageResponseTime }}ms</div>
                            <div class="info-label">系统平均响应时间</div>
                        </div>
                    </div>
                    <div class="info-card total-targets">
                        <div class="info-icon">🎯</div>
                        <div class="info-content">
                            <div class="info-value">{{ overallStatistics.total_expected_targets }}</div>
                            <div class="info-label">应检测目标总数</div>
                        </div>
                    </div>
                    <div class="info-card correct-detections">
                        <div class="info-icon">✅</div>
                        <div class="info-content">
                            <div class="info-value">{{ overallStatistics.total_correct_detections }}</div>
                            <div class="info-label">正确检测总数</div>
                        </div>
                    </div>
                </div>

                <!-- 总体数量统计优化 -->
                <div class="total-stats-section">
                    <h4 class="section-title">总体数量统计</h4>
                    <div class="total-stats-grid">
                        <div class="total-stat-card">
                            <div class="total-stat-icon">📈</div>
                            <div class="total-stat-value">{{ overallStatistics.total_detected_targets }}</div>
                            <div class="total-stat-label">总检测目标数</div>
                        </div>
                        <div class="total-stat-card warning">
                            <div class="total-stat-icon">⚠️</div>
                            <div class="total-stat-value">{{ overallStatistics.total_false_positives }}</div>
                            <div class="total-stat-label">总误报数量</div>
                        </div>
                        <div class="total-stat-card danger">
                            <div class="total-stat-icon">❌</div>
                            <div class="total-stat-value">{{ overallStatistics.total_false_negatives }}</div>
                            <div class="total-stat-label">总漏报数量</div>
                        </div>
                    </div>
                </div>

                <!-- 伪装材料统计优化 - 只在有伪装材料数据时显示 -->
                <div class="category-section camouflage-section" v-if="hasCamouflageData">
                    <div class="section-header">
                        <div class="section-title-group">
                            <h4 class="section-title">伪装材料统计</h4>
                            <div class="targets-count-badge">
                                {{ displayCamouflageTargetsCount }} 种材料
                            </div>
                        </div>
                        <div class="section-actions">
                            <span class="material-count">
                                共 {{ overallStatistics.camouflage_expected_total }} 个目标
                            </span>
                        </div>
                    </div>

                    <div class="category-content">
                        <!-- 伪装材料列表 -->
                        <div class="targets-list">
                            <h5 class="targets-title">伪装材料列表</h5>
                            <div class="targets-grid">
                                <span v-for="target in displayCamouflageTargetsList"
                                      :key="target"
                                      class="target-tag camouflage-tag">
                                    {{ target }}
                                </span>
                            </div>
                        </div>

                        <!-- 伪装材料数量统计 -->
                        <div class="category-stats-grid">
                            <div class="category-stat">
                                <div class="stat-icon">🎯</div>
                                <div class="stat-content">
                                    <span class="stat-label">应有总数</span>
                                    <span class="stat-value">{{ overallStatistics.camouflage_expected_total }}</span>
                                </div>
                            </div>
                            <div class="category-stat success">
                                <div class="stat-icon">✅</div>
                                <div class="stat-content">
                                    <span class="stat-label">正确检测</span>
                                    <span class="stat-value">{{ overallStatistics.camouflage_correct_total }}</span>
                                </div>
                            </div>
                            <div class="category-stat warning">
                                <div class="stat-icon">⚠️</div>
                                <div class="stat-content">
                                    <span class="stat-label">误报总数</span>
                                    <span class="stat-value">{{ overallStatistics.camouflage_false_positives_total }}</span>
                                </div>
                            </div>
                            <div class="category-stat danger">
                                <div class="stat-icon">❌</div>
                                <div class="stat-content">
                                    <span class="stat-label">漏报总数</span>
                                    <span class="stat-value">{{ overallStatistics.camouflage_false_negatives_total }}</span>
                                </div>
                            </div>
                        </div>

                        <!-- 伪装材料关键指标优化 -->
                        <div class="key-metrics">
                            <div class="key-metric primary">
                                <div class="metric-icon">🔍</div>
                                <div class="metric-content">
                                    <div class="metric-value">{{ formatPercentage(overallStatistics.camouflage_decipher_rate) }}</div>
                                    <div class="metric-label">伪装材料破译率</div>
                                </div>
                            </div>
                            <div class="key-metric primary">
                                <div class="metric-icon">📊</div>
                                <div class="metric-content">
                                    <div class="metric-value">{{ formatPercentage(camouflageRecognitionRate) }}</div>
                                    <div class="metric-label">伪装材料识别率</div>
                                </div>
                            </div>
                        </div>

                        <!-- 伪装材料各目标详细统计 -->
                        <div class="detailed-stats-subsection camouflage-detailed">
                            <div class="subsection-header">
                                <h4 class="subsection-title">伪装材料各目标详细统计</h4>
                                <div class="table-summary">
                                    共 {{ displayCamouflageTargetsCount }} 个伪装材料类型
                                </div>
                            </div>
                            <div class="detailed-stats-table">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>目标名称</th>
                                            <th>应有数量</th>
                                            <th>正确检测</th>
                                            <th>误报数量</th>
                                            <th>漏报数量</th>
                                            <th>检测数量</th>
                                            <th>识别率</th>
                                            <th>查准率</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr v-for="stats in displayCamouflageDetailedStats"
                                            :key="stats.display_name"
                                            class="camouflage-row">
                                            <td class="target-name-cell">
                                                <span class="target-color"
                                                      :style="{ backgroundColor: stats.display_color }">
                                                </span>
                                                {{ stats.display_name }}
                                            </td>
                                            <td>{{ stats.expected_count }}</td>
                                            <td class="success-cell">{{ stats.correct_detections }}</td>
                                            <td class="warning-cell">{{ stats.false_positives }}</td>
                                            <td class="danger-cell">{{ stats.false_negatives }}</td>
                                            <td>{{ stats.detected_count }}</td>
                                            <td class="rate-cell">{{ formatPercentage(stats.recognition_rate) }}</td>
                                            <td class="rate-cell">{{ formatPercentage(stats.precision) }}</td>
                                        </tr>
                                        <!-- 伪装材料总计行 -->
                                        <tr class="total-row camouflage-total">
                                            <td class="total-label">总计</td>
                                            <td class="total-value">{{ camouflageTotalStats.expected_count }}</td>
                                            <td class="total-value success-cell">{{ camouflageTotalStats.correct_detections }}</td>
                                            <td class="total-value warning-cell">{{ camouflageTotalStats.false_positives }}</td>
                                            <td class="total-value danger-cell">{{ camouflageTotalStats.false_negatives }}</td>
                                            <td class="total-value">{{ camouflageTotalStats.detected_count }}</td>
                                            <td class="total-value rate-cell">{{ formatPercentage(camouflageTotalStats.avg_recognition_rate) }}</td>
                                            <td class="total-value rate-cell">{{ formatPercentage(camouflageTotalStats.avg_precision) }}</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 军事目标统计优化 - 只在有军事目标数据时显示 -->
                <div class="category-section military-section" v-if="hasMilitaryData">
                    <div class="section-header">
                        <div class="section-title-group">
                            <h4 class="section-title">军事目标统计</h4>
                            <div class="targets-count-badge">
                                {{ overallStatistics.military_targets_count }} 种目标
                            </div>
                        </div>
                        <div class="section-actions">
                            <span class="material-count">
                                共 {{ overallStatistics.military_expected_total }} 个目标
                            </span>
                        </div>
                    </div>

                    <div class="category-content">
                        <!-- 军事目标列表 -->
                        <div class="targets-list">
                            <h5 class="targets-title">军事目标列表</h5>
                            <div class="targets-grid">
                                <span v-for="target in overallStatistics.military_targets_list"
                                      :key="target"
                                      class="target-tag military-tag">
                                    {{ target }}
                                </span>
                            </div>
                        </div>

                        <!-- 军事目标数量统计 -->
                        <div class="category-stats-grid">
                            <div class="category-stat">
                                <div class="stat-icon">🎯</div>
                                <div class="stat-content">
                                    <span class="stat-label">应有总数</span>
                                    <span class="stat-value">{{ overallStatistics.military_expected_total }}</span>
                                </div>
                            </div>
                            <div class="category-stat success">
                                <div class="stat-icon">✅</div>
                                <div class="stat-content">
                                    <span class="stat-label">正确检测</span>
                                    <span class="stat-value">{{ overallStatistics.military_correct_total }}</span>
                                </div>
                            </div>
                            <div class="category-stat warning">
                                <div class="stat-icon">⚠️</div>
                                <div class="stat-content">
                                    <span class="stat-label">误报总数</span>
                                    <span class="stat-value">{{ overallStatistics.military_false_positives_total }}</span>
                                </div>
                            </div>
                            <div class="category-stat danger">
                                <div class="stat-icon">❌</div>
                                <div class="stat-content">
                                    <span class="stat-label">漏报总数</span>
                                    <span class="stat-value">{{ overallStatistics.military_false_negatives_total }}</span>
                                </div>
                            </div>
                        </div>

                        <!-- 军事目标关键指标优化 -->
                        <div class="key-metrics">
                            <div class="key-metric secondary">
                                <div class="metric-icon">🎯</div>
                                <div class="metric-content">
                                    <div class="metric-value">{{ formatPercentage(overallStatistics.military_precision) }}</div>
                                    <div class="metric-label">军事目标查准率</div>
                                </div>
                            </div>
                            <div class="key-metric secondary">
                                <div class="metric-icon">📊</div>
                                <div class="metric-content">
                                    <div class="metric-value">{{ formatPercentage(overallStatistics.military_recall) }}</div>
                                    <div class="metric-label">军事目标查全率</div>
                                </div>
                            </div>
                        </div>

                        <!-- 军事目标各目标详细统计 -->
                        <div class="detailed-stats-subsection military-detailed">
                            <div class="subsection-header">
                                <h4 class="subsection-title">军事目标各目标详细统计</h4>
                                <div class="table-summary">
                                    共 {{ militaryTargetsCount }} 个军事目标类型
                                </div>
                            </div>
                            <div class="detailed-stats-table">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>目标名称</th>
                                            <th>应有数量</th>
                                            <th>正确检测</th>
                                            <th>误报数量</th>
                                            <th>漏报数量</th>
                                            <th>检测数量</th>
                                            <th>查准率</th>
                                            <th>查全率</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr v-for="(stats, targetType) in militaryDetailedStats"
                                            :key="targetType"
                                            class="military-row">
                                            <td class="target-name-cell">
                                                <span class="target-color"
                                                      :style="{ backgroundColor: getTargetColor(targetType) }">
                                                </span>
                                                {{ stats.target_name }}
                                            </td>
                                            <td>{{ stats.expected_count }}</td>
                                            <td class="success-cell">{{ stats.correct_detections }}</td>
                                            <td class="warning-cell">{{ stats.false_positives }}</td>
                                            <td class="danger-cell">{{ stats.false_negatives }}</td>
                                            <td>{{ stats.detected_count }}</td>
                                            <td class="rate-cell">{{ formatPercentage(stats.precision) }}</td>
                                            <td class="rate-cell">{{ formatPercentage(stats.recall) }}</td>
                                        </tr>
                                        <!-- 军事目标总计行 -->
                                        <tr class="total-row military-total">
                                            <td class="total-label">总计</td>
                                            <td class="total-value">{{ militaryTotalStats.expected_count }}</td>
                                            <td class="total-value success-cell">{{ militaryTotalStats.correct_detections }}</td>
                                            <td class="total-value warning-cell">{{ militaryTotalStats.false_positives }}</td>
                                            <td class="total-value danger-cell">{{ militaryTotalStats.false_negatives }}</td>
                                            <td class="total-value">{{ militaryTotalStats.detected_count }}</td>
                                            <td class="total-value rate-cell">{{ formatPercentage(militaryTotalStats.avg_precision) }}</td>
                                            <td class="total-value rate-cell">{{ formatPercentage(militaryTotalStats.avg_recall) }}</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 错误信息优化 -->
            <div v-if="errorMessage" class="error-message">
                <div class="error-alert">
                    <span class="error-icon">⚠️</span>
                    <div class="error-content">
                        <div class="error-title">处理错误</div>
                        <div class="error-text">{{ errorMessage }}</div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import axios from 'axios'

    export default {
        name: 'BatchProcess',
        data() {
            return {
                // 文件夹选择相关数据
                selectedFolder: null,
                isDragOver: false,
                isUploading: false,
                uploadMessage: '',
                uploadSuccess: false,

                // 处理状态
                isProcessing: false,
                currentProgress: 0,
                totalFiles: 0,
                currentFile: '',

                // 结果数据
                completedResults: [],
                overallStatistics: null,

                // 分页相关
                currentPage: 1,
                pageSize: 1,

                // 错误信息
                errorMessage: '',

                // 轮询定时器
                pollInterval: null,

                // 目标颜色配置
                TARGET_COLORS: {
                    "d1": "#008000", "d2": "#DAA520", "d3": "#808080", "d4": "#8B4513",
                    "d5": "#0000FF", "d6": "#ADD8E6", "d7": "#F0E68C", "d8": "#800080",
                    "d9": "#FF0000", "d10": "#00FFFF", "d11": "#FFFF00"
                },

                // 显示合并配置
                DISPLAY_MERGE_CONFIG: {
                    // 迷彩类合并：d4(棕色迷彩服) + d5(绿色迷彩服) -> 显示为"迷彩"
                    "迷彩": {
                        target_codes: ["d4", "d5"],
                        color: "#2E8B57" // 海绿色
                    },
                    // 光学伪装合并：d6(绿色伪装服) + d7(棕色伪装服) -> 显示为"光学伪装"
                    "光学伪装": {
                        target_codes: ["d6", "d7"],
                        color: "#8B7355" // 赭石色
                    }
                }
            }
        },
        computed: {
            progressPercentage() {
                if (this.totalFiles === 0) return 0
                return Math.round((this.currentProgress / this.totalFiles) * 100)
            },
            totalPages() {
                return this.completedResults.length
            },
            currentDataset() {
                if (this.completedResults.length === 0) return null
                return this.completedResults[this.currentPage - 1]
            },
            // 系统平均响应时间（毫秒）
            systemAverageResponseTime() {
                if (!this.overallStatistics || this.overallStatistics.total_files === 0) return 0
                return Math.round((this.overallStatistics.total_detection_time / this.overallStatistics.total_files) * 1000)
            },

            // 是否有伪装材料数据
            hasCamouflageData() {
                return this.overallStatistics &&
                    this.overallStatistics.camouflage_expected_total > 0
            },

            // 是否有军事目标数据
            hasMilitaryData() {
                return this.overallStatistics &&
                    this.overallStatistics.military_expected_total > 0
            },

            // 显示用的伪装材料详细统计
            displayCamouflageDetailedStats() {
                if (!this.overallStatistics || !this.overallStatistics.target_detailed_stats) return []

                const displayStats = {}

                // 先处理需要合并的目标
                for (const [displayName, config] of Object.entries(this.DISPLAY_MERGE_CONFIG)) {
                    displayStats[displayName] = {
                        display_name: displayName,
                        display_color: config.color,
                        expected_count: 0,
                        correct_detections: 0,
                        false_positives: 0,
                        false_negatives: 0,
                        detected_count: 0,
                        recognition_rate: 0,
                        precision: 0
                    }

                    // 合并配置中指定的目标
                    config.target_codes.forEach(targetCode => {
                        const stats = this.overallStatistics.target_detailed_stats[targetCode]
                        if (stats && stats.target_type === '伪装材料') {
                            const merged = displayStats[displayName]
                            merged.expected_count += stats.expected_count || 0
                            merged.correct_detections += stats.correct_detections || 0
                            merged.false_positives += stats.false_positives || 0
                            merged.false_negatives += stats.false_negatives || 0
                            merged.detected_count += stats.detected_count || 0
                        }
                    })

                    // 计算合并后的识别率和查准率
                    const merged = displayStats[displayName]
                    if (merged.expected_count > 0) {
                        merged.recognition_rate = merged.correct_detections / merged.expected_count
                    }
                    if (merged.detected_count > 0) {
                        merged.precision = merged.correct_detections / merged.detected_count
                    }
                }

                // 处理其他不需要合并的伪装材料
                for (const [targetCode, stats] of Object.entries(this.overallStatistics.target_detailed_stats)) {
                    if (stats.target_type === '伪装材料') {
                        // 检查这个目标是否已经在合并列表中
                        let isMerged = false
                        for (const config of Object.values(this.DISPLAY_MERGE_CONFIG)) {
                            if (config.target_codes.includes(targetCode)) {
                                isMerged = true
                                break
                            }
                        }

                        // 如果不在合并列表中，单独显示
                        if (!isMerged) {
                            displayStats[targetCode] = {
                                ...stats,
                                display_name: stats.target_name,
                                display_color: this.getTargetColor(targetCode)
                            }
                        }
                    }
                }

                return Object.values(displayStats)
            },

            // 显示用的伪装材料目标列表
            displayCamouflageTargetsList() {
                return this.displayCamouflageDetailedStats.map(stats => stats.display_name)
            },

            // 显示用的伪装材料类型数量
            displayCamouflageTargetsCount() {
                return this.displayCamouflageDetailedStats.length
            },

            // 原有的伪装材料详细统计（保持不变，用于计算总计）
            camouflageDetailedStats() {
                if (!this.overallStatistics || !this.overallStatistics.target_detailed_stats) return {}
                const result = {}
                for (const [targetType, stats] of Object.entries(this.overallStatistics.target_detailed_stats)) {
                    if (stats.target_type === '伪装材料') {
                        result[targetType] = stats
                    }
                }
                return result
            },

            // 军事目标详细统计
            militaryDetailedStats() {
                if (!this.overallStatistics || !this.overallStatistics.target_detailed_stats) return {}
                const result = {}
                for (const [targetType, stats] of Object.entries(this.overallStatistics.target_detailed_stats)) {
                    if (stats.target_type === '军事目标') {
                        result[targetType] = stats
                    }
                }
                return result
            },

            // 伪装材料类型数量（保持原样）
            camouflageTargetsCount() {
                return Object.keys(this.camouflageDetailedStats).length
            },

            // 军事目标类型数量
            militaryTargetsCount() {
                return Object.keys(this.militaryDetailedStats).length
            },

            // 伪装材料识别率（各类别破译率的平均值）
            camouflageRecognitionRate() {
                if (!this.camouflageDetailedStats || Object.keys(this.camouflageDetailedStats).length === 0) return 0

                const totalRate = Object.values(this.camouflageDetailedStats).reduce((sum, stats) => {
                    return sum + (stats.recognition_rate || 0)
                }, 0)

                return totalRate / Object.keys(this.camouflageDetailedStats).length
            },

            // 伪装材料总计统计
            camouflageTotalStats() {
                const stats = {
                    expected_count: 0,
                    correct_detections: 0,
                    false_positives: 0,
                    false_negatives: 0,
                    detected_count: 0,
                    avg_recognition_rate: 0,
                    avg_precision: 0
                }

                if (!this.camouflageDetailedStats) return stats

                const camouflageStats = Object.values(this.camouflageDetailedStats)
                if (camouflageStats.length === 0) return stats

                camouflageStats.forEach(item => {
                    stats.expected_count += item.expected_count || 0
                    stats.correct_detections += item.correct_detections || 0
                    stats.false_positives += item.false_positives || 0
                    stats.false_negatives += item.false_negatives || 0
                    stats.detected_count += item.detected_count || 0
                })

                // 计算平均识别率和查准率
                stats.avg_recognition_rate = camouflageStats.reduce((sum, item) => sum + (item.recognition_rate || 0), 0) / camouflageStats.length
                stats.avg_precision = camouflageStats.reduce((sum, item) => sum + (item.precision || 0), 0) / camouflageStats.length

                return stats
            },

            // 军事目标总计统计
            militaryTotalStats() {
                const stats = {
                    expected_count: 0,
                    correct_detections: 0,
                    false_positives: 0,
                    false_negatives: 0,
                    detected_count: 0,
                    avg_precision: 0,
                    avg_recall: 0
                }

                if (!this.militaryDetailedStats) return stats

                const militaryStats = Object.values(this.militaryDetailedStats)
                if (militaryStats.length === 0) return stats

                militaryStats.forEach(item => {
                    stats.expected_count += item.expected_count || 0
                    stats.correct_detections += item.correct_detections || 0
                    stats.false_positives += item.false_positives || 0
                    stats.false_negatives += item.false_negatives || 0
                    stats.detected_count += item.detected_count || 0
                })

                // 计算平均查准率和召回率
                stats.avg_precision = militaryStats.reduce((sum, item) => sum + (item.precision || 0), 0) / militaryStats.length
                stats.avg_recall = militaryStats.reduce((sum, item) => sum + (item.recall || 0), 0) / militaryStats.length

                return stats
            }
        },
        methods: {
            // 文件夹上传相关方法
            triggerFolderUpload() {
                this.$refs.folderInput.click()
            },

            handleFolderSelect(event) {
                const files = Array.from(event.target.files)
                if (files.length > 0) {
                    this.processSelectedFiles(files)
                }
            },

            handleDragOver(event) {
                event.preventDefault()
                this.isDragOver = true
            },

            handleDragLeave(event) {
                event.preventDefault()
                this.isDragOver = false
            },

            handleDrop(event) {
                event.preventDefault()
                this.isDragOver = false

                const files = Array.from(event.dataTransfer.files)
                if (files.length > 0) {
                    this.processSelectedFiles(files)
                }
            },

            processSelectedFiles(files) {
                // 过滤出 .mat 文件
                const matFiles = files.filter(file => file.name.endsWith('.mat'))

                if (matFiles.length === 0) {
                    this.uploadMessage = '选择的文件夹中没有找到 .mat 文件'
                    this.uploadSuccess = false
                    return
                }

                // 获取文件夹信息（假设所有文件在同一个文件夹）
                const firstFile = matFiles[0]
                const folderPath = firstFile.webkitRelativePath ?
                    firstFile.webkitRelativePath.split('/').slice(0, -1).join('/') :
                    '所选文件夹'

                this.selectedFolder = {
                    files: matFiles,
                    name: folderPath,
                    fileCount: matFiles.length
                }

                //this.uploadMessage = `已选择 ${matFiles.length} 个 .mat 文件`
                this.uploadMessage = `文件夹上传成功，请点击开始批量处理上传数据集`
                this.uploadSuccess = true
                this.errorMessage = ''
            },

            clearSelection() {
                this.selectedFolder = null
                this.$refs.folderInput.value = ''
                this.uploadMessage = ''
            },

            // 修改后的开始处理方法
            async startBatchProcess() {
                if (!this.selectedFolder) {
                    this.errorMessage = '请先选择数据集文件夹'
                    return
                }

                this.errorMessage = ''
                this.isProcessing = true
                this.isUploading = true
                this.uploadMessage = '文件上传中...'

                try {
                    // 创建 FormData 来上传文件
                    const formData = new FormData()

                    // 确保正确添加所有文件
                    this.selectedFolder.files.forEach(file => {
                        formData.append('files', file, file.webkitRelativePath || file.name)
                    })

                    const response = await axios.post(
                        'http://localhost:8000/api/batch-process-upload',
                        formData,
                        {
                            headers: {
                                'Content-Type': 'multipart/form-data'
                            },
                            timeout: 60000 // 增加超时时间到60秒
                        }
                    )

                    if (response.data.success) {
                        //this.uploadMessage = `成功上传 ${response.data.file_count} 个文件，开始处理`
                        this.uploadMessage = `数据集上传成功，开始处理`
                        this.uploadSuccess = true
                        this.isUploading = false
                        this.startPolling()
                    } else {
                        this.errorMessage = response.data.message || '启动处理失败'
                        this.isProcessing = false
                        this.isUploading = false
                        this.uploadMessage = '上传失败'
                        this.uploadSuccess = false
                    }
                } catch (error) {
                    this.handleError(error, '上传和处理数据集')
                    this.isProcessing = false
                    this.isUploading = false
                    this.uploadMessage = '上传失败'
                    this.uploadSuccess = false
                }
            },

            // 格式化百分比显示，保留两位小数
            formatPercentage(value) {
                if (value === null || value === undefined || isNaN(value)) return '0.00%'
                return (value * 100).toFixed(2) + '%'
            },

            // 获取目标颜色
            getTargetColor(targetType) {
                return this.TARGET_COLORS[targetType] || '#cccccc'
            },

            // 轮询状态
            startPolling() {
                if (this.pollInterval) {
                    clearInterval(this.pollInterval)
                }

                this.pollInterval = setInterval(async () => {
                    try {
                        const response = await axios.get('http://localhost:8000/api/processing-status2')
                        const status = response.data

                        this.updateStatus(status)

                        if (!status.is_processing && status.current_progress >= status.total_files) {
                            this.stopPolling()
                        }
                    } catch (error) {
                        console.error('轮询状态失败:', error)
                    }
                }, 1000)
            },

            stopPolling() {
                if (this.pollInterval) {
                    clearInterval(this.pollInterval)
                    this.pollInterval = null
                }
                this.isProcessing = false
            },

            updateStatus(status) {
                this.isProcessing = status.is_processing
                this.currentProgress = status.current_progress
                this.totalFiles = status.total_files
                this.currentFile = status.current_file
                this.completedResults = status.completed_results || []
                this.overallStatistics = status.overall_statistics

                // 如果有新结果，自动跳转到最后一页
                if (this.completedResults.length > 0 && this.currentPage === this.totalPages) {
                    this.currentPage = this.completedResults.length
                }
            },

            resetProcess() {
                this.stopPolling()
                this.isProcessing = false
                this.isUploading = false
                this.currentProgress = 0
                this.totalFiles = 0
                this.currentFile = ''
                this.completedResults = []
                this.overallStatistics = null
                this.currentPage = 1
                this.selectedFolder = null
                this.errorMessage = ''
                this.uploadMessage = ''
            },

            handleError(error, operation) {
                if (error.response) {
                    this.errorMessage = error.response.data.message || `${operation}请求失败`
                } else if (error.request) {
                    this.errorMessage = `无法连接到服务器，请检查后端服务是否启动`
                } else {
                    this.errorMessage = `${operation}错误: ${error.message}`
                }
            }
        },

        beforeUnmount() {
            this.stopPolling()
        }
    }
</script>

<style scoped>
    /* 基础样式重置 */
    .batch-process-root {
        min-height: 100vh;
        background: #ffffff;
        padding: 20px;
        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        line-height: 1.6;
    }

    .batch-process-container {
        max-width: 1400px;
        margin: 0 auto;
        background: white;
        border-radius: 16px;
        box-shadow: 0 20px 40px rgba(0,0,0,0.1);
        overflow: hidden;
    }

    /* 页面标题优化 */
    .page-header {
        background: linear-gradient(135deg, #2c3e50 0%, #3498db 100%);
        color: white;
        padding: 30px 40px;
        text-align: center;
    }

    .page-title {
        font-size: 2.5rem;
        font-weight: 700;
        margin: 0 0 10px 0;
        text-shadow: 0 2px 4px rgba(0,0,0,0.3);
    }

    .page-subtitle {
        font-size: 1.1rem;
        opacity: 0.9;
        font-weight: 300;
    }

    /* 控制面板优化 */
    .control-panel {
        padding: 30px 40px;
        background: #f8f9fa;
        border-bottom: 1px solid #e9ecef;
    }

    .control-row {
        display: flex;
        justify-content: center;
    }

    .button-group {
        display: flex;
        gap: 15px;
        align-items: center;
        flex-wrap: wrap;
        justify-content: center;
    }

    /* 上传区域优化 - 缩小并放在按钮组中 */
    .upload-section {
        margin: 0;
    }

    .upload-area {
        border: 2px dashed #dee2e6;
        border-radius: 8px;
        padding: 12px 20px;
        text-align: center;
        cursor: pointer;
        transition: all 0.3s ease;
        background: #f8f9fa;
        min-width: 140px;
        display: flex;
        align-items: center;
        gap: 8px;
    }

        .upload-area:hover {
            border-color: #3498db;
            background: #e3f2fd;
        }

        .upload-area.drag-over {
            border-color: #27ae60;
            background: #e8f5e8;
        }

        .upload-area.uploading {
            border-color: #f39c12;
            background: #fef9e7;
        }

    .upload-icon {
        font-size: 1.2rem;
    }

    .upload-text {
        flex: 1;
    }

    .upload-title {
        font-size: 0.9rem;
        font-weight: 600;
        color: #2c3e50;
    }

    .start-button, .reset-button {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 12px 24px;
        border: none;
        border-radius: 8px;
        font-size: 16px;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.3s ease;
    }

    .start-button {
        background: linear-gradient(135deg, #00b09b 0%, #96c93d 100%);
        color: white;
    }

        .start-button:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 8px 20px rgba(0, 176, 155, 0.3);
        }

        .start-button:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }

    .reset-button {
        background: #6c757d;
        color: white;
    }

        .reset-button:hover:not(:disabled) {
            background: #5a6268;
            transform: translateY(-1px);
        }

    /* 上传消息提示 */
    .upload-message {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 12px 20px;
        border-radius: 8px;
        margin-top: 15px;
        font-weight: 500;
    }

        .upload-message.success {
            background: #d5f4e6;
            color: #27ae60;
            border: 1px solid #82e5b1;
        }

        .upload-message.error {
            background: #fde8e8;
            color: #e74c3c;
            border: 1px solid #f5b7b1;
        }

    .message-icon {
        font-size: 1.2rem;
    }

    /* 处理状态优化 */
    .status-info {
        background: white;
        padding: 20px;
        border-radius: 12px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        margin-top: 20px;
    }

    .progress-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 15px;
    }

    .progress-text {
        display: flex;
        flex-direction: column;
        gap: 4px;
    }

    .progress-label {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    .progress-numbers {
        font-size: 18px;
        font-weight: 700;
        color: #2c3e50;
    }

    .current-file-info {
        display: flex;
        align-items: center;
        gap: 8px;
        background: #f8f9fa;
        padding: 8px 16px;
        border-radius: 6px;
    }

    .file-label {
        font-size: 14px;
        color: #666;
    }

    .file-name {
        font-weight: 600;
        color: #2c3e50;
    }

    .progress-bar-container {
        height: 8px;
        background: #e9ecef;
        border-radius: 4px;
        overflow: hidden;
    }

    .progress-fill {
        height: 100%;
        background: linear-gradient(90deg, #00b09b 0%, #96c93d 100%);
        border-radius: 4px;
        transition: width 0.3s ease;
    }

    /* 总体统计指标优化 */
    .overall-metrics {
        padding: 40px;
    }

    .metrics-header {
        text-align: center;
        margin-bottom: 40px;
    }

    .metrics-title {
        font-size: 2rem;
        color: #2c3e50;
        margin: 0 0 10px 0;
        font-weight: 700;
    }

    .metrics-summary {
        font-size: 1.1rem;
        color: #666;
    }

    /* 基本信息卡片优化 */
    .basic-info-cards {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 20px;
        margin-bottom: 40px;
    }

    .info-card {
        display: flex;
        align-items: center;
        background: white;
        padding: 25px;
        border-radius: 12px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        border-left: 6px solid;
        transition: transform 0.3s ease, box-shadow 0.3s ease;
    }

        .info-card:hover {
            transform: translateY(-4px);
            box-shadow: 0 8px 24px rgba(0,0,0,0.15);
        }

        .info-card.file-count {
            border-left-color: #3498db;
        }

        .info-card.avg-time {
            border-left-color: #e74c3c;
        }

        .info-card.total-targets {
            border-left-color: #9b59b6;
        }

        .info-card.correct-detections {
            border-left-color: #27ae60;
        }

    .info-icon {
        font-size: 32px;
        margin-right: 20px;
    }

    .info-content {
        flex: 1;
    }

    .info-value {
        font-size: 2rem;
        font-weight: 800;
        color: #2c3e50;
        margin-bottom: 4px;
    }

    .info-label {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    /* 总体数量统计优化 */
    .total-stats-section {
        margin-bottom: 40px;
    }

    .section-title {
        font-size: 1.5rem;
        color: #2c3e50;
        margin: 0 0 20px 0;
        font-weight: 600;
    }

    .total-stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 20px;
    }

    .total-stat-card {
        background: white;
        padding: 25px;
        border-radius: 12px;
        text-align: center;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        border-top: 4px solid #3498db;
        transition: transform 0.3s ease;
    }

        .total-stat-card:hover {
            transform: translateY(-2px);
        }

        .total-stat-card.warning {
            border-top-color: #f39c12;
        }

        .total-stat-card.danger {
            border-top-color: #e74c3c;
        }

    .total-stat-icon {
        font-size: 2.5rem;
        margin-bottom: 10px;
    }

    .total-stat-value {
        font-size: 2.2rem;
        font-weight: 800;
        margin-bottom: 8px;
        color: #2c3e50;
    }

    .total-stat-label {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    /* 分类统计样式优化 */
    .category-section {
        background: white;
        border-radius: 16px;
        padding: 30px;
        margin-bottom: 30px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        border: 2px solid;
    }

    .camouflage-section {
        border-color: #27ae60;
    }

    .military-section {
        border-color: #3498db;
    }

    .section-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 25px;
        padding-bottom: 20px;
        border-bottom: 2px solid #f8f9fa;
    }

    .section-title-group {
        display: flex;
        align-items: center;
        gap: 15px;
    }

    .section-title {
        font-size: 1.5rem;
        color: #2c3e50;
        margin: 0;
        font-weight: 600;
    }

    .targets-count-badge {
        padding: 6px 16px;
        border-radius: 20px;
        font-size: 14px;
        font-weight: 600;
    }

    .camouflage-section .targets-count-badge {
        background: #d5f4e6;
        color: #27ae60;
    }

    .military-section .targets-count-badge {
        background: #d6eaf8;
        color: #3498db;
    }

    .section-actions {
        display: flex;
        align-items: center;
        gap: 15px;
    }

    .material-count {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    /* 目标列表优化 */
    .targets-list {
        margin-bottom: 25px;
    }

    .targets-title {
        font-size: 1.1rem;
        color: #2c3e50;
        margin: 0 0 15px 0;
        font-weight: 600;
    }

    .targets-grid {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
    }

    .target-tag {
        padding: 8px 16px;
        border-radius: 20px;
        font-size: 14px;
        font-weight: 600;
        transition: transform 0.2s ease;
    }

        .target-tag:hover {
            transform: scale(1.05);
        }

    .camouflage-tag {
        background: linear-gradient(135deg, #d5f4e6 0%, #a3e4c1 100%);
        color: #27ae60;
        border: 1px solid #82e5b1;
    }

    .military-tag {
        background: linear-gradient(135deg, #d6eaf8 0%, #85c1e9 100%);
        color: #2980b9;
        border: 1px solid #5dade2;
    }

    /* 分类统计网格优化 */
    .category-stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 15px;
        margin-bottom: 25px;
    }

    .category-stat {
        display: flex;
        align-items: center;
        gap: 15px;
        padding: 20px;
        background: #f8f9fa;
        border-radius: 12px;
        border-left: 4px solid #3498db;
        transition: transform 0.2s ease;
    }

        .category-stat:hover {
            transform: translateX(4px);
        }

        .category-stat.success {
            border-left-color: #27ae60;
        }

        .category-stat.warning {
            border-left-color: #f39c12;
        }

        .category-stat.danger {
            border-left-color: #e74c3c;
        }

    .stat-icon {
        font-size: 1.5rem;
    }

    .stat-content {
        display: flex;
        flex-direction: column;
        gap: 4px;
    }

    .stat-label {
        color: #666;
        font-size: 13px;
        font-weight: 500;
    }

    .stat-value {
        font-weight: 700;
        color: #2c3e50;
        font-size: 1.2rem;
    }

    /* 关键指标优化 */
    .key-metrics {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
        gap: 20px;
        margin-bottom: 30px;
    }

    .key-metric {
        background: white;
        padding: 25px;
        border-radius: 16px;
        display: flex;
        align-items: center;
        gap: 20px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        border: 2px solid;
        transition: transform 0.3s ease;
    }

        .key-metric:hover {
            transform: translateY(-4px);
        }

        .key-metric.primary {
            border-color: #27ae60;
            background: linear-gradient(135deg, #f0f9f4 0%, #e1f7eb 100%);
        }

        .key-metric.secondary {
            border-color: #3498db;
            background: linear-gradient(135deg, #f0f8ff 0%, #e1f0ff 100%);
        }

    .metric-icon {
        font-size: 2.5rem;
    }

    .metric-content {
        flex: 1;
    }

    .metric-value {
        font-size: 2.2rem;
        font-weight: 800;
        margin-bottom: 8px;
    }

    .key-metric.primary .metric-value {
        color: #27ae60;
    }

    .key-metric.secondary .metric-value {
        color: #3498db;
    }

    .metric-label {
        font-size: 14px;
        color: #666;
        font-weight: 600;
    }

    /* 详细统计子部分样式 */
    .detailed-stats-subsection {
        background: #f8f9fa;
        border-radius: 12px;
        padding: 25px;
        margin-top: 20px;
        border: 1px solid #e9ecef;
    }

    .subsection-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 20px;
        padding-bottom: 15px;
        border-bottom: 1px solid #dee2e6;
    }

    .subsection-title {
        font-size: 1.2rem;
        color: #2c3e50;
        margin: 0;
        font-weight: 600;
    }

    .table-summary {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    .detailed-stats-table {
        overflow-x: auto;
        border-radius: 8px;
        border: 1px solid #e9ecef;
        background: white;
    }

        .detailed-stats-table table {
            width: 100%;
            border-collapse: collapse;
            font-size: 14px;
            min-width: 1000px;
        }

        .detailed-stats-table th,
        .detailed-stats-table td {
            padding: 16px;
            text-align: center;
            border-bottom: 1px solid #e9ecef;
        }

        .detailed-stats-table th {
            background: linear-gradient(135deg, #2c3e50 0%, #3498db 100%);
            color: white;
            font-weight: 600;
            position: sticky;
            top: 0;
            border: none;
        }

        .detailed-stats-table tr:hover {
            background: #f8f9fa;
        }

    .target-name-cell {
        display: flex;
        align-items: center;
        gap: 10px;
        text-align: left !important;
        font-weight: 600;
    }

    .target-color {
        width: 16px;
        height: 16px;
        border-radius: 4px;
        display: inline-block;
        border: 2px solid white;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .success-cell {
        color: #27ae60;
        font-weight: 700;
    }

    .warning-cell {
        color: #f39c12;
        font-weight: 700;
    }

    .danger-cell {
        color: #e74c3c;
        font-weight: 700;
    }

    .rate-cell {
        font-weight: 700;
        color: #2c3e50;
    }

    .military-row {
        background: #f8fdff;
    }

    .camouflage-row {
        background: #f8fff8;
    }

    /* 总计行样式 */
    .total-row {
        background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%) !important;
        color: white;
        font-weight: 700;
    }

        .total-row td {
            border-bottom: none !important;
            border-top: 2px solid #ecf0f1;
        }

    .total-label {
        text-align: left !important;
        font-weight: 800;
        font-size: 15px;
    }

    .total-value {
        font-weight: 700;
        font-size: 15px;
    }

    .camouflage-total {
        background: linear-gradient(135deg, #27ae60 0%, #2ecc71 100%) !important;
    }

    .military-total {
        background: linear-gradient(135deg, #2980b9 0%, #3498db 100%) !important;
    }

    /* 错误信息优化 */
    .error-message {
        padding: 0 40px 40px;
    }

    .error-alert {
        display: flex;
        align-items: center;
        gap: 15px;
        background: linear-gradient(135deg, #ffeaa7 0%, #fab1a0 100%);
        padding: 20px;
        border-radius: 12px;
        border-left: 6px solid #e74c3c;
    }

    .error-icon {
        font-size: 2rem;
    }

    .error-content {
        flex: 1;
    }

    .error-title {
        font-weight: 700;
        color: #c0392b;
        margin-bottom: 4px;
    }

    .error-text {
        color: #7f8c8d;
        font-weight: 500;
    }

    /* 响应式设计优化 */
    @media (max-width: 768px) {
        .batch-process-root {
            padding: 10px;
        }

        .batch-process-container {
            border-radius: 12px;
        }

        .page-header {
            padding: 20px;
        }

        .page-title {
            font-size: 2rem;
        }

        .control-panel {
            padding: 20px;
        }

        .button-group {
            flex-direction: column;
            width: 100%;
        }

        .upload-area {
            width: 100%;
            justify-content: center;
        }

        .overall-metrics {
            padding: 20px;
        }

        .basic-info-cards {
            grid-template-columns: 1fr;
        }

        .total-stats-grid {
            grid-template-columns: 1fr;
        }

        .category-stats-grid {
            grid-template-columns: 1fr;
        }

        .key-metrics {
            grid-template-columns: 1fr;
        }

        .section-header {
            flex-direction: column;
            gap: 15px;
            align-items: flex-start;
        }

        .subsection-header {
            flex-direction: column;
            gap: 10px;
            align-items: flex-start;
        }

        .targets-grid {
            justify-content: center;
        }

        .detailed-stats-table {
            font-size: 12px;
        }

        .category-section {
            padding: 20px;
        }

        .detailed-stats-subsection {
            padding: 15px;
        }

        .detailed-stats-table table {
            min-width: 800px;
        }
    }
</style>