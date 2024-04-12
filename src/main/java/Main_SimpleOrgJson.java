import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 对象转换对象
 */
public class Main_SimpleOrgJson {

    public static void main(String[] args) {


        String orgJson = "{\"Tid\":1663550920,\"Values\":[{\"StationNo\":\"6327c0c4\",\"Values\":[{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"41-1\"},\"address\":\"41\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"},{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"42-2\"},\"address\":\"42\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"},{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"43-3\"},\"address\":\"43\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"}]},{\"StationNo\":\"6327c0c3\",\"Values\":[{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"31-1\"},\"address\":\"31\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"},{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"32-1\"},\"address\":\"32\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"}]}]}"; // JSON字符串
        String aimJson = "{\"dev\":{\"a\":{\"1\":\"111\"}},\"time\":166355111}"; // JSON字符串


        List<JsonMapping> jsonMappings = new ArrayList<>();
        jsonMappings.add(new JsonMapping("root.time", "root.Tid", 4));
//        jsonMappings.add(new JsonMapping("root.dev", "root.Values", 4));
        jsonMappings.add(new JsonMapping("root.dev.a", "root.Values[*].StationNo", 3));
//        jsonMappings.add(new JsonMapping("root.dev.a", "root.Values[*].Values", 4));
        jsonMappings.add(new JsonMapping("root.dev.a.1", "root.Values[*].Values[*].address", 3));
        jsonMappings.add(new JsonMapping("root.dev.a.1", "root.Values[*].Values[*].Value.value", 4));


        JsonTranferUtil jsonTranferUtil = null;
        String checkResult ="";
        try {
            jsonTranferUtil = new JsonTranferUtil(orgJson, aimJson, jsonMappings);
            checkResult = jsonTranferUtil.getSimpleOrgJson();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("******************结果 **********************");
        ObjectMapper mapper = new ObjectMapper();
        System.out.println(checkResult);


    }
}
