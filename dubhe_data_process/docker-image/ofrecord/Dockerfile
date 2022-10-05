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

# 安装python环境 3.7

COPY Python-3.7.4.tgz /tmp

RUN apt-get install -y zlib1g=1:1.2.8.dfsg-2ubuntu4.3 --allow-downgrades

RUN apt-get install -y libffi6=3.2.1-4 --allow-downgrades

RUN apt-get install -y libffi-dev

RUN apt-get install -y libssl-dev

RUN apt-get install -y zlib*

RUN cd /tmp && tar -zxvf Python-3.7.4.tgz

RUN cd /tmp/Python-3.7.4 && ./configure && make && make install

RUN ln -s /usr/local/bin/python3.7 /usr/bin/python

RUN rm -rf /usr/bin/pip

RUN ln -s /usr/local/bin/pip3.7 /usr/bin/pip

RUN wget https://dubhe.oos-cn.ctyunapi.cn:443/oneflow.zip -O /tmp/oneflow.zip

RUN unzip /tmp/oneflow.zip -d /tmp

ENV LD_LIBRARY_PATH=/tmp/darknet_dependencies/:$LD_LIBRARY_PATH

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/ --upgrade pip

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/ /tmp/oneflow_cu102-0.3.4-cp37-cp37m-manylinux2014_x86_64.whl

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/ /tmp/oneflow_yolov3-0.0.0-py3-none-any.whl

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/ opencv-python

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  numpy

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  redis

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  nvidia-ml-py3

RUN pip install protobuf==3.17.0 --user -i https://mirrors.cloud.tencent.com/pypi/simple/

RUN echo 'success!!!'