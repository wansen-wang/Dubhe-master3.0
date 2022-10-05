import numpy as np
from PIL import Image

class attention_map_read:
    def __init__(self, data=None, l=None, x=None, y=None, img=None, r=1):
        self.data = data
        self.l = l
        self.x = x
        self.y = y
        self.img = img
        self.ratio = r

    def get_data(self):
        if self.data is not None:
            # 获得请求层的数据
            attn_data = self.data[self.l]

            # 获得attention map 头的个数，及其宽高
            num_head = attn_data.shape[0]
            h, w = attn_data.shape[-2], attn_data.shape[-1]

            # 获得请求点所对应的 坐标
            h_scale, w_scale = int(256/h), int(256/w)
            x, y = self.x // w_scale, self.y // h_scale

            # 获得请求点所对应的attention map值
            res = attn_data[:, y, x, :, :]
            res_out = np.zeros(shape=(num_head, 64, 64, 3), dtype='uint8')

            # 归一化图像数据
            img_data = self.img
            img_data = img_data - np.min(img_data)
            if np.max(img_data) != 0:
                img_data = img_data / np.max(img_data) * 255

            for i in range(num_head):
                mask = np.uint8(res[i])
                mask = np.array(Image.fromarray(mask).resize((64, 64)))

                # 全局归一化时 乘以比例，并设置255阈值
                mask = mask * self.ratio
                mask[mask > 255] = 255
                mask = mask.astype('uint8')

                # 热力图上色
                heat_map = np.array(map_JET(mask), dtype='float16')
                heat_map[:, :, [0, 1, 2]] = heat_map[:, :, [2, 1, 0]]

                # 将热力图叠加到归一化后的图像
                heat_map = heat_map * 0.7 + img_data * 0.3

                res_out[i, :, :, :] = heat_map

            return res_out
        else:
            return None
def map_JET(img):
    img = np.around(img)
    n, m = img.shape
    out = np.zeros((n, m, 3), dtype=np.uint8)
    # out = np.expand_dims(img, 2).repeat(3, 2)

    indices = np.where((img >= 0) & (img <= 31))
    values = img[indices]
    out[indices[0], indices[1], [0] * len(indices[0])] = 128 + 4 * values

    indices = np.where(img == 32)
    out[indices[0], indices[1], [0] * len(indices[0])] = 255

    indices = np.where((img >= 33) & (img <= 95))
    values = img[indices]
    out[indices[0], indices[1], [1] * len(indices[0])] = 4 + 4 * (values - 33)
    out[indices[0], indices[1], [0] * len(indices[0])] = 255

    indices = np.where(img == 96)
    out[indices[0], indices[1], [2] * len(indices[0])] = 2
    out[indices[0], indices[1], [1] * len(indices[0])] = 255
    out[indices[0], indices[1], [0] * len(indices[0])] = 254

    indices = np.where((img >= 97) & (img <= 158))
    values = img[indices]
    out[indices[0], indices[1], [2] * len(indices[0])] = 6 + 4 * (values - 97)
    out[indices[0], indices[1], [1] * len(indices[0])] = 255
    out[indices[0], indices[1], [0] * len(indices[0])] = 250 - 4 * (values - 97)

    indices = np.where(img == 159)
    out[indices[0], indices[1], [2] * len(indices[0])] = 254
    out[indices[0], indices[1], [1] * len(indices[0])] = 255
    out[indices[0], indices[1], [0] * len(indices[0])] = 1

    indices = np.where((img >= 160) & (img <= 223))
    values = img[indices]
    out[indices[0], indices[1], [2] * len(indices[0])] = 255
    out[indices[0], indices[1], [1] * len(indices[0])] = 252 - 4 * (values - 160)

    indices = np.where((img >= 224) & (img <= 255))
    values = img[indices]
    out[indices[0], indices[1], [2] * len(indices[0])] = 252 - 4 * (values - 224)

    return out

