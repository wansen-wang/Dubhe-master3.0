from nvidia.dali.pipeline import Pipeline
from nvidia.dali import ops
from nvidia.dali import types
from nvidia.dali.plugin.pytorch import DALIClassificationIterator

import numpy as np
import torch
from torch import nn


class HybridTrainPipeline(Pipeline):
    
    def __init__(self, batch_size, file_root, num_threads, device_id, num_shards, shard_id):
        super(HybridTrainPipeline, self).__init__(batch_size, num_threads, device_id)
        
        device_type = {0:"cpu"}
        if num_shards == 0:            
            self.input = ops.FileReader(file_root = file_root)
        else:
            self.input = ops.FileReader(file_root = file_root, num_shards = num_shards, shard_id = shard_id)
            
        # ##### 可自由更改 ###################################
        self.decode = ops.ImageDecoder(device = device_type.get(num_shards, "mixed"), output_type = types.RGB)
        self.res = ops.RandomResizedCrop(device=device_type.get(num_shards, "gpu"), size = 224)
        self.cmnp = ops.CropMirrorNormalize(device=device_type.get(num_shards, "gpu"),
                                            dtype = types.FLOAT,    # output_dtype=types.FLOAT,
                                            output_layout=types.NCHW,
                                            mean=0. ,# if spos_pre else [0.485 * 255, 0.456 * 255, 0.406 * 255],
                                            std=1. )# if spos_pre else [0.229 * 255, 0.224 * 255, 0.225 * 255])
        
        # ####################################################
        
    def define_graph(self, ):
        jpegs, labels = self.input(name="Reader")
        images = self.decode(jpegs)
        images = self.res(images)
        images = self.cmnp(images)
        return images, labels
    
    
class HybridValPipeline(Pipeline):
    
    def __init__(self, batch_size, file_root, num_threads, device_id, num_shards, shard_id):
        super(HybridValPipeline, self).__init__(batch_size, num_threads, device_id)
        
        device_type = {0:"cpu"}
        if num_shards == 0:            
            self.input = ops.FileReader(file_root = file_root)
        else:
            self.input = ops.FileReader(file_root = file_root, num_shards = num_shards, shard_id = shard_id)
        
        
        
        # ##### 可自由更改 ###################################
        self.decode = ops.ImageDecoder(device = device_type.get(num_shards, "mixed"), output_type = types.RGB)
        self.res = ops.RandomResizedCrop(device=device_type.get(num_shards, "gpu"), size = 224)
        self.cmnp = ops.CropMirrorNormalize(device=device_type.get(num_shards, "gpu"),
                                            dtype = types.FLOAT,    # output_dtype=types.FLOAT,
                                            output_layout=types.NCHW,
                                            mean=0. ,# if spos_pre else [0.485 * 255, 0.456 * 255, 0.406 * 255],
                                            std=1. )# if spos_pre else [0.229 * 255, 0.224 * 255, 0.225 * 255])
        
        # ####################################################
        
    def define_graph(self, ):
        jpegs, labels = self.input(name="Reader")
        images = self.decode(jpegs)
        images = self.res(images)
        images = self.cmnp(images)
        return images, labels
    
    
