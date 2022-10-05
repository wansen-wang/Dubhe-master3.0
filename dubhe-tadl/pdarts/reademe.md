# train stage
`python pdarts_train.py --data_dir '../data/' --result_path 'trial_id/result.json' --log_path 'trial_id/log' --search_space_path 'experiment_id/search_space.json' --best_selected_space_path 'experiment_id/best_selected_space.json' --trial_id 0 --model_lr 0.025 --arch_lr 3e-4 --epochs 2 --pre_epochs 1 --batch_size 64 --channels 16 --init_layers 5 --add_layer 0 6 12 --dropped_ops 3 2 1 --dropout_rates 0.1 0.4 0.7`

# select stage
`python pdarts_select.py --best_selected_space_path 'experiment_id/best_selected_space.json'`

# retrain stage
`python pdarts_retrain.py --data_dir '../data/' --result_path 'trial_id/result.json' --log_path 'trial_id/log' --best_selected_space_path 'experiment_id/best_selected_space.json' --best_checkpoint_dir 'experiment_id/' --trial_id 0 --batch_size 96 --epochs 2 --lr 0.025 --layers 20 --channels 36`

# output file
`result.json` 
```
{'type': 'Accuracy', 'result': {'sequence': 0, 'category': 'epoch', 'value': 0.1}}
{'type': 'Accuracy', 'result': {'sequence': 1, 'category': 'epoch', 'value': 0.0}}
{'type': 'Accuracy', 'result': {'sequence': 2, 'category': 'epoch', 'value': 0.0}}
{'type': 'Accuracy', 'result': {'sequence': 3, 'category': 'epoch', 'value': 0.0}}
{'type': 'Accuracy', 'result': {'sequence': 4, 'category': 'epoch', 'value': 0.0}}
{'type': 'Cost_time', 'result': {'value': '41.614346981048584 s'}}
```

`search_space.json`
```
{
  "op_list": {
    "_type": "layer_choice",
    "_value": [
      "maxpool",
      "avgpool",
      "skipconnect",
      "sepconv3x3",
      "sepconv5x5",
      "dilconv3x3",
      "dilconv5x5",
      "none"
    ]
  },
  "search_space": {
    "normal_n2_p0": "op_list",
    "normal_n2_p1": "op_list",
    "normal_n2_switch": {
      "_type": "input_choice",
      "_value": {
        "candidates": [
          "normal_n2_p0",
          "normal_n2_p1"
        ],
        "n_chosen": 2
      }
    },

    ...
  }
```

`best_selected_space.json`
```
{
  {
  "op_list": {
    "_type": "layer_choice",
    "_value": [
      "maxpool",
      "avgpool",
      "skipconnect",
      "sepconv3x3",
      "sepconv5x5",
      "dilconv3x3",
      "dilconv5x5"
    ]
  },
  "best_selected_space": {
    "normal_n2_p0": [
      false,
      false,
      false,
      false,
      true,
      false,
      false
    ],
    "normal_n2_p1": [
      true,
      false,
      false,
      false,
      false,
      false,
      false
    ],
  ...
}
```