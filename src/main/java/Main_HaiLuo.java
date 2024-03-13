import java.util.ArrayList;
import java.util.List;

public class Main_HaiLuo {

    public static void main(String[] args) {


        String orgJson = "{ \"dev\": { \"6327c0c4\": { \"41\": \"41-1\", \"42\": \"42-2\", \"43\": \"43-3\" },\"6327c0c3\": { \"31\": \"31-1\", \"32\": \"32-1\" } }, \"time\": 1663550920 }"; // JSON字符串
        String aimJson = "{\"Tid\":1709715147,\"Values\":[{\"StationNo\":\"dev\",\"Values\":[{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":9220},\"address\":\"2\",\"coefficient\":0.0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0.0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"}]}]}"; // JSON字符串


        List<JsonMapping> jsonMappings = new ArrayList<>();
        jsonMappings.add(new JsonMapping("root.Tid", "root.time", 4));
//        jsonMappings.add(new JsonMapping("root.Values", "root.dev", 4));
        jsonMappings.add(new JsonMapping("root.Values[*].StationNo", "root.dev.*", 2));
//        jsonMappings.add(new JsonMapping("root.Values[*].Values", "root.dev.*", 4));
        jsonMappings.add(new JsonMapping("root.Values[*].Values[*].address", "root.dev.*.*", 2));
        jsonMappings.add(new JsonMapping("root.Values[*].Values[*].Value.value", "root.dev.*.*", 4));


//
//        String orgJson = "{\"Tid\":1663550920,\"Values\":[{\"StationNo\":\"6327c0c4\",\"Values\":[{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"41-1\"},\"address\":\"41\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"},{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"42-2\"},\"address\":\"42\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"},{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"43-3\"},\"address\":\"43\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"}]},{\"StationNo\":\"6327c0c3\",\"Values\":[{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"31-1\"},\"address\":\"31\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"},{\"Value\":{\"quality\":\"GOOD\",\"timeStamp\":\"2024-03-06T08:52:25.792752300Z\",\"value\":\"32-1\"},\"address\":\"32\",\"coefficient\":0,\"contextCode\":\"\",\"dataFormat\":\"\",\"dataLayout\":\"\",\"dataType\":\"\",\"driverName\":\"\",\"id\":\"\",\"key\":\"\",\"maxValue\":\"9999\",\"minValue\":\"0\",\"offset\":0,\"sort\":0,\"stationNo\":\"dev\",\"switchKey\":\"\",\"tagCode\":\"IOTMqttDriver_AI00001\",\"tagName\":\"\",\"tagState\":\"\",\"troubleType\":\"\",\"unit\":\"\"}]}]}"; // JSON字符串
//        String aimJson = "{\"dev\":{\"a\":{\"1\":\"111\"}},\"time\":166355111}"; // JSON字符串
//
//
//        List<JsonMapping> jsonMappings = new ArrayList<>();
//        jsonMappings.add(new JsonMapping("root.time", "root.Tid", 4));
////        jsonMappings.add(new JsonMapping("root.dev", "root.Values", 4));
//        jsonMappings.add(new JsonMapping("root.dev.a", "root.Values[*].StationNo", 3));
////        jsonMappings.add(new JsonMapping("root.dev.a", "root.Values[*].Values", 4));
//        jsonMappings.add(new JsonMapping("root.dev.a.1", "root.Values[*].Values[*].address", 3));
//        jsonMappings.add(new JsonMapping("root.dev.a.1", "root.Values[*].Values[*].Value.value", 4));


        JsonTranferUtil jsonTranferUtil = null;
        try {
            jsonTranferUtil = new JsonTranferUtil(orgJson,aimJson,jsonMappings);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String tranJsonResult=jsonTranferUtil.tranJson();

        System.out.println("******************结果 **********************");
//        System.out.println("The Result:" + tranJsonResult.getIsSuccess()+"     "+tranJsonResult.getMsg());
//        System.out.println("The Result:" + tranJsonResult);




    }
}