class TorchWrapper:
    
    """
    将多个pipeline封装为一个iterator
    
    parameters:
            num_shards : int 显卡并行数
            data_loader : dali.pipeline.Pipeline类型 经过pipeline处理的数据结果
            iter_mode : str recursion, iter 指定多个pipeline合并的方式，默认recursion
    """
    
    
    def __init__(self, num_shards, data_loader, iter_mode = "recursion"):
        self.index = 0
        self.count = 0
        self.num_shards = num_shards
        self.data_loader = data_loader
        self.iter_mode = iter_mode
        if self.iter_mode not in {"recursion", "iter"}:
            raise Exception("iter_mode should be either 'recursion' or 'iter'")
        
    def __iter__(self,):
        return self
    
    def __len__(self, ):
        # 返回样本总量，而非batch_num
        if num_shards == 0:
            return self.data_loader.size
            
        else:
            return len(self.data_loader)*self.data_loader[0].size
    
    def __next__(self, ):
        if num_shards == 0:
            # 不使用GPU
            data = next(self.data_loader)
            return data[0]["data"], data[0]["label"].view(-1).long()
        
        else:
            # 使用一块或多块GPU
            if self.iter_mode == "recursion":
                return self._get_next_recursion()
            elif self.iter_mode == "iter":
                return self._get_next_iter(self.data_loader[0])
    
    def _get_next_iter(self, data_loader):
        
        if self.count == data_loader.size:
            self.index+=1
            data_loader = self.data_loader[self.index]
            
        self.count+=1
        data = next(data_loader)
        return data[0]["data"], data[0]["label"].view(-1).long()
    
    def _get_next_recursion(self, ):
        
        self.index = self.count%self.num_shards
        self.count+=1
        
        data_loader = self.data_loader[self.index]
        data = next(data_loader)

        return data[0]["data"], data[0]["label"].view(-1).long()

        
def get_iter_dali_cuda(batch_size=256, train_file_root="", val_file_root="", num_threads=4, device_id=[-1], num_shards=0, shard_id=[-1]):
    
    """
    获取可用于pytorch训练的数据迭代器
    数据的读取和处理部分可以使用多张GPU来完成
    
    1、创建dali pipeline
    2、封装为适用于pytorch的数据迭代器
    3、将多卡的各个pipeline封装在一起
    4、数据输出在cpu端，在cuda中
    
    数据需要保证如下形式：
    images
        |-file_list.txt
        |-images/dog
          |-dog_4.jpg
          |-dog_5.jpg
          |-dog_9.jpg
          |-dog_6.jpg
          |-dog_3.jpg
        |-images/kitten
          |-cat_10.jpg
          |-cat_5.jpg
          |-cat_9.jpg
          |-cat_8.jpg
          |-cat_1.jpg
   
    parameters:
    
            batch_size : int 每批数据的量
            file_root : str 数据的路径
            num_threads : int 读取数据的CPU线程数
            device_id : list of int GPU的物理编号
            shard_id : list of int GPU的虚拟编号
            num_shard : int 
    
    methods:
    
            get_train_pipeline(shard_id, device_id) : 创建dali的pipeline，用以读取并处理训练数据
            get_val_pipeline(shard_id, device_id) : 创建dali的pipeline，用以读取并处理验证数据
            get_dali_iter_for_torch(piplines, data_num) : 封装成可用于pytorch的数据迭代器
            get_data_size(pipeline) : 计算每个pipeline实际输出的数据总量，数据总量是文件中的数据量，实际输出是去掉了不满一个批次大小的数据
            
    例：
    # 分别从TRAIN_PATH和VAL_PATH读取训练和验证数据，batch_size选择256，启动4个线程来读取数据，用2块GPU处理数据，分别是第0号和第4号GPU
    # 程序默认使用所有显卡，和4线程
    # 如果使用单张GPU，请设置num_shards = 1, shard_id = [0], device_id保持一个列表形式
    # 如果不使用GPU，请使用get_iter_dali_cpu()
    train_data_iter, val_data_iter = get_iter_dali(batch_size=256,
                                                   train_file_root=TRAIN_PATH,
                                                   val_file_root=Val_PATH,
                                                   num_threads=4,
                                                   device_id=[0,4],
                                                   num_shards=2,
                                                   shard_id=[0,1])
    
    # 在torch中训练
    torch_model = TorchModel(para)
    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.Adam(torch_model.parameters())
    
    for epoch in range(epoches):
        for step, x,y in enumerate(train_data_iter):
        
            # 数据 : x
            # 标签 : y
            x = x.to("cuda:0")
            y = y.to("cuda:0")
            output = my_model(x)
    
            optimizer.zero_grad()
            loss = criterion(output, y)
            loss.backward()
            optimizer.step()
            ...
            ...
    
    """
 
    def get_train_pipeline(shard_id, device_id):
    
        pipeline = HybridTrainPipeline(batch_size = batch_size,
                                       file_root = train_file_root,
                                       num_threads = num_threads,
                                       num_shards = num_shards,
                                       shard_id = shard_id,
                                       device_id = device_id)
        return pipeline

    def get_val_pipeline(shard_id, device_id):
        
        pipeline = HybridValPipeline(batch_size = batch_size,
                                       file_root = val_file_root,
                                       num_threads = num_threads,
                                       num_shards = num_shards,
                                       shard_id = shard_id,
                                       device_id = device_id)
        return pipeline
    
    
    
    pipeline_for_train = [get_train_pipeline(shard_id = shard_id_index, device_id = device_id_index) \
                          for shard_id_index, device_id_index in zip(shard_id, device_id)]
    pipeline_for_val = [get_val_pipeline(shard_id = shard_id_index, device_id = device_id_index) \
                          for shard_id_index, device_id_index in zip(shard_id, device_id)]
    
    
    [pipeline.build() for pipeline in pipeline_for_train]
    [pipeline.build() for pipeline in pipeline_for_val]
    
    
    def get_data_size(pipeline):
        data_num = pipeline.epoch_size()["Reader"]
        batch_size = pipeline.batch_size
        return data_num//batch_size*batch_size
    
    
    data_num_train = get_data_size(pipeline_for_train[0])
    data_num_val = get_data_size(pipeline_for_val[0])
    def get_dali_iter_for_torch(pipelines, data_num):
        return [DALIClassificationIterator(pipelines=pipeline,
                              last_batch_policy="drop",size = data_num) for pipeline in pipelines]
    
    
    data_loader_train = get_dali_iter_for_torch(pipeline_for_train, data_num_train)
    data_loader_val = get_dali_iter_for_torch(pipeline_for_val, data_num_val)
    
    
    train_data_iter = TorchWrapper(num_shards, data_loader_train)
    val_data_iter = TorchWrapper(num_shards, data_loader_val)
    
    
    return train_data_iter, val_data_iter


