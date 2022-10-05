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

package org.dubhe.data.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import io.minio.messages.Bucket;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.dubhe.biz.base.constant.MagicNumConstant;
import org.dubhe.biz.file.utils.MinioUtil;
import org.dubhe.biz.log.enums.LogEnum;
import org.dubhe.biz.log.utils.LogUtil;
import org.dubhe.data.domain.dto.FileCreateDTO;
import org.dubhe.data.domain.entity.Label;
import org.dubhe.data.service.impl.FileServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @description oneflow文本格式转换
 * @date 2020-07-16
 */
@Component
public class ConversionUtil {

    private static final int ARRAY_LENGTH = MagicNumConstant.FOUR;

    private static final String TXT_FILE_FORMATS = ".txt";

    private static final String JPEG_FILE_FORMATS = "JPEG";

    @Value("${minio.bucketName}")
    private String bucket;

    @Autowired
    private FileServiceImpl fileService;

    @Autowired
    private MinioUtil minioUtil;

    /**
     * 将annotation信息转换为txt
     *
     * @param path 图片文件路径
     * @param datasetId 数据集ID
     */
    public void txtConversion(String path, Long datasetId) {
        List<String> imagePaths = new ArrayList<>();
        try {
            imagePaths = minioUtil.getObjects(bucket, path);
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "getObjects is failed:{}", e);
            return;
        }
        Map<String,Integer> labelMap = new HashMap<>();
        try {
            String labelIdsPath = path.replace("/origin","/annotation/");
            String labelIdsString = minioUtil.readString(bucket, labelIdsPath + "labelsIds.text");
            Map<Integer,String> idLabelMap = JSONObject.parseObject(labelIdsString, Map.class);
            labelMap = idLabelMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "ReadJson is failed:{}", e);
        }
        for (int n = 0; n < imagePaths.size(); n++) {
            String imagePath = imagePaths.get(n);
            if (imagePath.endsWith(TXT_FILE_FORMATS)) {
                continue;
            }
            String imageName = StringUtils.substringAfterLast(imagePath, "/");
            String annName = StringUtils.substringBeforeLast(imageName, ".");
            FileCreateDTO fileCreateDTO = fileService.getBaseMapper().selectWidthAndHeight(annName, datasetId);
            int width = fileCreateDTO.getWidth();
            int height = fileCreateDTO.getHeight();
            String annPath = StringUtils.substringBeforeLast(path, "/") + File.separator + "annotation" + File.separator;
            JSONArray objects = null;
            try {
                objects = JSONObject.parseArray((minioUtil.readString(bucket, annPath + annName)));
            } catch (Exception e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "ReadJson is failed:{}", e);
                continue;
            }
            StringBuffer content = new StringBuffer();
            for (Object object : objects) {
                JSONObject jsonObject = (JSONObject) object;
                String categoryName = jsonObject.getString("category_id");
                Integer categoryId = labelMap.get(categoryName);
                JSONArray jsonArray = (JSONArray) jsonObject.get("bbox");
                BigDecimal[] bbox = new BigDecimal[ARRAY_LENGTH];
                for (int j = 0; j < ARRAY_LENGTH; j++) {
                    bbox[j] = new BigDecimal(jsonArray.get(j).toString());
                }
                double[] newBbox = bboxCocoYolo(bbox[0].doubleValue(), bbox[1].doubleValue(), bbox[2].doubleValue(), bbox[3].doubleValue(), width, height);
                if (categoryId == null) {
                    continue;
                }
                String caId = String.valueOf(categoryId - 1);
                String newX = String.valueOf(newBbox[0]);
                String newY = String.valueOf(newBbox[1]);
                String newW = String.valueOf(newBbox[2]);
                String newH = String.valueOf(newBbox[3]);
                content.append(caId).append(" ").append(newX).append(" ").append(newY).append(" ").append(newW).append(" ")
                        .append(newH).append("\n");
            }
            try {
                minioUtil.writeString(bucket, path + File.separator + annName + TXT_FILE_FORMATS, content.toString());
                LogUtil.info(LogEnum.BIZ_DATASET, "write to file:" + content.toString());
            } catch (Exception e) {
                LogUtil.error(LogEnum.BIZ_DATASET, "write to file failed:{}", e);
            }
        }
    }

    /**
     * 格式转换
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param w      宽度
     * @param h      高度
     * @param width  图片宽
     * @param height 图片高
     * @return double[]
     */
    private static double[] bboxCocoYolo(double x, double y, double w, double h, int width, int height) {
        double[] newBbox = new double[ARRAY_LENGTH];
        newBbox[0] = (x + 0.5 * w) / width;
        newBbox[1] = (y + 0.5 * h) / height;
        newBbox[2] = w / width;
        newBbox[3] = h / height;
        for (int i = 0; i < ARRAY_LENGTH; i++) {
            BigDecimal bd = new BigDecimal(newBbox[i]);
            newBbox[i] = bd.setScale(MagicNumConstant.SIX, BigDecimal.ROUND_HALF_UP).doubleValue();
        }
        return newBbox;
    }

    public static JSONObject buildCOCOCommon(){
        JSONObject cocoObject =new JSONObject();
        cocoObject.put("info",buildCOCOInfo());
        cocoObject.put("licenses",buildCOCOLicenses());
        return cocoObject;
    }

    private static JSONObject buildCOCOInfo(){
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
        JSONObject info = new JSONObject();
        info.put("description","Exported from Tianshu");
        info.put("url","");
        info.put("version","");
        info.put("contributor","");
        info.put("year",calendar.get(Calendar.YEAR));
        info.put("date_created",format.format(calendar.getTime()));
        return info;
    }

    private static JSONArray buildCOCOLicenses(){
        JSONObject license = new JSONObject();
        license.put("id",0);
        license.put("name","placeholder license");
        license.put("url","");
        JSONArray licenseArray = new JSONArray();
        licenseArray.add(license);
        return licenseArray;
    }

    public  void writeYOLOCommon(String targetDir,List<Label> labels,List<Long> labelIds){
       // obj.data
        StringBuilder objData= new StringBuilder();
        objData.append("classes=").append(labels.size()).append("\n");
        objData.append("train = data/train.txt").append("\n");
        objData.append("names = data/obj.names").append("\n");
        objData.append("backup = backup").append("\n");

        //标签
        List<String> labelNames= Lists.newArrayList();
        for(Label label:labels){
            labelNames.add(label.getName());
            labelIds.add(label.getId());
        }

        try {
            minioUtil.writeString(bucket, targetDir + "obj.names", Strings.join(labelNames, '\n'));
            minioUtil.writeString(bucket, targetDir + "obj.data", objData.toString());
        } catch (Exception e) {
            LogUtil.error(LogEnum.BIZ_DATASET, "MinIO file write exception, {}", e);
        }
    }

    public static String buildYoloAnnotation(Integer categoryIndex, JSONArray bboxArray,int width,int height){
        BigDecimal[] bbox = new BigDecimal[ARRAY_LENGTH];
        for (int j = 0; j < ARRAY_LENGTH; j++) {
            bbox[j] = new BigDecimal(bboxArray.get(j).toString());
        }
        double[] newBbox = bboxCocoYolo(bbox[0].doubleValue(), bbox[1].doubleValue(),
                bbox[2].doubleValue(), bbox[3].doubleValue(), width, height);

        return categoryIndex+" "+newBbox[0]+" "+newBbox[1]+" "+newBbox[2]+" "+newBbox[3]+"\n";
    }

}
