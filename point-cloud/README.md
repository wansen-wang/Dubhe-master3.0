# Super Fast and Accurate 3D Object Detection based on 3D LiDAR Point Clouds

[![python-image]][python-url]
[![pytorch-image]][pytorch-url]

---

## 1. Getting Started
### 1.1 Requirement

The instructions for setting up a virtual environment is [here](https://github.com/maudzung/virtual_environment_python3).

```shell script
cd SFA3D/
pip install -r requirements.txt
```

### 1.2 Data Preparation
Download the 3D KITTI detection dataset from [here](http://www.cvlibs.net/datasets/kitti/eval_object.php?obj_benchmark=3d).

The downloaded data includes:

- Velodyne point clouds _**(29 GB)**_
- Training labels of object data set _**(5 MB)**_



Please make sure that you construct the source code & dataset directories structure as below.

## 2. How to run


### 2.1 Inference

The pre-trained model was pushed to this repo.
- **CPU**
```
python inference.py --no_cuda=True
```
- **GPU**
```
python inference.py
```
Label of inference

- Pedestrian
- Car
- Cyclist

### 2.2 Training
#### 2.2.1 CPU
```
python train.py --no_cuda=True
```

#### 2.2.2 Single machine, single gpu

```shell script
python train.py --gpu_idx 0
```

#### 2.2.3 Distributed Data Parallel Training
- **Single machine (node), multiple GPUs**

```
python train.py --multiprocessing-distributed --world-size 1 --rank 0 --batch_size 64 --num_workers 8
```

- **Two machines (two nodes), multiple GPUs**

   - _**First machine**_
    ```
    python train.py --dist-url 'tcp://IP_OF_NODE1:FREEPORT' --multiprocessing-distributed --world-size 2 --rank 0 --batch_size 64 --num_workers 8
    ```

   - _**Second machine**_
    ```
    python train.py --dist-url 'tcp://IP_OF_NODE2:FREEPORT' --multiprocessing-distributed --world-size 2 --rank 1 --batch_size 64 --num_workers 8
    ```

## References
[1] SFA3D: [PyTorch Implementation](https://github.com/maudzung/SFA3D)

## Folder structure
### Dataset
```
└── kitti/    
     ├── image_2/ (left color camera，非必须)
     ├── calib/ (非必须)
     ├── label_2/ (标注结果/标签，非必须)
     └── velodyne/ (点云文件，必须) 
```
### Checkpoints & Algorithm
```
${ROOT}
└── checkpoints/
    ├── fpn_resnet_18/    
        ├── fpn_resnet_18_epoch_300.pth (点云目标检测标注模型)     
└── sfa/ (点云标注算法)
    ├── config/
    ├── data_process/
    ├── models/
    ├── utils/
    ├── inference.py
    └── train.py
├── README.md 
├── LICENSE
└── requirements.txt
```



[python-image]: https://img.shields.io/badge/Python-3.6-ff69b4.svg
[python-url]: https://www.python.org/
[pytorch-image]: https://img.shields.io/badge/PyTorch-1.5-2BAF2B.svg
[pytorch-url]: https://pytorch.org/
