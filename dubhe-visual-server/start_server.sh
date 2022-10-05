#!/bin/bash

DIRNAME=$(pwd)

cd backend
nohup python main.py --port 9898 > django.log 2>&1 &
echo 'http 服务启动'

cd ../parser_service
nohup python master.py >parser.log 2>&1 &
echo 'parser 服务启动'

cd ../service_utils
nohup python monitor.py >monitor.log 2>&1 &
echo 'monitor 服务启动'
tail -f /home/dubhe-visual-server-v2/backend/django.log
