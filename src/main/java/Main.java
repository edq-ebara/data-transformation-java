import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 对象转换对象
 */
public class Main {

    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();

//        String orgJson = "{ \"dev\": { \"6327c0c4\": { \"41\": \"41-1\", \"42\": \"42-2\", \"43\": \"43-3\" },\"6327c0c3\": { \"31\": \"31-1\", \"32\": \"32-1\" } }, \"time\": 1663550920 }"; // JSON字符串
//        String aimJson = "{\"Tid\":1709715147,\"Values\":[{\"StationNo\":\"dev\",\"Values\":[{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":9220},\"address\":\"2\",\"coefficient\":0.0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0.0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"}]}]}"; // JSON字符串
        String orgJson = "{\"Tid\":1709715147,\"Values\":[{\"StationNo\":\"6327c0c4\",\"Values\":[{\"Value\":{\"value\":\"41-1\"},\"address\":\"6327c0c4_001\"},{\"Value\":{\"value\":\"42-2\"},\"address\":\"6327c0c4_002\"}]},{\"StationNo\":\"6327c0c3\",\"Values\":[{\"Value\":{\"value\":\"31-1\"},\"address\":\"6327c0c3_001\"}]}]}"; // JSON字符串
        String aimJson = "{\"dev\":{\"6327\":{\"1\":\"a\"}},\"time\":1663550920}"; // JSON字符串


        List<JsonMapping> jsonMappings = new ArrayList<>();

//        jsonMappings.add(new JsonMapping("root.dev", "root.Values[*].Values", 4, new JsonMappingOptions(2, 2, 3, 1)));
//        jsonMappings.add(new JsonMapping("root.dev.*", "root.Values[*].Values[*].address", 3, new JsonMappingOptions(2, 2, 1, 2)));
//
//        jsonMappings.add(new JsonMapping("root.dev.*", "root.Values[*].Values[*].Value.value", 4, new JsonMappingOptions(2, 2, 1, 2)));

        jsonMappings.add(new JsonMapping("root.dev.*.*", "#Time#", 4, new JsonMappingOptions(1, 1, 1, 1)));

        JsonTranferUtil jsonTranferUtil = null;
        String result ="";
        String resultError ="";

        try {
            jsonTranferUtil = new JsonTranferUtil(orgJson, aimJson, jsonMappings);
            result = jsonTranferUtil.tranJson();
            resultError = mapper.writeValueAsString( jsonTranferUtil.getTranErrors());
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("******************结果 **********************");

        System.out.println(result);
        System.out.println(resultError);

//        List<CheckResult> checkResult = jsonTranferUtil.checkJsonMapping();
//
//        System.out.println("******************结果 **********************");
//        ObjectMapper mapper = new ObjectMapper();
//        try {
//            System.out.println(mapper.writeValueAsString(checkResult));
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
    }
}
