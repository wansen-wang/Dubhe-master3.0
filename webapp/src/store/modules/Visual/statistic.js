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

const state = {
  categoryInfo: '',
  initStateFlag: false,
  dataSets: [], // 所有数据集是相同的tag
  dataSetsState: [], // 数据集的显示状态，监听layout.js的userSelectRunFile，是一个数组，里面是数据集的下标
  histTags: [],
  oldHistData: [],
  histData: [], // 直接存储处理后的数据
  oldDistData: [],
  distData: [],
  binNum: 30,
  clickState: false, // 有没有被点击,没用
  histMode: '三维', // threed和orthographic切换
  showNumber: 100,
  histShow: true,
  distShow: false,
  // 控制面板信息栏显示
  histCheckedArray: [], // 用户定制选中
  distCheckedArray: [],
  // 不同run不同颜色
  statisticColor: [
    '#9FA5FA',
    '#6dd2f0',
    '#c06f98',
    '#f07c82',
    '#57c3c2',
    '#9359b1',
    '#8cc269',
    '#ffa60f',
    '#de5991',
    '#EA7E53',
    '#cc95c0',
  ], // #6464FF
  // 控制面板信息
  statisticInfo: [],
  errorMessage: '',
  freshFlag: true, // 上一步刷新的请求全部结束后再进行这一步的请求
  downLoadArray: [], // 下载svg图暂存id
  // 在加载数据和渲染时不允许用户操作控制面板
  featchHistDataFinished: true,
  featchDistDataFinished: true,
  updateFlag: false,
};

const getters = {
  getInitStateFlag: (state) => state.initStateFlag,
  getShowNumber: (state) => state.showNumber,
  getMode: (state) => state.histMode,
  getDistData: (state) => state.distData,
  getHistData: (state) => state.histData,
  getBinNum: (state) => state.binNum,
  getShowStatisticFlag: (state) => state.showStatisticFlag,
  getDataSets: (state) => state.dataSets,
  getCategoryInfo: (state) => state.categoryInfo,
  getClickState: (state) => state.clickState,
  getDataSetsState: (state) => state.dataSetsState,
  getStatisticColor: (state) => state.statisticColor,
  getHistCheckedArray: (state) => state.histCheckedArray,
  getDistCheckedArray: (state) => state.distCheckedArray,
  getStatisticInfo: (state) => state.statisticInfo,
  getErrorMessage: (state) => state.errorMessage,
  getHistShow: (state) => state.histShow,
  getDistShow: (state) => state.distShow,
  getDownLoadArray: (state) => state.downLoadArray,
  getFeatchHistDataFinished: (state) => state.featchHistDataFinished,
  getFeatchDistDataFinished: (state) => state.featchDistDataFinished,
  getUpdateFlag: (state) => state.updateFlag,
};

const actions = {
  async getSelfCategoryInfo(context, param) {
    if (context.state.freshFlag) {
      context.commit('setFreshFlag', false);
      context.commit('setSelfCategoryInfo', param);
      // 如果是第一个，直接获取数据
      if (param[2].initStateFlag) {
        context.dispatch('featchAllHistData');
      }
    }
  },
  async getIntervalSelfCategoryInfo(context, param) {
    // 上一次还没有请求结束，这一次就不响应了
    if (context.state.featchDistDataFinished && context.state.featchHistDataFinished) {
      context.commit('setIntervalSelfCategoryInfo', param);
      context.commit('setUpdateFlag');
    }
  },
  async featchAllDistData(context) {
    context.commit('setFeatchDistDataFinished', false);
    for (let k = 0, count = 0; k < context.state.dataSets.length; k++) {
      for (let i = 0; i < context.state.histTags[k].length; i++, count++) {
        await http
          .useGet(port.category.distribution, {
            run: context.state.dataSets[k],
            tag: context.state.histTags[k][i],
          })
          .then((res) => {
            if (Number(res.data.code) !== 200) {
              context.commit(
                'setErrorMessage',
                `${context.state.dataSets[k]},${context.state.histTags[k][i]},${res.data.msg}`
              );
              return;
            }
            context.commit('storeDistData', [
              context.state.dataSets[k],
              context.state.histTags[k][i],
              res.data.data[context.state.histTags[k][i]],
              k,
              count,
            ]);
            context.commit('manageDistData', count);
          });
      }
    }
    context.commit('setFeatchDistDataFinished', true);
  },
  async featchAllHistData(context) {
    context.commit('setFeatchHistDataFinished', false);
    for (let k = 0, count = 0; k < context.state.dataSets.length; k++) {
      for (let i = 0; i < context.state.histTags[k].length; i++, count++) {
        await http
          .useGet(port.category.histogram, {
            run: context.state.dataSets[k],
            tag: context.state.histTags[k][i],
          })
          .then((res) => {
            if (Number(res.data.code) !== 200) {
              context.commit(
                'setErrorMessage',
                `${context.state.dataSets[k]},${context.state.histTags[k][i]},${res.data.msg}`
              );
              return;
            }
            // 根据数据step个数确定显示比例
            const dataLen = res.data.data[context.state.histTags[k][i]].length;
            if (dataLen > 50 && 5000.0 / dataLen < context.state.showNumber) {
              context.commit('changeShownumber', Math.round(5000.0 / dataLen));
            }
            // 根据上面也可以确定桶个数的最大值
            context.commit('storeHistData', [
              context.state.dataSets[k],
              context.state.histTags[k][i],
              res.data.data[context.state.histTags[k][i]],
              k,
              count,
            ]);
            context.commit('manageHistData', { index: count, length: 1 });
          });
      }
    }
    context.commit('setFeatchHistDataFinished', true);
    context.commit('setFreshFlag', true);
  },
};

