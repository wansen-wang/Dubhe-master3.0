/** Copyright 2020 Tianshu AI Platform. All Rights Reserved. * * Licensed under the Apache License,
Version 2.0 (the "License"); * you may not use this file except in compliance with the License. *
You may obtain a copy of the License at * * http://www.apache.org/licenses/LICENSE-2.0 * * Unless
required by applicable law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. * See the License for the specific language governing permissions and * limitations under
the License. * ============================================================= */

<template>
  <div class="app-container greyBg">
    <div class="detail-header">
      <DetailDashboard
        :activePath="state.activePath"
        :detail="state.detail"
        :isFinished="isFinished"
        :inProgress="inProgress"
        :enablePause="enablePause"
        :enableStart="enableStart"
        :refreshTime="state.refreshTime"
        :updateState="updateState"
        :refresh="refresh"
        :command="command"
      />
      <Config
        :toggleSearchSpace="toggleSearchSpace"
        :toggleSelectedSpace="toggleSelectedSpace"
        :toggleExpConfig="toggleExpConfig"
      />
    </div>
    <el-drawer title="Search Space" :visible.sync="state.searchSpaceVisible">
      <TextEditor :txt="state.searchSpace" class="my-auto f1" style="max-height: unset;" />
    </el-drawer>
    <el-drawer title="Best Selected Space" :visible.sync="state.selectedSpaceVisible">
      <TextEditor :txt="state.selectedSpace" class="my-auto f1" style="max-height: unset;" />
    </el-drawer>
    <el-drawer title="Experiment Config" :visible.sync="state.expConfigVisible">
      <TextEditor :txt="state.expConfig" class="my-auto f1" style="max-height: unset;" />
    </el-drawer>
    <div class="stage-content">
      <el-tabs
        v-model="state.activeStage"
        class="stage-tabs el-tabs-large"
        type="card"
        @tab-click="changeTab"
      >
        <el-tab-pane label="TRAIN" name="TRAIN" />
        <el-tab-pane label="SELECT" name="SELECT" />
        <el-tab-pane label="RETRAIN" name="RETRAIN" />
      </el-tabs>
      <el-card v-if="state.activePath[0] === 'LOG'">
        <div class="mb-10">????????????</div>
        <LogContainer
          ref="logContainer"
          class="mt-20"
          :log-getter="getExpLog"
          :options="logOptions"
          :log-lines="50"
        />
      </el-card>
      <Stage
        v-else
        :activePath="state.activePath"
        :detail="state.detail"
        :experimentId="experimentId"
        :configMap="state.configMap"
        :info="stageInfo"
        :param="stageParam"
        :runParam="stageRunParam"
        :metric="stageMetric"
        :updateState="updateState"
        :refresh="refresh"
      />
    </div>
  </div>
</template>
<script>
import { reactive, computed, onMounted, watch, ref } from '@vue/composition-api';

import { useLocalStorage } from '@/hooks';
import {
  expDetailOverview,
  expStageInfo,
  expStageParam,
  expStageRuntimeParam,
  getSearchSpace,
  getSelectedSpace,
  getExpConfig,
  getExpLog,
  expYaml,
  expStageAccuracy,
  expStageIntermediate,
  expStageRuntime,
} from '@/api/tadl';
import TextEditor from '@/components/textEditor';
import LogContainer from '@/components/LogContainer';

import {
  expInprogress,
  expIsFinished,
  expEnablePause,
  getStageName,
  getStageOrder,
  expEnableStart,
  extractData,
  extractScatterData,
  extractSeriesData,
  STAGE_SEQUENCE,
} from '../util';
import DetailDashboard from './components/detailDashboard';
import Config from './components/config';
import Stage from './stage';

