import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 对象转换对象
 */
public class Main_Check {

    public static void main(String[] args) {

        String orgJson = "{\"Tid\":1709192119,\"Values\":[{\"Value\":{\"quality\":\"GOOD\",\"tid\":1709192048,\"timeStamp\":\"2024-02-29T07:34:08.966607900Z\",\"value\":3333},\"address\":\"2\",\"coefficient\":0.0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0.0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00004\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"},{\"Value\":{\"quality\":\"GOOD\",\"tid\":1709192048,\"timeStamp\":\"2024-02-29T07:34:08.964608500Z\",\"value\":2222},\"address\":\"1\",\"coefficient\":0.0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0.0,\"sort\":0,\"stationNo\":\"01030203_2003\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00002\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"}]}"; // JSON字符串
        String aimJson = "{\"dev\":{\"6327c0c4\":{\"a\":\"0\"}},\"time\":1000000000}"; // JSON字符串


        List<JsonMapping> jsonMappings = new ArrayList<>();
        jsonMappings.add(new JsonMapping("root._ti_me1", "root.Tid1", 4));
        jsonMappings.add(new JsonMapping("root.dev.6327c0c4", "root.Values[*].stationNo", 3));
        jsonMappings.add(new JsonMapping("root.dev.6327c0c4", "root.Values", 4));
        jsonMappings.add(new JsonMapping("root.dev.6327c0c4.a", "root.Values[*].address", 3));
        jsonMappings.add(new JsonMapping("root.dev.*.a", "root.Values[*].Value.value", 4));


        JsonTranferUtil jsonTranferUtil = null;
        try {
            jsonTranferUtil = new JsonTranferUtil(orgJson, aimJson, jsonMappings);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<CheckResult> checkResult = jsonTranferUtil.checkJsonMapping();

        System.out.println("******************结果 **********************");
        ObjectMapper mapper = new ObjectMapper();
        try {
            System.out.println(mapper.writeValueAsString(checkResult));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }


    }
}
