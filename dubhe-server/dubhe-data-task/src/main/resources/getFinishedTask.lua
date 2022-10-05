local namespace = KEYS[1]
local finished_task_queue_name = "finished_task"
local finished_task_queue = redis.call("get", namespace..":"..finished_task_queue_name)
local time = redis.call('TIME')
local current_task_queue
local keys = redis.call("keys",namespace..":*:*:finished:*")

-- 定义分割字符串函数
local __split
function __split(str, reps)
    local r = {}
    if (str == nil) then
        return nil
    end
    string.gsub(str, "[^"..reps.."]+", function(w) table.insert(r,w) end)
    return r
end
-- 定义分割字符串函数

if (finished_task_queue == false and #keys > 0) or (finished_task_queue ~= nil and redis.call("exists", finished_task_queue) == 0 and #keys > 0) then
    redis.call("set", namespace..":"..finished_task_queue_name, keys[1])
    current_task_queue = keys[1]
else
    for i=1,#keys,1
    do
        if keys[i] == finished_task_queue then
            if i < #keys then
                redis.call("set", namespace..":"..finished_task_queue_name, keys[i+1])
                current_task_queue = keys[i+1]
            else
                redis.call("set", namespace..":"..finished_task_queue_name, keys[1])
                current_task_queue = keys[1]
            end
        end
    end
end

if current_task_queue == nil then
    return nil
else
    local element = redis.call('zrangebyscore', current_task_queue, 0, 9999999999999, 'limit', 0, 1)
    if table.getn(element)>0 then
        redis.call("zrem", current_task_queue, element[1])
    end
    return element
end