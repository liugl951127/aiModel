package com.aiplatform.agent.memory;

import com.aiplatform.agent.entity.Message;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class MemoryCodec {

    private MemoryCodec() {
    }

    static String encode(List<Message> list) {
        return JSON.toJSONString(list);
    }

    static List<Message> decode(String json) {
        JSONArray arr = JSON.parseArray(json);
        List<Message> result = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Message m = new Message();
            m.setRole(o.getString("role"));
            m.setContent(o.getString("content"));
            m.setToolName(o.getString("toolName"));
            m.setStep(o.getInteger("step"));
            result.add(m);
        }
        return result;
    }
}
