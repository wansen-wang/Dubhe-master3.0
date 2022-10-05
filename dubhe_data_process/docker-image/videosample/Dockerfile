FROM python:3.6.3

MAINTAINER root

RUN cp /etc/apt/sources.list /etc/apt/sources.list.bak

ADD sources.list /etc/apt/

# 安装基础包

RUN DEBIAN_FRONTEND=noninteractive apt-get update 

ENV LANG C.UTF-8

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/ --upgrade pip

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  opencv-python-headless

RUN pip install --user -i https://mirrors.cloud.tencent.com/pypi/simple/  redis

RUN echo 'success!!!'