def get_iter_dali_cpu(batch_size=256, train_file_root="", val_file_root="", num_threads=4):

    pipeline_train = HybridTrainPipeline(batch_size = batch_size,
                                   file_root = train_file_root,
                                   num_threads = num_threads,
                                   num_shards = 0,
                                   shard_id = -1,
                                   device_id = 0)
    
    
    pipeline_val = HybridTrainPipeline(batch_size = batch_size,
                                   file_root = val_file_root,
                                   num_threads = num_threads,
                                   num_shards = 0,
                                   shard_id = -1,
                                   device_id = 0)
    
    pipeline_train.build()
    pipeline_val.build()
    
    def get_data_size(pipeline):
        data_num = pipeline.epoch_size()["Reader"]
        batch_size = pipeline.batch_size
        return data_num//batch_size*batch_size
    
    data_num_train = get_data_size(pipeline_train)
    data_num_val = get_data_size(pipeline_val)
    
    data_loader_train = DALIClassificationIterator(pipelines=pipeline_train,
                                  last_batch_policy="drop",size = data_num_train)
    data_loader_val = DALIClassificationIterator(pipelines=pipeline_val,
                                  last_batch_policy="drop",size = data_num_val)
    
    train_data_iter = TorchWrapper(0,data_loader_train)
    val_data_iter = TorchWrapper(0,data_loader_val)
    
    return train_data_iter, val_data_iter
    
    
    
if __name__ == "__main__":
    
    PATH = "./imagenet"
    TRAIN_PATH = "./imagenet/train"
    VALID_PATH = "./imagenet/val"
    
    train_data_iter_cuda, val_data_iter_cuda = get_iter_dali_cuda(batch_size=256,
                                                                  train_file_root=TRAIN_PATH,
                                                                  val_file_root=TRAIN_PATH,
                                                                  num_threads=4,
                                                                  device_id=[0,4],
                                                                  num_shards=2,
                                                                  shard_id=[0,1])
    
    train_data_iter_cpu, val_data_iter_cpu = get_iter_dali_cpu(batch_size=256,
                                                               train_file_root=TRAIN_PATH,
                                                               val_file_root=TRAIN_PATH,
                                                               num_threads=4)