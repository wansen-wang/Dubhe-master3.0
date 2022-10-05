# Cream of the Crop: Distilling Prioritized Paths For One-Shot Neural Architecture Search

## 0x01 requirements

* Install the following requirements:

```
future
thop
timm<0.4
yacs
ptflops==0.6.4
#tensorboardx
#tensorboard
#opencv-python
#torch-scope
#git+https://github.com/sovrasov/flops-counter.pytorch.git
#git+https://github.com/Tramac/torchscope.git
```

* (required) Build and install apex to accelerate the training
 (see [yuque](https://www.yuque.com/kcgyxv/ukpea3/mxz5xy)),
 a little bit faster than pytorch DistributedDataParallel.

* Put the imagenet data in `./data` Using the following script:

```
cd TADL_DIR/pytorch/cream/
ln -s /mnt/data .
```

##  0x02 Quick Start

* Run the following script to search an architecture.

```
python trainer.py
```

* Selector (deprecated)

```
python selector.py
```

* Train searched architectures.

> Note: exponential moving average(model_ema) is not available yet.

```
python retrainer.py
```

<!--
* Test trained models.

```
$ cp configs/test.yaml.example configs/test.yaml
$ python -m torch.distributed.launch --nproc_per_node=1 ./test.py --cfg ./configs/test.yaml

> 01/26 02:06:27 AM | [Model-14] Flops: 13.768M Params: 2.673M
> 01/26 02:06:30 AM | Training on Process 0 with 1 GPUs.
> 01/26 02:06:30 AM | Restoring model state from checkpoint...
> 01/26 02:06:30 AM | Loaded checkpoint './pretrained/14.pth.tar' (epoch 591)
> 01/26 02:06:30 AM | Loaded state_dict_ema
> 01/26 02:06:32 AM | Test_EMA: [   0/390]  Time: 1.573 (1.573)  Loss:  0.9613 (0.9613)  Prec@1: 82.8125 (82.8125)  Prec@5: 91.4062 (91.4062)
> ...
> 01/26 02:07:50 AM | Test_EMA: [ 390/390]  Time: 0.077 (0.203)  Loss:  3.4356 (2.0912)  Prec@1: 25.0000 (53.7640)  Prec@5: 53.7500 (77.2840)
```
-->
