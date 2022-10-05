# train stage
`python pcdarts_train.py --data_dir '../data/' --result_path 'trial_id/result.json' --log_path 'trial_id/log' --search_space_path 'experiment_id/search_space.json' --best_selected_space_path 'experiment_id/best_selected_space.json' --trial_id 0 --layers 5 --model_lr 0.025 --arch_lr 3e-4 --epochs 2 --pre_epochs 1 --batch_size 64 --channels 16`

# select stage
`python pcdarts_select.py --best_selected_space_path 'experiment_id/best_selected_space.json' `

# retrain stage
`python pcdarts_retrain.py --data_dir '../data/' --result_path 'trial_id/result.json' --log_path 'trial_id/log' --best_selected_space_path 'experiment_id/best_selected_space.json' --best_checkpoint_dir 'experiment_id/' --trial_id 0 --batch_size 96 --epochs 2 --lr 0.01 --layers 20 --channels 36`

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
  "normal_n2_p0": "dilconv5x5",
  "normal_n2_p1": "dilconv5x5",
  "normal_n2_switch": [
    "normal_n2_p0",
    "normal_n2_p1"
  ],
  "normal_n3_p0": "sepconv3x3",
  "normal_n3_p1": "dilconv5x5",
  "normal_n3_p2": [],
  "normal_n3_switch": [
    "normal_n3_p0",
    "normal_n3_p1"
  ],
  "normal_n4_p0": [],
  "normal_n4_p1": "dilconv5x5",
  "normal_n4_p2": "sepconv5x5",
  "normal_n4_p3": [],
  "normal_n4_switch": [
    "normal_n4_p1",
    "normal_n4_p2"
  ],
  ...
}
```