export default {
  name: 'DetailContainer',
  components: {
    DetailDashboard,
    Config,
    TextEditor,
    Stage,
    LogContainer,
  },
  setup(props, ctx) {
    const { $route } = ctx.root;
    const { params = {} } = $route;

    const [refreshTime, saveRefreshTime] = useLocalStorage('refreshTime', 10);
    const [cachedRefreshTime, saveCachedRefreshTime] = useLocalStorage('cachedRefreshTime', 10);

    const { experimentId } = params;
    const logContainer = ref(null);

    const state = reactive({
      activePath: ['TRAIN', 'general'],
      detail: {},
      loading: false,
      error: null,
      activeStage: 'TRAIN', // ??????????????????
      prevActiveStage: 'TRAIN',
      stageInfoMap: {},
      stageParamMap: {},
      stageRunParamMap: {},
      configMap: {}, // ??????????????????
      stageYamlMap: {}, // ????????? yaml ??????
      stageMetricMap: {}, // ??????Metric ??????
      refreshTime, // ????????????
      cachedRefreshTime, // ??????????????????????????????
      searchSpace: '',
      selectedSpace: '',
      expConfig: '',
      searchSpaceVisible: false,
      selectedSpaceVisible: false,
      expConfigVisible: false,
      algrithomLog: '',
      systemLog: '',
    });

    // ??????????????????
    const isFinished = computed(() => expIsFinished(state.detail.status));
    const inProgress = computed(() => expInprogress(state.detail.status));
    const enablePause = computed(() => expEnablePause(state.detail.status));
    const enableStart = computed(() => expEnableStart(state.detail.status));

    const activeStageName = computed(() => state.activePath[0]);

    // ????????????
    const stageInfo = computed(() => state.stageInfoMap[activeStageName.value]);
    // ??????????????????
    const stageParam = computed(() => state.stageParamMap[activeStageName.value]);
    // ??????????????????
    const stageRunParam = computed(() => state.stageRunParamMap[activeStageName.value]);
    // ????????? yaml ??????
    const stageYaml = computed(() => state.stageYamlMap[activeStageName.value]);
    // ??????????????????
    const stageMetric = computed(() => state.stageMetricMap[activeStageName.value]);

    const updateState = (params) => {
      return new Promise((resolve) => {
        // ????????????????????????????????????
        if (typeof params === 'function') {
          const next = params(state);
          Object.assign(state, next);
          resolve(state);
        }
        // ????????????
        Object.assign(state, params);
        resolve(state);
      });
    };

    // ??????????????????
    const queryExpDetail = async () => {
      const detail = await expDetailOverview(experimentId);
      updateState({
        detail,
        activeStage: getStageName(detail.runStage || 1),
        activePath: [getStageName(detail.runStage || 1), 'general'],
      });
      return detail;
    };

    // ????????????????????????
    const updateStateBy = (stageName, key, value) => {
      updateState((state) => {
        const next = {
          ...state[key],
          [stageName]: value,
        };
        return {
          ...state,
          [key]: next,
        };
      });
    };

    // ????????????????????????
    const queryExpStageInfo = async ({ stageOrder }) => {
      const stageInfo = await expStageInfo(experimentId, stageOrder);
      const stageName = getStageName(stageOrder);
      updateStateBy(stageName, 'stageInfoMap', stageInfo);
    };

    // ??????????????????????????????
    const queryExpStageParam = async ({ stageOrder }) => {
      const stageParam = await expStageParam(experimentId, stageOrder);
      const stageName = getStageName(stageOrder);
      updateStateBy(stageName, 'stageParamMap', stageParam);
    };

    // ??????????????????
    const queryExpStageRuntimeParam = async ({ stageOrder }) => {
      const stageRunParam = await expStageRuntimeParam(experimentId, stageOrder);
      const stageName = getStageName(stageOrder);
      updateStateBy(stageName, 'stageRunParamMap', stageRunParam);
    };

    const queryExpYaml = async ({ stageOrder }) => {
      const stageYaml = await expYaml(experimentId, stageOrder);
      const stageName = getStageName(stageOrder);
      updateStateBy(stageName, 'stageYamlMap', stageYaml);
    };

    const defaultConfig = {
      autoFit: true,
      seriesField: null,
      smooth: false, // ????????????
      meta: {
        value: {
          // max: 21, // ????????????????????????
        },
      },
      xAxis: {
        title: {
          text: 'x???',
          spacing: 30,
          style: {
            fontSize: 20,
          },
        },
      },
      yAxis: {
        title: {
          text: 'y???',
          style: {
            fontSize: 20,
          },
        },
      },
    };

    const scatterConfig = {
      regressionLine: {
        type: 'loess',
      },
    };

    // ???????????????????????????
    // ??????????????????????????????
    // ???????????????????????????
    const queryStageMetric = async ({ stageOrder }) => {
      const rawAccuracy = await expStageAccuracy(experimentId, stageOrder);
      const rawIntermediate = await expStageIntermediate(experimentId, stageOrder);
      const rawRuntime = await expStageRuntime(experimentId, stageOrder);
      const stageName = getStageName(stageOrder);
      updateStateBy(stageName, 'stageMetricMap', {
        accuracyData: extractData(rawAccuracy),
        accuracyConfig: {
          ...defaultConfig,
          ...rawAccuracy.config,
          xAxis: { title: { text: rawAccuracy.config.xFieldName }, tickInterval: 1 },
          yAxis: { title: { text: rawAccuracy.config.yFieldName } },
        },
        accuracyScatterData: extractScatterData(rawAccuracy),
        accuracyScatterConfig: {
          ...defaultConfig,
          ...scatterConfig,
          ...rawAccuracy.config,
          xAxis: { title: { text: rawAccuracy.config.xFieldName }, tickInterval: 1 },
          yAxis: { title: { text: rawAccuracy.config.yFieldName }, min: 0 },
        },
        intermediateData: extractSeriesData(rawIntermediate),
        intermediateConfig: {
          ...defaultConfig,
          ...rawIntermediate.config,
          xAxis: { title: { text: rawIntermediate.config.xFieldName }, tickInterval: 1 },
          yAxis: { title: { text: rawIntermediate.config.yFieldName } },
        },
        runtimeData: extractData(rawRuntime),
        runtimeConfig: {
          ...defaultConfig,
          ...rawRuntime.config,
          xAxis: { title: { text: 'trial' }, tickInterval: 1 },
          yAxis: { title: { text: '????????????/min' } },
        },
      });
    };

    const queryStageInfo = (params) => {
      Promise.all([
        queryExpStageInfo(params),
        queryExpStageParam(params),
        queryExpStageRuntimeParam(params),
        queryExpYaml(params),
        queryStageMetric(params),
      ]);
    };

    const refresh = async () => {
      const { runStage } = await queryExpDetail();
      queryStageInfo({ stageOrder: runStage || 1 });
    };

    // TODO
    // ????????????????????????
    // const queryExpConfig = async () => {
    //   const configMap = await getExpConfig(experimentId);
    //   updateState((state) => {
    //     return {
    //       ...state,
    //       configMap,
    //     };
    //   });
    // };

    const toggleSearchSpace = async () => {
      const result = await getSearchSpace(experimentId).then((res) => JSON.parse(res.fileStr));
      state.searchSpaceVisible = !state.searchSpaceVisible;
      state.searchSpace = result;
    };
    const toggleSelectedSpace = async () => {
      const result = await getSelectedSpace(experimentId).then((res) => JSON.parse(res.fileStr));
      state.selectedSpaceVisible = !state.selectedSpaceVisible;
      state.selectedSpace = result;
    };
    const toggleExpConfig = async () => {
      const result = await getExpConfig(experimentId).then((res) => JSON.parse(res.fileStr));
      state.expConfigVisible = !state.expConfigVisible;
      state.expConfig = result;
    };

    const logOptions = computed(() => {
      return {
        experimentId,
      };
    });

    const resetLogger = () => {
      setTimeout(() => {
        logContainer.value.reset(true);
      }, 0);
    };

    const setRefresher = (time) => {
      if (time > 0) {
        return setInterval(() => {
          refresh();
        }, time * 1000);
      }
      return false;
    };

    let refresher = setRefresher(props.refreshTime);
    const command = (cmd) => {
      // ???????????????????????????????????????
      if (state.refreshTime > 0) {
        saveCachedRefreshTime(state.refreshTime);
        updateState({ cachedRefreshTime: state.refreshTime });
      }
      saveRefreshTime(cmd);
      updateState({ refreshTime: cmd });
      clearInterval(refresher);
      refresher = setRefresher(cmd);
    };

    const changeTab = (tab) => {
      Object.assign(state, {
        prevActiveStage: tab.name,
      });
      // ????????????????????????tab??????????????????????????????????????????
      if (STAGE_SEQUENCE[tab.name] === state.detail.runStage) {
        command(state.cachedRefreshTime);
      } else {
        command(0);
      }
      updateState({ activePath: [tab.name, 'general'] });
    };

    // ??????????????????
    watch(
      () => state.activePath[0],
      (next) => {
        if (next === 'LOG') {
          resetLogger();
          return;
        }
        const stageOrder = getStageOrder(next);
        queryStageInfo({ stageOrder });
      }
    );

    onMounted(() => {
      refresh();
      // queryExpConfig();
    });

    return {
      state,
      isFinished,
      inProgress,
      enablePause,
      enableStart,
      updateState,
      stageInfo,
      stageParam,
      stageRunParam,
      stageYaml,
      stageMetric,
      refresh,
      saveRefreshTime,
      experimentId,
      toggleSearchSpace,
      toggleSelectedSpace,
      toggleExpConfig,
      getExpLog,
      logOptions,
      logContainer,
      command,
      changeTab,
    };
  },
};
</script>
<style lang="scss">
@import '@/assets/styles/variables.scss';

