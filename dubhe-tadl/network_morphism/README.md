# Network Morphism
The implementation of the Network Morphism algorithm is based on
[Auto-Keras: An Efficient Neural Architecture Search System](https://arxiv.org/pdf/1806.10282.pdf)

Train stage
```
python network_morphism_train.py 
--trial_id 0 
--experiment_dir 'tadl' 
--log_path 'tadl/train/0/log' 
--data_dir '../data/' 
--result_path 'trial_id/result.json' 
--log_path 'trial_id/log' 
--search_space_path 'experiment_id/search_space.json'
--best_selected_space_path 'experiment_id/best_selected_space.json' 
--lr 0.001 --epochs 100 --batch_size 32 --opt 'SGD'
```

select stage
```
python network_morphism_select.py
```

retrain stage
```
python network_morphism_retrain.py 
--data_dir '../data/'  
--experiment_dir 'tadl' 
--result_path 'trial_id/result.json' 
--log_path 'trial_id/log' 
--best_selected_space_path 'experiment_id/best_selected_space.json' 
--best_checkpoint_dir 'experiment_id/' 
--trial_id 0 --batch_size 32 --opt 'SGD' --epochs 100 --lr 0.001 


```

The best model searched achieved 88.1% on CIFAR-10 dataset after 100 trials.

Dependencies:
```
Python = 3.6.13
pytorch = 1.8.0
torchvision = 0.9.0
scipy = 1.5.2
scikit-learn = 0.24.1
```