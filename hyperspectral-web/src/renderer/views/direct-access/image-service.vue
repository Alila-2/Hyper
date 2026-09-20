<template>
    <div class="direct-access-container">
        <div class="access-header">
            <h2>系统监控服务</h2>
            <p>服务地址: {{ serviceUrl }}</p>
            <el-button type="primary"
                       icon="el-icon-link"
                       @click="openInBrowser"
                       class="browser-btn">
                在浏览器中打开
            </el-button>
        </div>

        <div class="connection-status">
            <el-alert :title="statusTitle"
                      :type="statusType"
                      :description="statusDescription"
                      :closable="false"
                      show-icon>
                <div class="status-actions" v-if="!isConnected">
                    <el-button type="primary" @click="testConnection">
                        测试连接
                    </el-button>
                    <el-button @click="openInBrowser">
                        在浏览器中打开
                    </el-button>
                </div>
            </el-alert>
        </div>

        <div class="service-info">
            <el-card class="info-card">
                <div slot="header">
                    <span>服务信息</span>
                </div>
                <div class="info-content">
                    <div class="info-item">
                        <label>服务地址:</label>
                        <span class="ip-address">{{ serviceConfig.url }}</span>
                    </div>
                    <div class="info-item">
                        <label>协议:</label>
                        <span>{{ serviceConfig.protocol.toUpperCase() }}</span>
                    </div>
                    <div class="info-item">
                        <label>状态:</label>
                        <el-tag :type="statusTagType">
                            {{ statusText }}
                        </el-tag>
                    </div>
                </div>
            </el-card>
        </div>

        <div class="quick-actions">
            <el-button-group>
                <el-button type="primary"
                           icon="el-icon-link"
                           @click="openInBrowser">
                    打开监控页面
                </el-button>
                <el-button icon="el-icon-refresh"
                           @click="testConnection">
                    测试连接
                </el-button>
                <el-button icon="el-icon-document-copy"
                           @click="copyUrl">
                    复制地址
                </el-button>
            </el-button-group>
        </div>

        <div class="browser-tips">
            <el-alert title="使用说明"
                      type="info"
                      :closable="false">
                <p>由于安全限制，系统监控服务需要在外部浏览器中打开。</p>
                <p>点击"在浏览器中打开"按钮将在您的默认浏览器中访问监控页面。</p>
            </el-alert>
        </div>
    </div>
</template>

<script>
    const { shell } = require('electron').remote || require('electron');

    export default {
        name: 'Monitoring',
        data() {
            return {
                serviceConfig: {
                    url: '192.168.1.123', // 监控服务IP
                    title: '系统监控',
                    protocol: 'http'
                },
                loading: false,
                error: false,
                errorMessage: '',
                connectionTested: false,
                isConnected: false
            }
        },
        computed: {
            serviceUrl() {
                return `${this.serviceConfig.protocol}://${this.serviceConfig.url}`
            },
            statusTitle() {
                if (!this.connectionTested) return '准备就绪';
                return this.isConnected ? '连接正常' : '连接失败';
            },
            statusType() {
                if (!this.connectionTested) return 'info';
                return this.isConnected ? 'success' : 'error';
            },
            statusDescription() {
                if (!this.connectionTested) return '点击测试连接按钮检查服务状态';
                return this.isConnected
                    ? '服务可正常访问，点击下方按钮在浏览器中打开'
                    : this.errorMessage || '无法连接到监控服务，请检查服务状态';
            },
            statusText() {
                if (!this.connectionTested) return '未测试';
                return this.isConnected ? '可访问' : '不可访问';
            },
            statusTagType() {
                if (!this.connectionTested) return 'info';
                return this.isConnected ? 'success' : 'danger';
            }
        },
        methods: {
            // 在外部浏览器中打开
            openInBrowser() {
                try {
                    if (typeof shell !== 'undefined' && shell.openExternal) {
                        shell.openExternal(this.serviceUrl);
                    } else {
                        // 备用方案：使用window.open
                        window.open(this.serviceUrl, '_blank');
                    }
                    this.$message.success('正在浏览器中打开监控页面...');
                } catch (error) {
                    console.error('打开浏览器失败:', error);
                    this.$message.error('打开浏览器失败，请手动访问: ' + this.serviceUrl);
                }
            },

            // 测试连接
            async testConnection() {
                this.loading = true;
                this.connectionTested = true;

                try {
                    // 使用fetch测试连接
                    const response = await fetch(this.serviceUrl, {
                        method: 'HEAD',
                        mode: 'no-cors',
                        cache: 'no-cache'
                    });

                    this.isConnected = true;
                    this.error = false;
                    this.$message.success('连接测试成功！服务可正常访问');

                } catch (error) {
                    console.error('连接测试失败:', error);
                    this.isConnected = false;
                    this.error = true;
                    this.errorMessage = `连接测试失败: ${error.message}`;
                    this.$message.error('连接测试失败，请检查服务状态');
                } finally {
                    this.loading = false;
                }
            },

            // 复制URL到剪贴板
            copyUrl() {
                navigator.clipboard.writeText(this.serviceUrl)
                    .then(() => {
                        this.$message.success('地址已复制到剪贴板');
                    })
                    .catch(err => {
                        console.error('复制失败:', err);
                        this.$message.error('复制失败');
                    });
            }
        },
        mounted() {
            // 组件加载后自动测试连接
            this.testConnection();
        }
    }
</script>

<style scoped>
    .direct-access-container {
        padding: 20px;
        height: calc(100vh - 84px);
        display: flex;
        flex-direction: column;
        gap: 20px;
    }

    .access-header {
        display: flex;
        align-items: center;
        gap: 20px;
        padding: 20px;
        background: #fff;
        border-radius: 8px;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    }

        .access-header h2 {
            color: #303133;
            margin: 0;
            font-size: 24px;
        }

        .access-header p {
            color: #606266;
            margin: 0;
            flex: 1;
        }

    .browser-btn {
        margin-left: auto;
    }

    .connection-status {
        margin: 0 20px;
    }

    .status-actions {
        margin-top: 10px;
        display: flex;
        gap: 10px;
    }

    .service-info {
        margin: 0 20px;
    }

    .info-card {
        border: none;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    }

    .info-content {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .info-item {
        display: flex;
        align-items: center;
        gap: 10px;
    }

        .info-item label {
            font-weight: 600;
            color: #303133;
            min-width: 80px;
        }

    .ip-address {
        font-family: 'Courier New', monospace;
        background: #f5f7fa;
        padding: 4px 8px;
        border-radius: 4px;
        border: 1px solid #e6ebf5;
    }

    .quick-actions {
        display: flex;
        justify-content: center;
        margin: 0 20px;
    }

    .browser-tips {
        margin: 0 20px;
    }

    /* 响应式设计 */
    @media (max-width: 768px) {
        .access-header {
            flex-direction: column;
            align-items: flex-start;
            gap: 10px;
        }

        .browser-btn {
            margin-left: 0;
            align-self: stretch;
        }

        .info-item {
            flex-direction: column;
            align-items: flex-start;
            gap: 5px;
        }

        .status-actions {
            flex-direction: column;
        }

        .quick-actions {
            flex-direction: column;
            gap: 10px;
        }

        .el-button-group {
            flex-direction: column;
        }

            .el-button-group .el-button {
                margin: 5px 0;
            }
    }
</style>