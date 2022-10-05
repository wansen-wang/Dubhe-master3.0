# -*- coding: utf-8 -*-
from datetime import datetime
from io import TextIOBase
import logging
from logging import FileHandler, Formatter, Handler, StreamHandler
from pathlib import Path
import sys
import time
from typing import Optional

time_format = '%Y-%m-%d %H:%M:%S'

formatter = Formatter(
    '[%(asctime)s] %(levelname)s (%(name)s/%(threadName)s) %(message)s',
    time_format
)

def init_logger() -> None:
    _setup_root_logger(StreamHandler(sys.stdout), logging.INFO)

    logging.basicConfig()


def _prepare_log_dir(path: Optional[str]) -> Path:
    if path is None:
        return Path()
    ret = Path(path)
    ret.mkdir(parents=True, exist_ok=True)
    return ret

def _setup_root_logger(handler: Handler, level: int) -> None:
    _setup_logger('tadl', handler, level)

def _setup_logger(name: str, handler: Handler, level: int) -> None:
    handler.setFormatter(formatter)
    logger = logging.getLogger(name)
    logger.addHandler(handler)
    logger.setLevel(level)
    logger.propagate = False

class _LogFileWrapper(TextIOBase):
    # wrap the logger file so that anything written to it will automatically get formatted

    def __init__(self, log_file: TextIOBase):
        self.file: TextIOBase = log_file
        self.line_buffer: Optional[str] = None
        self.line_start_time: Optional[datetime] = None

    def write(self, s: str) -> int:
        cur_time = datetime.now()
        if self.line_buffer and (cur_time - self.line_start_time).total_seconds() > 0.1:
            self.flush()

        if self.line_buffer:
            self.line_buffer += s
        else:
            self.line_buffer = s
            self.line_start_time = cur_time

        if '\n' not in s:
            return len(s)

        time_str = cur_time.strftime(time_format)
        lines = self.line_buffer.split('\n')
        for line in lines[:-1]:
            self.file.write(f'[{time_str}] PRINT {line}\n')
        self.file.flush()

        self.line_buffer = lines[-1]
        self.line_start_time = cur_time
        return len(s)

    def flush(self) -> None:
        if self.line_buffer:
            time_str = self.line_start_time.strftime(time_format)
            self.file.write(f'[{time_str}] PRINT {self.line_buffer}\n')
            self.file.flush()
            self.line_buffer = None

