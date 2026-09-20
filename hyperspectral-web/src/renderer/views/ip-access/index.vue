<template>
    <div class="main-container1">
        <iframe id="ipFrame" src="http://192.168.1.121/" frameborder="0" width="100%" height="100%"></iframe>

        <!-- 遮挡logo -->
        <div class="logo-overlay"></div>

        <!-- ✅ 透明背景的加载转圈（拍摄/处理时显示，完成后自动消失） -->
        <div v-if="showProcessingDialog" class="loading-fab" role="status" aria-live="polite" aria-label="正在处理">
            <span class="spinner" aria-hidden="true"></span>
        </div>

        <button @click="autoCapture" class="capture-btn" :disabled="isCapturing">
            {{ isCapturing ? '拍摄中...' : '一键拍摄' }}
        </button>
    </div>
</template>

<script>
    import axios from 'axios'

    export default {
        name: 'IpFrameView',
        data() {
            return {
                isCapturing: false,
                showProcessingDialog: false,
                processingMessage: '',
                processingProgress: ''
            }
        },
        methods: {
            async autoCapture() {
                if (this.isCapturing) return
                this.isCapturing = true
                const baseURL = 'http://192.168.1.121:8020'

                try {
                    this.$message.info('开始成像')

                    // 显示透明加载圈
                    this.showProcessingDialog = true
                    this.processingMessage = '正在检查当前文件状态...'
                    this.processingProgress = ''

                    const checkResponse = await axios.post('http://localhost:8000/api/check-capture-complete')
                    if (!checkResponse.data.success) throw new Error('无法获取当前文件状态')

                    const latestHdrBefore = checkResponse.data.latest_hdr_before
                    const latestDir = checkResponse.data.latest_dir

                    // 切换与拍摄流程
                    await axios.post(`${baseURL}/camera/change`, null, { params: { camera: 'color' } })
                    await this.delay(4000)
                    await axios.put(`${baseURL}/camera/colorCamera`, null, { params: { operate: 'snapshot' } })
                    await axios.post(`${baseURL}/camera/change`, null, { params: { camera: 'spectral' } })
                    await axios.put(`${baseURL}/camera/spectralCamera`, null, { params: { operate: 'center' } })
                    await this.delay(3000)
                    await axios.put(`${baseURL}/camera/spectralCamera`, null, { params: { operate: 'start' } })

                    // 等待拍摄完成
                    this.processingMessage = '等待拍摄完成...'
                    const waitResponse = await axios.post(
                        'http://localhost:8000/api/wait-for-capture-complete',
                        { latest_hdr_before: latestHdrBefore, latest_dir: latestDir },
                        { timeout: 610000 }
                    )

                    if (waitResponse.data.success) {
                        this.processingMessage = '拍摄完成，开始预处理...'
                        await this.processCapturedData()
                        this.$message.success('拍摄与处理完成')
                    } else {
                        throw new Error(waitResponse.data.message || '拍摄未完成')
                    }
                } catch (error) {
                    console.error('❌ 拍摄出错:', error)
                    let errorMsg = '自动拍摄失败'
                    if (error && error.response) {
                        const msg = (error.response && error.response.data && error.response.data.message) ? error.response.data.message : ''
                        errorMsg = `服务器错误: ${error.response.status} - ${msg}`
                    } else if (error && error.request) {
                        errorMsg = '网络错误：无法连接到服务'
                    } else {
                        errorMsg = `请求出错：${(error && error.message) ? error.message : ''}`
                    }
                    this.$message.error(errorMsg)
                } finally {
                    this.isCapturing = false
                    // 完成/失败后立即隐藏加载圈
                    this.showProcessingDialog = false
                }
            },

            async processCapturedData() {
                try {
                    // ✅ 修改：调用带状态管理的预处理接口
                    const response = await axios.post('http://localhost:8000/api/pre_load-hyperspectral-data', {}, {
                        timeout: 60000
                    })

                    if (response.data.success) {
                        // ✅ 保存完整的预处理结果，供可视化界面使用
                        localStorage.setItem('latestProcessingResult', JSON.stringify(response.data))

                        console.log('预处理完成，数据已保存:', {
                            原始图像尺寸: `${response.data.original_width} × ${response.data.original_height}`,
                            伪彩图尺寸: `${response.data.pseudo_width} × ${response.data.pseudo_height}`,
                            总波段数: response.data.total_bands,
                            可视化波段: response.data.visualization_bands
                        })

                        this.$message.success('数据预处理完成')
                    } else {
                        throw new Error(response.data.message || '预处理失败')
                    }
                } catch (error) {
                    console.error('❌ 预处理出错:', error)
                    let errorMsg = '预处理失败'

                    if (error.response) {
                        errorMsg = error.response.data.message || '服务器处理错误'
                    } else if (error.code === 'ECONNABORTED') {
                        errorMsg = '预处理超时，请稍后手动处理数据'
                    } else {
                        errorMsg = error.message || '预处理过程中发生错误'
                    }

                    this.$message.error(`数据处理失败：${errorMsg}`)
                    throw error
                }
            },

            delay(ms) {
                return new Promise(resolve => setTimeout(resolve, ms))
            }
        }
    }
</script>

<style scoped>
    /* 样式保持不变 */
    .main-container1 {
        flex: 1;
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 0;
        position: relative;
        height: 100vh;
        overflow: hidden;
        background: #f5f5f5;
    }

    iframe {
        width: 100%;
        height: 100%;
        border: 0;
        border-radius: 0;
    }

    .logo-overlay {
        position: absolute;
        top: 0px;
        left: 15px;
        width: 180px;
        height: 35px;
        background: #08132a;
        pointer-events: none;
        z-index: 10;
        border-radius: 4px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    }

    .loading-fab {
        position: fixed;
        top: 16px;
        right: 16px;
        z-index: 10000;
        background: transparent;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 42px;
        height: 42px;
    }

    .spinner {
        width: 26px;
        height: 26px;
        border: 3px solid rgba(229, 231, 235, 0.7);
        border-top: 3px solid #667eea;
        border-radius: 50%;
        animation: spin 1s linear infinite;
    }

    .capture-btn {
        position: absolute;
        bottom: 40px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 1000;
        padding: 14px 32px;
        border: none;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white;
        border-radius: 12px;
        cursor: pointer;
        font-weight: 600;
        font-size: 16px;
        box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3);
        transition: all 0.3s ease;
        min-width: 120px;
    }

        .capture-btn:hover:not(:disabled) {
            transform: translateX(-50%) translateY(-3px);
            box-shadow: 0 8px 25px rgba(0, 0, 0, 0.4);
            background: linear-gradient(135deg, #5a67d8 0%, #6b46c1 100%);
        }

        .capture-btn:disabled {
            opacity: 0.6;
            cursor: not-allowed;
            transform: translateX(-50%);
        }

        .capture-btn:active:not(:disabled) {
            transform: translateX(-50%) translateY(-1px);
        }

    @keyframes spin {
        0% {
            transform: rotate(0deg);
        }

        100% {
            transform: rotate(360deg);
        }
    }
</style>
