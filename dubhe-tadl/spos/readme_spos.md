# Single Path One-Shot(SPOS)
## **简介** 
该方法由[Single Path One-Shot Neural Architecture Search with Uniform Sampling](https://arxiv.org/abs/1904.00420)
中提出，主体思想可以分为两个部分，分别是Single Path和One-shot。其中One-Shot指，前期训练一个超网络，
后期对超网络不断进行采样或剪枝等等的方法来获得最终的子网络。而Single Path指，在对于训练好的超网络，每一个模型都是超网络的一条路径。
该算法整体来看即：将网络的层级结构视为一条路径，路径的节点即每个神经层，每个节点有多种选择（多种神经层），对每个节点进行采样得到一个确定的神经层，
并连接每个节点成为一个路径，该路径即最终采样得到的子网络。
  
本实例参照microsoft nni中的spos repo实现了spos的超网训练、子网络的进化搜索、最终选取网络的重训练。

## 使用介绍

- 模型的训练用到了NVIDIA dali工具，需要提前[安装](https://docs.nvidia.com/deeplearning/dali/user-guide/docs/installation.html)
- 模型的训练使用imagenet数据集，需要提前准备
- 模型的flops计算需要用到一个flops查找表，可以在[megvii](https://onedrive.live.com/?authkey=%21ADesvSdfsq%5FcN48&id=E7CA2ABE6D98E66F%21106&cid=E7CA2ABE6D98E66F)
下载。同时这里还可以下载到官方提供的supernet模型，以及最终重训练的模型等等。

### **目录结构**
可以将imagenet数据放在```./data```目录下，标准的数据处理方式可以参考[这里](https://gist.github.com/BIGBALLON/8a71d225eff18d88e469e6ea9b39cef4)
  
imagenet文件准备好之后，训练集和测试集应分别包含1000个子文件夹。
  
将文件准备齐全之后，目录结构应类似如下：
```
spos
├── architecture_final.json
├── blocks.py
├── config_search.yml
├── data
│   ├── imagenet
│   │   ├── train
│   │   └── val
│   └── op_flops_dict.pkl
├── dataloader.py
├── network.py
├── readme.md
├── scratch.py
├── supernet.py
├── tester.py
├── evolution_tuner.py
└── utils.py
```

###  **超网络的训练** 
```python supernet.py```
- 如果不需要训练整个超网络，可以试用上述地址中下载的supernet网络，并将其放在```./data```目录下
- 训练完成之后，checkpoint会到处在```./checkpoints```路径下
— 为了和[官方repo](https://github.com/megvii-model/SinglePathOneShot) 保持一致，数据的通道使用BGR模式，同时数据的输入范围保持在[0,255].

###  **子网络的进化搜索**
首先准备搜索空间
  
```python tester.py --mode gen```
  
然后进行基于进化算法的搜索
   
```python search.py```
- 每次进化都会选出若干最优，其数目定义在dali_loader.py中，最终的准确率保存在```./acc```，路径下
- 进化的模型结构（仅包含结构的json文件）保存在```./checkpoints```路径下
- 模型结构的映射关系保存在```./id2cand```路径下

###  **最终模型的重训练**  
```python scartch.py```




today