.stage-content {
  margin: 30px 0;

  .stage-tabs {
    .el-tabs__header {
      margin: 0;
      border: none;
    }

    .el-tabs__nav {
      border: none;
    }

    .el-tabs__item.is-active {
      color: $primaryColor;
      background-color: #fff;
    }

    .el-tabs__item {
      margin-right: 10px;
      margin-left: 2px;
      color: #fff;
      background-color: $primaryColor;
      border-top-left-radius: 6px;
      border-top-right-radius: 6px;
    }
  }

  .app-content-section {
    margin-bottom: 30px;
  }

  .stage-card {
    .el-tabs__header {
      margin: 0;
    }

    .el-tabs__nav-wrap {
      padding-left: 16px;
      margin-bottom: 10px;
      background-color: #fff;

      &::after {
        height: 0;
      }
    }
  }
}

.app-content-title {
  overflow: hidden;
  font-size: 18px;
  font-weight: 500;
  line-height: 28px;
  color: rgba(0, 0, 0, 0.85);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-descriptions-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;

  .app-descriptions-title {
    flex: auto;
    overflow: hidden;
    font-size: 16px;
    font-weight: 700;
    line-height: 1.5715;
    color: rgba(0, 0, 0, 0.85);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.app-container {
  padding: 30px 32px;

  .detail-header {
    box-shadow: 0 2px 7px 0 rgba(209, 209, 217, 0.5);
  }
}
</style>