const mutations = {
  setSelfCategoryInfo: (state, param) => {
    state.categoryInfo = ['histogram', 'distribution'];
    state.dataSets = param[0];
    for (let i = 0; i < state.dataSets.length; i++) {
      state.histTags.push(param[1][i].histogram);
    }
    state.dataSetsState = [];
    for (let i = 0; i < state.dataSets.length; i++) {
      state.dataSetsState[state.dataSets[i]] = true;
    }
    state.initStateFlag = param[2].initStateFlag;
  },
  setIntervalSelfCategoryInfo: (state, param) => {
    state.categoryInfo = ['histogram', 'distribution'];
    state.dataSets = param[0];
    state.histTags = [];
    for (let i = 0; i < state.dataSets.length; i++) {
      state.histTags.push(param[1][i].histogram);
    }
    state.initStateFlag = false;
    // 然后判断run和tag的长度有没有变化，去修改distData、histData数据，让数据和runtag保持一致
    let len = 0;
    for (let i = 0; i < state.dataSets.length; i++) {
      len += param[1][i].histogram.length;
    }
    if (len < state.histData.length) {
      state.oldHistData = state.oldHistData.slice(0, len);
      state.oldDistData = state.oldDistData.slice(0, len);
      state.histData = state.histData.slice(0, len);
      state.distData = state.distData.slice(0, len);
    }
  },
  setInitStateFlag: (state, param) => {
    state.initStateFlag = param;
  },
  setClickState: (state, param) => {
    state.clickState = param;
  },
  changeMode: (state, curMode) => {
    state.histMode = curMode;
    state.isSwitchFlag = true;
    // 在模型切换时需要替换掉downloadArray中的id
    const newDownLoadArray = [];
    if (state.histMode === '三维') {
      for (let i = 0; i < state.downLoadArray.length; i++) {
        newDownLoadArray.push(state.downLoadArray[i].replace(/overlay/, 'offset'));
      }
      state.downLoadArray = newDownLoadArray;
    } else {
      for (let i = 0; i < state.downLoadArray.length; i++) {
        newDownLoadArray.push(state.downLoadArray[i].replace(/offset/, 'overlay'));
      }
      state.downLoadArray = newDownLoadArray;
    }
  },
  changeShownumber: (state, curNumber) => {
    state.showNumber = curNumber;
  },
  storeDistData: (state, data) => {
    const k = data.pop();
    if (state.oldDistData.length > k) {
      state.oldDistData.splice(k, 1, data);
    } else {
      state.oldDistData.push(data);
    }
  },
  manageDistData: (state, param) => {
    // 还要考虑是否是更新的数据
    const k = param; // 每次只处理最新获取到的数据
    const oneData = state.oldDistData[k][2];
    const newData = [];
    for (let i = 0; i <= 8; i++) {
      // 每个图9条线
      const linedata = [];
      for (let j = 0; j < oneData.length; j++) {
        const temp = [];
        temp.push(oneData[j][1]); // step,x
        temp.push(oneData[j][2][i][1]); // value,y
        linedata.push(temp);
      }
      newData.push(linedata);
    }
    // 为了画area,需要再处理一下，除了第一步，其余都要加上上一条的y轴数据
    for (let i = 1; i < newData.length; i++) {
      for (let j = 0; j < newData[i - 1].length; j++) {
        newData[i][j].push(newData[i - 1][j][1]);
      }
    }
    const newTempData = [
      state.oldDistData[k][0],
      state.oldDistData[k][1],
      newData,
      state.oldDistData[k][3],
      k,
    ];
    if (param >= state.distData) {
      state.distData.push(newTempData);
      state.distCheckedArray.push(false);
    } else {
      state.distData.splice(k, 1, newTempData);
    }
  },
  clearHistData: (state) => {
    state.oldHistData = [];
    state.histData = [];
    state.histCheckedArray = [];
  },
  clearDistData: (state) => {
    state.oldDistData = [];
    state.distData = [];
    state.distCheckedArray = [];
  },
  storeHistData: (state, data) => {
    const index = data.pop();
    if (state.oldHistData.length > index) {
      state.oldHistData.splice(index, 1, data);
    } else {
      state.oldHistData.push(data);
    }
  },
  manageHistData: (state, param) => {
    // 不仅在取出初始数据时调用，在修改桶数时也调用这个函数
    // 加个param，是数组开始下标和长度
    const histDataTemp = [];
    for (let k = param.index, len = param.index + param.length; k < len; k++) {
      const data = state.oldHistData[k][2];
      const newdata = [];
      let min = 1000;
      let max = -1000;
      for (let i = 0; i < data.length; i++) {
        if (min > data[i][2]) min = data[i][2];
        if (max < data[i][3]) max = data[i][3];
      }
      const binWidth = (max - min) / state.binNum;
      for (let i = 0; i < data.length; i++) {
        // 遍历step
        const onedata = data[i][4];
        // 处理一下首尾
        onedata[0][0] = onedata[0][1] - binWidth / 2;
        onedata[onedata.length - 1][1] = onedata[onedata.length - 1][0] + binWidth / 2;
        const newOneData = [];
        let binleft = min;
        let binright = binleft;
        let curbucket = 0;
        // 让首尾为0
        newOneData.push([min - binWidth / 2, 0, data[i][1]]);
        for (let j = 0; j < state.binNum; j++) {
          binleft = binright;
          binright = binleft + binWidth;
          let count = 0;
          for (; curbucket < onedata.length - 1; curbucket++) {
            if (binright < onedata[curbucket][0]) break;
            const maxleft = Math.max(binleft, onedata[curbucket][0]);
            const curBinWidth = onedata[curbucket][1] - onedata[curbucket][0];
            if (binright <= onedata[curbucket][1]) {
              if (curBinWidth !== 0)
                count += ((binright - maxleft) / curBinWidth) * onedata[curbucket][2];
              break;
            } else if (curBinWidth !== 0)
              count += ((onedata[curbucket][1] - maxleft) / curBinWidth) * onedata[curbucket][2];
          }
          newOneData.push([(binleft + binright) / 2, count, data[i][1]]);
        }
        newOneData.push([max + binWidth / 2, 0, data[i][1]]);
        newdata.push(newOneData);
      }
      histDataTemp.push([
        state.oldHistData[k][0],
        state.oldHistData[k][1],
        newdata,
        state.oldHistData[k][3],
        k,
      ]);
    }
    if (param.index >= state.histData.length) {
      state.histCheckedArray.push(false);
      state.histData.push(histDataTemp[0]);
    } else {
      state.histData.splice(param.index, param.length, ...histDataTemp);
    }
  },
  setBinNum: (state, binNum) => {
    state.binNum = binNum;
  },
  setDataSetsState: (state, param) => {
    state.dataSetsState = param;
  },
  setHistShow: (state, param) => {
    state.histShow = param;
  },
  setDistShow: (state, param) => {
    state.distShow = param;
  },
  setStatisticInfo: (state, param) => {
    state.statisticInfo = param;
  },
  setHistCheckedArray(state, param) {
    state.histCheckedArray[param.idx] = param.value;
  },
  setDistCheckedArray(state, param) {
    state.distCheckedArray[param.idx] = param.value;
  },
  setErrorMessage(state, param) {
    state.errorMessage = param;
  },
  setFreshFlag(state, param) {
    state.freshFlag = param;
  },
  setDownLoadArray(state, param) {
    if (param[0]) {
      state.downLoadArray.push(param[1]);
    } else {
      const i = state.downLoadArray.indexOf(param[1]);
      if (i !== -1) {
        state.downLoadArray.splice(i, 1);
      }
    }
  },
  setFeatchHistDataFinished(state, param) {
    state.featchHistDataFinished = param;
  },
  setFeatchDistDataFinished(state, param) {
    state.featchDistDataFinished = param;
  },
  setUpdateFlag(state) {
    state.updateFlag = !state.updateFlag;
  },
};

export default {
  namespaced: true,
  state,
  getters,
  actions,
  mutations,
};
