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

package org.dubhe.data.machine.constant;

public class ModelStateMachineConstant {

    private ModelStateMachineConstant(){
    }

    public static final String MODEL_STATE_MACHINE = "modelStateMachine";

    public static final String START_MODEL_SERVICE = "startModel";

    public static final String START_MODEL_SERVICE_FINISH = "startModelFinish";

    public static final String START_MODEL_SERVICE_FAIL = "startModelFail";

    public static final String STOP_MODEL_SERVICE = "stopModel";

    public static final String STOP_MODEL_SERVICE_FINISH = "stopModelFinish";
}
