/**
 * Copyright 2020 Tianshu AI Platform. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =============================================================
 */
package org.dubhe.train.constant;

/**
 * @description 炼知默认重组训练模型数据
 * @date 2022-06-23
 */
public interface ATlasTrainConstant {

	String DATASET_1 = "StanfordCars";
	String DATASET_2 = "FGVCAircraft";
	String TASK_DATASET = "NYUv2";


	String LAYERWISE_MODEL_STRUCT = "ResNet34";

	String CFL_MODEL_STRUCT_1 = "ResNet34";
	String CFL_MODEL_STRUCT_2 = "ResNet50";

	String TASK_MODEL_STRUCT_1 = "seg-segnet-vgg16-bn";
	String TASK_MODEL_STRUCT_2 = "depth-segnet-vgg16-bn";

}
