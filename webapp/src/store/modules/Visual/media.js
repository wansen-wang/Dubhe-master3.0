/** Copyright 2020 Tianshu AI Platform. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =============================================================
 */

import http from '@/utils/VisualUtils/request';
import port from '@/utils/VisualUtils/api';

/* eslint-disable */

const state = {
  categoryInfo: "", // 存放自己的类目信息
  detailData: "", // 具体数据
  clickState: false,
  showrun: {},
  totaltag: "",
  freshInfo: {},
  errorMessage: "",
  showFlag: {
    firstTime: true
  },
  IntervalChange: false
};

const getters = {
  categoryInfo: state => state.categoryInfo,
  detailData: state => state.detailData,
  showrun: state => state.showrun,
  getTotaltag: state => state.totaltag,
  getFreshInfo: state => state.freshInfo,
  getErrorMessage: state => state.errorMessage,
  getShowFlag: state => state.showFlag,
  getIntervalChange: state => state.IntervalChange
};

const actions = {
  async getSelfCategoryInfo(context, param) {
    let initDetailData = {};
    // 根据自己的类目增加相应的判断
    for (let i = 0; i < param[1].length; i++) {
      Object.keys(param[1][i]).forEach(value => {
        initDetailData[value] = [];
      });
    }
    context.commit("setSelfCategoryInfo", param);
    context.commit("setInitDetailDataInfo", initDetailData);
  },
  async getIntervalSelfCategoryInfo(context, param) {
    let initDetailData = {};
    // 根据自己的类目增加相应的判断
    for (let i = 0; i < param[1].length; i++) {
      Object.keys(param[1][i]).forEach(value => {
        initDetailData[value] = [];
      });
    }
    context.commit("setSelfCategoryInfo", param);
    context.commit("setIntervalDetailDataInfo", initDetailData);
  },
  async getData(context, param) {
    // 类目  tags
    // if(context.state.detailData[param[0]].length === 0) {
    for (let j = 0; j < param[1].length; j++) {
      for (let i = 0; i < context.state.categoryInfo[0].length; i++) {
        if (
          Object.keys(context.state.categoryInfo[1][i]).indexOf(param[0]) > -1
        ) {
          if (
            context.state.categoryInfo[1][i][param[0]].indexOf(param[1][j]) > -1
          ) {
            let parameter = {
              run: context.state.categoryInfo[0][i],
              tag: param[1][j]
            };
            await http.useGet(port.category[param[0]], parameter).then(res => {
              // port.category.scalar 'scalar' 换成你需要的接口
              if (+res.data.code !== 200) {
                context.commit(
                  "setErrorMessage",
                  res.data.msg + "_" + new Date().getTime()
                );
                return;
              }
              context.commit("setDetailData", [
                param[0],
                {
                  run: context.state.categoryInfo[0][i],
                  value: res.data.data,
                  port: port.category[param[0]],
                  parameter: parameter
                }
              ]);
            });
          }
        }
      }
    }
    // }
  }
};

const mutations = {
  setShowFlag: (state, param) => {
    state.showFlag[param[0]] = param[1];
  },
  setSelfCategoryInfo: (state, param) => {
    state.categoryInfo = param;
  },
  setInitDetailDataInfo: (state, param) => {
    state.detailData = param;
  },
  setIntervalDetailDataInfo: (state, param) => {
    if (state.detailData == "") {
      state.detailData = param;
    } else {
      Object.keys(param).forEach(value => {
        if (Object.keys(state.detailData).indexOf(value) == -1) {
          state.detailData[value] = [];
        }
      });
    }
    // state.IntervalChange = !state.IntervalChange 母鸡为什么这个不更新也会去请求新数据
  },
  setDetailData: (state, param) => {
    // state.detailData[param[0]].push(param[1])

    // param分为两部分
    // param[0]为string，存储大tag标签，例如loss，mean，conv1等
    // param[1]为对象，param[1]['run']存储训练模型名称，param[1]['value']存储对应param[0]的标量数据类型
    let keys = []; // keys存储标量数据类型及模型名称，如loss-log

    for (let k in param[1]["value"]) {
      keys.push(k + "-" + param[1]["run"]);
    }

    let keys2 = []; // keys2存储第一次加载数据的存储标量数据类型及模型名称，用于和keys比较，判断替换下一次请求的数据
    let index = []; // 一个tag下多个模型编号
    for (let key in state.detailData[param[0]]) {
      for (let kk in state.detailData[param[0]][key]["value"]) {
        keys2.push(kk + "-" + state.detailData[param[0]][key]["run"]);
        index.push(key);
      }
    }
    for (let i = 0; i < keys.length; i++) {
      if (keys2.indexOf(keys[i]) != -1) {
        state.detailData[param[0]][index[keys2.indexOf(keys[i])]]["value"] =
          param[1]["value"];
      } else {
        state.detailData[param[0]].push(param[1]);
      }
    }
  },
  setClickState: (state, param) => {
    state.clickState = param;
  },
  setshowrun: (state, param) => {
    for (let i = 0; i < state.categoryInfo[0].length; i++) {
      if (param.indexOf(state.categoryInfo[0][i]) > -1) {
        state.showrun[state.categoryInfo[0][i]] = true;
      } else {
        state.showrun[state.categoryInfo[0][i]] = false;
      }
    }
  },
  setTotaltag: (state, param) => {
    state.totaltag = param;
  },
  setFreshInfo: (state, param) => {
    state.freshInfo[param[0]] = param[1]; // false表示类目被打开
  },
  setErrorMessage: (state, param) => {
    state.errorMessage = param;
  }
};

export default {
  namespaced: true,
  state,
  getters,
  actions,
  mutations
};
