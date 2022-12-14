## 依赖

- 依赖 Python3.7 环境，建议使用 Anaconda 创建虚拟环境

```shell
# 首次使用，执行
bash init.sh
# 创建虚拟环境完毕后，后续启动服务，请执行
source start_server.sh
# 关闭服务，请执行
source stop_server.sh
```

- 依赖 [Redis](https://redis.io/download) 作为中间件

## 启动

启动及部署过程参看文档：[部署可视化服务](http://tianshu.org.cn/?/course/1.html)

## 初始请求

在用户初始请求时，先请求等待页面`api/init?id=test&trainJobName=test`其中参数`id`与参数`trainJobName`必须指定。这里请求的是用户为test，train为test的日志。

待后端载入完成指定用后的日志后，若成功则返回：

```json
{"code": 200, "msg": "ok", "data": {"msg": "success", "session_id": "avzppbc6e2jo3t5bbhpokh96gp1vrgju"}}
```

# **以下数据格式仅供参考，以实际返回为准**

## 类目信息

在初始化完成后，需请求类目信息。

请求api`api/getCategory`返回所有类目的标签，若日志中不存在某一类目信息，则返回空数组。

其中图`graph`tag信息，固定为`s_graph`与`c_graph`；超参数没有tag，若日志中含有超参数则返回tag为`true`

格式如下：

返回数据格式如下：

```python
{
	.:    {
            scalar: {}, 
            media: {image: [], audio: [], text: []}, 
            statistic: {histogram: []}, 
            graph: [],
        	...
    	}
	train: {
            scalar: {epoch_loss: ["epoch_loss"], epoch_accuracy: ["epoch_accuracy"]}
            media: {image: [],…}
            statistic: {histogram: []}
            graph: ["c_graph"]
            embedding: ["layer1/weights/Variable:0", "layer1/biases/Variable:0", …]
        	hparams: ["true"]
            custom: ["true"]
            }
	vgg: {
            scalar: {,…}
            media: {,…}
            statistic: {,…}
            graph: ["s_graph"]
            embedding: []
        	hparams: []
            custom: ["true"]
		 }
}
```

## Scalar

根据tag，请求api：`api/scalar?run=.&tag=layer1/weights/summaries/mean`得到tag为`layer1/weights/summaries/mean`的数据

其中`run`与`tag`缺一不可

返回数据格式

```json
{
    "layer1/weights/summaries/mean": 
    [
    	{"wall_time": 1587176310.3070214, "step": 0, "value": -6.488610961241648e-05}, 
    						..., 
    	{"wall_time": 1587176425.348953, "step": 190, "value": -0.002039810409769416}
	]
}
```

随后随着数据量的增大，可能直接返回数组形式

```json
[
    [1587176310.3070214, 0, 0.12780000269412994],
 					...
 	[1587176425.348953, 190, 0.9401999711990356]
]
```

第一列是`wall_time`，第二列是`step`，第三列是`value`

## Image

由于前端不能处理图片，所以图片请求分为两个地址

图片的信息，请求api：`api/image?run=.&tag=input_reshape/input/image/0`可获得tag为`input_reshape/input/image/0`的图片信息

其中`run`与`tag`缺一不可

返回数据格式

```python
{
	"input_reshape/input/image/0": 
    [
	 	{"wall_time": 1587176317.1721938, "step": 10, "width": 28, "height": 28},
							...
	 	{"wall_time": 1587176425.348953, "step": 190, "width": 28, "height": 28}
    ]
}
```

拿到图片信息之后，再向后台请求图片

请求api：`api/image_raw?step=0&run=.&tag=input_reshape/input/image/0`可获得tag为`input_reshape/input/image/0`在第0代时候的图片

其中`step`、`run`与`tag`缺一不可

返回数据为图像本身

## Histogram

请求api：`api/histogram?run=.&tag=layer1/weights/summaries/histogram/histogram_summary`可获得tag为`layer1/weights/summaries/histogram/histogram_summary`的数据

其中`run`与`tag`缺一不可

返回数据格式

```json
{
   "layer1/activations/histogram_summary": 
	[
        [1587176317.1721938, 10, 0.0, 5.297224044799805, 
 			[[0.0, 0.17657413482666015, 2801825.0], 
  							...
  			 [5.120649909973144, 5.297224044799805, 2.0]]
         ],
							...
         [1587176425.348953, 190, 0.0, 6.3502936363220215, 
  			[[0.0, 0.2116764545440674, 3229943.0],
   							...
   			 [6.1386171817779545, 6.3502936363220215, 8.0]]
         ]
   	 ]
}
```

格式为

```json
[
    wall_time, step, min, max, 
	[[left, right, num],
			...
	 [left, right, num]]
]
```

## Distribution

请求api：`api/distribution?run=.&tag=layer1/weights/summaries/histogram/histogram_summary`可获得tag为`layer1/weights/summaries/histogram/histogram_summary`的数据

其中`run`与`tag`缺一不可

返回数据格式

```json
{"layer1/activations/histogram_summary":
 	[
    	[1587176310.3070214, 0, 
  			[[0, 0.0], [668, 0.020253705367086237],..., [10000, 4.971314430236816]]], 	
								...,
		[1587176419.1604998, 180, 
			[[0, 0.0], [668, 0.021192153914175192],..., [10000, 6.3502936363220215]]]
	]
}
```

格式为

```json
[
    wall_time, step,
		[[precentage, value],...,[precentage, value]]
]
```

precentage取值分别为0, 668, 1587, 3085, 5000, 6915, 8413, 9332, 10000 对应标准正态分布的百分位数。

## Text

请求api：`api/text?run=.&tag=custom_tag`可获得tag为`custom_tag`的数据

其中`run`与`tag`缺一不可（目前只有一个数据集，run可随意给，不进行校验）

返回数据格式

```json
{
    "custom_tag": 
 	[
 		{"wall_time": 1585807655.373738, "step": 0, 
 		 "value": "\u8fd9\u662f\u7b2c0\u53e5\u8bdd"},
 						..., 
		{"wall_time": 1585807656.327519, "step": 99, 
          "value": "\u8fd9\u662f\u7b2c99\u53e5\u8bdd"}
	]
}
```

## Audio

与图片类似，由于前端不能处理音频，所以音频请求分为两个地址

音频的信息，请求api：`api/audio?run=.&tag=waveform/audio_summary`可获得tag为`waveform/audio_summary`的音频信息

其中`run`与`tag`缺一不可（目前只有一个数据集，run可随意给，不进行校验）

返回数据格式

```json
{
    "waveform/audio_summary":
 	[
        {"wall_time": 1587475006.5022004, "step": 1, "label": "<p><em>Wave type:</em> <code>sine_wave</code>. <em>Frequency:</em> 448.98 Hz. <em>Sample:</em> 1 of 1.</p>", "contentType": "audio/wav"}, 
									...
	    {"wall_time": 1587475006.7304769, "step": 49, "label": "<p><em>Wave type:</em> <code>sine_wave</code>. <em>Frequency:</em> 880.00 Hz. <em>Sample:</em> 1 of 1.</p>", "contentType": "audio/wav"}
    ]
}
```

拿到音频信息之后，再向后台请求音频

请求api：`api/audio_raw?step=0&run=.&tag=waveform/audio_summary`和得到音频

其中`step`、`run`与`tag`缺一不可（目前只有一个数据集，run可随意给，不进行校验）

返回数据为音频本身

## Embedding

高维数据，由于降维过程较为费时，数据首先在后端进行处理，然后返回降维后的数据。

具体请求也是分为两步，第一步根据run和tag得到数据对应的step信息。

1. 请求指定训练集和标签，返回对应标签的所有step和shape

   请求`api` ：`/api/projector?run=train&tag=outputs`  其中 run 、tag 缺一不可 返回数据格式如下：

   ```python
   {
       "outputs": [0, 1, 2, ... ,n],
       "shape": [n,m]
   }
   ```

2. 请求指定训练集，标签，step,method,dims 返回降维后的数据和原始标签

   请求`api` ：`api/projector_data?run=train&tag=outputs&step=0&method=pca&dims=3` ，其中 run 、tag、step、method 缺一不可，dims默认为3。返回数据格式如下：

   ```python
   {
   		"0": [
               	# 降维后的数据
       			[[-1.5627723114617857, -3.9668523435955056, -0.18872563897943656],
   				[-1.5627723114617857, -3.9668523435955056, -0.18872563897943656],
   				[-1.5627723114617857, -3.9668523435955056, -0.18872563897943656],
   				              				.....
   				[-1.5627723114617857, -3.9668523435955056, -0.18872563897943656]],
               	# label信息
               	 [7,0,5,6,7,1,...,9,6,4]
               ]
   }
   ```
   
3. 请求指定训练集，标签，序号 返回原始训练数据

   请求`api` ：api/projector_sample?run=.&tag=outputs&index=0 ，其中 run 、tag、index 缺一不可。返回原始数据：图片，音频，文本

   

## Graph

由于计算图graph每个网络中只包含一个，所以请求时只需给定run参数即可

请求`api` ：`/api/graph?run=train`

返回run为train的计算图，数据格式为

```json
{
    "net": "[{...}]",  //graph计算图数据
 	"operator": "[...]" //操作结点分类数据
}
```

## exception

异常信息目前以projector的方式进行存取，标签信息请查看embedding（tag以 "**Variable:0**" 结尾）。

具体请求分为两步:

1. 请求指定训练集和标签，返回对应标签的所有step

   请求`api` ：`/api/exception?run=train&tag=layer1/weights/Variable:0`  其中 run 、tag 缺一不可 返回数据格式如下：

   ```python
   {
       "layer1/weights/Variable:0": [0, 1, 2, ... ,n]
   }
   ```

2. 请求指定训练集，标签，step 返回平铺后的异常数据

   请求`api` ：`api/exception_data?run=train&tag=layer1/weights/Variable:0&step=0` ，其中 run 、tag、step缺一不可，返回数据格式如下：

   ```python
   {
       	"0":[ 
               	[c1,c2],  # 平铺前的数据维度大小(长度不定)
               	min,
               	max,
               	mean,
               	[[-1.5627723114617857, -3.9668523435955056, -0.18872563897943656],
   				[-1.5627723114617857, -3.9668523435955056, -0.18872563897943656],
   				[-1.5627723114617857, -3.9668523435955056, -0.18872563897943656],
   				              				.....
   				[-1.5627723114617857, -3.9668523435955056, -0.18872563897943656]]
           	]
   }
   ```
   
   
   
3. 请求指定训练集，标签，step 返回异常数据的直方图信息

   请求`api` ：`api/exception_hist?run=train&tag=layer1/weights/Variable:0&step=0` ，其中 run 、tag、step缺一不可，返回数据格式如下

   ```python
   {
       	"0":[ 
               	min,
               	max,
               	[[left, right, count],
                    [left, right, count],
   					.....
   				[left, right, count]]
           	]
   }
   ```

## Hyperparam

请求api: `api/hyperparm?run=hparams`，可获得（如果有数据的话）run为hparams的超参数数据

返回数据格式：

```
{"hparamsInfo": [{groupid_1: 
                    {"hparams": [{"name": 超参数1, "data": 数据1}, ..., {"name": 超参数2, "data": 数据2}], 
				    "start_time_secs": 开始时间}
				 }
				,..., 
                 {groupid_n: 
                    {"hparams": [{"name": 超参数1, "data": 数据1}, ..., {"name": 超参数2, "data": 数据2}], // 多个超参数的名字与值
				    "start_time_secs": 开始时间}
				}], // 超参数信息，可能有多个
 "metrics": [{"tag": 量度1, "value": [值1, 值2, ...., 值n]},..., {"tag": 量度n, "value": [值1, 值2, ...., 值n]}]}
 //超参数的量度，可能有多个。适用于所有的超参数信息
```

例如train中的超参数数据为：

```
{"hparamsInfo": [{"3df0d7cf35bec5a33c9fe551db732c24df204d7886b226c5a41cce285d0d4fd5": 
                    {"hparams": [{"name": "num_units", "data": 32.0}, 
                                 {"name": "optimizer", "data": "sgd"}, 
                                 {"name": "dropout", "data": 0.2}], 
                    "start_time_secs": 1589421877.1109092}
                }], // 超参数信息，可能有多个
 "metrics": [{"tag": "accuracy", "value": [0.8216999769210815, 0.8241999745368958, 0.7746999859809875, 0.765999972820282, 0.8411999940872192, 0.8307999968528748, 0.7940999865531921, 0.7904999852180481]}]} //超参数的量度，可能有多个。适用于所有的超参数信息
```

## Transformer

Transformer分为视觉Transformer和文本Transformer

### 视觉Transformer

请求api: `api/transformer?run=image&tag=transformer-img0&l=0&x=0&y=0&g=0&r=0`，l为模型第几层数据，x为交互时点击图像的x坐标，y为交互时点击图像的y坐标，g表示是否全局归一化，r表示全局归一化时注意力值缩放的比例。

返回数据格式：

```python
{
   tag:{
        "img": img_data,                  # 图像数据
	    "attn_map": attn_map_datas,   # 图像点击区域所对应的注意力值
	    "layer": l,                   # 返回的是第几层的数据
	}
}
```

例如：

```python
{
   "transformer-img0": {
      "img": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAQAAAAEACAIA7", # 图像数据
	 "attn_map": [
                    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAViklEQVR4nF",
	 			    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAbu0lEQVR4nD",
	 			    ......
	 			    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAW2UlEQVR4nE"
				 ] # 图像点击区域所对应的第l层的注意力值
	 "layer": "0", # 返回的是第几层的数据
    }
}
```

### 文本Transformer

文本Transformer请求分为两步，首先请求文本数据，然后请求对应句子的注意力值。

#### 请求文本数据

请求api: `api/transformer?run=text&tag=a-transformer-sentence`

返回数据格式：

```python
{
   tag: [
        sentence1,
	    sentence2，
	    ......,
	    sentence
     ]
}
```

例如：

```python
{
   "a-transformer-sentence": [
                                "it's a charming and often affecting journey",
	                            "a gorgeous, witty , seductive movie.",
	                            ......
	                            "too slow , too long and too little happens"
                            ]
}
```



#### 请求注意力值

请求api: `api/transformer?run=text&tag=a-transformer-0`

返回数据格式：

```python
{
   tag:{
	"wall_time": 1654566787.412733,
	"step": 0,
	"data":{
		"attention": {
	 		"all":{
	 			"attn": attention_data, # 注意力值
                "left_text": sentence1, # 左侧句子的token
                "right_text"： sentence2, # 右侧句子的token
	 		    }	
	 	    }
	 	"bidrectional": "True",  # 是否双向模型
	 	"default_filter": "all", # 默认filter,为all
	 	"displayMode": "light",  # 显示方案（dark or light）
	 	"layer": "0",  # 默认为0，0为显示层
	 	"head": "0"    # 默认为0，0为显示头
	}
    }
}
```

例如：

```python
{
   'a-transformer-0': {
	"wall_time": 1654566787.412733,
	"step": 0,
	"data":{
		"attention": {
	 		"all":{
	 			"attn": [[[[0.0354335643351078, 0.08718656003475189, 0.03417610377073288, ...]]],
	 			         [[[0.35616645216941833, 0.01612984947860241, 0.01865621656179428, ...]]],
                         ...]# 注意力值
                "left_text": ["[cls]", "it", "is", "a", "charming", "journey"], # 左侧句子的token
                "right_text"： ["[cls]", "it", "is", "a", "charming", "journey"], # 右侧句子的token
	 		}	
	 	}
	 	"bidrectional": "True",  # 是否双向模型
	 	"default_filter": "all", # 默认filter,为all
	 	"displayMode": "light",  # 显示方案（dark or light）
	 	"layer": "0",  # 默认为0，0为显示层
	 	"head": "0"    # 默认为0，0为显示头
	 }
    }
}
```

## featuremap

#### 请求特征图数据

请求api: `api/featuremap?run=featuremap&tag=SequentialtoConv2d[0]-GradCam&range=0&task=Classification`

tag为‘该层结构图uid属性+特征图方法’；range为请求图片起始位置，一次请求16张图片；task为模型任务（'Classification', 'Segmentation', 'Detection'）

返回数据格式：

```python
   {
      SequentialtoConv2d[0]-GradCam: [
         {
            "wall_time": "1652704541.6665807",  
            "step": 0, 
            "Remaining_pictures": 0,    #剩余特征图张数
            "label": label,    #模型识别的真实标签
            "sorce_data": sorce,   #模型预测结果
            "value": featuremap_data  #特征图数据
         }
      ]
    }
```

例如：

```python
   {
      SequentialtoConv2d[0]-GradCam: [
         {
            "wall_time": "1652704541.6665807",  
            "step": 0, 
            "Remaining_pictures": 0,    
            "label": [1, 1],    
            "sorce_data": [[0.9298991560935974, 0.07010085135698318], [0.36296913027763367, 0.637030839920044]],   #模型预测结果
            "value":[
               "0": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAOA...",
               "1": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAOA..."
            ]
         }
      ]
   }
```

## state

RNN隐状态可视化分为两步：首先获取全部维度的隐状态数据进行可视化。当前端传回需要匹配的模式后，返回匹配到的模式数据

### 请求隐状态数据

请求api: `api/state?run=rnn&tag=state&pos=0&range=24`

pos为请求数据初始位置；range为请求字符数

返回数据格式：

```python
   {
      data：{
         "data：hidden_state_data,     #隐状态数据
         "max": max_value,     #隐状态最大值
         "min": min_value,     #隐状态最小值
         "right": num,         #右侧剩余字符串个数
         "word": word          #请求字符串   
      }
   }
```

例如：

```python
   {
      data：{
         "data：[[-0.016503384336829185, 0.07723195850849152, 0.014958282932639122, -0.024111060425639153,…],…],
         "max": 0.12384635955095291,
         "min": -0.15024906396865845,     
         "right":  83,         
         "word": "在用神经网络进行数字识别时，需要对神经网络进行训"         
      }
   }
```

### 请求匹配模式数据

请求api: `api/state_select?run=rnn&tag=state&threshold=0&pattern=110`

threshold为阈值;pattern为匹配模式：1为隐状态高于阈值，0为隐状态低于阈值

返回数据格式：

```python
   {
      data：res    #匹配到的字符
   }
```

例如：

```python
   {
      data：[{id: 0, start_pos: 0, data: "在用神"}, {id: 1, start_pos: 3, data: "经网络"},…]
   }
```
