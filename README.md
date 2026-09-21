# 高光谱融合探测系统

## 项目结构

- `hyperspectral-server/`：当前后端项目，基于 Spring Boot。
- `hyperspectral-web/`：当前前端项目，作为新项目的前端起点使用，来源于原 `fd/`。
- `train_fuse_final.py`：融合计算成像默认使用的盲核估计与 MIAE 融合算法。
- `miae_fusion_runner.py`：Spring Boot 与 MIAE 算法之间的命令行桥接程序。
- `Hysure-python/`：HySure 融合算法及 Spring Boot 调用桥接。
- `lab-htd-main/`：HTD-Mamba 探测运行源码，不包含数据集和模型权重。

## 大文件说明

为保持仓库轻量，本仓库不包含高光谱数据集、MAT 输入、模型权重、运行结果和前端演示视频。
这些文件需在本地准备，并通过 `application.yml` 中对应的 `HYPER_*` 环境变量指定路径。
依赖中的 OpenCV 由 Maven 自动下载，不需要在仓库中保存 JAR 文件。

## 后端目录约定

后端统一采用“技术层优先”结构，而不是“功能模块优先”结构。  
即在 `hyperspectral-server/src/main/java/com/hyper/spectral/` 下优先按：

- `controller/`
- `service/`
- `dto/`
- `vo/`

进行分层。其中特别约定：

- `service/` 根目录下直接存放六个模块的服务接口文件。
- `service/impl/` 放置各模块对应的服务实现类。

其余技术层再按业务模块细分，例如：

- `controller/acquisition/`
- `service/AcquisitionService.java`
- `service/impl/MockAcquisitionService.java`
- `dto/fusion/`
- `vo/visualization/`

共享能力继续放在 `common/`、`config/`、`exception/`、`support/adapter/`。

## 启动方式

### 后端

```bash
cd hyperspectral-server
mvn spring-boot:run
```

#### Windows CMD 临时切换数据目录

在本地数据根目录下按日期存放 HDR、IMG 等配套文件时，可以在启动后端前临时设置可视化和融合输入目录：

```cmd
set HYPER_VISUALIZATION_ROOT=\\DESKTOP-KK21D6V\Wayho
set HYPER_FUSION_SEARCH_ROOT=\\DESKTOP-KK21D6V\Wayho
set HYPER_MIAE_PYTHON=D:\Anaconda3\envs\htd\python.exe
mvn spring-boot:run
```
- `HYPER_VISUALIZATION_ROOT`：高光谱可视化的数据查找根目录。
- `HYPER_FUSION_SEARCH_ROOT`：融合算法的输入数据查找根目录。
- `set` 设置仅对当前 CMD 窗口有效，关闭窗口后自动失效；修改变量后需要重启后端。
- 例如 `D_20251012_134851.hdr` 及其同名 `.img` 文件应放在 `E:\Java\code\Hyper\20251012\` 下。
- 融合结果仍输出到 `hyperspectral-server/runtime/fusion-results/`，不受上述输入目录配置影响。

### MIAE 融合算法

“融合计算成像”默认调用 `train_fuse_final.py`。流程保留原有图像配准，随后依次进行盲 PSF/SRF
估计、MIAE 训练和分块推理。每次任务会在 `hyperspectral-server/runtime/fusion-results/miae_*`
中生成 `result/X.mat`、预览 PNG、模型参数和训练日志。页面光谱曲线通过内存映射
`X_normalized.npy` 读取，不会在每个鼠标位置重新启动 Python。

默认使用 `htd` Conda 环境和 CUDA。主要配置可以通过环境变量覆盖：

- `HYPER_MIAE_PYTHON`：Python 解释器绝对路径；未设置时使用 `conda run -n htd python`。
- `HYPER_MIAE_DEVICE`：计算设备，默认 `cuda`，可设为 `cpu`。
- `HYPER_MIAE_BLIND_ITERATIONS`：盲核训练次数，默认 `3000`。
- `HYPER_MIAE_FUSION_ITERATIONS`：融合网络训练次数，默认 `5000`。
- `HYPER_MIAE_BATCH_SIZE`：训练批大小，系统默认 `16`；显存充足时可提高到脚本原始值 `64`。
- `HYPER_MIAE_TIMEOUT_SECONDS`：单次融合超时，默认 `10800` 秒。

如需临时回退 HySure，可设置 `HYPER_MIAE_ENABLED=false` 和
`HYPER_HYSURE_ENABLED=true`，然后重启后端。

### 融合 MAT 流式写入

融合流程生成的 MATLAB V5 文件已改为流式写入。旧实现会先把完整高光谱立方体写入多个
`ByteArrayOutputStream`，再整体复制到磁盘。对于 `846×846×320` 的 float32 融合结果，
最终 MAT 约为 874 MiB，但保存阶段会产生约 2.7～3 GiB 的额外堆内存占用，JVM 堆不足时
可能只留下一个 128 字节的 MAT 文件头。

当前实现具有以下特性：

- 使用 64 KiB 缓冲区，将立方体中的 float32 数据按 MATLAB 列优先顺序直接写入磁盘。
- `HRHSI` 仍保存为 `single`，`wavelengths` 仍保存为 `double`，不改变融合结果、波段顺序、
  数据精度、PNG 生成和光谱曲线查看。
- 最终 MAT 文件大小不变，但写入阶段的额外内存由数 GiB 降至约 64 KiB。
- 所有通过 `Dataset.MatIO.write` 生成的 MATLAB V5 文件都会使用流式写入，包括
  `preprocessed.mat`、`registered.mat` 和最终的 `fusion_*.mat`。
- 写入时先在目标目录生成随机命名的 `.tmp` 文件，完整关闭后再原子替换正式 MAT；写入失败
  不会发布只有文件头或数据不完整的正式文件。
- 单个 MATLAB V5 变量目前限制在 2 GiB 以内；如果以后输出尺寸超过该限制，需要接入
  MATLAB V7.3/HDF5 存储。

流式写入显著降低了 MAT 保存阶段的内存峰值，但 CNMF 计算本身仍会使用大量矩阵内存。
当前尺寸建议使用 `-Xmx8g`，内存充足时可以使用 `-Xmx10g`。修改代码或 JVM 参数后需要
重启后端，并重新执行融合；此前生成的 128 字节 MAT 无法从 PNG 恢复完整光谱数据。

### 前端

```bash
cd hyperspectral-web
npm install
npm run dev:web
```

## 当前原则

- 新功能统一落在 `hyperspectral-server/` 和 `hyperspectral-web/`。
- 前端以 `hyperspectral-web/` 为唯一开发入口，不再以 `fd/` 作为开发落点。
- 后端结构统一遵循“技术层优先”。
- 后端 `service/` 根目录固定存放六个模块的接口文件，`service/impl/` 固定存放实现类。
- 后端迁移以兼容旧接口契约为优先；公开源码包不包含旧 FastAPI 实现。
