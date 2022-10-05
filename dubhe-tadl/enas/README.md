# Efficient Neural Architecture Search (ENAS)

## 1. Requirements 
```
torch
torchvision
collections
argparser
pickle
pytest-shutil
```

## 2.Train
### Stage1: search an architecture

* macro search 

```
python trainer.py --trial_id=0 --search_for macro  --best_selected_space_path='./macro_selected_space.json' --result_path='./macro_result.json'
```

* micro search

```
python trainer.py --trial_id=0 --search_for micro  --best_selected_space_path='./micro_selected_space.json' --result_path='./micro_result.json'
```

### Stage2: select (deprecated)
```
python selector.py
```

### stage3: retrain
* macro search

```
python retrainer.py --search_for macro  --best_checkpoint_dir='./macro_checkpoint.pth' --best_selected_space_path=
'./macro_selected_space.json'  --result_path='./macro_result.json'
```

* micro search

```
python retrainer.py --search_for micro  --best_checkpoint_dir='./micro_checkpoint.pth' --best_selected_space_path=
'./micro_selected_space.json'  --result_path='./micro_result.json'
```