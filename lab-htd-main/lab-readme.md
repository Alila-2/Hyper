# HTD-Mamba Lab Quick Start

## 1. 环境

Windows + NVIDIA 显卡建议使用 Python 3.10：

```bash
conda create -n htd-mamba python=3.10 -y
conda activate htd-mamba
pip install -r requirements.txt
```

检查是否能用 GPU：

```bash
python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'cpu')"
```

程序默认 `--device auto`：优先 `cuda:0`，没有 CUDA 时 macOS 用 `mps`，再不行用 CPU。

仓库保留了原版 Mamba 的 `setup.py`、`csrc/`、`mamba_ssm/`，Linux CUDA
环境如果额外执行 `pip install .` 成功安装 `selective_scan_cuda`，程序会自动使用
CUDA 加速；普通 Windows 使用流程不需要这一步。

## 2. 数据放哪里

把数据集 `.mat` 放到：

```text
datasets/
```

文件名就是数据集名。例如：

```text
datasets/D_20251023_132740.mat
```

训练/检测时写：

```bash
python main.py --dataset D_20251023_132740 ...
```

支持的数据立方体变量名：

```text
X
hyperspectral_data
data
```

未在 `main.py` 里单独注册的数据集会使用通用默认参数：

```text
patch_size = 11
m = 15
```

这也是当前 `detection1`、`detection2`、`detection3` 使用的参数。

例如，拿到 `D_20251023_132740.mat` 后放到：

```text
datasets/D_20251023_132740.mat
```

如果里面已有：

```text
hyperspectral_data: 512 x 569 x 320
map: 512 x 569
d1, d2, d3, d8
```

## 3. 没有 GT/先验时：先打标签

打开标注工具：

```bash
python tools/gt_label_tool.py datasets/D_20251023_132740.mat d1
```

换类别就改 `dN`：

```bash
python tools/gt_label_tool.py datasets/D_20251023_132740.mat d2
```

换显示波段：

```bash
python tools/gt_label_tool.py datasets/D_20251023_132740.mat d1 --bands 67,45,120
```

标注工具只写回 `.mat` 内部的：

```text
map
dN
```

不会生成 PNG 或其他结果文件。界面里左键标记，右键取消，`s` 保存并关闭，`u` 撤销，`q` 关闭。

## 4. 训练

单数据集训练：

```bash
python main.py --state train --dataset D_20251023_132740 --epoch 20
```

如果想避免覆盖默认 ckpt 目录，可以指定运行名：

```bash
python main.py --state train --dataset D_20251023_132740 --run-name test_run --epoch 20
```

临时修改模型结构参数：

```bash
python main.py --state train --dataset D_20251023_132740 --epoch 20 --patch-size 11 --m 15
```

同一个 ckpt 训练和检测必须使用相同的 `--m`。

ckpt 默认保存到：

```text
models/D_20251023_132740/ckpt_0_.pt
models/D_20251023_132740/ckpt_1_.pt
...
```

联合训练一个文件夹内所有同波段 `.mat`：

```bash
python main.py --state train --datasets datasets --run-name joint_320 --train-band 320 --epoch 5
```

波段不匹配的数据会打印日志并跳过。

## 5. 检测

用数据集内部已有先验检测：

```bash
python main.py --state eval --dataset D_20251023_132740 --prior all --weight checkpoints/joint_ckpt.pt
```

`checkpoints/joint_ckpt.pt` 是当前联合训练 5 个 epoch 后选定的最终模型。自己重新训练后，也可以把 `--weight` 换成 `models/<run-name>/ckpt_*.pt`。

检测结果默认保存到：

```text
results/D_20251023_132740/detection_d1.mat
results/D_20251023_132740/detection_d2.mat
...
```

指定检测结果目录：

```bash
python main.py --state eval --dataset D_20251023_132740 --prior all --weight checkpoints/joint_ckpt.pt --result results/my_run
```

此时输出到：

```text
results/my_run/detection_d1.mat
results/my_run/detection_d2.mat
...
```

如果希望内部没有的先验从可选的 `wzcl.mat` 补齐：

```bash
python main.py --state eval --dataset D_20251023_132740 --prior all --weight checkpoints/joint_ckpt.pt --fill-priors
```

不加 `--fill-priors` 时，只用 `.mat` 内部已有的 `dN`。如果加了
`--fill-priors` 但找不到 `wzcl.mat`，程序会打印日志并跳过外部补全，不会中断
已有内部先验的检测。

## 6. 输出说明

主程序不弹图，但会静默保存检测结果：

```text
detection_d1.mat
detection_d1.png
detection_d2.mat
detection_d2.png
...
```

`--prior all` 还会额外保存一张总览图：

```text
detection_all_vis.png
```

每个检测结果 `.mat` 包含：

```text
detection_map
```

默认后处理是：

```text
rbf, gamma=10
```

关闭后处理：

```bash
python main.py --state eval --dataset D_20251023_132740 --prior all --weight checkpoints/joint_ckpt.pt --postprocess none
```
