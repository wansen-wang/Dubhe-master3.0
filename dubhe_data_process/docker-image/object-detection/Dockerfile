FROM nvidia/cuda:10.0-cudnn7-devel-ubuntu18.04

MAINTAINER root

RUN cp /etc/apt/sources.list /etc/apt/sources.list.bak

ADD sources.list /etc/apt/

# 安装基础包

RUN rm /etc/apt/sources.list.d/cuda.list

RUN DEBIAN_FRONTEND=noninteractive apt-get update 

RUN DEBIAN_FRONTEND=noninteractive apt-get -y install wget curl 

RUN apt-get install -y zip

RUN apt-get install -y libgl1-mesa-glx

RUN apt-get install -y libglib2.0-0

ENV LANG C.UTF-8

RUN apt-get update

# 安装python环境 3.5
RUN apt install -y python3.5

RUN ln -s /usr/bin/python3.5 /usr/bin/python

RUN curl -O https://bootstrap.pypa.io/pip/3.5/get-pip.py

RUN python get-pip.py --user -i https://mirrors.cloud.tencent.com/pypi/simple/

RUN ln -s /root/.local/bin/pip3.5 /usr/bin/pip

RUN wget https://dubhe.oos-cn.ctyunapi.cn:443/oneflow.zip -O /tmp/oneflow.zip

RUN unzip /tmp/oneflow.zip -d /tmp

ENV LD_LIBRARY_PATH=/tmp/darknet_dependencies/:$LD_LIBRARY_PATH

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/ --upgrade pip

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/ /tmp/oneflow-0.0.1-cp35-cp35m-linux_x86_64.whl

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/ /tmp/oneflow_yolov3-0.0.0-py3-none-any.whl

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  pydicom

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/ opencv-python

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  scikit-image

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  Pillow

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  redis

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  scipy

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  nvidia-ml-py3

RUN pip install protobuf==3.17.0 --user -i https://mirrors.cloud.tencent.com/pypi/simple/

RUN echo 'success!!!'