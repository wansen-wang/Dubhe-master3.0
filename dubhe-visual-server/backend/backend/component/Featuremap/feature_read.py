class featuremap_read:
    def __init__(self, data=None, ranges=None, tag=None, sorce_data=None, label_data=None):
        self.data = data
        self.ranges = ranges
        self.tag = tag
        self.sorce = sorce_data
        self.label = label_data

    def get_data(self):
        if self.data:
            result = []
            _data = self.data
            img_len = len(_data[0]['value'])
            over_len = img_len - (self.ranges+16)
            if over_len >= 0:
                values = {'wall_time': _data[0]['wall_time'],
                          'step': _data[0]['step'],
                          'Remaining_pictures': over_len,
                          'value':  _data[0]['value'][self.ranges:(self.ranges+16), :, :],
                          'sorce_data': self.sorce[0]['value'].tolist()
                          }

            else:
                values = {'wall_time': _data[0]['wall_time'],
                          'step': _data[0]['step'],
                          'Remaining_pictures': 0,
                          'value': _data[0]['value'][self.ranges:, :, :],
                          'sorce_data': self.sorce[0]['value'].tolist()
                          }
            if self.label:
                values["label"] = self.label[0]['value'].tolist()
            result.append(values)
            return result
        else:
            return None
